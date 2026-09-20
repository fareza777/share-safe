package com.sharesafe.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.AdBanner
import com.sharesafe.app.ui.components.InfoRow
import com.sharesafe.app.ui.components.PrimaryButton
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.startActivitySafely
import com.sharesafe.app.ui.toastOnMain
import kotlinx.coroutines.launch

/**
 * Everything that is not part of the redaction flow: what the app is, what it does with your data,
 * how to tell someone else about it, and how to get the onboarding back. The privacy block is
 * written as a statement of fact rather than marketing copy, because each line is enforced by the
 * manifest or by a test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adsVisible = rememberAdsVisible()
    var showLicenses by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val packageName = context.packageName.removeSuffix(".debug")
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val historyBytes = remember { HistoryStore.instance.totalBytes() }

    fun rateApp() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        scope.launch {
            context.startActivitySafely(market) {
                scope.launch {
                    context.startActivitySafely(web) { reason -> context.toastOnMain(reason) }
                }
            }
        }
    }

    fun shareApp() {
        val text = context.getString(
            R.string.about_share_body,
            context.getString(R.string.app_name),
            "https://play.google.com/store/apps/details?id=$packageName",
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        scope.launch {
            context.startActivitySafely(
                Intent.createChooser(intent, context.getString(R.string.about_share_title)),
            ) { reason -> context.toastOnMain(reason) }
        }
    }

    Column(modifier.fillMaxSize().testTag(TestTags.ABOUT_SCREEN)) {
        TopAppBar(
            title = { Text(stringResource(R.string.about_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_version, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.about_rate),
                    icon = Icons.Rounded.Star,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.ABOUT_RATE),
                    onClick = ::rateApp,
                )
                PrimaryButton(
                    text = stringResource(R.string.about_share_app),
                    icon = Icons.Rounded.Share,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.ABOUT_SHARE),
                    onClick = ::shareApp,
                )
            }

            AboutCard(title = stringResource(R.string.about_privacy_title)) {
                InfoRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.home_privacy_scan_title),
                    body = stringResource(R.string.settings_on_device_body),
                )
                InfoRow(
                    icon = Icons.Rounded.WifiOff,
                    title = stringResource(R.string.home_privacy_offline_title),
                    body = stringResource(R.string.about_no_internet_body),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                // The ads disclosure is stated here in the same breath as the offline guarantee:
                // a privacy screen that omits the one network dependency would be worthless.
                InfoRow(
                    icon = Icons.Rounded.SmartDisplay,
                    title = stringResource(R.string.home_privacy_ads_title),
                    body = stringResource(R.string.home_privacy_ads_body),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InfoRow(
                    icon = Icons.Rounded.Verified,
                    title = stringResource(R.string.preview_verify_ok),
                    body = stringResource(R.string.about_verify_body),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                InfoRow(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.about_storage_title),
                    body = stringResource(R.string.about_storage_body, historyBytes / 1024),
                )
            }

            AboutCard(title = stringResource(R.string.about_how_title)) {
                InfoRow(
                    icon = Icons.Rounded.AutoFixHigh,
                    title = stringResource(R.string.about_auto_title),
                    body = stringResource(R.string.about_auto_body),
                )
                InfoRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.about_engine_title),
                    body = stringResource(R.string.about_engine_body),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }

            AboutCard(title = stringResource(R.string.about_more_title)) {
                TextButton(
                    onClick = { showLicenses = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.about_licenses),
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = {
                        // Replaying onboarding from here is the polite way to "reset" a first-run
                        // experience without wiping the user's settings.
                        confirmReset = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.ABOUT_REPLAY_ONBOARDING),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.about_replay_onboarding),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AdBanner(visible = adsVisible)

            Text(
                text = stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.about_licenses_body), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.about_replay_onboarding)) },
            text = { Text(stringResource(R.string.about_replay_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    SettingsStore.instance.setOnboardingDone(false)
                    onReplayOnboarding()
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
