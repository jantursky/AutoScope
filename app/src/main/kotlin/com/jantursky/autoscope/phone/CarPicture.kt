package com.jantursky.autoscope.phone

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Snapshot
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pure mapping from typed car values and the report to what the Car tab draws and lists. */

enum class LinkKind { PROJECTION, NATIVE, NONE }

enum class EnergyKind(val label: String) { BATTERY("Battery"), FUEL("Fuel") }

data class Energy(val kind: EnergyKind, val fraction: Float) {
    val percent: Int get() = (fraction * 100).roundToInt()
}

/** [CLOSED] is a known port that is neither open nor plugged in. */
enum class ChargePort { UNKNOWN, CLOSED, OPEN, CONNECTED }

/** Acceleration in g, scaled down to the unit circle when stronger than 1 g. x is right, y is up. */
data class GForce(val x: Float, val y: Float)

data class CarPicture(
    val title: String,
    val subtitle: String?,
    val headingDegrees: Float?,
    val link: LinkKind,
    val energy: Energy?,
    val rangeKm: Int?,
    val chargePort: ChargePort,
    val speedKmh: Int?,
    val gForce: GForce?,
    val rows: List<RowModel>,
)

private const val NO_SESSION_HINT = "Connect to Android Auto to see live car data"
private const val UNNAMED_CAR = "Car model not reported"
private const val MS_TO_KMH = 3.6f
private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun linkKind(snapshot: Snapshot): LinkKind = when (connectionPill(snapshot).text) {
    "Projection" -> LinkKind.PROJECTION
    "Native" -> LinkKind.NATIVE
    else -> LinkKind.NONE
}

/** Smallest signed turn in degrees (-180..180) that takes [from] to [to]. */
fun shortestTurn(from: Float, to: Float): Float = ((to - from + 180f) % 360f + 360f) % 360f - 180f

fun carPicture(telemetry: CarTelemetry, snapshot: Snapshot): CarPicture {
    val t = telemetry
    val readings = listOf(
        t.make, t.model, t.year, t.headingDegrees, t.speedMetersPerSecond, t.fuelFraction, t.batteryFraction,
        t.rangeMeters, t.chargePortOpen, t.chargePortConnected, t.gForceX, t.gForceY,
    )
    val noSession = readings.none { it.ok || it.reason != NO_CAR_SESSION }
    val name = listOfNotNull(t.make.valueIfOk(), t.model.valueIfOk(), t.year.valueIfOk()?.toString())
        .filter { it.isNotBlank() }.joinToString(" ")
    val energy = energy(t)
    val rangeKm = t.rangeMeters.valueIfOk()?.let { (it / 1000f).roundToInt() }
    val port = chargePort(t)
    val speedKmh = t.speedMetersPerSecond.valueIfOk()?.let { (it * MS_TO_KMH).roundToInt() }
    val g = gForce(t)
    val heading = t.headingDegrees.valueIfOk()
    return CarPicture(
        title = when {
            noSession -> NO_CAR_SESSION
            name.isEmpty() -> UNNAMED_CAR
            else -> name
        },
        subtitle = NO_SESSION_HINT.takeIf { noSession },
        headingDegrees = heading,
        link = linkKind(snapshot),
        energy = energy,
        rangeKm = rangeKm,
        chargePort = port,
        speedKmh = speedKmh,
        gForce = g,
        rows = listOf(
            row("Make", t.make, t.make.valueIfOk()),
            row("Model", t.model, t.model.valueIfOk()),
            row("Year", t.year, t.year.valueIfOk()?.toString()),
            row("Heading", t.headingDegrees, heading?.let(::headingText)),
            row("Speed", t.speedMetersPerSecond, speedKmh?.let { "$it km/h" }),
            row("Energy", if (t.batteryFraction.ok || !t.fuelFraction.ok) t.batteryFraction else t.fuelFraction,
                energy?.let { "${it.kind.label} ${it.percent}%" }),
            row("Range", t.rangeMeters, rangeKm?.let { "$it km" }),
            row("Charge port", if (t.chargePortOpen.ok) t.chargePortOpen else t.chargePortConnected, chargePortText(port, t)),
            row("G-force", if (t.gForceX.ok) t.gForceY else t.gForceX, gForceText(t)),
        ),
    )
}

private fun <T> Reading<T>.valueIfOk(): T? = value.takeIf { ok }

private fun energy(t: CarTelemetry): Energy? {
    t.batteryFraction.valueIfOk()?.let { return Energy(EnergyKind.BATTERY, it.coerceIn(0f, 1f)) }
    return t.fuelFraction.valueIfOk()?.let { Energy(EnergyKind.FUEL, it.coerceIn(0f, 1f)) }
}

private fun chargePort(t: CarTelemetry): ChargePort = when {
    t.chargePortConnected.valueIfOk() == true -> ChargePort.CONNECTED
    t.chargePortOpen.valueIfOk() == true -> ChargePort.OPEN
    t.chargePortOpen.ok || t.chargePortConnected.ok -> ChargePort.CLOSED
    else -> ChargePort.UNKNOWN
}

private fun chargePortText(port: ChargePort, t: CarTelemetry): String? = when (port) {
    ChargePort.UNKNOWN -> null
    ChargePort.CONNECTED -> "Connected"
    ChargePort.OPEN -> "Open"
    ChargePort.CLOSED -> if (t.chargePortOpen.ok) "Closed" else "Not connected"
}

private fun gForce(t: CarTelemetry): GForce? {
    val x = t.gForceX.valueIfOk() ?: return null
    val y = t.gForceY.valueIfOk() ?: return null
    val strength = hypot(x, y)
    return if (strength > 1f) GForce(x / strength, y / strength) else GForce(x, y)
}

private fun gForceText(t: CarTelemetry): String? {
    val x = t.gForceX.valueIfOk() ?: return null
    val y = t.gForceY.valueIfOk() ?: return null
    return String.format(Locale.ROOT, "x %.2f, y %.2f g", x, y)
}

private fun headingText(degrees: Float): String {
    val normalized = Math.floorMod(degrees.roundToInt(), 360)
    return "$normalized° ${COMPASS[((normalized + 22) / 45) % 8]}"
}

/** [text] non-null means the reading is usable; otherwise the row shows why it is missing. */
private fun row(label: String, source: Reading<*>, text: String?) = RowModel(
    key = label,
    label = label,
    value = text ?: "-",
    reason = source.reason.takeIf { text == null },
    style = if (text == null) statusStyle(source.status, source.reason) else StatusStyle.NONE,
)
