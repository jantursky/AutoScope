package com.jantursky.autoscope.phone

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status

internal const val TOUCH_TESTER_LABEL = "Touch tester"

/** A car value that has no reading, with the short reason and how calmly to show it. */
data class MissingValue(val label: String, val reason: String, val style: StatusStyle)

/**
 * One-glance summary of how well this car session works. [tone] is amber only for real problems (a missing
 * permission, a touch tester without a surface); values the car simply does not report are listed but calm.
 */
data class CarVerdict(
    val word: String,
    val tone: Tone,
    val summary: String,
    val link: LinkKind,
    val hardwareOk: Int,
    val hardwareTotal: Int,
    val missing: List<MissingValue>,
    val touch: String,
    val touchStyle: StatusStyle,
)

/** Everything the Car tab draws, computed together so one state change updates the page once. */
data class CarPageModel(val picture: CarPicture, val verdict: CarVerdict, val wifi: WifiQuality)

fun carPageModel(telemetry: CarTelemetry, snapshot: Snapshot) =
    CarPageModel(carPicture(telemetry, snapshot), carVerdict(snapshot, telemetry), wifiQuality(snapshot))

fun carVerdict(snapshot: Snapshot, telemetry: CarTelemetry): CarVerdict {
    val link = linkKind(snapshot)
    val hardware = snapshot.fields(Group.HARDWARE)
    val touch = snapshot.fields(Group.TOUCH).firstOrNull { it.label == TOUCH_TESTER_LABEL }
    val connected = link != LinkKind.NONE
    val missing = if (connected) missingValues(telemetry) else emptyList()
    val touchProblem = connected && touch != null && touch.status == Status.UNAVAILABLE && touch.reason != NO_CAR_SESSION
    // Connected, but the car-app side never started: the host only runs AutoScope as a media app (a sideloaded
    // Car App Library app is refused by real cars), so no car value can exist.
    val mediaOnly = connected && hardware.isNotEmpty() && hardware.all { it.reason == NO_CAR_SESSION }
    val problem = touchProblem || missing.any { it.style == StatusStyle.WARNING }
    val (word, tone, summary) = when {
        !connected -> Triple("No car session", Tone.IDLE, "Connect to Android Auto to check this car")
        mediaOnly -> Triple("Media only", Tone.IDLE, "Car data needs the car app, which this car does not start")
        problem -> Triple("Partly working", Tone.WARNING, "Some values need attention")
        else -> Triple("Ready", Tone.GOOD, "No problems found")
    }
    return CarVerdict(
        word = word,
        tone = tone,
        summary = summary,
        link = link,
        hardwareOk = hardware.count { it.status == Status.OK },
        hardwareTotal = hardware.size,
        missing = missing,
        touch = touchText(touch),
        touchStyle = touch?.let(::statusStyle) ?: StatusStyle.MUTED,
    )
}

/** Rows of the verdict card; none without a car session, where the verdict word says it all. */
fun verdictRows(verdict: CarVerdict): List<RowModel> {
    if (verdict.link == LinkKind.NONE) return emptyList()
    val count = if (verdict.hardwareTotal == 0) "-" else "${verdict.hardwareOk} of ${verdict.hardwareTotal} OK"
    return listOf(
        RowModel("link", "Link", linkText(verdict.link), null, StatusStyle.NONE),
        RowModel("values", "Car values", count, null, StatusStyle.NONE),
        RowModel("touch", TOUCH_TESTER_LABEL, verdict.touch, null, verdict.touchStyle),
    ) + verdict.missing.map { RowModel("missing:${it.label}", it.label, it.reason, null, it.style) }
}

private fun linkText(link: LinkKind) = when (link) {
    LinkKind.PROJECTION -> "Android Auto (projection)"
    LinkKind.NATIVE -> "Android Automotive (native)"
    LinkKind.NONE -> "Not connected"
}

private fun touchText(touch: Field?): String = when {
    touch == null -> "not reported"
    touch.status == Status.OK -> touch.value ?: "-"
    else -> shortReason(touch.status, touch.reason, unavailable = "not available")
}

private fun missingValues(t: CarTelemetry): List<MissingValue> {
    val energy = if (t.batteryFraction.ok || !t.fuelFraction.ok) t.batteryFraction else t.fuelFraction
    val port = if (t.chargePortOpen.ok) t.chargePortOpen else t.chargePortConnected
    val readings = listOf(
        "Car model" to t.make, "Heading" to t.headingDegrees, "Speed" to t.speedMetersPerSecond,
        "Energy level" to energy, "Range" to t.rangeMeters, "EV charge port" to port, "G-force" to t.gForceX,
    )
    return readings.filterNot { it.second.ok }.map { (label, reading) ->
        MissingValue(label, shortReason(reading), statusStyle(reading.status, reading.reason))
    }
}

private fun shortReason(reading: Reading<*>) = shortReason(reading.status, reading.reason)

/** Few-word reason: the full texts live in the group pages. */
internal fun shortReason(status: Status, reason: String?, unavailable: String = "not reported"): String = when (status) {
    Status.OK -> "OK"
    Status.PERMISSION_MISSING -> "permission missing"
    Status.NOT_SUPPORTED_BY_HOST -> "not supported by host"
    Status.UNAVAILABLE -> if (reason == NO_CAR_SESSION) "no car session" else unavailable
}
