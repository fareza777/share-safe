package com.sharesafe.app.core.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Sharing is a plain ACTION_SEND with a read grant on one cached file. Nothing is copied to the
 * clipboard and no metadata about the share target is stored.
 */
object ShareHelper {

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    const val TELEGRAM_PACKAGE = "org.telegram.messenger"

    fun buildSendIntent(payload: SharePayload, caption: String? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = payload.mimeType
            putExtra(Intent.EXTRA_STREAM, payload.uri)
            if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(null, payload.uri)
        }

    fun shareSheetIntent(
        context: Context,
        payload: SharePayload,
        title: String,
        caption: String? = null,
    ): Intent {
        val send = buildSendIntent(payload, caption)
        return Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Sends straight to one package when the user taps a dedicated button. */
    fun directIntent(context: Context, payload: SharePayload, packageName: String): Intent =
        buildSendIntent(payload).apply {
            setPackage(packageName)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Batch hand-off: several finished files in one ACTION_SEND_MULTIPLE. The clip data carries a
     * read grant per URI, which is what actually lets a receiving app open them on API 29+.
     */
    fun shareManyIntent(
        context: Context,
        uris: List<Uri>,
        title: String,
        mimeType: String = "image/*",
        caption: String? = null,
    ): Intent {
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "ShareSafe",
                uris.first(),
            ).apply {
                uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
            }
        }
        return Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, PackageManager.MATCH_ALL)
        true
    }.getOrDefault(false)

    fun whatsappPackage(context: Context): String? = when {
        isInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
        isInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
        else -> null
    }

    fun telegramPackage(context: Context): String? =
        TELEGRAM_PACKAGE.takeIf { isInstalled(context, it) }

    fun canHandleSend(context: Context, mimeType: String = "image/png"): Boolean = runCatching {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType(mimeType),
            0,
        ).isNotEmpty()
    }.getOrDefault(false)
}
