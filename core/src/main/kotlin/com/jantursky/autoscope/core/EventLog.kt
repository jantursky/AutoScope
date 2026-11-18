package com.jantursky.autoscope.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Thread safe, bounded, timestamped log. Serves as the lifecycle log source. */
class EventLog(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val capacity: Int = 200,
    override val group: Group = Group.LIFECYCLE,
) : FieldSource {

    private val entries = ArrayDeque<Pair<Long, String>>()
    private val lock = Any()

    fun add(message: String) {
        synchronized(lock) {
            entries.addLast(clock.millis() to message)
            while (entries.size > capacity) entries.removeFirst()
        }
    }

    /** Newest entries first, so the car screen shows the latest events on the first page. */
    override fun read(): List<Field> {
        val copy = synchronized(lock) { entries.toList() }
        if (copy.isEmpty()) return listOf(Field.ok("Log", "empty", SOURCE))
        return copy.asReversed().map { (time, message) -> Field.ok(formatTime(time), message, SOURCE) }
    }

    private fun formatTime(millis: Long): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        const val SOURCE = "Session/Screen lifecycle"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
