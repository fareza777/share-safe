package com.sharesafe.app.core.export

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(val id: String, val extension: String, val mimeType: String) {
    PNG("png", "png", "image/png"),
    JPEG("jpeg", "jpg", "image/jpeg");

    companion object {
        fun fromId(id: String?): ExportFormat = entries.firstOrNull { it.id == id } ?: PNG
    }
}

/**
 * What the exported file looks like. Redaction is always baked into the pixels; these options only
 * decide the container and the size. Every export is re-encoded from scratch, so EXIF, GPS and
 * other embedded metadata from the original screenshot can never ride along.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PNG,
    /** Longest edge of the exported file, or 0 to keep the original size. */
    val maxLongEdge: Int = 0,
    val jpegQuality: Int = 92,
) {
    /** Downscales a copy when a size cap is set; returns the input when nothing has to change. */
    fun scaledFor(bitmap: Bitmap): Bitmap {
        if (maxLongEdge <= 0) return bitmap
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxLongEdge) return bitmap
        val ratio = maxLongEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        /** Offered long-edge caps in pixels; 0 keeps the original resolution. */
        val SIZE_CHOICES = listOf(0, 1080, 720)
        val DEFAULT = ExportOptions()
    }
}

/** A file ready to be handed to another app: where it is and what it is. */
data class SharePayload(
    val uri: android.net.Uri,
    val mimeType: String,
)
