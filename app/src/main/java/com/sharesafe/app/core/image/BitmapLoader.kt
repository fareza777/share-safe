package com.sharesafe.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decoding entry point. ImageDecoder handles EXIF rotation for us (API 28+), and every decode is
 * capped so a 12 MP screenshot cannot blow up a low-RAM device. Allocator is forced to software
 * because redaction has to write into the pixels.
 */
object BitmapLoader {

    const val MAX_SOURCE_DIM = 3072

    suspend fun loadSource(context: Context, uri: Uri, maxDim: Int = MAX_SOURCE_DIM): Bitmap =
        withContext(Dispatchers.IO) {
            decode(context, uri, maxDim)
        }

    private fun decode(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val resolver = context.contentResolver
        val attempts = listOf(maxDim, maxDim / 2, 1080)
        var lastError: Throwable? = null
        attempts.forEach { cap ->
            try {
                val source = ImageDecoder.createSource(resolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val longest = max(width, height)
                    if (cap > 0 && longest > cap) {
                        val ratio = cap.toFloat() / longest
                        decoder.setTargetSize(
                            (width * ratio).roundToInt().coerceAtLeast(1),
                            (height * ratio).roundToInt().coerceAtLeast(1),
                        )
                    }
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Unable to decode $uri")
    }

    // --- Thumbnails -------------------------------------------------------------------------

    private val thumbnailCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun loadThumbnail(context: Context, uri: Uri, maxDim: Int = 320): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$uri@$maxDim"
            thumbnailCache.get(key)?.let { return@withContext it }
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val width = info.size.width
                    val height = info.size.height
                    val longest = max(width, height)
                    if (longest > maxDim) {
                        val ratio = maxDim.toFloat() / longest
                        decoder.setTargetSize(
                            (width * ratio).roundToInt().coerceAtLeast(1),
                            (height * ratio).roundToInt().coerceAtLeast(1),
                        )
                    }
                }
            }.getOrNull()?.also { thumbnailCache.put(key, it) }
        }

    fun clearThumbnailCache() {
        thumbnailCache.evictAll()
    }
}
