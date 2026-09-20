package com.sharesafe.app.core.render

import com.sharesafe.app.core.model.CropConfig
import com.sharesafe.app.core.model.CropMethod
import com.sharesafe.app.core.model.CropResult
import com.sharesafe.app.core.model.IntRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only pixel access so the trim heuristics can run (and be tested) without a Bitmap.
 * Implementations return packed ARGB.
 */
interface PixelSampler {
    val width: Int
    val height: Int
    fun argb(x: Int, y: Int): Int
}

/**
 * Screenshot crop heuristics. Status and navigation bars are located from the device's own inset
 * sizes, then blank borders are found by walking rows/columns that stay close to the edge colour.
 * No ML, no Android types, fully unit-testable.
 */
object AutoTrim {

    private const val COLOR_TOLERANCE = 26
    /** Ceiling for trimming real system bars, derived from the device insets. */
    private const val MAX_TOP_FRACTION = 0.12f
    private const val MAX_BOTTOM_FRACTION = 0.10f
    /**
     * Ceiling for uniform-edge trimming. Deliberately tight: a coloured app header is uniform too,
     * and eating it would be a worse outcome than leaving a thin border behind.
     */
    private const val MAX_BLANK_FRACTION = 0.05f
    private const val MIN_KEEP_FRACTION = 0.4f
    private const val SAMPLE_STEP = 4

    fun estimate(
        sampler: PixelSampler,
        config: CropConfig,
        statusBarPx: Int = 0,
        navBarPx: Int = 0,
    ): CropResult {
        var left = 0
        var top = 0
        var right = sampler.width
        var bottom = sampler.height

        var usedSystemBars = false
        if (config.trimSystemBars) {
            val maxTop = (sampler.height * MAX_TOP_FRACTION).toInt()
            val maxBottom = (sampler.height * MAX_BOTTOM_FRACTION).toInt()
            val topTrim = statusBarPx.coerceIn(0, maxTop)
            val bottomTrim = navBarPx.coerceIn(0, maxBottom)
            if (topTrim > 0 || bottomTrim > 0) {
                top += topTrim
                bottom -= bottomTrim
                usedSystemBars = true
            }
        }

        var usedBlankEdges = false
        if (config.trimBlankEdges) {
            val blankTop = countUniformRows(sampler, top, bottom, left, right, fromTop = true)
            val blankBottom = countUniformRows(sampler, top, bottom, left, right, fromTop = false)
            val blankLeft = countUniformColumns(sampler, top, bottom, left, right, fromLeft = true)
            val blankRight = countUniformColumns(sampler, top, bottom, left, right, fromLeft = false)
            if (blankTop > 0 || blankBottom > 0 || blankLeft > 0 || blankRight > 0) {
                top += blankTop
                bottom -= blankBottom
                left += blankLeft
                right -= blankRight
                usedBlankEdges = true
            }
        }

        val minWidth = max(1, (sampler.width * MIN_KEEP_FRACTION).toInt())
        val minHeight = max(1, (sampler.height * MIN_KEEP_FRACTION).toInt())
        if (right - left < minWidth) {
            left = 0
            right = sampler.width
        }
        if (bottom - top < minHeight) {
            top = 0
            bottom = sampler.height
        }

        val method = when {
            usedSystemBars && usedBlankEdges -> CropMethod.BOTH
            usedSystemBars -> CropMethod.SYSTEM_BARS
            usedBlankEdges -> CropMethod.BLANK_EDGES
            else -> CropMethod.NONE
        }
        return CropResult(IntRect(left, top, right, bottom), method)
    }

    /**
     * Scales a device inset (status/nav bar height in screen pixels) onto an image that may have a
     * different width than the screen it was captured on.
     */
    fun scaleInset(insetPx: Int, imageWidth: Int, screenWidth: Int): Int {
        if (insetPx <= 0 || imageWidth <= 0 || screenWidth <= 0) return 0
        return (insetPx.toDouble() * imageWidth / screenWidth).toInt()
    }

