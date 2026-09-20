package com.sharesafe.app.core.detect

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.sharesafe.app.core.model.NormRect

/** Bundled ML Kit face detection. Boxes are padded 18% so hair and chin are covered too. */
object FaceEngine {

    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build(),
        )
    }

    suspend fun detect(bitmap: Bitmap): List<NormRect> {
        if (bitmap.isRecycled) return emptyList()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        return runCatching {
            detector.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().map { face ->
                val box = face.boundingBox
                NormRect(
                    left = (box.left / width).coerceIn(0f, 1f),
                    top = (box.top / height).coerceIn(0f, 1f),
                    right = (box.right / width).coerceIn(0f, 1f),
                    bottom = (box.bottom / height).coerceIn(0f, 1f),
                ).padded(0.18f)
            }
        }.getOrDefault(emptyList())
    }
}
