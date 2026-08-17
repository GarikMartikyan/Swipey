package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [groupedByDay] is the session grid's headers.
 *
 * The zone offset is a parameter rather than ambient, which is the only reason these can
 * assert anything: the same list has to group identically here and on a phone in Yerevan.
 */
class GroupedByDayTest {
    private val utc = 0L
    private val yerevan = 4 * 3600L
    private val day = 86_400L

    private fun item(id: Long, at: Long) =
        MediaItem(id, false, 100, at, null, 1, "Camera", "f$id.jpg")

    @Test fun emptyGivesNoDays() {
        assertTrue(emptyList<MediaItem>().groupedByDay(utc).isEmpty())
    }

    @Test fun itemsOnOneDayMakeOneGroup() {
        val items = listOf(item(1, 0), item(2, 3600), item(3, day - 1))
        val days = items.groupedByDay(utc)
        assertEquals(1, days.size)
        assertEquals(listOf(1L, 2L, 3L), days[0].items.map { it.id })
        assertEquals(0L, days[0].dayStartSec)
    }

    @Test fun aMidnightBoundarySplitsTheGroup() {
        val items = listOf(item(1, day - 1), item(2, day))
        val days = items.groupedByDay(utc)
        assertEquals(2, days.size)
        assertEquals(listOf(1L), days[0].items.map { it.id })
        assertEquals(listOf(2L), days[1].items.map { it.id })
    }

    /**
     * The whole reason the offset is a parameter.
     *
     * Local midnight in a +4 zone is 20:00 UTC, so 19:00 and 21:00 UTC straddle it — one
     * day apart there, and the same UTC day. Pick times either side of the *local*
     * boundary, not the UTC one: 20:00 and 23:00 UTC look like they cross midnight but are
     * both 00:00–03:00 the next local day, which is one group in both zones.
     */
    @Test fun theZoneDecidesWhereTheDayBreaks() {
        val items = listOf(item(1, 19 * 3600), item(2, 21 * 3600))
        assertEquals("same UTC day", 1, items.groupedByDay(utc).size)
        assertEquals("either side of local midnight", 2, items.groupedByDay(yerevan).size)
    }

    @Test fun orderIsPreservedRatherThanSorted() {
        // A shuffled session genuinely is in this order, and the grid must show it.
        val items = listOf(item(1, 2 * day), item(2, 0), item(3, 2 * day))
        val days = items.groupedByDay(utc)
        assertEquals("the same day appearing twice is the truth about this queue", 3, days.size)
        assertEquals(listOf(1L, 2L, 3L), days.flatMap { d -> d.items.map { it.id } })
    }

    @Test fun everyItemSurvivesGrouping() {
        val items = (0L until 50L).map { item(it, it * 7 * 3600) }
        val days = items.groupedByDay(yerevan)
        assertEquals(50, days.sumOf { it.items.size })
        assertEquals(items.map { it.id }, days.flatMap { d -> d.items.map { it.id } })
    }

    @Test fun aDayStartIsMidnightInThatZone() {
        val days = listOf(item(1, 20 * 3600)).groupedByDay(yerevan)
        val local = days[0].dayStartSec + yerevan
        assertEquals("day start must land on local midnight", 0L, local % day)
    }
}
