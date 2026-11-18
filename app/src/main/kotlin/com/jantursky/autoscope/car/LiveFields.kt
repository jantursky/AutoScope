package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group

/**
 * Fields whose value changes continuously: timers, counters, memory and sensor readings. The car screens
 * show them but do not refresh for them, because every invalidate() re-sends the template and the host
 * scrolls the list back to the top. Their values update when a screen is built (tab switch, page open,
 * back navigation) or on "Refresh values".
 */
object LiveFields {

    /** Groups where every entry is live. Each log entry is a new timestamped label. */
    val GROUPS: Set<Group> = setOf(Group.LIFECYCLE)

    val LABELS: Set<String> = setOf(
        // CONNECTION and PHONE (phone side sources)
        "Time connected",
        "Battery",
        "Battery temperature",
        "Free memory",
        "Wi-Fi link speed",
        // PERFORMANCE
        "Invalidate to template",
        "Templates in the last minute",
        "Java heap",
        "Native heap",
        "Process PSS",
        // HARDWARE
        "Raw speed",
        "Display speed",
        "Odometer",
        "Battery level",
        "Fuel level",
        "Range remaining",
        "Accelerometer",
        "Gyroscope",
        "Compass",
        "Car location",
        // TOUCH; the touch tester itself still refreshes on every touch event
        "Clicks",
        "Scrolls",
        "Flings",
        "Scales",
        "Events per second",
        "Last event",
    )

    fun isLive(group: Group, field: Field): Boolean = group in GROUPS || field.label in LABELS

    /**
     * The part of [fields] whose change needs a new template: labels and statuses of all fields and the
     * values of fields that are not live. A live group contributes nothing.
     */
    fun structure(group: Group, fields: List<Field>): List<Field> =
        if (group in GROUPS) {
            emptyList()
        } else {
            fields.map { if (isLive(group, it)) it.copy(value = null, reason = null) else it }
        }
}
