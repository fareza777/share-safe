package com.sharesafe.app.core.batch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.sharesafe.app.core.detect.ScanOptions
import com.sharesafe.app.core.detect.SensitiveScanner
import com.sharesafe.app.core.export.ExportOptions
import com.sharesafe.app.core.export.ImageExporter
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.FaceMaskStyle
import com.sharesafe.app.core.model.RedactionRegion
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.model.SensitiveKind
import com.sharesafe.app.core.render.AutoTrim
import com.sharesafe.app.core.render.BitmapSampler
import com.sharesafe.app.core.render.Bitmaps
import com.sharesafe.app.core.render.Bitmaps.sanitized
import com.sharesafe.app.core.render.RenderPipeline
import com.sharesafe.app.core.verify.SecureRender
import com.sharesafe.app.data.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One image in a batch run. */
data class BatchProtectItem(val uri: Uri, val displayName: String)

/** Where one image got to. */
sealed interface BatchItemStatus {
    data object Queued : BatchItemStatus
    data object Working : BatchItemStatus

    data class Done(
        val regionCount: Int,
        val verified: Boolean,
        /** True when verification still saw something after the repair rounds. */
        val needsReview: Boolean,
        val exportUri: Uri?,
        val kinds: List<SensitiveKind>,
    ) : BatchItemStatus

    data class Failed(val message: String) : BatchItemStatus
}

data class BatchProtectSummary(
    val total: Int,
    val done: Int,
    val failed: Int,
    val needsReview: Int,
    val regionsHidden: Int,
    /** Gallery URIs of everything that was written, ready to hand to a share sheet. */
    val exportUris: List<Uri>,
)

/**
 * Batch Protect.
 *
 * The one design rule here is **one image at a time, start to finish**. A batch used to feel like a
 * memory test because every result was held in RAM until the end; now each image is decoded,
 * scanned, redacted, verified, repaired, written to the gallery and reduced to a small thumbnail
 * before the next one is even opened. Peak memory is therefore one full-resolution bitmap plus a
 * handful of thumbnails, no matter how long the batch is — which is also why this runs sequentially
 * instead of parallel: two full-resolution screenshots at once is exactly the crash we are avoiding.
 *
 * Each image goes through the same [SecureRender] loop as a single-image export, so a batch cannot be
 * "less verified" than doing them by hand.
 */
object BatchProtectEngine {

    /** Analysis is done on a downscale; only the final render touches full resolution. */
    private const val ANALYSIS_MAX_DIM = 1600

    /** Thumbnails are what the UI keeps, so they are deliberately tiny. */
    const val THUMBNAIL_MAX_DIM = 320

    /**
     * How many images may be open at once. Exposed as a constant because "memory efficient" is a
     * testable property: it must stay 1.
     */
    const val MAX_CONCURRENT_IMAGES = 1

