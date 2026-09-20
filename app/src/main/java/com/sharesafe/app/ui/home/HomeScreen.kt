package com.sharesafe.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sharesafe.app.R
import com.sharesafe.app.core.ads.AdsConfig
import com.sharesafe.app.core.batch.BatchProgress
import com.sharesafe.app.core.history.HistoryCalendar
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.core.image.ScreenshotItem
import com.sharesafe.app.core.image.ScreenshotRepository
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.AdBanner
import com.sharesafe.app.ui.components.AnimatedCounter
import com.sharesafe.app.ui.components.AppearIn
import com.sharesafe.app.ui.components.InfoRow
import com.sharesafe.app.ui.components.PlusUnlockDialog
import com.sharesafe.app.ui.components.SectionHeader
import com.sharesafe.app.ui.components.SelectChip
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.components.rememberPlusActive
import com.sharesafe.app.ui.components.rememberPressScale
import com.sharesafe.app.ui.theme.MintSecondary
import com.sharesafe.app.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

/**
 * Landing screen. There is exactly one primary action — protect a screenshot — and everything the
 * app can do happens behind it.
 *
 * Earlier versions offered "pick a screenshot" *and* "do it for me" side by side, which read as two
 * names for the same journey. They were: both ended in the same place. So the automatic pipeline
 * became the only path, and the manual editor was pushed to where a manual editor belongs — behind
 * "Keep editing" on the preview, for the minority of cases that need it.
 *
 * The recents strip is therefore not a second mode either: tapping a thumbnail protects that
 * screenshot right away, and pressing and holding switches into multi-select for a batch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProtect: (Uri, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
    /** Hands the selection to Batch Protect, which runs it one image at a time. */
    onStartBatch: (List<ScreenshotItem>) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = SettingsStore.instance
    val adsVisible = rememberAdsVisible()

    var showDisclosure by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    var mediaAccess by remember { mutableStateOf(ScreenshotRepository.canReadGallery(context)) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // A selection larger than the free batch size asks first instead of refusing: the dialog's
    // second option simply runs the first few images, so nothing is ever blocked outright.
    var batchPrompt by remember { mutableStateOf<List<ScreenshotItem>?>(null) }
    val plusActive = rememberPlusActive()

    val historyEntries by HistoryStore.instance.entries.collectAsState()
    val historyStats = remember(historyEntries) { HistoryCalendar.stats(historyEntries) }

    fun refresh() {
        mediaAccess = ScreenshotRepository.canReadGallery(context)
        if (!mediaAccess) {
            items = emptyList()
            return
        }
        scope.launch {
            items = ScreenshotRepository.queryRecent(context, limit = 30)
        }
    }

    // Re-query whenever the screen comes back to the foreground: the screenshot the user just took
    // is what they want to redact next, so the strip must not go stale.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) onProtect(uri, uri.lastPathSegment?.substringAfterLast('/').orEmpty())
    }

    val requestMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refresh()
    }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun startBatch() {
        val chosen = items.filter { it.uri.toString() in selected }
        if (chosen.isEmpty()) return
        exitSelection()
        if (chosen.size > AdsConfig.FREE_BATCH_LIMIT && !plusActive) {
            batchPrompt = chosen
            return
        }
        BitmapLoader.clearThumbnailCache()
        onStartBatch(chosen)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Edge to edge: the header must not sit under the status bar, and the last card must
            // clear the gesture bar.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        AppearIn(enabled = animations) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hero(Modifier.weight(1f))
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag(TestTags.HOME_HISTORY_ENTRY),
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = stringResource(R.string.history_title),
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(TestTags.HOME_SETTINGS_ENTRY),
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                    )
                }
            }
        }

        AppearIn(enabled = animations, index = 1) { TrustChips() }

        if (historyEntries.isNotEmpty()) {
            AppearIn(enabled = animations, index = 2) {
                StatsStrip(stats = historyStats, animations = animations)
            }
        }

        AppearIn(enabled = animations, index = 3) {
            PrimaryActionCard(
                onPick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                animations = animations,
            )
        }

        AppearIn(enabled = animations, index = 4) { FlowSteps() }

        RecentSection(
            items = items,
            hasAccess = mediaAccess,
            selectionMode = selectionMode,
            selected = selected,
            onGrant = { showDisclosure = true },
            onProtect = { item -> onProtect(item.uri, item.displayName) },
            onToggleSelectionMode = {
                if (selectionMode) exitSelection() else selectionMode = true
            },
            onToggleSelected = { key ->
                selected = if (key in selected) selected - key else selected + key
            },
            onBatch = ::startBatch,
        )

        AppearIn(enabled = animations, index = 5) { PrivacySection() }

        // Banner sits at the very bottom, below the last piece of content, so it can never cover a
        // thumbnail. Home is chrome: the user's own pixels only ever appear in the editor and the
        // preview, which carry no ads at all.
        AdBanner(visible = adsVisible)

        Spacer(Modifier.height(24.dp))
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.home_media_disclosure_title)) },
            text = { Text(stringResource(R.string.home_media_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    requestMedia.launch(ScreenshotRepository.requiredPermissions())
                }) { Text(stringResource(R.string.home_media_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.home_media_disclosure_skip))
                }
            },
        )
    }

    batchPrompt?.let { chosen ->
        PlusUnlockDialog(
            title = stringResource(R.string.batch_limit_title, chosen.size),
            body = stringResource(R.string.batch_limit_body, AdsConfig.FREE_BATCH_LIMIT),
            confirmLabel = stringResource(R.string.batch_limit_watch),
            alternativeLabel = stringResource(
                R.string.batch_limit_first,
                AdsConfig.FREE_BATCH_LIMIT,
            ),
            onAlternative = {
                batchPrompt = null
                onStartBatch(chosen.take(AdsConfig.FREE_BATCH_LIMIT))
            },
            onDismiss = { batchPrompt = null },
            onUnlocked = {
                batchPrompt = null
                onStartBatch(chosen)
            },
        )
    }
}

