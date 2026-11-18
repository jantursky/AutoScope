package com.jantursky.autoscope.car

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import java.util.Locale

/** One drawn value, or why there is none. */
sealed interface Tile<out T> {
    data class Shown<T>(val value: T) : Tile<T>
    data class NoData(val reason: String) : Tile<Nothing>
}

/** The energy the car drawing shows: fuel, or the battery when there is no fuel level. */
data class Energy(val label: String, val fraction: Float)

/** Charge port state; a null part was not reported. */
data class ChargePort(val open: Boolean?, val connected: Boolean?) {
    val lit: Boolean get() = open == true || connected == true
}

/** A text line under the car drawing; [dim] when it gives the reason for missing data. */
data class CarLine(val text: String, val dim: Boolean)

/** What the car tile draws. Each part is drawn dim when it is [Tile.NoData]. */
data class CarTile(
    /** Make, model and year, as far as the car reports them. */
    val name: Tile<String>,
    val heading: Tile<Float>,
    val speedKmh: Tile<Float>,
    val energy: Tile<Energy>,
    val rangeKm: Tile<Float>,
    val chargePort: Tile<ChargePort>,
    val force: Tile<Force>,
) {
    /** Speed, energy and charge port lines, then a line for each missing heading or g-force. */
    fun lines(): List<CarLine> = listOfNotNull(
        line("Speed", speedKmh) { String.format(Locale.US, "%.0f km/h", it) },
        energyLine(),
        line("Charge port", chargePort, ::portText),
        (heading as? Tile.NoData)?.let { CarLine("Heading: ${it.reason}", dim = true) },
        (force as? Tile.NoData)?.let { CarLine("G-force: ${it.reason}", dim = true) },
    )

    private fun energyLine(): CarLine {
        val range = (rangeKm as? Tile.Shown)?.let { String.format(Locale.US, "%.0f km", it.value) }
        return when (energy) {
            is Tile.Shown -> {
                val level = String.format(Locale.US, "%s %.0f %%", energy.value.label, energy.value.fraction * 100)
                CarLine(listOfNotNull(level, range).joinToString(", "), dim = false)
            }
            is Tile.NoData -> range?.let { CarLine("Range $it", dim = false) } ?: CarLine("Energy: ${energy.reason}", dim = true)
        }
    }

    private fun <T> line(label: String, tile: Tile<T>, format: (T) -> String): CarLine = when (tile) {
        is Tile.Shown -> CarLine(format(tile.value), dim = false)
        is Tile.NoData -> CarLine("$label: ${tile.reason}", dim = true)
    }

    private fun portText(port: ChargePort): String = listOfNotNull(
        port.open?.let { if (it) "Port open" else "Port closed" },
        port.connected?.let { if (it) "plugged in" else "unplugged" },
    ).joinToString(", ")
}

/** What the Visual screen draws, built from the [CarTelemetry] and the speed history. */
data class VisualModel(
    val heading: Tile<Float>,
    val force: Tile<Force>,
    val speedKmh: Tile<Float>,
    /** Speed in km/h over the last minute, oldest first. */
    val speedHistory: Tile<List<Sample>>,
    val car: CarTile,
) {
    companion object {
        const val COMPASS = "Compass"
        const val ACCELEROMETER = "Accelerometer"
        const val RAW_SPEED = "Raw speed"

        const val NO_VALUE = "No value yet"

        fun build(telemetry: CarTelemetry, speedHistory: List<Sample>): VisualModel {
            val heading = tile(telemetry.headingDegrees)
            val force = force(telemetry.gForceX, telemetry.gForceY)
            val speedKmh = tile(telemetry.speedMetersPerSecond).map(Gauges::kmh)
            return VisualModel(
                heading = heading,
                force = force,
                speedKmh = speedKmh,
                speedHistory = when (speedKmh) {
                    is Tile.NoData -> speedKmh
                    is Tile.Shown -> speedHistory.map { it.copy(value = Gauges.kmh(it.value)) }
                        .ifEmpty { null }?.let { Tile.Shown(it) } ?: Tile.NoData(NO_VALUE)
                },
                car = CarTile(
                    name = name(telemetry),
                    heading = heading,
                    speedKmh = speedKmh,
                    energy = energy(telemetry.fuelFraction, telemetry.batteryFraction),
                    rangeKm = tile(telemetry.rangeMeters).map { it / 1000f },
                    chargePort = chargePort(telemetry.chargePortOpen, telemetry.chargePortConnected),
                    force = force,
                ),
            )
        }

        fun <T> tile(reading: Reading<T>): Tile<T> {
            val value = reading.value
            return if (reading.ok && value != null) Tile.Shown(value) else Tile.NoData(reading.reason ?: NO_VALUE)
        }

        /** Both axes, or the reason of the first one missing. */
        fun force(x: Reading<Float>, y: Reading<Float>): Tile<Force> {
            val tx = tile(x)
            val ty = tile(y)
            return when {
                tx is Tile.NoData -> tx
                ty is Tile.NoData -> ty
                else -> Tile.Shown(Force((tx as Tile.Shown).value, (ty as Tile.Shown).value))
            }
        }

        /** Fuel when it is OK, else the battery; with neither, the reason of the fuel level. */
        fun energy(fuel: Reading<Float>, battery: Reading<Float>): Tile<Energy> {
            val f = tile(fuel)
            if (f is Tile.Shown) return Tile.Shown(Energy("Fuel", f.value))
            val b = tile(battery)
            return if (b is Tile.Shown) Tile.Shown(Energy("Battery", b.value)) else f as Tile.NoData
        }

        fun chargePort(open: Reading<Boolean>, connected: Reading<Boolean>): Tile<ChargePort> {
            val o = tile(open)
            val c = tile(connected)
            return when {
                o is Tile.NoData && c is Tile.NoData -> o
                else -> Tile.Shown(ChargePort((o as? Tile.Shown)?.value, (c as? Tile.Shown)?.value))
            }
        }

        fun name(telemetry: CarTelemetry): Tile<String> {
            val parts = listOf(telemetry.make, telemetry.model, telemetry.year).mapNotNull { (tile(it) as? Tile.Shown)?.value }
            return if (parts.isEmpty()) Tile.NoData(telemetry.make.reason ?: NO_VALUE) else Tile.Shown(parts.joinToString(" "))
        }

        private fun <T, R> Tile<T>.map(transform: (T) -> R): Tile<R> = when (this) {
            is Tile.Shown -> Tile.Shown(transform(value))
            is Tile.NoData -> this
        }
    }
}
