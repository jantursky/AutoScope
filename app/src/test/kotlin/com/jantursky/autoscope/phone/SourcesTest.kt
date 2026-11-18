package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun List<Field>.statuses() = map { it.status }
private fun List<Field>.byLabel(label: String) = first { it.label == label }

class HostSourceTest {
    private class Env(
        val library: String? = "1.7.0",
        val auto: () -> AppVersion? = { AppVersion("12.0", 120000) },
    ) : HostEnvironment {
        override fun carAppLibraryVersion() = library
        override fun packageVersion(packageName: String) = auto()
        override val device = DeviceInfo("14", 34, "Acme", "Phone 1")
    }

    @Test fun allFieldsWhenInstalled() {
        val fields = HostSource(Env()).read()
        assertTrue(fields.all { it.status == Status.OK })
        assertEquals("12.0", fields.byLabel("Android Auto version name").value)
        assertEquals("14 (API 34)", fields.byLabel("Phone Android").value)
    }

    @Test fun missingAndroidAutoIsUnavailable() {
        val field = HostSource(Env(auto = { null })).read().byLabel("Android Auto version name")
        assertEquals(Status.UNAVAILABLE, field.status)
        assertEquals("Android Auto not installed", field.reason)
    }

    @Test fun securityExceptionIsPermissionMissing() {
        val fields = HostSource(Env(auto = { throw SecurityException("no") })).read()
        assertEquals(Status.PERMISSION_MISSING, fields.byLabel("Android Auto version code").status)
    }

    @Test fun unreadableLibraryVersionIsUnavailable() {
        assertEquals(Status.UNAVAILABLE, HostSource(Env(library = null)).read().byLabel("Car App Library version").status)
    }
}

class BatterySourceTest {
    private fun source(reading: () -> BatteryReading?) = BatterySource(object : BatteryAdapter {
        override fun read() = reading()
    })

    @Test fun formatsStatusPercentAndTemperature() {
        val fields = source { BatteryReading(2, 80, 100, 315) }.read()
        assertEquals("Charging, 80%", fields[0].value)
        assertEquals("31.5 °C", fields[1].value)
    }

    @Test fun missingBroadcastIsUnavailable() {
        assertEquals(listOf(Status.UNAVAILABLE, Status.UNAVAILABLE), source { null }.read().statuses())
    }

    @Test fun unknownScaleHasNoPercent() {
        assertEquals("Full, level unknown", source { BatteryReading(5, 1, -1, null) }.read()[0].value)
        assertEquals(Status.UNAVAILABLE, source { BatteryReading(5, 1, -1, null) }.read()[1].status)
    }

    @Test fun failureIsUnavailable() {
        assertEquals(Status.UNAVAILABLE, source { throw IllegalStateException("x") }.read()[0].status)
    }
}

class PowerSourceTest {
    private class Adapter(val thermal: Int? = 0, val saver: Boolean? = false) : PowerAdapter {
        override fun thermalStatus() = thermal
        override fun isPowerSaveMode() = saver
    }

    @Test fun thermalNotSupportedBelowApi29() {
        val fields = PowerSource(Adapter(), 28).read()
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, fields.byLabel("Thermal status").status)
        assertEquals("Off", fields.byLabel("Power save mode").value)
    }

    @Test fun readsValuesOnApi29() {
        val fields = PowerSource(Adapter(thermal = 3, saver = true), 34).read()
        assertEquals("Severe", fields.byLabel("Thermal status").value)
        assertEquals("On", fields.byLabel("Power save mode").value)
    }

    @Test fun missingServiceIsUnavailable() {
        val fields = PowerSource(Adapter(null, null), 34).read()
        assertEquals(listOf(Status.UNAVAILABLE, Status.UNAVAILABLE), fields.statuses())
    }
}

class BluetoothSourceTest {
    private fun read(state: () -> Int?) = BluetoothSource(object : BluetoothAdapterAccess {
        override fun state() = state()
    }).read().single()

    @Test fun state() = assertEquals("On", read { 12 }.value)
    @Test fun noAdapter() = assertEquals(Status.UNAVAILABLE, read { null }.status)
    @Test fun securityExceptionIsPermissionMissing() =
        assertEquals(Status.PERMISSION_MISSING, read { throw SecurityException("BLUETOOTH_CONNECT") }.status)
}

class WifiSourceTest {
    private class Adapter(
        val enabled: () -> Boolean? = { true },
        val link: () -> WifiLink? = { WifiLink(5180, 866, 5) },
    ) : WifiAdapter {
        override fun isEnabled() = enabled()
        override fun connectedLink() = link()
    }

    @Test fun connectedShowsBandAndSpeed() {
        val fields = WifiSource(Adapter()).read()
        assertEquals("On, connected", fields.byLabel("Wi-Fi").value)
        assertEquals("5 GHz", fields.byLabel("Wi-Fi band").value)
        assertEquals("866 Mbps, Wi-Fi 5 (802.11ac)", fields.byLabel("Wi-Fi link speed").value)
    }

    @Test fun speedWithoutStandard() {
        val fields = WifiSource(Adapter(link = { WifiLink(2412, 72, null) })).read()
        assertEquals("72 Mbps", fields.byLabel("Wi-Fi link speed").value)
        assertEquals("2.4 GHz", fields.byLabel("Wi-Fi band").value)
    }

    @Test fun unknownLinkValuesAreUnavailable() {
        val fields = WifiSource(Adapter(link = { WifiLink(-1, -1, null) })).read()
        assertEquals(listOf(Status.OK, Status.UNAVAILABLE, Status.UNAVAILABLE), fields.statuses())
    }

    @Test fun wifiOff() {
        val fields = WifiSource(Adapter(enabled = { false })).read()
        assertEquals("Off", fields.byLabel("Wi-Fi").value)
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Wi-Fi band").status)
    }

    @Test fun notConnected() {
        val fields = WifiSource(Adapter(link = { null })).read()
        assertEquals("On, not connected", fields.byLabel("Wi-Fi").value)
    }

    @Test fun missingServiceIsUnavailable() {
        assertTrue(WifiSource(Adapter(enabled = { null })).read().all { it.status == Status.UNAVAILABLE })
    }

    @Test fun securityExceptionIsPermissionMissing() {
        assertTrue(WifiSource(Adapter(enabled = { throw SecurityException("x") })).read().all { it.status == Status.PERMISSION_MISSING })
        val fields = WifiSource(Adapter(link = { throw SecurityException("x") })).read()
        assertEquals(listOf(Status.OK, Status.PERMISSION_MISSING, Status.PERMISSION_MISSING), fields.statuses())
    }
}

class MemorySourceTest {
    private fun read(r: () -> MemoryReading?) = MemorySource(object : MemoryAdapter {
        override fun read() = r()
    }).read()

    @Test fun formats() {
        val fields = read { MemoryReading(1024L * 1024 * 1024, 4L * 1024 * 1024 * 1024, true) }
        assertEquals("1.0 GB of 4.0 GB", fields[0].value)
        assertEquals("Yes", fields[1].value)
    }

    @Test fun missingServiceIsUnavailable() = assertTrue(read { null }.all { it.status == Status.UNAVAILABLE })
}
