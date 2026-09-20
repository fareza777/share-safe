package com.sharesafe.app.core.verify

import com.sharesafe.app.core.model.IntRect
import com.sharesafe.app.core.model.NormRect

/**
 * Turns "the verifier still saw something here" into "hide this too".
 *
 * The verifier reports leftovers in the coordinate space of the **exported** bitmap, while
 * redactions live in the coordinate space of the **source** image. Between the two sit the crop and
 * the beautifier padding, so the mapping is exact rather than approximate:
 *
 * 1. exported pixels → position inside the rendered content rect (0..1 of the cropped image)
 * 2. cropped position → source pixels via the crop offset and size
 * 3. source pixels → source-normalized rectangle
 *
 * Pure Kotlin on purpose: this is the part of the automatic repair path that must be provable in a
 * JVM unit test, because getting it wrong would silently hide the wrong pixels.
 */
object AutoFixPlanner {

    /** Grown by a fraction of its own size so the repaired box has a margin to spare. */
    private const val DEFAULT_PADDING = 0.02f

    /**
     * Returns the extra rectangles that should be redacted, in source-normalized space. Additions
     * already covered by [existing] redactions (or covering one) are dropped, so re-running the
     * repair cannot stack boxes on top of each other.
     */
    fun plan(
        leftovers: List<NormRect>,
        content: IntRect,
        crop: IntRect,
        sourceWidth: Int,
        sourceHeight: Int,
        exportedWidth: Int,
        exportedHeight: Int,
        paddingFraction: Float = DEFAULT_PADDING,
        existing: List<NormRect> = emptyList(),
    ): List<NormRect> {
        if (leftovers.isEmpty()) return emptyList()
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()
        if (exportedWidth <= 0 || exportedHeight <= 0) return emptyList()
        if (crop.width <= 0 || crop.height <= 0) return emptyList()

        val usableContent = if (content.width > 0 && content.height > 0) {
            content
        } else {
            IntRect(0, 0, exportedWidth, exportedHeight)
        }

        val planned = ArrayList<NormRect>(leftovers.size)
        leftovers.forEach { leftover ->
            val mapped = mapOne(
                leftover = leftover,
                content = usableContent,
                crop = crop,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                exportedWidth = exportedWidth,
                exportedHeight = exportedHeight,
            ) ?: return@forEach

            val padded = mapped.padded(paddingFraction).clampToUnit()
            if (padded.isEmpty()) return@forEach
            // Anything outside the crop is not in the exported image at all.
            if (!padded.intersects(NormRect(0f, 0f, 1f, 1f))) return@forEach

            val duplicate = existing.any { it.containsRect(padded) || padded.containsRect(it) }
            val alreadyPlanned = planned.any { it.containsRect(padded) || padded.containsRect(it) }
            if (!duplicate && !alreadyPlanned) planned += padded
        }
        return planned
    }

    private fun mapOne(
        leftover: NormRect,
        content: IntRect,
        crop: IntRect,
        sourceWidth: Int,
        sourceHeight: Int,
        exportedWidth: Int,
        exportedHeight: Int,
    ): NormRect? {
        val contentWidth = content.width.toFloat()
        val contentHeight = content.height.toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) return null

        val leftPx = leftover.left * exportedWidth
        val rightPx = leftover.right * exportedWidth
        val topPx = leftover.top * exportedHeight
        val bottomPx = leftover.bottom * exportedHeight

        // Position inside the rendered content, 0..1 — this is the cropped image's own space.
        val relLeft = (leftPx - content.left) / contentWidth
        val relRight = (rightPx - content.left) / contentWidth
        val relTop = (topPx - content.top) / contentHeight
        val relBottom = (bottomPx - content.top) / contentHeight

        // Back to source pixels, then to source-normalized.
        val sourceLeft = crop.left + relLeft * crop.width
        val sourceRight = crop.left + relRight * crop.width
        val sourceTop = crop.top + relTop * crop.height
        val sourceBottom = crop.top + relBottom * crop.height

        val mapped = NormRect(
            left = sourceLeft / sourceWidth,
            top = sourceTop / sourceHeight,
            right = sourceRight / sourceWidth,
            bottom = sourceBottom / sourceHeight,
        ).clampToUnit()

        // A repair box that ends up degenerate (fully outside the crop) is not worth adding; a
        // single pixel of overlap is still worth it, so clamp to the crop instead of dropping.
        val clamped = intersect(mapped, cropToNorm(crop, sourceWidth, sourceHeight)) ?: return null
        return clamped
    }

    private fun cropToNorm(crop: IntRect, sourceWidth: Int, sourceHeight: Int): NormRect = NormRect(
        left = crop.left.toFloat() / sourceWidth,
        top = crop.top.toFloat() / sourceHeight,
        right = crop.right.toFloat() / sourceWidth,
        bottom = crop.bottom.toFloat() / sourceHeight,
    ).clampToUnit()

    private fun intersect(a: NormRect, b: NormRect): NormRect? {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right - left <= 0f || bottom - top <= 0f) return null
        return NormRect(left, top, right, bottom)
    }
}