/** Compact odometer of what this app has already protected, straight from History. */
@Composable
private fun StatsStrip(stats: com.sharesafe.app.core.history.HistoryStats, animations: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.HOME_STATS),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatTile(
                value = stats.totalExports,
                label = stringResource(R.string.home_stat_exports),
                animations = animations,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stats.totalRegions,
                label = stringResource(R.string.home_stat_regions),
                animations = animations,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stats.currentStreak,
                label = stringResource(R.string.home_stat_streak),
                animations = animations,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    value: Int,
    label: String,
    animations: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedCounter(value = value, enabled = animations)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The single call to action. It is one tap from a screenshot to a redacted, verified, shareable
 * file — the strip of three steps underneath exists only so the promise is legible before the tap.
 */
@Composable
private fun PrimaryActionCard(onPick: () -> Unit, animations: Boolean) {
    val (interaction, scale) = rememberPressScale(animations)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Surface(
            onClick = onPick,
            interactionSource = interaction,
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.HOME_PICK),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                    ),
            ) {
                // Two translucent discs give the card depth without an image asset.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 34.dp, y = (-30).dp)
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 18.dp, y = 26.dp)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                )

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.22f),
                        ) {
                            Text(
                                text = stringResource(R.string.home_action_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.home_action_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_action_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.86f),
                        )
                    }
                }
            }
        }
    }
}

/** Two pills that state the promise in the first screenful, before any scrolling. */
@Composable
private fun TrustChips() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrustChip(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.splash_chip_local),
        )
        TrustChip(
            icon = Icons.Rounded.WifiOff,
            label = stringResource(R.string.splash_chip_offline),
        )
    }
}

@Composable
private fun TrustChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatchCard(progress: BatchProgress) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_batch_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(
                    R.string.home_batch_running,
                    (progress.index + 1).coerceAtMost(progress.total.coerceAtLeast(1)),
                    progress.total,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            LinearProgressIndicator(
                progress = {
                    if (progress.total == 0) 0f
                    else progress.index.toFloat() / progress.total.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hero(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(VioletPrimary, MintSecondary))),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowSteps() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowStep(1, stringResource(R.string.home_flow_scan), Modifier.weight(1f))
        FlowStep(2, stringResource(R.string.home_flow_redact), Modifier.weight(1f))
        FlowStep(3, stringResource(R.string.home_flow_share), Modifier.weight(1f))
    }
}

/** One of the three steps the single tap performs, so the promise is legible before the tap. */
@Composable
private fun FlowStep(index: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecentSection(
    items: List<ScreenshotItem>,
    hasAccess: Boolean,
    selectionMode: Boolean,
    selected: Set<String>,
    onGrant: () -> Unit,
    onProtect: (ScreenshotItem) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onBatch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SectionHeader(stringResource(R.string.home_recent_title))
            }
            if (hasAccess && items.isNotEmpty()) {
                TextButton(
                    onClick = onToggleSelectionMode,
                    modifier = Modifier.testTag(TestTags.HOME_SELECT_TOGGLE),
                ) {
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.action_cancel)
                        } else {
                            stringResource(R.string.home_select)
                        },
                    )
                }
            }
        }

        when {
            !hasAccess -> Surface(
                onClick = onGrant,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_recent_grant),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.home_pick_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items.isEmpty() -> Text(
                text = stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(items, key = { it.uri.toString() }) { item ->
                        val key = item.uri.toString()
                        ScreenshotThumbnail(
                            item = item,
                            selectionMode = selectionMode,
                            isSelected = key in selected,
                            isNewest = item.uri == items.first().uri,
                            onClick = {
                                if (selectionMode) onToggleSelected(key) else onProtect(item)
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    onToggleSelectionMode()
                                    onToggleSelected(key)
                                }
                            },
                        )
                    }
                }

                if (selectionMode) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.home_selected_count, selected.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        SelectChip(
                            label = stringResource(R.string.home_redact_selected, selected.size),
                            selected = true,
                            icon = Icons.Rounded.AutoFixHigh,
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.testTag(TestTags.HOME_BATCH_ACTION),
                            onClick = onBatch,
                        )
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.home_recent_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotThumbnail(
    item: ScreenshotItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    isNewest: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, item.uri) {
        value = BitmapLoader.loadThumbnail(context, item.uri, maxDim = 360)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .testTag(TestTags.HOME_RECENT_THUMB)
            .width(112.dp)
            .aspectRatio(0.62f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxSize()) {
            val current = bitmap
            if (current != null && !current.isRecycled) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isNewest && !selectionMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_recent_new),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (selectionMode) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.45f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_privacy_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.home_privacy_scan_title),
                body = stringResource(R.string.home_privacy_local),
                tint = MaterialTheme.colorScheme.primary,
            )
            InfoRow(
                icon = Icons.Rounded.WifiOff,
                title = stringResource(R.string.home_privacy_offline_title),
                body = stringResource(R.string.home_privacy_offline_body),
                tint = MintSecondary,
            )
            InfoRow(
                icon = Icons.Rounded.Image,
                title = stringResource(R.string.home_privacy_export_title),
                body = stringResource(R.string.home_privacy_export),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            // Stated rather than hidden: the app has ads, and this is exactly what they can see.
            InfoRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.home_privacy_ads_title),
                body = stringResource(R.string.home_privacy_ads_body),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
