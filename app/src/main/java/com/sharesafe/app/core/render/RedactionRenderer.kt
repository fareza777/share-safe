package com.sharesafe.app.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.sharesafe.app.core.model.FaceMaskStyle
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
 *
 * Faces can be drawn through a feathered oval instead of their bounding box ([FaceMaskStyle]). Only
 * the *shape of the mask* changes — the same pixels are destroyed either way — because a hard
 * rectangle around a face tells everyone exactly what was hidden, which is the opposite of the
 * point. Rectangles are still used for the opaque styles: an oval black bar looks like a sticker,
 * and there would be nothing soft about it anyway.
 */
object RedactionRenderer {

    const val DEFAULT_TINT = 0xFF4F46E5.toInt()

    /** Fully opaque out to 80% of the mask radius, then fading to nothing at the edge. */
    private const val FEATHER_CORE = 0xFFFFFFFF.toInt()

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
        maskRects: List<Rect> = emptyList(),
        maskStyle: FaceMaskStyle = FaceMaskStyle.BOX,
    ): Bitmap {
        if (rects.isEmpty() && maskRects.isEmpty()) return target
        val output = Bitmaps.toMutableArgb8888(target)
        val canvas = Canvas(output)
        // An oval only makes sense for the pixel-destroying styles; see the class comment.
        val softMasks = maskStyle == FaceMaskStyle.SOFT_OVAL &&
            (style == RedactionStyle.BLUR || style == RedactionStyle.PIXELATE)

        // Cosmetic styles first, opaque fills last, so an overlap can never weaken a black bar.
        val ordered = (rects + if (softMasks) emptyList() else maskRects)
            .filterNotNull()
            .sortedBy { if (isOpaque(style)) 1 else 0 }
        ordered.forEach { rect -> drawOne(output, canvas, rect, style, strength, tintColor) }

        if (softMasks) {
            maskRects.forEach { rect ->
                val safe = sanitize(rect, output.width, output.height) ?: return@forEach
                softMask(output, canvas, safe, style, strength)
            }
        }
        return output
    }

    private fun drawOne(
        target: Bitmap,
        canvas: Canvas,
        rect: Rect,
        style: RedactionStyle,
        strength: Float,
        tintColor: Int,
    ) {
        val safe = sanitize(rect, target.width, target.height) ?: return
        when (style) {
            RedactionStyle.BLACK_BAR -> opaqueFill(target, canvas, safe, Color.BLACK)
            RedactionStyle.TINT -> opaqueFill(target, canvas, safe, tintColor)
            RedactionStyle.PIXELATE -> pixelate(target, canvas, safe, strength)
            RedactionStyle.BLUR -> blur(target, canvas, safe, strength)
        }
    }

    /**
     * Destroys the pixels of [rect] with the current style, then fades the result out towards the
     * edges of an inscribed ellipse. The fade is a radial alpha mask applied with `DST_IN`, which is
     * why it is one composited bitmap rather than a clipped draw: a clipped oval would leave a hard,
     * readable edge behind.
     */
    private fun softMask(
        target: Bitmap,
        canvas: Canvas,
        rect: Rect,
        style: RedactionStyle,
        strength: Float,
    ) {
        val width = rect.width()
        val height = rect.height()
        if (width < 4 || height < 4) {
            drawOne(target, canvas, rect, style, strength, DEFAULT_TINT)
            return
        }

        var patch: Bitmap? = null
        var feathered: Bitmap? = null
        try {
            patch = redactedPatch(target, rect, style, strength) ?: return
            feathered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val patchCanvas = Canvas(feathered)
            patchCanvas.drawBitmap(patch, 0f, 0f, null)

            val radius = (max(width, height) / 2f) * 1.06f
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = RadialGradient(
                    width / 2f,
                    height / 2f,
                    radius,
                    intArrayOf(FEATHER_CORE, FEATHER_CORE, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.80f, 1f),
                    Shader.TileMode.CLAMP,
                )
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            // Stretch the circular gradient vertically so the feather follows the box's aspect.
            val verticalScale = height.toFloat() / width.toFloat()
            patchCanvas.save()
            patchCanvas.scale(1f, verticalScale, 0f, 0f)
            patchCanvas.drawRect(0f, 0f, width.toFloat(), width.toFloat(), maskPaint)
            patchCanvas.restore()

            canvas.drawBitmap(feathered, rect.left.toFloat(), rect.top.toFloat(), null)
        } finally {
            patch?.let { if (!it.isRecycled) it.recycle() }
            feathered?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    /** A standalone, already-redacted copy of one rectangle — the input to the oval mask. */
    private fun redactedPatch(
        source: Bitmap,
        rect: Rect,
        style: RedactionStyle,
        strength: Float,
    ): Bitmap? {
        val width = rect.width()
        val height = rect.height()
        if (width <= 0 || height <= 0) return null
        var region: Bitmap? = null
        return try {
            region = Bitmap.createBitmap(source, rect.left, rect.top, width, height)
            val patch = region.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val local = Rect(0, 0, width, height)
            when (style) {
                RedactionStyle.PIXELATE -> pixelate(patch, Canvas(patch), local, strength)
                RedactionStyle.BLUR -> blur(patch, Canvas(patch), local, strength)
                else -> opaqueFill(patch, Canvas(patch), local, averageColor(source, rect))
            }
            patch
        } finally {
            region?.let { if (!it.isRecycled) it.recycle() }
        }
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
