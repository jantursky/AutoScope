package com.jantursky.autoscope.phone

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Events shown in the list and the bar; the tracker keeps more. */
internal const val TIMELINE_LIMIT = 12

/** Narrowest share of the bar a segment gets, so a short gap stays visible. */
private const val MIN_SEGMENT_SHARE = 0.03f

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** One list row: when the state began, the state, and how long it lasted ("now" while it still lasts). */
data class TimelineRow(val time: String, val state: String, val duration: String)

/** One stretch of the bar. [share] of the full width, [connected] colors it green, otherwise grey. */
data class BarSegment(val connected: Boolean, val share: Float)

/** [rows] newest first, [segments] oldest first (the bar reads left to right in time). */
data class ConnectionTimeline(val rows: List<TimelineRow>, val segments: List<BarSegment>)

/**
 * Turns the tracker's history into rows and bar segments. Event times are monotonic; [nowMono] and [nowWall]
 * are read together and map them to the wall clock, which is exact enough for a seconds display.
 */
fun connectionTimeline(events: List<ConnectionEvent>, nowMono: Long, nowWall: Long, zone: ZoneId): ConnectionTimeline {
    val recent = events.takeLast(TIMELINE_LIMIT)
    val formatter = TIME.withZone(zone)
    val ends = recent.drop(1).map { it.at } + null
    val rows = recent.zip(ends).map { (event, end) ->
        val started = Instant.ofEpochMilli(nowWall - (nowMono - event.at))
        TimelineRow(formatter.format(started), connectionTypeName(event.type), end?.let { formatSpan(it - event.at) } ?: "now")
    }
    val lengths = recent.zip(ends).map { (event, end) -> ((end ?: nowMono) - event.at).coerceAtLeast(0).toFloat() }
    val total = lengths.sum()
    val raw = lengths.map { if (total > 0f) it / total else 1f / lengths.size }
    val boosted = raw.map { maxOf(it, MIN_SEGMENT_SHARE) }
    val scale = boosted.sum()
    val segments = recent.zip(boosted).map { (event, share) -> BarSegment(isConnected(event.type), share / scale) }
    return ConnectionTimeline(rows.reversed(), segments)
}

/** "45 s", "12 min", "1 h 05 min", "2 h". Rounds down. */
fun formatSpan(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return when {
        minutes < 1 -> "$seconds s"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}
