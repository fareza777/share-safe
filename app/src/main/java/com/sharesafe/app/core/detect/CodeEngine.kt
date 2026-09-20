package com.sharesafe.app.core.detect

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sharesafe.app.core.model.NormRect

data class CodeHit(
    val bounds: NormRect,
    val label: String,
    /** True when the decoded payload is a payment request, credential or personal data. */
    val containsPersonalData: Boolean,
)

/** Bundled ML Kit barcode scanning: QR codes and 1D barcodes are hidden by default. */
object CodeEngine {

    private val scanner: BarcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(bitmap: Bitmap): List<CodeHit> {
        if (bitmap.isRecycled) return emptyList()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val barcodes = scanner.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
        return barcodes.mapNotNull { barcode ->
            val box = barcode.boundingBox ?: return@mapNotNull null
            val bounds = NormRect(
                left = (box.left / width).coerceIn(0f, 1f),
                top = (box.top / height).coerceIn(0f, 1f),
                right = (box.right / width).coerceIn(0f, 1f),
                bottom = (box.bottom / height).coerceIn(0f, 1f),
            )
            val payload = CodePayloadClassifier.classify(
                format = codeFormatOf(barcode.valueType),
                rawValue = barcode.rawValue,
            )
            CodeHit(bounds, describe(barcode, payload), payload.containsPersonalData)
        }
    }

    /**
     * Human label for the detection list. Only the *kind* of payload is surfaced — QRIS payment,
     * WiFi credentials, a link with personal data — never the decoded string itself, which stays
     * in memory for the duration of the scan.
     */
    private fun describe(barcode: Barcode, payload: CodePayload): String {
        val shape = if (barcode.format == Barcode.FORMAT_QR_CODE) "QR" else "Barcode"
        return "$shape · ${CodePayloadClassifier.label(payload)}"
    }

    private fun codeFormatOf(valueType: Int): CodeFormat = when (valueType) {
        Barcode.TYPE_URL -> CodeFormat.URL
        Barcode.TYPE_WIFI -> CodeFormat.WIFI
        Barcode.TYPE_EMAIL -> CodeFormat.EMAIL
        Barcode.TYPE_PHONE -> CodeFormat.PHONE
        Barcode.TYPE_SMS -> CodeFormat.SMS
        Barcode.TYPE_GEO -> CodeFormat.GEO
        Barcode.TYPE_CONTACT_INFO -> CodeFormat.CONTACT
        Barcode.TYPE_CALENDAR_EVENT -> CodeFormat.CALENDAR
        else -> CodeFormat.OTHER
    }
}
