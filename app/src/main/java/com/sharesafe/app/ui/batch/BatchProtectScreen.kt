package com.sharesafe.app.ui.batch

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.batch.BatchItemStatus
import com.sharesafe.app.core.batch.BatchProtectSummary
import com.sharesafe.app.core.export.ImageExporter
import com.sharesafe.app.core.export.ShareHelper
import com.sharesafe.app.core.image.BitmapLoader
import com.sharesafe.app.data.SettingsStore
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.GradientActionButton
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.theme.MintSecondary
import com.sharesafe.app.ui.toastOnMain

/**
 * Batch Protect: the run, the progress, and the results.
 *
 * The screen is built around one idea — a batch should feel like watching a conveyor belt rather
 * than waiting for a black box. Each row states exactly where its image is, the header says what is
 * happening right now, and the note about memory is there because it is a real property of the
 * engine (one image at a time) rather than a marketing line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProtectScreen(
    viewModel: BatchProtectViewModel,
    onBack: () -> Unit,
    onOpenOne: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val state by viewModel.state.collectAsState()
    val hapticsEnabled by SettingsStore.instance.haptics.collectAsState()
    val adsVisible = rememberAdsVisible()
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    // The run is over: a single tick confirms it without turning into a notification stream.
    LaunchedEffect(state.summary) {
        if (state.summary != null && hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Leaving mid-run would strand the progress; the back gesture waits for the current image.
    BackHandler(enabled = state.running) {
        context.toastOnMain(context.getString(R.string.batch_running_back))
    }

    Column(modifier.fillMaxSize().testTag(TestTags.BATCH_SCREEN)) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.batch_title))
                    Text(
                        text = if (state.running) {
                            stringResource(R.string.batch_subtitle_running, state.doneCount + state.failedCount, state.total)
                        } else {
                            stringResource(R.string.batch_subtitle_done, state.doneCount, state.total)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.running) {
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
                actionIconContentColor = Color.White,
            ),
        )

        if (state.rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyBatch()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ProgressCard(
                        progress = state.progress,
                        done = state.doneCount,
                        failed = state.failedCount,
                        total = state.total,
                        currentName = state.currentName,
                        running = state.running,
                        animations = animations,
                    )
                }

                state.summary?.let { summary ->
                    item {
                        SummaryCard(
                            summary = summary,
                            onShareAll = { shareAll(context, summary.exportUris) },
                        )
                    }
                }

                item {
                    state.error?.let { message ->
                        ErrorCard(
                            message = message,
                            onRetry = { viewModel.start(state.rows.map { it.item }, force = true) },
                        )
                    }
                }

                itemsIndexed(state.rows, key = { _, row -> row.item.uri.toString() }) { index, row ->
                    BatchRow(
                        row = row,
                        index = index,
                        onClick = { detailIndex = index },
                        onRetry = { viewModel.retry(index) },
                        animations = animations,
                    )
                }
            }

            BottomBar(
                state = state,
                onShareAll = { shareAll(context, state.exportUris) },
                onRetryFailed = {
                    state.rows.forEachIndexed { index, row ->
                        if (row.status is BatchItemStatus.Failed) viewModel.retry(index)
                    }
                },
            )
        }

        // One ad slot, at the very bottom of the chrome, same rule as everywhere else: never over a
        // result the user is inspecting.
        if (adsVisible && state.rows.isNotEmpty()) {
            com.sharesafe.app.ui.components.AdBanner(
                visible = true,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    detailIndex?.let { index ->
        state.rows.getOrNull(index)?.let { row ->
            BatchDetailDialog(
                row = row,
                onDismiss = { detailIndex = null },
                onOpenInEditor = {
                    val uri = (row.status as? BatchItemStatus.Done)?.exportUri
                        ?: row.item.uri
                    detailIndex = null
                    onOpenOne(uri, row.item.displayName)
                },
                onShare = {
                    (row.status as? BatchItemStatus.Done)?.exportUri?.let { uri ->
                        shareAll(context, listOf(uri))
                    }
                },
            )
        }
    }
}

/**
 * Hands every finished file to one chooser. It shares the *gallery* URIs, so nothing has to be
 * re-encoded or kept in memory to do this — which is the whole reason the batch writes as it goes.
 */
private fun shareAll(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) {
        context.toastOnMain(context.getString(R.string.batch_share_empty))
        return
    }
    val capped = uris.take(ImageExporter.MAX_SHARE_BATCH)
    val intent = ShareHelper.shareManyIntent(
        context = context,
        uris = capped,
        title = context.getString(R.string.batch_share_title, capped.size),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { context.toastOnMain(context.getString(R.string.batch_share_failed)) }
}

