package com.sharesafe.app.ui.history

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharesafe.app.R
import com.sharesafe.app.core.export.ShareHelper
import com.sharesafe.app.core.export.SharePayload
import com.sharesafe.app.core.history.HistoryCalendar
import com.sharesafe.app.core.history.HistoryEntry
import com.sharesafe.app.core.history.HistoryStats
import com.sharesafe.app.data.HistoryStore
import com.sharesafe.app.ui.TestTags
import com.sharesafe.app.ui.components.AdBanner
import com.sharesafe.app.ui.components.AnimatedCounter
import com.sharesafe.app.ui.components.AppearIn
import com.sharesafe.app.ui.components.rememberAdsVisible
import com.sharesafe.app.ui.startActivitySafely
import com.sharesafe.app.ui.toastOnMain
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * History as a calendar: which days produced a redacted export, how much was hidden, and the files
 * themselves so they can be shared again. Only the redacted copies live here — the balance between
 * "useful" and "a place where private data piles up" is settled in favour of the former because the
 * originals are never written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    animations: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = HistoryStore.instance
    val entries by store.entries.collectAsState()
    val adsVisible = rememberAdsVisible()

    val zone = remember { java.time.ZoneId.systemDefault() }
    val grouped = remember(entries) { HistoryCalendar.group(entries, zone) }
    val stats = remember(entries) { HistoryCalendar.stats(entries, zone) }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val activeDay = selected
        ?: grouped.keys.maxOrNull()
        ?: today
    val dayEntries = grouped[activeDay].orEmpty()

    fun share(entry: HistoryEntry) {
        val uri: Uri = store.shareUri(entry) ?: run {
            context.toastOnMain(context.getString(R.string.history_missing))
            return
        }
        val payload = SharePayload(uri = uri, mimeType = entry.mimeType)
        val intent = ShareHelper.shareSheetIntent(
            context = context,
            payload = payload,
            title = context.getString(R.string.share_sheet_title),
        )
        scope.launch {
            context.startActivitySafely(intent) { reason -> context.toastOnMain(reason) }
        }
    }

    Column(modifier.fillMaxSize().testTag(TestTags.HISTORY_SCREEN)) {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = {
                if (entries.isNotEmpty()) {
                    IconButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.testTag(TestTags.HISTORY_CLEAR),
                    ) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.history_clear),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (entries.isEmpty()) {
                EmptyHistory()
            } else {
                AppearIn(enabled = animations) { StatsCard(stats, animations) }
                AppearIn(enabled = animations, index = 1) {
                    Column {
                        MonthHeader(
                            month = month,
                            onPrevious = { month = month.minusMonths(1) },
                            onNext = { month = month.plusMonths(1) },
                            onToday = {
                                month = YearMonth.now()
                                selected = LocalDate.now()
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        MonthGrid(
                            month = month,
                            counts = grouped.mapValues { it.value.size },
                            selected = activeDay,
                            today = today,
                            onSelect = { date ->
                                selected = date
                                month = YearMonth.from(date)
                            },
                        )
                    }
                }
                AppearIn(enabled = animations, index = 2) {
                    Column {
                        Text(
                            text = dayLabel(activeDay, today),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (dayEntries.isEmpty()) {
                            Text(
                                text = stringResource(R.string.history_day_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                dayEntries.forEach { entry ->
                                    HistoryRow(
                                        entry = entry,
                                        onShare = { share(entry) },
                                        onDelete = { store.delete(entry.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // At the bottom of the list, never over a thumbnail: this screen shows redacted
            // copies of the user's own screenshots, so the ad sits after them, not on them.
            AdBanner(visible = adsVisible)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    store.clear()
                    selected = null
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatsCard(stats: HistoryStats, animations: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.HOME_STATS),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.history_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.history_streak,
                        stats.currentStreak,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = stats.totalExports,
                    label = stringResource(R.string.history_stat_exports),
                    animations = animations,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stats.totalRegions,
                    label = stringResource(R.string.history_stat_regions),
                    animations = animations,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stats.daysActive,
                    label = stringResource(R.string.history_stat_days),
                    animations = animations,
                    modifier = Modifier.weight(1f),
                )
            }
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedCounter(
                value = value,
                enabled = animations,
                modifier = Modifier,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToday) { Text(stringResource(R.string.history_today)) }
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.history_prev_month))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.history_next_month))
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    counts: Map<LocalDate, Int>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month) { HistoryCalendar.monthGrid(month) }
    val weekdayLabels = remember {
        (1..7).map { day ->
            java.time.DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        count = date?.let { counts[it] } ?: 0,
                        isSelected = date != null && date == selected,
                        isToday = date != null && date == today,
                        onClick = { date?.let(onSelect) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier.height(44.dp))
        return
    }
    val hasHistory = count > 0
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    hasHistory -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .testTag(TestTags.HISTORY_CALENDAR_DAY)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    hasHistory -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(if (hasHistory) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> Color.White
                            isToday -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val store = HistoryStore.instance
    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, entry.id) {
        value = store.thumbnail(entry, maxDim = 220)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.HISTORY_ITEM),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = thumbnail
                if (bitmap != null && !bitmap.isRecycled) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entry.sourceName,
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
                    text = entry.sourceName.ifBlank { "screenshot" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = remember(entry.timestamp) {
                        java.time.Instant.ofEpochMilli(entry.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (entry.verified) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = stringResource(R.string.preview_verify_ok),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.history_regions,
                            entry.regionCount,
                            entry.regionCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${entry.width}×${entry.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onShare) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.history_share_again),
                )
            }
            IconButton(onClick = {
                onDelete()
                context.toastOnMain(context.getString(R.string.history_deleted))
            }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> stringResource(R.string.history_today)
    date == today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

/** Kept for callers that want a plain list of the most recent exports. */
@Composable
fun RecentHistoryStrip(
    onOpen: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by HistoryStore.instance.entries.collectAsState()
    if (entries.isEmpty()) return
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(entries.take(12), key = { it.id }) { entry ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                onClick = { onOpen(entry) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = entry.sourceName.ifBlank { "screenshot" },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
