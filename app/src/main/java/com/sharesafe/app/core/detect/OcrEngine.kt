package com.sharesafe.app.core.detect

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sharesafe.app.core.model.NormRect

/**
 * Bundled, on-device Latin OCR (ML Kit). The recognizer is created once per process: the model ships
 * inside the APK, so the first scan is fast and nothing is ever downloaded.
 */
object OcrEngine {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Element-level spans with normalized boxes. Element granularity (rather than whole lines) is
     * what lets ShareSafe highlight only the phone number inside a sentence.
     */
    suspend fun recognizeSpans(bitmap: Bitmap): List<TextSpan> {
        if (bitmap.isRecycled) return emptyList()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()

        val spans = ArrayList<TextSpan>(64)
        result.textBlocks.forEach { block ->
            val lines = block.lines
            var emitted = false
            lines.forEach { line ->
                val elements = line.elements
                val boxed = elements.mapNotNull { element ->
                    element.boundingBox?.let { it to element.text }
                }
                if (boxed.isNotEmpty()) {
                    boxed.forEachIndexed { index, (box, text) ->
                        spans += TextSpan(
                            text = text,
                            bounds = box.toNormalized(width, height),
                            joinAfter = if (index == boxed.lastIndex) '\n' else ' ',
                        )
                    }
                    emitted = true
                } else {
                    line.boundingBox?.let { box ->
                        spans += TextSpan(line.text, box.toNormalized(width, height))
                        emitted = true
                    }
                }
            }
            if (!emitted) {
                block.boundingBox?.let { box ->
                    spans += TextSpan(block.text, box.toNormalized(width, height))
                }
            }
        }
        return spans
    }

    /** Raw text of the image; used by the export verifier. */
    suspend fun recognizeText(bitmap: Bitmap): String {
        if (bitmap.isRecycled) return ""
        return recognizeSpans(bitmap).joinToString("\n") { it.text }
    }

    private fun Rect.toNormalized(width: Float, height: Float): NormRect = NormRect(
        left = (left / width).coerceIn(0f, 1f),
        top = (top / height).coerceIn(0f, 1f),
        right = (right / width).coerceIn(0f, 1f),
        bottom = (bottom / height).coerceIn(0f, 1f),
    )
}
