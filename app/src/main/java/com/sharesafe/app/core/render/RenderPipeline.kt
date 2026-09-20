package com.sharesafe.app.core.render

import android.graphics.Bitmap
import android.graphics.Rect
import com.sharesafe.app.core.model.BeautifyConfig
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.RedactionStyle
import com.sharesafe.app.core.render.Bitmaps.sanitized
import com.sharesafe.app.core.render.Bitmaps.toAndroidRect
import com.sharesafe.app.core.render.Bitmaps.toPixels
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one place pixels are produced. Editor preview, share export and the verification step all go
 * through here, so they can never disagree about geometry.
 */
object RenderPipeline {

    data class Request(
        val source: Bitmap,
        /** Crop window in source pixels. */
        val crop: IntRect,
        /** Enabled redaction boxes in normalized source space. */
        val regions: List<NormRect>,
        val style: RedactionStyle,
        val strength: Float,
        val tintColor: Int = RedactionRenderer.DEFAULT_TINT,
        val beautify: BeautifyConfig = BeautifyConfig(),
        /** 0 means full resolution. */
        val targetMaxDim: Int = 0,
    )

    /** Rendered pixels plus where the screenshot content and the redactions ended up in them. */
    data class Result(
        val bitmap: Bitmap,
        /** Region of [bitmap] holding the cropped screenshot (excludes beautify padding). */
        val contentRect: Rect,
        /** Pixel rectangles that were redacted inside [bitmap]. */
        val redactedRects: List<Rect>,
    )

    fun render(request: Request): Bitmap = renderDetailed(request).bitmap

    /**
     * Crops, redacts and beautifies [Request.source]. The source bitmap is only ever read; the
     * returned bitmap is owned by the caller and should be recycled when replaced.
     */
    fun renderDetailed(request: Request): Result {
        val source = request.source
        val crop = request.crop.sanitized(source.width, source.height)

        val scale = if (request.targetMaxDim > 0) {
            val longest = max(crop.width, crop.height)
            if (longest > request.targetMaxDim) request.targetMaxDim.toFloat() / longest else 1f
        } else {
            1f
        }

        val base = buildBase(source, crop, scale)
        val averageColor = Bitmaps.averageColor(base)

        val rects = request.regions.mapNotNull { region ->
            region
                .toPixels(source.width, source.height, crop, scale)
                .toAndroidRect(base.width, base.height)
        }

        val redacted = if (rects.isEmpty()) {
            base
        } else {
            val out = RedactionRenderer.render(base, rects, request.style, request.strength, request.tintColor)
            if (out !== base) base.recycle()
            out
        }

        val beautified = BeautifyRenderer.render(redacted, request.beautify, averageColor)
        val padding = BeautifyRenderer.paddingFor(redacted.width, redacted.height, request.beautify)
        if (beautified !== redacted) redacted.recycle()

        return Result(
            bitmap = beautified,
            contentRect = Rect(
                padding,
                padding,
                padding + beautified.width - padding * 2,
                padding + beautified.height - padding * 2,
            ),
            redactedRects = rects.map { rect ->
                Rect(rect.left + padding, rect.top + padding, rect.right + padding, rect.bottom + padding)
            },
        )
    }

    /**
     * Produces the private working buffer for a render. It is always a fresh bitmap so the caller's
     * source image is never mutated by redaction, even when the crop/scale would be a no-op.
     */
    private fun buildBase(source: Bitmap, crop: IntRect, scale: Float): Bitmap {
        if (scale >= 1f) {
            val cropped = Bitmaps.crop(source, crop)
            return if (cropped === source) {
                Bitmaps.copyArgb8888(source)
            } else {
                Bitmaps.toMutableArgb8888(cropped)
            }
        }
        val targetWidth = max(1, (crop.width * scale).roundToInt())
        val targetHeight = max(1, (crop.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val out = Bitmaps.toMutableArgb8888(scaled)
        if (out !== scaled) scaled.recycle()
        return out
    }

    /** Preview scale that keeps the canvas responsive on large screenshots. */
    fun previewMaxDim(screenWidthPx: Int, screenHeightPx: Int): Int =
        min(1600, max(960, max(screenWidthPx, screenHeightPx)))
}
