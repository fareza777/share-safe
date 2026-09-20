package com.sharesafe.app.core.verify

import android.graphics.Bitmap
import android.graphics.Rect
import com.sharesafe.app.core.detect.CodeEngine
import com.sharesafe.app.core.detect.DetectionMapper
import com.sharesafe.app.core.detect.FaceEngine
import com.sharesafe.app.core.detect.OcrEngine
import com.sharesafe.app.core.detect.SensitivePatterns
import com.sharesafe.app.core.model.NormRect
import com.sharesafe.app.core.model.SensitiveKind

data class VerifyResult(
    val leftoverKinds: List<SensitiveKind>,
    /**
     * Where the leftovers are, normalized to the exported bitmap. This is what makes automatic
     * repair possible: the caller maps these back into source space, hides them and verifies again.
     */
    val leftoverBounds: List<NormRect> = emptyList(),
    /** False when verification could not run (for example the recognizer failed) — never a pass. */
    val completed: Boolean,
    /** Which checks actually ran, so the UI can say what the verdict covers. */
    val checkedCodes: Boolean = false,
    val checkedFaces: Boolean = false,
    val failureReason: String? = null,
) {
    val leftoverCount: Int get() = leftoverKinds.size
    val isClean: Boolean get() = completed && leftoverKinds.isEmpty()
}

/**
 * Post-export safety net: the exported PNG is scanned again — text, barcodes and faces — and
 * anything that lives **outside** the redacted rectangles is reported, together with where it is.
 * A QR tile or a face the user forgot to hide shows up here instead of in someone else's chat.
 *
 * [ignoreKinds] carries the categories the user deliberately switched off in the editor, so a
 * disabled heuristic is not reported as a failure and is not auto-repaired either.
 */
object RedactionVerifier {

    suspend fun verify(
        exported: Bitmap,
        redactedRects: List<Rect>,
        width: Int = exported.width,
        height: Int = exported.height,
        checkCodes: Boolean = true,
        checkFaces: Boolean = true,
        ignoreKinds: Set<SensitiveKind> = emptySet(),
    ): VerifyResult = try {
        val leftovers = ArrayList<Pair<SensitiveKind, NormRect>>()

        val spans = OcrEngine.recognizeSpans(exported)
        val detections = DetectionMapper.buildDetections(
            spans = spans,
            matches = SensitivePatterns.findMatches(spans.joinToString(" ") { it.text }),
        )
        detections
            .filterNot { detection -> covered(redactedRects, detection.bounds, width, height) }
            .forEach { leftovers += it.kind to it.bounds }

        var codesChecked = false
        if (checkCodes) {
            val codes = runCatching { CodeEngine.scan(exported) }.getOrNull()
            if (codes != null) {
                codesChecked = true
                codes
                    .filterNot { hit -> covered(redactedRects, hit.bounds, width, height) }
                    .forEach { leftovers += SensitiveKind.QR to it.bounds }
            }
        }

        var facesChecked = false
        if (checkFaces) {
            val faces = runCatching { FaceEngine.detect(exported) }.getOrNull()
            if (faces != null) {
                facesChecked = true
                // A blurred or pixelated face can still be detected; it only counts as a leftover
                // when its centre sits outside every redacted rectangle.
                faces
                    .filterNot { bounds ->
                        val cx = ((bounds.left + bounds.right) / 2f * width).toInt()
                        val cy = ((bounds.top + bounds.bottom) / 2f * height).toInt()
                        redactedRects.any { it.contains(cx, cy) }
                    }
                    .forEach { bounds -> leftovers += SensitiveKind.FACE to bounds }
            }
        }

        val relevant = leftovers.filterNot { (kind, _) -> kind in ignoreKinds }
        VerifyResult(
            leftoverKinds = relevant.map { it.first }.distinct(),
            leftoverBounds = relevant.map { it.second },
            completed = true,
            checkedCodes = codesChecked,
            checkedFaces = facesChecked,
        )
    } catch (error: Throwable) {
        VerifyResult(emptyList(), completed = false, failureReason = error.message)
    }

    private fun covered(
        redactedRects: List<Rect>,
        bounds: NormRect,
        width: Int,
        height: Int,
    ): Boolean {
        val box = Rect(
            (bounds.left * width).toInt(),
            (bounds.top * height).toInt(),
            (bounds.right * width).toInt(),
            (bounds.bottom * height).toInt(),
        )
        return redactedRects.any { redacted -> covers(redacted, box) }
    }

    /** A match counts as hidden only when a redacted rectangle fully covers it. */
    private fun covers(redacted: Rect, detected: Rect): Boolean {
        val margin = 2
        return detected.left >= redacted.left - margin &&
            detected.top >= redacted.top - margin &&
            detected.right <= redacted.right + margin &&
            detected.bottom <= redacted.bottom + margin
    }
}
