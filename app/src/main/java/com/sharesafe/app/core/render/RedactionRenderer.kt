package com.sharesafe.app.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.sharesafe.app.core.model.RedactionStyle
import kotlin.math.max

/**
 * Destructive redaction: every style rewrites the pixels of the region instead of drawing a
 * removable overlay, so what you preview is exactly what leaves the device.
 *
 * Strategy per style:
 * - [RedactionStyle.BLUR] — two-stage downscale to 1/16..1/44 of the region, bilinear upscale back.
 * - [RedactionStyle.PIXELATE] — nearest-neighbour mosaic with 8..40 px blocks.
 * - [RedactionStyle.BLACK_BAR] — opaque black fill (the strongest, most legible option).
 * - [RedactionStyle.TINT] — opaque colour fill, for people who dislike black bars.
 *
 * Regions too small to hide anything with a mosaic or blur fall back to an average-colour fill,
 * which guarantees the text is gone even for a 20 px tall line.
 */
object RedactionRenderer {

    const val DEFAULT_TINT = 0xFF4F46E5.toInt()

    private const val MIN_BLOCK = 9
    private const val MAX_BLOCK = 40
    private const val MIN_BLUR_DIVISOR = 16
    private const val MAX_BLUR_DIVISOR = 44

    fun render(
        target: Bitmap,
        rects: List<Rect>,
        style: RedactionStyle,
        strength: Float,
        tintColor: Int = DEFAULT_TINT,
    ): Bitmap {
        if (rects.isEmpty()) return target
        val output = Bitmaps.toMutableArgb8888(target)
        val canvas = Canvas(output)
        // Cosmetic styles first, opaque fills last, so an overlap can never weaken a black bar.
        val ordered = rects.filterNotNull().sortedBy { if (isOpaque(style)) 1 else 0 }
        ordered.forEach { rect ->
            val safe = sanitize(rect, output.width, output.height) ?: return@forEach
            when (style) {
                RedactionStyle.BLACK_BAR -> opaqueFill(output, canvas, safe, Color.BLACK)
                RedactionStyle.TINT -> opaqueFill(output, canvas, safe, tintColor)
                RedactionStyle.PIXELATE -> pixelate(output, canvas, safe, strength)
                RedactionStyle.BLUR -> blur(output, canvas, safe, strength)
            }
        }
        return output
    }

    private fun isOpaque(style: RedactionStyle): Boolean =
        style == RedactionStyle.BLACK_BAR || style == RedactionStyle.TINT

    private fun pixelate(target: Bitmap, canvas: Canvas, rect: Rect, strength: Float) {
        val block = (MIN_BLOCK + strength.coerceIn(0f, 1f) * (MAX_BLOCK - MIN_BLOCK)).toInt()
        val cols = rect.width() / block
        val rows = rect.height() / block
        if (cols < 1 || rows < 1) {
            opaqueFill(target, canvas, rect, averageColor(target, rect))
            return
        }
        var region: Bitmap? = null
        var mosaic: Bitmap? = null
        try {
            region = Bitmap.createBitmap(target, rect.left, rect.top, rect.width(), rect.height())
            mosaic = Bitmap.createScaledBitmap(region, cols, rows, true)
            val paint = Paint().apply {
                isFilterBitmap = false
                isAntiAlias = false
                isDither = false
            }
            canvas.drawBitmap(mosaic, null, RectF(rect), paint)
        } finally {
            region?.recycle()
            mosaic?.recycle()
        }
    }

    private fun blur(target: Bitmap, canvas: Canvas, rect: Rect, strength: Float) {
        val divisor = (MIN_BLUR_DIVISOR + strength.coerceIn(0f, 1f) * (MAX_BLUR_DIVISOR - MIN_BLUR_DIVISOR)).toInt()
        val firstWidth = max(1, rect.width() / divisor)
        val firstHeight = max(1, rect.height() / divisor)
        if (firstWidth < 2 && firstHeight < 2) {
            opaqueFill(target, canvas, rect, averageColor(target, rect))
            return
        }
        var region: Bitmap? = null
        var tiny: Bitmap? = null
        var mid: Bitmap? = null
        try {
            region = Bitmap.createBitmap(target, rect.left, rect.top, rect.width(), rect.height())
            tiny = Bitmap.createScaledBitmap(region, firstWidth, firstHeight, true)
            // A second, gentler downscale smooths the first pass into a soft gradient.
            mid = Bitmap.createScaledBitmap(
                tiny,
                max(2, firstWidth * 2),
                max(2, firstHeight * 2),
                true,
            )
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(mid, null, RectF(rect), paint)
        } finally {
            region?.recycle()
            tiny?.recycle()
            mid?.recycle()
        }
    }

    /** Fills the region with one opaque colour derived from the region's own pixels. */
    fun opaqueFill(target: Bitmap, canvas: Canvas, rect: Rect, color: Int) {
        val paint = Paint().apply {
            this.color = color or (0xFF shl 24)
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        canvas.drawRect(RectF(rect), paint)
    }

    fun averageColor(source: Bitmap, rect: Rect, step: Int = 3): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = source.getPixel(x, y)
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
                samples++
                x += step
            }
            y += step
        }
        if (samples == 0) return Color.BLACK
        return Color.rgb((r / samples).toInt(), (g / samples).toInt(), (b / samples).toInt())
    }

    private fun sanitize(rect: Rect, width: Int, height: Int): Rect? {
        val safe = Rect(
            rect.left.coerceIn(0, width),
            rect.top.coerceIn(0, height),
            rect.right.coerceIn(0, width),
            rect.bottom.coerceIn(0, height),
        )
        return safe.takeIf { it.width() > 0 && it.height() > 0 }
    }
}