    /**
     * Walks rows inwards from one edge while they are blank: a single flat colour that also matches
     * the band we already trimmed. A row of app content has text or images in it, so its samples
     * spread far from its own mean and the walk stops there — which is what keeps this heuristic
     * from eating a coloured app header.
     */
    private fun countUniformRows(
        sampler: PixelSampler,
        top: Int,
        bottom: Int,
        left: Int,
        right: Int,
        fromTop: Boolean,
    ): Int {
        val maxRows = ((bottom - top) * MAX_BLANK_FRACTION).toInt()
        if (maxRows <= 0) return 0
        var reference = -1
        var count = 0
        for (step in 0 until maxRows) {
            val y = if (fromTop) top + step else bottom - 1 - step
            if (y < top || y >= bottom) break
            val line = sampleLine(sampler, horizontal = true, fixed = y, start = left, end = right)
            if (!line.isBlank) break
            if (reference == -1) {
                reference = line.mean
            } else if (colorDistance(reference, line.mean) > COLOR_TOLERANCE) {
                break
            }
            reference = blend(reference, line.mean)
            count++
        }
        return count
    }

    private fun countUniformColumns(
        sampler: PixelSampler,
        top: Int,
        bottom: Int,
        left: Int,
        right: Int,
        fromLeft: Boolean,
    ): Int {
        val maxCols = ((right - left) * MAX_BLANK_FRACTION).toInt()
        if (maxCols <= 0) return 0
        var reference = -1
        var count = 0
        for (step in 0 until maxCols) {
            val x = if (fromLeft) left + step else right - 1 - step
            if (x < left || x >= right) break
            val line = sampleLine(sampler, horizontal = false, fixed = x, start = top, end = bottom)
            if (!line.isBlank) break
            if (reference == -1) {
                reference = line.mean
            } else if (colorDistance(reference, line.mean) > COLOR_TOLERANCE) {
                break
            }
            reference = blend(reference, line.mean)
            count++
        }
        return count
    }

    private class LineStats(val mean: Int, val isBlank: Boolean)

    /** Mean colour of a row/column plus whether it is flat enough to call blank. */
    private fun sampleLine(
        sampler: PixelSampler,
        horizontal: Boolean,
        fixed: Int,
        start: Int,
        end: Int,
    ): LineStats {
        var r = 0L
        var g = 0L
        var b = 0L
        var samples = 0
        var minLuma = Int.MAX_VALUE
        var maxLuma = Int.MIN_VALUE
        var index = start
        while (index < end) {
            val color = if (horizontal) sampler.argb(index, fixed) else sampler.argb(fixed, index)
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            r += red
            g += green
            b += blue
            val luma = (red * 30 + green * 59 + blue * 11) / 100
            if (luma < minLuma) minLuma = luma
            if (luma > maxLuma) maxLuma = luma
            samples++
            index += SAMPLE_STEP
        }
        if (samples == 0) return LineStats(0, false)
        val mean = pack((r / samples).toInt(), (g / samples).toInt(), (b / samples).toInt())
        return LineStats(mean, isBlank = maxLuma - minLuma <= COLOR_TOLERANCE)
    }

    private fun pack(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db
    }

    private fun blend(a: Int, b: Int): Int {
        fun channel(shift: Int): Int = (((a shr shift) and 0xFF) * 3 + ((b shr shift) and 0xFF)) / 4
        return pack(channel(16), channel(8), channel(0))
    }

    /** Average colour of the whole image, used by the beautifier's AUTO background. */
    fun averageColor(sampler: PixelSampler): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var samples = 0
        var y = 0
        val stepY = max(1, min(sampler.height, 64) / 64)
        val stepX = max(1, min(sampler.width, 64) / 64)
        while (y < sampler.height) {
            var x = 0
            while (x < sampler.width) {
                val color = sampler.argb(x, y)
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
                samples++
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) return 0xFF000000.toInt()
        return pack((r / samples).toInt(), (g / samples).toInt(), (b / samples).toInt())
    }
}
