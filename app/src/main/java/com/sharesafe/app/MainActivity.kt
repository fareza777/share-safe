package com.sharesafe.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.LocaleSupport
import com.sharesafe.app.ui.ShareSafeRoot
import com.sharesafe.app.ui.theme.ShareSafeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val incomingShare = MutableStateFlow<Uri?>(null)
    private val shareTarget: StateFlow<Uri?> = incomingShare.asStateFlow()

    /**
     * The chosen language is applied here rather than in a wrapper Activity: `attachBaseContext`
     * runs before `onCreate`, so the very first composition is already in the right language and
     * the splash does not flash English before switching.
     */
    override fun attachBaseContext(newBase: Context) {
        SettingsStore.attach(newBase)
        super.attachBaseContext(
            LocaleSupport.wrap(newBase, SettingsStore.instance.language.value),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        captureShare(intent)
        setContent {
            val themeMode by SettingsStore.instance.themeMode.collectAsState()
            val dynamicColor by SettingsStore.instance.dynamicColor.collectAsState()
            val palette by SettingsStore.instance.palette.collectAsState()
            ShareSafeTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                palette = palette,
            ) {
                Surface(modifier = Modifier) {
                    ShareSafeRoot(
                        shareTarget = shareTarget.collectAsState().value,
                        onShareTargetConsumed = { incomingShare.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureShare(intent)
    }

    /**
     * ShareSafe is also a share target: another app hand us an image and it opens straight in the
     * editor, so the safe path starts one tap earlier. The URI read grant lives only as long as
     * this activity, which is why it is consumed immediately instead of being stored.
     */
    private fun captureShare(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> intent.sharedImageUri()
            else -> null
        } ?: return
        if (uri.scheme.isNullOrBlank()) return
        incomingShare.value = uri
    }
}

/**
 * Reads EXTRA_STREAM without the typed getParcelableExtra overload, which only exists on API 33+.
 * The explicit type argument keeps Kotlin from having to infer the Java method's type parameter.
 */
private fun Intent.sharedImageUri(): Uri? {
    @Suppress("DEPRECATION")
    val raw: Any? = getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)
    return when (raw) {
        is Uri -> raw
        is List<*> -> raw.filterIsInstance<Uri>().firstOrNull()
        else -> null
    }
}
