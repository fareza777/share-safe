package com.sharesafe.app.core.batch

import android.content.Context
import android.net.Uri
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.detect.SensitiveScanner
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.export.ImageExporter
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.render.AutoTrim
import com.sharesafe.app.core.render.BitmapSampler
import com.sharesafe.app.core.render.Bitmaps
import com.sharesafe.app.core.render.Bitmaps.sanitized
import com.sharesafe.app.core.render.RenderPipeline
import com.sharesafe.app.data.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatchItem(val uri: Uri, val displayName: String)

data class BatchProgress(val index: Int, val total: Int, val currentName: String = "")

data class BatchSummary(
    val total: Int,
    val saved: Int,
    val failed: Int,
    val regionsRedacted: Int = 0,
)

/**
 * One-tap pass over several screenshots: detect, redact with the current defaults and save each
 * result into Pictures/ShareSafe. Everything runs on device, one full-resolution bitmap at a time,
 * so a 20-screenshot batch cannot exhaust memory.
 */
object BatchRedactor {

    private const val ANALYSIS_MAX_DIM = 1600

    suspend fun redactToGallery(
        context: Context,
        items: List<BatchItem>,
        scanOptions: ScanOptions,
        exportOptions: ExportOptions,
        style: RedactionStyle,
        strength: Float,
        onProgress: (BatchProgress) -> Unit = {},
    ): BatchSummary {
        var saved = 0
        var failed = 0
        var regions = 0

        items.forEachIndexed { index, item ->
            onProgress(BatchProgress(index, items.size, item.displayName))
            val source = runCatching { BitmapLoader.loadSource(context, item.uri) }.getOrNull()
            if (source == null) {
                failed++
                return@forEachIndexed
            }
            try {
                regions += withContext(Dispatchers.Default) {
                    redactOne(
                        context = context,
                        source = source,
                        displayName = item.displayName,
                        scanOptions = scanOptions,
                        exportOptions = exportOptions,
                        style = style,
                        strength = strength,
                    )
                }
                saved++
            } catch (_: Throwable) {
                failed++
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        }
        onProgress(BatchProgress(items.size, items.size))
        return BatchSummary(
            total = items.size,
            saved = saved,
            failed = failed,
            regionsRedacted = regions,
        )
    }

    /** Redacts and stores one image; returns how many regions were hidden. */
    private suspend fun redactOne(
        context: Context,
        source: android.graphics.Bitmap,
        displayName: String,
        scanOptions: ScanOptions,
        exportOptions: ExportOptions,
        style: RedactionStyle,
        strength: Float,
    ): Int {
        val analysis = Bitmaps.scaledDown(source, ANALYSIS_MAX_DIM)
        try {
            val scan = SensitiveScanner.scan(analysis, scanOptions)
            // No device insets are known for a gallery image, so only the blank-edge heuristic runs.
            val crop = AutoTrim.estimate(
                sampler = BitmapSampler(source),
                config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
            )
            val result = RenderPipeline.renderDetailed(
                RenderPipeline.Request(
                    source = source,
                    crop = crop.rect.sanitized(source.width, source.height),
                    regions = scan.detections.map { it.bounds },
                    style = style,
                    strength = strength,
                    beautify = BeautifyConfig(),
                    targetMaxDim = 0,
                ),
            )
            try {
                ImageExporter.saveToGallery(context, result.bitmap, options = exportOptions)
                    ?: error("MediaStore rejected the export")
                // Batch exports land in History too, so the calendar reflects everything that was
                // ever produced without the user having to remember it.
                runCatching {
                    HistoryStore.instance.record(
                        bitmap = result.bitmap,
                        sourceName = displayName,
                        kinds = scan.detections.map { it.kind.name },
                        regionCount = scan.detections.size,
                        verified = false,
                    )
                }
            } finally {
                if (!result.bitmap.isRecycled) result.bitmap.recycle()
            }
            return scan.detections.size
        } finally {
            if (analysis !== source && !analysis.isRecycled) analysis.recycle()
        }
    }
}
