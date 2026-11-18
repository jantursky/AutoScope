package com.jantursky.autoscope.phone

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID_PATTERN = Regex("""autoscope-(\d{8}-\d{6}-\d{3})-(.*)""")
private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

/** Title and time line of a saved report row. */
data class SavedReportLabel(val title: String, val whenText: String)

/** Instant encoded in a report id ("autoscope-yyyyMMdd-HHmmss-SSS-reason", UTC), or null for foreign ids. */
fun reportInstant(id: String): Instant? {
    val stamp = ID_PATTERN.matchEntire(id)?.groupValues?.get(1) ?: return null
    return runCatching { LocalDateTime.parse(stamp, STAMP).toInstant(ZoneOffset.UTC) }.getOrNull()
}

/** "just now", "5 min ago", "3 h ago", "2 d ago". */
fun relativeTime(then: Instant, now: Instant): String {
    val minutes = (now.epochSecond - then.epochSecond).coerceAtLeast(0) / 60
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}

/** Reason part of the id as a title ("phone-button" becomes "Phone button"). */
private fun reportTitle(id: String): String {
    val slug = ID_PATTERN.matchEntire(id)?.groupValues?.get(2).orEmpty()
    return slug.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }.ifEmpty { id }
}

fun savedReportLabel(id: String, now: Instant, zone: ZoneId, locale: Locale): SavedReportLabel {
    val instant = reportInstant(id) ?: return SavedReportLabel(id, "")
    val clock = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", locale).withZone(zone).format(instant)
    return SavedReportLabel(reportTitle(id), "$clock  ·  ${relativeTime(instant, now)}")
}