    suspend fun protect(
        context: Context,
        items: List<BatchProtectItem>,
        scanOptions: ScanOptions,
        style: RedactionStyle,
        strength: Float,
        exportOptions: ExportOptions,
        beautify: BeautifyConfig,
        faceMask: FaceMaskStyle,
        ignoreKinds: Set<SensitiveKind>,
        verify: Boolean = true,
        autoFix: Boolean = true,
        onUpdate: (index: Int, status: BatchItemStatus, thumbnail: Bitmap?) -> Unit,
    ): BatchProtectSummary {
        var done = 0
        var failed = 0
        var needsReview = 0
        var regions = 0
        val exportUris = ArrayList<Uri>(items.size)

        items.forEachIndexed { index, item ->
            onUpdate(index, BatchItemStatus.Working, null)

            val source = runCatching { BitmapLoader.loadSource(context, item.uri) }.getOrNull()
            if (source == null) {
                failed++
                onUpdate(index, BatchItemStatus.Failed("Could not open the image"), null)
                return@forEachIndexed
            }

            var thumbnail: Bitmap? = null
            try {
                val outcome = withContext(Dispatchers.Default) {
                    protectOne(
                        context = context,
                        source = source,
                        item = item,
                        scanOptions = scanOptions,
                        style = style,
                        strength = strength,
                        exportOptions = exportOptions,
                        beautify = beautify,
                        faceMask = faceMask,
                        ignoreKinds = ignoreKinds,
                        verify = verify,
                        autoFix = autoFix,
                    )
                }
                thumbnail = outcome.thumbnail
                regions += outcome.regionCount
                if (outcome.needsReview) needsReview++
                outcome.exportUri?.let { exportUris += it }
                done++
                onUpdate(
                    index,
                    BatchItemStatus.Done(
                        regionCount = outcome.regionCount,
                        verified = outcome.verified,
                        needsReview = outcome.needsReview,
                        exportUri = outcome.exportUri,
                        kinds = outcome.kinds,
                    ),
                    thumbnail,
                )
            } catch (error: Throwable) {
                failed++
                thumbnail?.let { if (!it.isRecycled) it.recycle() }
                onUpdate(index, BatchItemStatus.Failed(error.message ?: "failed"), null)
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        }

        return BatchProtectSummary(
            total = items.size,
            done = done,
            failed = failed,
            needsReview = needsReview,
            regionsHidden = regions,
            exportUris = exportUris,
        )
    }

    private class OneOutcome(
        val thumbnail: Bitmap?,
        val regionCount: Int,
        val verified: Boolean,
        val needsReview: Boolean,
        val exportUri: Uri?,
        val kinds: List<SensitiveKind>,
    )

    private suspend fun protectOne(
        context: Context,
        source: Bitmap,
        item: BatchProtectItem,
        scanOptions: ScanOptions,
        style: RedactionStyle,
        strength: Float,
        exportOptions: ExportOptions,
        beautify: BeautifyConfig,
        faceMask: FaceMaskStyle,
        ignoreKinds: Set<SensitiveKind>,
        verify: Boolean,
        autoFix: Boolean,
    ): OneOutcome {
        val analysis = Bitmaps.scaledDown(source, ANALYSIS_MAX_DIM)
        try {
            val scan = SensitiveScanner.scan(analysis, scanOptions)
            val active = scan.detections.filterNot { it.kind in ignoreKinds }
            val regions = active.map { it.bounds }
            val faces = active
                .filter { it.kind in RedactionRegion.FACE_LIKE_KINDS }
                .map { it.bounds }

            // No window insets are known for a gallery image, so only the blank-edge heuristic runs.
            val crop = AutoTrim.estimate(
                sampler = BitmapSampler(source),
                config = CropConfig(trimSystemBars = false, trimBlankEdges = true),
            )

            val outcome = SecureRender.renderVerified(
                request = RenderPipeline.Request(
                    source = source,
                    crop = crop.rect.sanitized(source.width, source.height),
                    regions = regions,
                    faceRegions = faces,
                    faceMask = faceMask,
                    style = style,
                    strength = strength,
                    beautify = beautify,
                    targetMaxDim = 0,
                ),
                ignoreKinds = ignoreKinds,
                verifyEnabled = verify,
                // A batch cannot be reviewed one by one, so a leftover that is still readable is
                // repaired here exactly as the single-image flow would.
                autoFix = autoFix && verify,
                checkFaces = scanOptions.detectFaces,
            )

            var thumbnail: Bitmap? = null
            var exportUri: Uri? = null
            try {
                val bitmap = outcome.bitmap
                exportUri = ImageExporter.saveToGalleryUri(context, bitmap, options = exportOptions)
                    ?: error("MediaStore rejected the export")
                // Batch exports land in History too, so the calendar reflects everything that was
                // ever produced without the user having to remember it.
                runCatching {
                    HistoryStore.instance.record(
                        bitmap = bitmap,
                        sourceName = item.displayName,
                        kinds = active.map { it.kind.name },
                        regionCount = active.size,
                        verified = outcome.verify?.isClean == true,
                    )
                }
                thumbnail = Bitmaps.scaledDown(bitmap, THUMBNAIL_MAX_DIM)
                    .let { if (it === bitmap) it.copy(Bitmap.Config.ARGB_8888, false) else it }
            } finally {
                if (!outcome.bitmap.isRecycled) outcome.bitmap.recycle()
            }

            return OneOutcome(
                thumbnail = thumbnail,
                regionCount = active.size,
                verified = outcome.verify?.isClean == true,
                needsReview = outcome.verify?.let { !it.isClean && it.completed } == true,
                exportUri = exportUri,
                kinds = active.map { it.kind }.distinct(),
            )
        } finally {
            if (analysis !== source && !analysis.isRecycled) analysis.recycle()
        }
    }
}
