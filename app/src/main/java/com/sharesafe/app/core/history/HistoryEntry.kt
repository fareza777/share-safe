package com.sharesafe.app.core.history

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One finished job: what was exported, when, and what was hidden. The entry points at the private
 * copy of the **redacted** file — the original screenshot is never stored — so History can show a
 * thumbnail and share the safe version again without keeping anything dangerous on disk.
 */
data class HistoryEntry(
    val id: String,
    val timestamp: Long,
    val sourceName: String,
    /** SensitiveKind names, kept as strings so the codec stays free of Android types. */
    val kinds: List<String>,
    val regionCount: Int,
    val fileName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val verified: Boolean,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}

/**
 * Line-oriented codec: one entry per line, tab separated. No JSON library, no reflection, and the
 * whole thing is pure Kotlin so the format is covered by JVM unit tests.
 */
object HistoryCodec {

    private const val VERSION = "v1"
    private const val SEPARATOR = '\t'
    private const val KIND_SEPARATOR = ','
    private const val FIELD_COUNT = 12

    fun encode(entry: HistoryEntry): String = buildString {
        append(VERSION).append(SEPARATOR)
        append(entry.id).append(SEPARATOR)
        append(entry.timestamp).append(SEPARATOR)
        append(escape(entry.sourceName)).append(SEPARATOR)
        append(entry.kinds.joinToString(KIND_SEPARATOR.toString()) { escape(it) }).append(SEPARATOR)
        append(entry.regionCount).append(SEPARATOR)
        append(escape(entry.fileName)).append(SEPARATOR)
        append(escape(entry.mimeType)).append(SEPARATOR)
        append(entry.width).append(SEPARATOR)
        append(entry.height).append(SEPARATOR)
        append(entry.byteSize).append(SEPARATOR)
        append(if (entry.verified) 1 else 0)
    }

    fun decode(line: String): HistoryEntry? {
        val parts = line.split(SEPARATOR)
        if (parts.size != FIELD_COUNT || parts[0] != VERSION) return null
        val timestamp = parts[2].toLongOrNull() ?: return null
        val id = parts[1].takeIf { it.isNotBlank() } ?: return null
        val fileName = unescape(parts[6]).takeIf { it.isNotBlank() } ?: return null
        return HistoryEntry(
            id = id,
            timestamp = timestamp,
            sourceName = unescape(parts[3]),
            kinds = parts[4].split(KIND_SEPARATOR)
                .map { unescape(it) }
                .filter { it.isNotBlank() },
            regionCount = parts[5].toIntOrNull() ?: 0,
            fileName = fileName,
            mimeType = unescape(parts[7]).ifBlank { "image/png" },
            width = parts[8].toIntOrNull() ?: 0,
            height = parts[9].toIntOrNull() ?: 0,
            byteSize = parts[10].toLongOrNull() ?: 0L,
            verified = parts[11] == "1",
        )
    }

    fun decodeAll(content: String): List<HistoryEntry> =
        content.lineSequence().mapNotNull { line -> decode(line) }.toList()

    fun encodeAll(entries: List<HistoryEntry>): String =
        entries.joinToString(separator = "\n", postfix = "\n") { encode(it) }

    /** Tabs and newlines would break the record, so they are folded into spaces. */
    private fun escape(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun unescape(value: String): String = value.trim()
}

/** What the History header shows at a glance. */
data class HistoryStats(
    val totalExports: Int,
    val totalRegions: Int,
    val daysActive: Int,
    val currentStreak: Int,
    val lastExportAt: Long?,
)

/**
 * Calendar maths for the History screen, kept separate from the UI so month grids and streaks can
 * be unit tested without a device.
 */
object HistoryCalendar {

    /** Six weeks of cells (Monday first), `null` for the padding days outside the month. */
    fun monthGrid(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val cells = ArrayList<LocalDate?>(42)
        repeat(leading) { cells += null }
        for (day in 1..days) cells += month.atDay(day)
        while (cells.size % 7 != 0) cells += null
        while (cells.size < 42) cells += null
        return cells
    }

    fun group(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()):
        Map<LocalDate, List<HistoryEntry>> =
        entries.groupBy { it.date(zone) }.mapValues { (_, day) -> day.sortedByDescending { it.timestamp } }

    fun stats(entries: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): HistoryStats {
        if (entries.isEmpty()) return HistoryStats(0, 0, 0, 0, null)
        val days = entries.map { it.date(zone) }.distinct().sortedDescending()
        val today = LocalDate.now()
        var streak = 0
        var cursor = if (days.first() == today || days.first() == today.minusDays(1)) days.first() else null
        while (cursor != null && days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return HistoryStats(
            totalExports = entries.size,
            totalRegions = entries.sumOf { it.regionCount },
            daysActive = days.size,
            currentStreak = streak,
            lastExportAt = entries.maxOfOrNull { it.timestamp },
        )
    }

    /** "Today", "Yesterday" or a locale-formatted date — computed from the entries, not the clock. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): RelativeDay = when {
        date == today -> RelativeDay.TODAY
        date == today.minusDays(1) -> RelativeDay.YESTERDAY
        ChronoUnit.DAYS.between(date, today) < 7 -> RelativeDay.THIS_WEEK
        else -> RelativeDay.OLDER
    }

    enum class RelativeDay { TODAY, YESTERDAY, THIS_WEEK, OLDER }
}
