package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status

/** One gauge tile: a value to draw, or why there is none. */
sealed interface Tile<out T> {
    data class Shown<T>(val value: T) : Tile<T>
    data class NoData(val reason: String) : Tile<Nothing>
}

/** What the Visual screen draws, built from the HARDWARE fields and the raw [SensorSamples]. */
data class VisualModel(
    val heading: Tile<Float>,
    val force: Tile<Force>,
    val speedKmh: Tile<Float>,
    /** Speed in km/h over the last minute, oldest first. */
    val speedHistory: Tile<List<Sample>>,
) {
    companion object {
        const val COMPASS = "Compass"
        const val ACCELEROMETER = "Accelerometer"
        const val RAW_SPEED = "Raw speed"

        const val PERMISSION_MISSING = "permission missing"
        const val UNSUPPORTED = "unsupported"
        const val NO_VALUE = "no value"

        /**
         * The field status decides whether a tile has data, so the tiles say the same as the rows; a raw
         * value is drawn only while its field is OK.
         */
        fun build(
            hardware: List<Field>,
            heading: Float?,
            force: Force?,
            speedMetersPerSecond: Float?,
            speedHistory: List<Sample>,
        ): VisualModel {
            fun field(label: String) = hardware.firstOrNull { it.label == label }
            val speed = field(RAW_SPEED)
            return VisualModel(
                heading = tile(field(COMPASS), heading),
                force = tile(field(ACCELEROMETER), force),
                speedKmh = tile(speed, speedMetersPerSecond?.let(Gauges::kmh)),
                speedHistory = tile(
                    speed,
                    speedHistory.map { it.copy(value = Gauges.kmh(it.value)) }.ifEmpty { null },
                ),
            )
        }

        fun <T> tile(field: Field?, value: T?): Tile<T> {
            val reason = when (field?.status) {
                Status.OK -> if (value == null) NO_VALUE else null
                Status.PERMISSION_MISSING -> PERMISSION_MISSING
                Status.NOT_SUPPORTED_BY_HOST -> UNSUPPORTED
                Status.UNAVAILABLE, null -> NO_VALUE
            }
            return if (reason == null && value != null) Tile.Shown(value) else Tile.NoData(reason ?: NO_VALUE)
        }
    }
}
