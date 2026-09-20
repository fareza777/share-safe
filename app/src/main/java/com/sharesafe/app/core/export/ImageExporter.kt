package com.sharesafe.app.core.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * PNG or JPEG, chosen by the user. Both are written from decoded pixels, so the result never
 * inherits EXIF/GPS metadata from the source screenshot, and the redaction stays baked in.
 * Files written for sharing live in a cache subfolder that is pruned on every export.
 */
object ImageExporter {

    private const val SHARE_DIR = "share"
    private const val GALLERY_FOLDER = "ShareSafe"
    private const val GALLERY_FOLDER_LABEL = "Pictures/$GALLERY_FOLDER"
    private const val MAX_CACHED_FILES = 6

    /** How many images may be handed to one share sheet at a time. */
    const val MAX_SHARE_BATCH = 12

    suspend fun exportForShare(
        context: Context,
        bitmap: Bitmap,
        options: ExportOptions = ExportOptions.DEFAULT,
        timestamp: Long = System.currentTimeMillis(),
    ): SharePayload = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        prune(directory)
        val file = File(directory, "sharesafe_$timestamp.${options.format.extension}")
        file.outputStream().use { write(bitmap, it, options) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        SharePayload(uri = uri, mimeType = options.format.mimeType)
    }

    /**
     * Inserts into Pictures/ShareSafe through MediaStore; no permission needed on API 29+.
     * Returns the destination folder for the "Saved to …" message.
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        options: ExportOptions = ExportOptions.DEFAULT,
        displayName: String = "sharesafe_${System.currentTimeMillis()}",
    ): String? = saveToGalleryUri(context, bitmap, options, displayName)?.let { GALLERY_FOLDER_LABEL }

    /**
     * The same write, but returning the MediaStore URI — which is what a batch needs in order to
     * hand several finished images to a share sheet without keeping any of them in memory.
     */
    suspend fun saveToGalleryUri(
        context: Context,
        bitmap: Bitmap,
        options: ExportOptions = ExportOptions.DEFAULT,
        displayName: String = "sharesafe_${System.currentTimeMillis()}",
    ): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.${options.format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, options.format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { write(bitmap, it, options) }
                ?: error("Unable to open the destination stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun write(bitmap: Bitmap, stream: OutputStream, options: ExportOptions) {
        val scaled = options.scaledFor(bitmap)
        try {
            when (options.format) {
                ExportFormat.PNG -> {
                    if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        error("PNG encoding failed")
                    }
                }

                ExportFormat.JPEG -> {
                    // JPEG has no alpha channel: flatten onto white so transparent corners of a
                    // beautified card do not turn black.
                    val opaque = if (scaled.hasAlpha()) flatten(scaled) else scaled
                    try {
                        if (!opaque.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality.coerceIn(60, 100), stream)) {
                            error("JPEG encoding failed")
                        }
                    } finally {
                        if (opaque !== scaled) opaque.recycle()
                    }
                }
            }
            stream.flush()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun flatten(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return output
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.size <= MAX_CACHED_FILES) return
        files.drop(MAX_CACHED_FILES).forEach { runCatching { it.delete() } }
    }
}
