package com.jantursky.autoscope

import com.jantursky.autoscope.core.Status

/** One typed car value with the reason it may be missing. Same statuses as the report fields. */
data class Reading<out T>(val value: T?, val status: Status, val reason: String? = null) {
    val ok: Boolean get() = status == Status.OK && value != null

    companion object {
        fun <T> of(value: T) = Reading(value, Status.OK)
        fun <T> missing(status: Status, reason: String?) = Reading<T>(null, status, reason)
        fun <T> noSession() = Reading<T>(null, Status.UNAVAILABLE, "No car session")
    }
}

/**
 * Typed latest car values for drawing (phone Car tab, car Visual screen). Text for reports stays in the
 * Field sources; this exists because drawing needs numbers, not formatted strings.
 * Units: degrees clockwise from north, metres per second, fractions 0..1, metres, g.
 */
data class CarTelemetry(
    val make: Reading<String>,
    val model: Reading<String>,
    val year: Reading<Int>,
    val headingDegrees: Reading<Float>,
    val speedMetersPerSecond: Reading<Float>,
    val fuelFraction: Reading<Float>,
    val batteryFraction: Reading<Float>,
    val rangeMeters: Reading<Float>,
    val chargePortOpen: Reading<Boolean>,
    val chargePortConnected: Reading<Boolean>,
    val gForceX: Reading<Float>,
    val gForceY: Reading<Float>,
) {
    companion object {
        val EMPTY = CarTelemetry(
            Reading.noSession(), Reading.noSession(), Reading.noSession(), Reading.noSession(),
            Reading.noSession(), Reading.noSession(), Reading.noSession(), Reading.noSession(),
            Reading.noSession(), Reading.noSession(), Reading.noSession(), Reading.noSession(),
        )
    }
}
