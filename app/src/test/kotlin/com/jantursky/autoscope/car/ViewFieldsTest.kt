package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import com.jantursky.autoscope.phone.BluetoothAdapterAccess
import com.jantursky.autoscope.phone.BluetoothSource
import com.jantursky.autoscope.phone.WifiAdapter
import com.jantursky.autoscope.phone.WifiLink
import com.jantursky.autoscope.phone.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewFieldsTest {

    private val context = RowContext(8, emptyList(), null)

    private fun ok(label: String) = Field.ok(label, "v", "t")

    private val snapshot = Snapshot(
        mapOf(
            Group.CONNECTION to listOf(ok("Connection type")),
            Group.PHONE to listOf(ok("Battery"), ok("Wi-Fi"), ok("Wi-Fi band"), ok("Wi-Fi link speed"), ok("Bluetooth")),
            Group.HARDWARE to listOf(
                ok("Make"), ok("Raw speed"), ok("Accelerometer"), ok("Gyroscope"), ok("Compass"), ok("Car location"),
            ),
            Group.PERMISSIONS to listOf(
                ok("Car speed"),
                Field("Location", null, "t", Status.PERMISSION_MISSING, "Tap Grant permissions"),
            ),
        ),
    )

    private fun titles(view: CarView, ctx: RowContext = context) = ViewRows.rows(view, snapshot, ctx).map { it.title }

    @Test
    fun `sensors shows only the four sensor fields`() {
        assertEquals(listOf("Accelerometer", "Gyroscope", "Compass", "Car location"), titles(CarView.SENSORS))
    }

    @Test
    fun `hardware leaves the sensors to their own view`() {
        assertEquals(listOf("Grant permissions", "Make", "Raw speed"), titles(CarView.HARDWARE))
    }

    @Test
    fun `network shows the connection group and the phone radios`() {
        assertEquals(
            listOf("Connection type", "Wi-Fi", "Wi-Fi band", "Wi-Fi link speed", "Bluetooth"),
            titles(CarView.NETWORK),
        )
    }

    @Test
    fun `overview keeps the connection but leaves the radios to the network view`() {
        assertEquals(
            listOf("AutoScope diagnostic tool", "Mark this moment", "Connection type", "Battery"),
            titles(CarView.OVERVIEW),
        )
    }

    @Test
    fun `permissions lists the group behind a grant row that names what is missing`() {
        val rows = ViewRows.rows(CarView.PERMISSIONS, snapshot, RowContext(8, listOf(CarPermissions.FINE_LOCATION), null))

        assertEquals(listOf("Grant permissions", "Car speed", "Location"), rows.map { it.title })
        assertEquals(RowAction.GrantPermissions, rows[0].action)
        assertEquals("Missing: ACCESS_FINE_LOCATION", rows[0].lines.single())
        assertTrue(rows[2].lines.single().startsWith("PERMISSION_MISSING"))
    }

    @Test
    fun `granting keeps the permissions row count and titles`() {
        val missing = titles(CarView.PERMISSIONS, RowContext(8, CarPermissions.HARDWARE, null))
        assertEquals(missing, titles(CarView.PERMISSIONS))
        assertEquals("All granted", ViewRows.rows(CarView.PERMISSIONS, snapshot, context)[0].lines.single())
    }

    @Test
    fun `visual picks its three fields in a fixed order`() {
        assertEquals(listOf("Compass", "Accelerometer", "Raw speed"), titles(CarView.VISUAL))
    }

    @Test
    fun `a view changes only with its selected fields`() {
        val otherPhoneField = Snapshot(snapshot.groups + (Group.PHONE to listOf(ok("Battery"), ok("Wi-Fi"))))
        assertNotEquals(CarView.NETWORK.visible(snapshot), CarView.NETWORK.visible(otherPhoneField))

        val batteryChanged = Snapshot(
            snapshot.groups + (Group.PHONE to snapshot.fields(Group.PHONE).map {
                if (it.label == "Battery") Field.unavailable("Battery", "t", "gone") else it
            }),
        )
        assertEquals(CarView.NETWORK.visible(snapshot), CarView.NETWORK.visible(batteryChanged))
    }

    @Test
    fun `network labels are the ones the phone sources produce`() {
        val wifi = WifiSource(object : WifiAdapter {
            override fun isEnabled() = true
            override fun connectedLink() = WifiLink(5180, 400, null)
        }).read()
        val bluetooth = BluetoothSource(object : BluetoothAdapterAccess {
            override fun state() = 12
        }).read()

        assertEquals(ViewFields.NETWORK_LABELS, (wifi + bluetooth).map { it.label }.toSet())
    }

    @Test
    fun `sensor and visual labels exist in the hardware items`() {
        val hardwareLabels = HardwareFeature.entries.flatMap { it.labels }.toSet()
        assertTrue(hardwareLabels.containsAll(ViewFields.SENSOR_LABELS))
        assertTrue(hardwareLabels.containsAll(ViewFields.VISUAL_LABELS))
        assertEquals(4, ViewFields.SENSOR_LABELS.size)
    }
}
