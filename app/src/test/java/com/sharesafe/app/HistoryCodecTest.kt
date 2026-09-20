package com.sharesafe.app

import com.sharesafe.app.core.history.HistoryCalendar
import com.sharesafe.app.core.history.HistoryCodec
import com.sharesafe.app.core.history.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HistoryCodecTest {

    private fun entry(
        id: String = "h1",
        timestamp: Long = 1_760_000_000_000,
        sourceName: String = "Screenshot_2026.png",
        kinds: List<String> = listOf("PHONE", "EMAIL"),
    ) = HistoryEntry(
        id = id,
        timestamp = timestamp,
        sourceName = sourceName,
        kinds = kinds,
        regionCount = 3,
        fileName = "$id.png",
        mimeType = "image/png",
        width = 1080,
        height = 1920,
        byteSize = 123_456,
        verified = true,
    )

    @Test
    fun roundTripsEveryField() {
        val original = entry()
        val decoded = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun survivesAwkwardFileNames() {
        // Names come from other apps and can contain tabs or newlines; they must not break the file.
        val decoded = HistoryCodec.decode(
            HistoryCodec.encode(entry(sourceName = "weird\tname\nwith breaks.png")),
        )
        assertEquals("weird name with breaks.png", decoded?.sourceName)
    }

    @Test
    fun rejectsMalformedLines() {
        assertNull(HistoryCodec.decode(""))
        assertNull(HistoryCodec.decode("v1\th1"))
        assertNull(HistoryCodec.decode("v2\th1\t1\tname\t\t\tfile.png\timage/png\t1\t1\t1\t1"))
        assertNull(HistoryCodec.decode("nonsense"))
    }

    @Test
    fun decodesAllAndSkipsTheBrokenLines() {
        val content = HistoryCodec.encode(entry(id = "a")) + "garbage\n" + HistoryCodec.encode(entry(id = "b"))
        val decoded = HistoryCodec.decodeAll(content)
        assertEquals(listOf("a", "b"), decoded.map { it.id })
    }

    @Test
    fun unverifiedEntriesStayUnverified() {
        val decoded = HistoryCodec.decode(HistoryCodec.encode(entry().copy(verified = false)))
        assertEquals(false, decoded?.verified)
    }

    @Test
    fun monthGridStartsOnMondayAndAlwaysFillsSixWeeks() {
        val cells = HistoryCalendar.monthGrid(YearMonth.of(2026, 9))
        assertEquals(42, cells.size)
        // 1 September 2026 is a Tuesday, so the first cell is padding and the second is the 1st.
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(LocalDate.of(2026, 9, 30), cells[30])
        assertNull(cells[31])
        assertNull(cells[41])
    }

    @Test
    fun groupsEntriesByTheirLocalDay() {
        val zone = ZoneId.of("Asia/Jakarta")
        val today = LocalDate.now(zone)
        val first = entry(id = "a", timestamp = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val second = entry(id = "b", timestamp = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli())

        val grouped = HistoryCalendar.group(listOf(first, second), zone)
        assertEquals(1, grouped.size)
        assertEquals(listOf("b", "a"), grouped.getValue(today).map { it.id })
    }

    @Test
    fun countsAStreakThatIncludesTodayOrYesterday() {
        val zone = ZoneId.of("Asia/Jakarta")
        val today = LocalDate.now(zone)
        fun at(daysAgo: Long) = entry(
            id = "d$daysAgo",
            timestamp = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        val stats = HistoryCalendar.stats(listOf(at(0), at(1), at(3)), zone)
        assertEquals(3, stats.totalExports)
        assertEquals(9, stats.totalRegions)
        assertEquals(3, stats.daysActive)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun emptyHistoryHasZeroStats() {
        val stats = HistoryCalendar.stats(emptyList())
        assertEquals(0, stats.totalExports)
        assertEquals(0, stats.currentStreak)
        assertTrue(stats.lastExportAt == null)
    }
}
