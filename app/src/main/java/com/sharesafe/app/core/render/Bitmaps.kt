package com.sharesafe.app.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [PixelSampler] over a Bitmap that reads one row at a time. Copying a full 12 MP pixel array just
 * to measure edge colours would cost tens of megabytes, which matters on low-RAM devices.
 */
class BitmapSampler(private val bitmap: Bitmap) : PixelSampler {
    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    private val rowBuffer = IntArray(width)
    private var cachedRow = -1

    override fun argb(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        if (cachedRow != y) {
            bitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
            cachedRow = y
        }
        return rowBuffer[x]
    }
}

object Bitmaps {

    /** Copies into a mutable ARGB_8888 bitmap when needed — redaction writes into the pixels. */
    fun toMutableArgb8888(source: Bitmap): Bitmap =
        if (source.config == Bitmap.Config.ARGB_8888 && source.isMutable) {
            source
        } else {
            copyArgb8888(source)
        }

    /**
     * Always allocates a fresh mutable ARGB_8888 buffer. Redaction is destructive by design, so the
     * source image must never be the buffer that gets written into: turning a region back on after
     * turning it off has to reveal the original pixels again.
     */
    fun copyArgb8888(source: Bitmap): Bitmap =
        source.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Unable to copy bitmap into a mutable buffer")

    /** Downscales so the longest edge is at most [maxDim]; returns the input when already small. */
    fun scaledDown(source: Bitmap, maxDim: Int): Bitmap {
        if (maxDim <= 0) return source
        val longest = max(source.width, source.height)
        if (longest <= maxDim) return source
        val ratio = maxDim.toFloat() / longest
        val targetWidth = max(1, (source.width * ratio).roundToInt())
        val targetHeight = max(1, (source.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    fun crop(source: Bitmap, rect: IntRect): Bitmap {
        val safe = rect.sanitized(source.width, source.height)
        if (safe.left == 0 && safe.top == 0 && safe.right == source.width && safe.bottom == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, safe.left, safe.top, safe.width, safe.height)
    }

    fun averageColor(source: Bitmap): Int = AutoTrim.averageColor(BitmapSampler(source))

    /** Converts a normalized source-space rectangle into pixels of a cropped/scaled target. */
    fun NormRect.toPixels(
        sourceWidth: Int,
        sourceHeight: Int,
        crop: IntRect,
        scale: Float = 1f,
    ): RectF {
        val left = (this.left * sourceWidth - crop.left) * scale
        val top = (this.top * sourceHeight - crop.top) * scale
        val right = (this.right * sourceWidth - crop.left) * scale
        val bottom = (this.bottom * sourceHeight - crop.top) * scale
        return RectF(
            left.coerceIn(0f, crop.width * scale),
            top.coerceIn(0f, crop.height * scale),
            right.coerceIn(0f, crop.width * scale),
            bottom.coerceIn(0f, crop.height * scale),
        )
    }

    fun RectF.toAndroidRect(width: Int, height: Int): Rect? {
        val safe = Rect(
            left.roundToInt().coerceIn(0, width),
            top.roundToInt().coerceIn(0, height),
            right.roundToInt().coerceIn(0, width),
            bottom.roundToInt().coerceIn(0, height),
        )
        return safe.takeIf { it.width() > 0 && it.height() > 0 }
    }

    fun IntRect.sanitized(maxWidth: Int, maxHeight: Int): IntRect {
        val left = left.coerceIn(0, max(0, maxWidth - 1))
        val top = top.coerceIn(0, max(0, maxHeight - 1))
        val right = right.coerceIn(left + 1, max(left + 1, maxWidth))
        val bottom = bottom.coerceIn(top + 1, max(top + 1, maxHeight))
        return IntRect(left, top, right, bottom)
    }

    fun fill(bitmap: Bitmap, color: Int) {
        Canvas(bitmap).drawColor(color)
    }

    /** Rounded-corner bitmap, used for the beautify wrapper and preview corners. */
    fun rounded(bitmap: Bitmap, radiusPx: Float): Bitmap {
        if (radiusPx <= 0.5f) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        paint.xfermode = null
        return output
    }

    fun longestEdge(bitmap: Bitmap): Int = max(bitmap.width, bitmap.height)

    fun describe(bitmap: Bitmap): String = "${bitmap.width}x${bitmap.height}"

    fun minEdge(bitmap: Bitmap): Int = min(bitmap.width, bitmap.height)
}
