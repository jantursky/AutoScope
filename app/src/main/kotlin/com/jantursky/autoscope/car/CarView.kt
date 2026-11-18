package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/** What a view's screen is, which decides how deep it may be pushed (see [Navigation]). */
enum class ViewKind { LIST, PANE, MAP }

/**
 * The car views. TabTemplate allows at most four tabs (TabsConstraints in 1.7.0): Overview, Screen,
 * Hardware and More. More is a list of rows that each open one of [MORE_ENTRIES].
 */
enum class CarView(val title: String, val summary: String, val kind: ViewKind, val groups: List<Group>) {
    OVERVIEW("Overview", "Host, connection, phone and performance", ViewKind.LIST,
        listOf(Group.CONNECTION, Group.HOST, Group.PHONE, Group.PERFORMANCE)),
    SCREEN("Screen", "Display and template limits", ViewKind.LIST, listOf(Group.SCREEN, Group.CONSTRAINTS)),
    HARDWARE("Hardware", "Car model, speed, energy and mileage", ViewKind.LIST, listOf(Group.HARDWARE)),
    MORE("More", "Touch, visual, sensors, network, permissions, log", ViewKind.LIST, emptyList()),
    TOUCH("Touch", "Touch tester and tap latency", ViewKind.PANE, listOf(Group.TOUCH)),
    VISUAL("Visual", "Compass, g-force and speed gauges", ViewKind.MAP, listOf(Group.HARDWARE)),
    SENSORS("Sensors", "Accelerometer, gyroscope, compass, location", ViewKind.LIST, listOf(Group.HARDWARE)),
    NETWORK("Network", "Car connection, Wi-Fi and Bluetooth", ViewKind.LIST, listOf(Group.CONNECTION, Group.PHONE)),
    PERMISSIONS("Permissions", "Granted and missing permissions", ViewKind.LIST, listOf(Group.PERMISSIONS)),
    LOG("Log", "Session, screen and configuration events", ViewKind.LIST, listOf(Group.LIFECYCLE)),
    ;

    /** The fields this view shows, in group order. */
    fun fields(snapshot: Snapshot): List<Field> = groups.flatMap { ViewFields.select(this, it, snapshot.fields(it)) }

    /**
     * The part of [snapshot] whose change refreshes this view. Live values ([LiveFields]) are left out, so
     * the host does not scroll the list back to the top on every tick.
     */
    fun visible(snapshot: Snapshot): List<List<Field>> =
        groups.map { LiveFields.structure(it, ViewFields.select(this, it, snapshot.fields(it))) }

    companion object {
        val TABS = listOf(OVERVIEW, SCREEN, HARDWARE, MORE)
        val MORE_ENTRIES = listOf(TOUCH, VISUAL, SENSORS, NETWORK, PERMISSIONS, LOG)
    }
}

/**
 * Which fields of a group each view shows. The sensors and the network fields have views of their own
 * in More, so Hardware and Overview leave them out.
 */
object ViewFields {

    val SENSOR_LABELS: Set<String> = listOf(
        HardwareFeature.ACCELEROMETER,
        HardwareFeature.GYROSCOPE,
        HardwareFeature.COMPASS,
        HardwareFeature.LOCATION,
    ).flatMap { it.labels }.toSet()

    /** The PHONE fields about the phone's radios, as WifiSource and BluetoothSource label them. */
    val NETWORK_LABELS: Set<String> = setOf("Wi-Fi", "Wi-Fi band", "Wi-Fi link speed", "Bluetooth")

    /** The HARDWARE fields the Visual screen draws. */
    val VISUAL_LABELS: List<String> = listOf(VisualModel.COMPASS, VisualModel.ACCELEROMETER, VisualModel.RAW_SPEED)

    fun select(view: CarView, group: Group, fields: List<Field>): List<Field> = when (view) {
        CarView.OVERVIEW -> if (group == Group.PHONE) fields.filterNot { it.label in NETWORK_LABELS } else fields
        CarView.HARDWARE -> fields.filterNot { it.label in SENSOR_LABELS }
        CarView.SENSORS -> fields.filter { it.label in SENSOR_LABELS }
        CarView.NETWORK -> if (group == Group.PHONE) fields.filter { it.label in NETWORK_LABELS } else fields
        CarView.VISUAL -> VISUAL_LABELS.mapNotNull { label -> fields.firstOrNull { it.label == label } }
        CarView.SCREEN, CarView.MORE, CarView.TOUCH, CarView.PERMISSIONS, CarView.LOG -> fields
    }
}
