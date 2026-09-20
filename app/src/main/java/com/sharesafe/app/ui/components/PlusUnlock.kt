package com.sharesafe.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.ads.AD_FREE_WINDOW_MILLIS
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.toastOnMain

/**
 * The single place a rewarded video is offered.
 *
 * "Plus" is one thing, not three: a video buys 24 hours of no banners, no interstitials, the premium
 * beautify looks and an uncapped batch. It is never the gate in front of hiding something — a user
 * who skips every ad still gets the full redaction engine, which is the entire point of the app.
 *
 * Sharing one dialog keeps the copy, the loading state and the failure path identical everywhere it
 * appears (Settings, the editor's preset row, Batch Protect).
 */
@Composable
fun PlusUnlockDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    /**
     * A way to continue *without* watching anything. Every use of this dialog has to offer one, or
     * the reward would stop being a shortcut and start being a toll booth.
     */
    alternativeLabel: String? = null,
    onAlternative: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val rewarded = rememberRewardedSlot()
    var busy by remember { mutableStateOf(false) }
    val plusUntil by SettingsStore.instance.adFreeUntil.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    if (!rewarded.isReady) {
                        context.toastOnMain(context.getString(R.string.settings_ads_unavailable))
                        return@Button
                    }
                    busy = true
                    rewarded.show(
                        onReward = {
                            SettingsStore.instance.grantAdFree(
                                System.currentTimeMillis() + AD_FREE_WINDOW_MILLIS,
                            )
                            onUnlocked()
                        },
                        onFinished = {
                            busy = false
                            // A video that failed to play must not leave the dialog open forever.
                            if (plusUntil <= System.currentTimeMillis()) onDismiss()
                        },
                    )
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_ads_loading))
                } else {
                    Icon(
                        Icons.Rounded.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alternativeLabel != null && onAlternative != null) {
                    TextButton(onClick = onAlternative, enabled = !busy) { Text(alternativeLabel) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** True while the rewarded window is open. Reactive, so a grant updates every caller at once. */
@Composable
fun rememberPlusActive(): Boolean {
    val until by SettingsStore.instance.adFreeUntil.collectAsState()
    return until > System.currentTimeMillis()
}
