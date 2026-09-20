package com.sharesafe.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.sharesafe.app.core.history.HistoryCodec
import com.sharesafe.app.core.history.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local, offline record of what ShareSafe has exported: timestamp, what was hidden, and a private
 * copy of the **redacted** result. Keeping the safe pixels makes History useful (thumbnail, re-share,
 * re-open) while making a leak impossible — the original screenshot is never written.
 *
 * Storage is deliberately boring: one tab-separated line per entry in `filesDir/history.tsv` plus
 * one image per entry in `filesDir/history/`. No database, nothing outside the app sandbox, and the
 * whole index can be wiped in one call.
 */
/**
 * The store keeps an **application** context only (see [attach]), which is why the singleton field
 * below is safe; lint's static-field heuristic cannot see that.
 */
@SuppressLint("StaticFieldLeak")
class HistoryStore private constructor(private val context: Context) {

    private val indexFile = File(context.filesDir, INDEX_NAME)
    private val imageDirectory = File(context.filesDir, IMAGE_DIR_NAME)
    private val writeMutex = Mutex()

    private val _entries = MutableStateFlow(loadIndex())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    suspend fun record(
        bitmap: Bitmap,
        sourceName: String,
        kinds: List<String>,
        regionCount: Int,
        verified: Boolean,
        /** True when the export really has transparent pixels (rounded beautify corners). */
        transparent: Boolean = false,
    ): HistoryEntry? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) return@withContext null
        writeMutex.withLock {
            runCatching {
                imageDirectory.mkdirs()
                val id = "h${System.currentTimeMillis()}_${(0..0xFFFF).random().toString(16)}"
                val stored = storeCopy(bitmap, id, transparent)
                val entry = HistoryEntry(
                    id = id,
                    timestamp = System.currentTimeMillis(),
                    sourceName = sourceName.ifBlank { "screenshot" },
                    kinds = kinds.distinct(),
                    regionCount = regionCount,
                    fileName = stored.name,
                    mimeType = if (stored.name.endsWith("jpg")) "image/jpeg" else "image/png",
                    width = bitmap.width,
                    height = bitmap.height,
                    byteSize = stored.length(),
                    verified = verified,
                )
                val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
                persist(updated)
                pruneFiles(updated)
                updated
            }.getOrNull()?.let { updated ->
                _entries.value = updated
                updated.first()
            }
        }
    }

    fun delete(id: String) {
        val current = _entries.value
        val target = current.firstOrNull { it.id == id } ?: return
        runCatching { File(imageDirectory, target.fileName).delete() }
        val updated = current.filterNot { it.id == id }
        _entries.value = updated
        runCatching { persist(updated) }
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { indexFile.delete() }
        runCatching { imageDirectory.listFiles()?.forEach { it.delete() } }
    }

    /** The stored redacted copy, if it is still on disk. */
    fun fileFor(entry: HistoryEntry): File? =
        File(imageDirectory, entry.fileName).takeIf { it.isFile }

    /** Read grant URI for re-sharing a history item. */
    fun shareUri(entry: HistoryEntry): Uri? = fileFor(entry)?.let { file ->
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    /** Downsampled decode for the History grid; never throws. */
    suspend fun thumbnail(entry: HistoryEntry, maxDim: Int = 320): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileFor(entry) ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val longest = max(bounds.outWidth, bounds.outHeight)
                val sample = if (longest <= maxDim) 1 else {
                    var value = 1
                    while (longest / (value * 2) >= maxDim) value *= 2
                    value
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.getOrNull()
        }

    fun totalBytes(): Long = _entries.value.sumOf { it.byteSize }

    private fun storeCopy(bitmap: Bitmap, id: String, transparent: Boolean): File {
        imageDirectory.mkdirs()
        // PNG only when the pixels really need an alpha channel (rounded beautify corners).
        // Anything else goes to JPEG, because a screenshot is opaque by definition and a history
        // copy of 120 entries has no business filling the user's storage.
        val usePng = transparent
        val extension = if (usePng) "png" else "jpg"
        val file = File(imageDirectory, "$id.$extension")
        val scaled = scaleForStorage(bitmap)
        try {
            file.outputStream().use { stream ->
                if (usePng) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } else {
                    scaled.compress(Bitmap.CompressFormat.JPEG, HISTORY_QUALITY, stream)
                }
                stream.flush()
            }
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
        return file
    }

    private fun scaleForStorage(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_STORED_EDGE) return bitmap
        val ratio = MAX_STORED_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun pruneFiles(entries: List<HistoryEntry>) {
        val keep = entries.map { it.fileName }.toSet()
        imageDirectory.listFiles()?.forEach { file ->
            if (file.name !in keep) runCatching { file.delete() }
        }
    }

    private fun loadIndex(): List<HistoryEntry> = runCatching {
        if (!indexFile.isFile) return emptyList()
        HistoryCodec.decodeAll(indexFile.readText())
            .sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    private fun persist(entries: List<HistoryEntry>) {
        indexFile.writeText(HistoryCodec.encodeAll(entries.sortedByDescending { it.timestamp }))
    }

    companion object {
        private const val INDEX_NAME = "history.tsv"
        private const val IMAGE_DIR_NAME = "history"
        private const val MAX_ENTRIES = 120
        private const val MAX_STORED_EDGE = 1440
        private const val HISTORY_QUALITY = 88

        @Volatile
        private var instanceOrNull: HistoryStore? = null

        val instance: HistoryStore
            get() = checkNotNull(instanceOrNull) { "HistoryStore.attach() was not called" }

        fun attach(context: Context) {
            if (instanceOrNull == null) {
                synchronized(this) {
                    if (instanceOrNull == null) {
                        instanceOrNull = HistoryStore(context.applicationContext)
                    }
                }
            }
        }
    }
}
