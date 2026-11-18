package com.jantursky.autoscope.phone

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTimelineTest {
    private val off = 0
    private val projection = 2

    // Monotonic 0 is 12:00:00 UTC on 1970-01-01; "now" is 40 minutes later.
    private val noon = 12 * 3600 * 1000L
    private val minute = 60_000L

    private fun timeline(vararg events: ConnectionEvent, nowMono: Long = 40 * minute) =
        connectionTimeline(events.toList(), nowMono, noon + nowMono, ZoneOffset.UTC)

    @Test fun noEventsGiveEmptyTimeline() {
        assertEquals(ConnectionTimeline(emptyList(), emptyList()), timeline())
    }

    @Test fun rowsAreNewestFirstWithDurationsAndNowForTheCurrentOne() {
        val t = timeline(ConnectionEvent(off, 0), ConnectionEvent(projection, 10 * minute), ConnectionEvent(off, 22 * minute))
        assertEquals(
            listOf(
                TimelineRow("12:22:00", "Not connected", "now"),
                TimelineRow("12:10:00", "Projection (Android Auto)", "12 min"),
                TimelineRow("12:00:00", "Not connected", "10 min"),
            ),
            t.rows,
        )
    }

    @Test fun segmentsAreOldestFirstAndProportional() {
        val t = timeline(ConnectionEvent(off, 0), ConnectionEvent(projection, 10 * minute))
        assertEquals(listOf(BarSegment(false, 0.25f), BarSegment(true, 0.75f)), t.segments)
    }

    @Test fun shortGapKeepsAMinimumWidthAndSharesStillSumToOne() {
        val t = timeline(ConnectionEvent(projection, 0), ConnectionEvent(off, 39 * minute + 59_000), nowMono = 40 * minute)
        assertEquals(true, t.segments.last().share >= 0.029f)
        assertEquals(1f, t.segments.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
    }

    @Test fun eventsAtTheSameInstantStillGiveEvenShares() {
        val t = timeline(ConnectionEvent(off, 5), ConnectionEvent(projection, 5), nowMono = 5)
        assertEquals(listOf(0.5f, 0.5f), t.segments.map { it.share })
    }

    @Test fun onlyTheLatestEventsAreShown() {
        val events = (0 until 30).map { ConnectionEvent(if (it % 2 == 0) off else projection, it * minute) }
        val t = connectionTimeline(events, 40 * minute, noon + 40 * minute, ZoneOffset.UTC)
        assertEquals(TIMELINE_LIMIT, t.rows.size)
        assertEquals(TIMELINE_LIMIT, t.segments.size)
        assertEquals("12:29:00", t.rows.first().time)
    }

    @Test fun spansAreRoundedDown() {
        assertEquals("45 s", formatSpan(45_999))
        assertEquals("1 min", formatSpan(60_000))
        assertEquals("12 min", formatSpan(12 * minute + 59_000))
        assertEquals("1 h 05 min", formatSpan(65 * minute))
        assertEquals("2 h", formatSpan(120 * minute))
        assertEquals("0 s", formatSpan(-5))
    }
}
