package com.sharesafe.app.core.detect

import com.sharesafe.app.core.model.Detection
import com.sharesafe.app.core.model.DetectionOrigin
import com.sharesafe.app.core.model.NormRect

/**
 * One OCR element (or line/block fallback) with its normalized box. Bounds are relative to the
 * bitmap that was scanned, which the scanner keeps at a fixed scale of the source image.
 */
data class TextSpan(
    val text: String,
    val bounds: NormRect,
    val joinAfter: Char = '\n',
)

/**
 * Pure mapping stage: regex ranges over joined OCR text become redaction boxes over image
 * geometry. Kept free of Android types so the production mapper and the unit tests share it.
 */
object DetectionMapper {

    private const val PADDING_FRACTION = 0.10f
    private const val MIN_PADDING = 0.001f

    fun buildDetections(
        spans: List<TextSpan>,
        matches: List<SensitiveMatch>,
        includeLongNumbers: Boolean = false,
    ): List<Detection> {
        if (spans.isEmpty()) return emptyList()

        val joined = StringBuilder()
        val ranges = ArrayList<Pair<IntRange, TextSpan>>(spans.size)
        spans.forEachIndexed { index, span ->
            val start = joined.length
            joined.append(span.text)
            ranges += (start until joined.length) to span
            if (index != spans.lastIndex) joined.append(span.joinAfter)
        }

        val text = joined.toString()
        val effectiveMatches = if (matches.isNotEmpty() || !includeLongNumbers) {
            matches
        } else {
            SensitivePatterns.findMatches(text, includeLongNumbers = true)
        }
        if (effectiveMatches.isEmpty()) return emptyList()

        val raw = effectiveMatches.mapNotNull { match ->
            val hitSpans = ranges.filter { (range, _) -> rangesOverlap(match.range, range) }
            if (hitSpans.isEmpty()) return@mapNotNull null
            val bounds = hitSpans
                .map { it.second.bounds }
                .reduce { acc, next -> acc.union(next) }
                .padded(PADDING_FRACTION, MIN_PADDING)
            val label = text.substring(match.range.first, match.range.last + 1).trim()
            Detection(
                id = "",
                kind = match.kind,
                bounds = bounds,
                label = label,
                origin = DetectionOrigin.TEXT_PATTERN,
            )
        }

        return finalize(raw)
    }

    /**
     * Merges boxes of the same kind that overlap (fragmented OCR lines, duplicated regex hits),
     * then assigns stable ids for the UI.
     */
    fun finalize(detections: List<Detection>): List<Detection> {
        val merged = ArrayList<Detection>()
        detections
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .forEach { candidate ->
                val target = merged.indexOfFirst { existing ->
                    existing.kind == candidate.kind &&
                        existing.origin == candidate.origin &&
                        existing.bounds.intersects(candidate.bounds)
                }
                if (target >= 0) {
                    val existing = merged[target]
                    merged[target] = existing.copy(bounds = existing.bounds.union(candidate.bounds))
                } else {
                    merged += candidate
                }
            }

        val idCounters = HashMap<String, Int>()
        return merged.map { detection ->
            val key = detection.kind.name.lowercase()
            val next = (idCounters[key] ?: 0) + 1
            idCounters[key] = next
            detection.copy(id = "$key-$next")
        }
    }

    private fun rangesOverlap(a: IntRange, b: IntRange): Boolean =
        a.first <= b.last && b.first <= a.last
}
