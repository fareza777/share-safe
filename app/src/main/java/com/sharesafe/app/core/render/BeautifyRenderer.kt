package com.sharesafe.app.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.sharesafe.app.core.model.BackgroundStyle
import com.sharesafe.app.core.model.BeautifyConfig
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The "share-ready screenshot" wrapper: padding, rounded corners and a background derived from the
 * image itself. Runs at any resolution, so the exported file matches the exported preview.
 */
object BeautifyRenderer {

    /** Padding in pixels for a given content size; shared with the export geometry. */
    fun paddingFor(contentWidth: Int, contentHeight: Int, config: BeautifyConfig): Int {
        if (!config.enabled) return 0
        val longest = max(contentWidth, contentHeight)
        return (config.paddingFraction.coerceIn(0f, BeautifyConfig.MAX_PADDING) * longest).roundToInt()
    }

    fun render(content: Bitmap, config: BeautifyConfig, averageColor: Int): Bitmap {
        if (!config.enabled) return content

        val padding = paddingFor(content.width, content.height, config)
        val radius = (config.cornerFraction.coerceIn(0f, BeautifyConfig.MAX_CORNER) * content.width)
            .coerceAtLeast(0f)

        val width = content.width + padding * 2
        val height = content.height + padding * 2
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawBackground(canvas, width, height, config.background, averageColor)

        val rounded = Bitmaps.rounded(content, radius)
        // A shadow is what makes a screenshot read as a card rather than as a crop, but it has to
        // scale with the frame: at 2 px an inset it is invisible, at 40 px it would smudge.
        val shadowStrength = config.shadow.coerceIn(0f, 1f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (shadowStrength > 0.02f) {
                setShadowLayer(
                    max(1f, padding * (0.35f + 0.9f * shadowStrength)),
                    0f,
                    max(1f, padding * (0.12f + 0.40f * shadowStrength)),
                    shadowAlpha(shadowStrength),
                )
            }
        }
        if (radius > 0.5f) {
            canvas.drawRoundRect(
                RectF(
                    padding.toFloat(),
                    padding.toFloat(),
                    (padding + content.width).toFloat(),
                    (padding + content.height).toFloat(),
                ),
                radius,
                radius,
                shadow,
            )
        } else {
            canvas.drawRect(
                RectF(
                    padding.toFloat(),
                    padding.toFloat(),
                    (padding + content.width).toFloat(),
                    (padding + content.height).toFloat(),
                ),
                shadow,
            )
        }
        canvas.drawBitmap(rounded, padding.toFloat(), padding.toFloat(), null)
        if (rounded !== content) rounded.recycle()
        return output
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        style: BackgroundStyle,
        averageColor: Int,
    ) {
        val (top, bottom) = when (style) {
            // Keep the auto background dark enough that a light screenshot still pops.
            BackgroundStyle.AUTO -> shift(averageColor, 0.34f) to shift(averageColor, 0.5f)
            BackgroundStyle.SOLID -> {
                val flat = shift(averageColor, 0.42f)
                flat to flat
            }
            BackgroundStyle.NIGHT -> 0xFF0C0C16.toInt() to 0xFF1B1B30.toInt()
            BackgroundStyle.PAPER -> 0xFFF4F2FC.toInt() to 0xFFE5E1F6.toInt()
            BackgroundStyle.GRADIENT -> 0xFF5B5BF6.toInt() to 0xFF00C2A8.toInt()
        }
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                top,
                bottom,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }

    /** Converts a 0..1 shadow strength into an ARGB alpha byte. */
    private fun shadowAlpha(strength: Float): Int =
        ((0.18f + 0.55f * strength) * 255f).toInt().coerceIn(0, 255) shl 24

    /** Scales a colour towards black so auto backgrounds stay in a comfortable range. */
    private fun shift(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