@Composable
private fun ProgressCard(
    progress: Float,
    done: Int,
    failed: Int,
    total: Int,
    currentName: String,
    running: Boolean,
    animations: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.BATCH_PROGRESS),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (running) {
                        stringResource(R.string.batch_working)
                    } else {
                        stringResource(R.string.batch_finished)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$done / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (currentName.isNotBlank() && running) {
                Text(
                    text = currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Stated because it is engineered, not because it sounds nice: see BatchProtectEngine.
            Text(
                text = stringResource(R.string.batch_memory_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed > 0) {
                Text(
                    text = stringResource(R.string.batch_failed_note, failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: BatchProtectSummary, onShareAll: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.BATCH_SUMMARY),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.batch_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.batch_summary_body, summary.done, summary.total),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.regionsHidden > 0) {
                Text(
                    text = stringResource(R.string.batch_summary_regions, summary.regionsHidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.needsReview > 0) {
                Text(
                    text = stringResource(R.string.batch_summary_review, summary.needsReview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onShareAll, modifier = Modifier.testTag(TestTags.BATCH_SHARE_ALL)) {
                Text(stringResource(R.string.batch_share_all, summary.exportUris.size))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.batch_error_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun BatchRow(
    row: BatchProtectViewModel.Row,
    index: Int,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    animations: Boolean,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.BATCH_ROW),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = row.thumbnail
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = row.item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = row.item.displayName.ifBlank { stringResource(R.string.batch_unnamed) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                StatusLine(row.status)
                if (row.status is BatchItemStatus.Failed) {
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            StatusIcon(row.status)
        }
    }
}

@Composable
private fun StatusLine(status: BatchItemStatus) {
    val (text, color) = when (status) {
        BatchItemStatus.Queued -> stringResource(R.string.batch_row_queued) to
            MaterialTheme.colorScheme.onSurfaceVariant

        BatchItemStatus.Working -> stringResource(R.string.batch_row_working) to
            MaterialTheme.colorScheme.primary

        is BatchItemStatus.Failed -> stringResource(R.string.batch_row_failed, status.message) to
            MaterialTheme.colorScheme.error

        is BatchItemStatus.Done -> when {
            status.needsReview -> stringResource(R.string.batch_row_review, status.regionCount) to
                MaterialTheme.colorScheme.error

            status.verified -> stringResource(R.string.batch_row_verified, status.regionCount) to
                MintSecondary

            else -> stringResource(R.string.batch_row_done, status.regionCount) to
                MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusIcon(status: BatchItemStatus) {
    when (status) {
        BatchItemStatus.Queued -> Icon(
            Icons.Rounded.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        BatchItemStatus.Working -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )

        is BatchItemStatus.Failed -> Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )

        is BatchItemStatus.Done -> when {
            status.needsReview -> Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )

            status.verified -> Icon(
                Icons.Rounded.Verified,
                contentDescription = null,
                tint = MintSecondary,
                modifier = Modifier.size(20.dp),
            )

            else -> Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BottomBar(
    state: BatchProtectViewModel.UiState,
    onShareAll: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.batch_saved_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradientActionButton(
                text = stringResource(R.string.batch_share_all, state.exportUris.size),
                subtitle = stringResource(R.string.batch_share_body),
                icon = Icons.AutoMirrored.Rounded.Send,
                enabled = state.exportUris.isNotEmpty() && !state.running,
                modifier = Modifier.testTag(TestTags.BATCH_SHARE_ALL),
                onClick = onShareAll,
            )
            if (state.failedCount > 0) {
                OutlinedButton(
                    onClick = onRetryFailed,
                    enabled = !state.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.BATCH_RETRY),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.batch_retry_failed, state.failedCount))
                }
            }
        }
    }
}

@Composable
private fun EmptyBatch() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.batch_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.batch_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatchDetailDialog(
    row: BatchProtectViewModel.Row,
    onDismiss: () -> Unit,
    onOpenInEditor: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val uri = (row.status as? BatchItemStatus.Done)?.exportUri
    val preview by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = uri?.let { BitmapLoader.loadThumbnail(context, it, maxDim = 1080) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = row.item.displayName.ifBlank { stringResource(R.string.batch_detail_title) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val bitmap = preview
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                StatusLine(row.status)
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.batch_open_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenInEditor) {
                Text(stringResource(R.string.batch_open_in_editor), fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShare, enabled = uri != null) {
                    Text(stringResource(R.string.batch_share_one))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
        },
    )
}

