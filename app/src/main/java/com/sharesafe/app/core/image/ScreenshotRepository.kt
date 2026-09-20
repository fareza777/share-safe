package com.sharesafe.app.core.image

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenshotItem(
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long,
    val width: Int,
    val height: Int,
)

/**
 * Reads the newest screenshots straight from MediaStore. Only used when the user grants gallery
 * access; the system photo picker path needs no permission at all.
 */
object ScreenshotRepository {

    private const val SOURCE_FOLDER = "Pictures/ShareSafe"

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Full access means the real media permission is granted. On Android 14+ the "allow all photos"
     * choice grants READ_MEDIA_IMAGES and deliberately leaves READ_MEDIA_VISUAL_USER_SELECTED
     * ungranted, so that one must never be part of the requirement.
     */
    fun hasFullAccess(context: Context): Boolean {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return isGranted(context, primary)
    }

    /** True when the user picked only a subset of photos (Android 14+ partial access). */
    fun canReadGallery(context: Context): Boolean =
        hasFullAccess(context) || hasPartialAccessOnly(context)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Android 14+ "select photos" grant: usable, but the list only shows the picked subset. */
    fun hasPartialAccessOnly(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
            !isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
    }

    suspend fun queryRecent(context: Context, limit: Int = 40): List<ScreenshotItem> =
        withContext(Dispatchers.IO) {
            if (!canReadGallery(context)) return@withContext emptyList()
            runCatching { query(context, limit) }.getOrDefault(emptyList())
        }

    private fun query(context: Context, limit: Int): List<ScreenshotItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val selection = buildString {
            append("(")
            append(MediaStore.Images.Media.RELATIVE_PATH).append(" LIKE ?")
            append(" OR ")
            append(MediaStore.Images.Media.BUCKET_DISPLAY_NAME).append(" LIKE ?")
            append(")")
            append(" AND ").append(MediaStore.Images.Media.RELATIVE_PATH).append(" NOT LIKE ?")
            append(" AND ").append(MediaStore.MediaColumns.IS_PENDING).append(" = 0")
        }
        val selectionArgs = arrayOf("%Screenshot%", "%Screenshot%", "%ShareSafe%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val results = ArrayList<ScreenshotItem>(limit)
        val resolver = context.contentResolver
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(collection, projection, args, null)
        } else {
            resolver.query(collection, projection, selection, selectionArgs, "$sortOrder LIMIT $limit")
        }
        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                results += ScreenshotItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(nameColumn).orEmpty(),
                    dateAddedSeconds = c.getLong(dateColumn),
                    width = c.getInt(widthColumn),
                    height = c.getInt(heightColumn),
                )
            }
        }
        return results.filterNot { it.uri.path?.contains(SOURCE_FOLDER) == true }
    }
}
