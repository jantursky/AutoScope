package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status

/** Raw battery broadcast values. [tenthsOfCelsius] is null when the device reports none. */
data class BatteryReading(val status: Int, val level: Int, val scale: Int, val tenthsOfCelsius: Int?)

interface BatteryAdapter {
    /** Latest sticky ACTION_BATTERY_CHANGED values, null when none is available. */
    fun read(): BatteryReading?
}

/** ACTION_BATTERY_CHANGED is a sticky broadcast: no permission needed. */
class BatterySource(private val adapter: BatteryAdapter) : FieldSource {
    override val group = Group.PHONE

    override fun read(): List<Field> {
        val reading = try {
            adapter.read()
        } catch (e: RuntimeException) {
            null
        } ?: return LABELS.map { Field.unavailable(it, SOURCE, "No battery broadcast") }
        val percent = if (reading.scale > 0) reading.level * 100 / reading.scale else null
        return listOf(
            Field.ok(LABELS[0], "${batteryStatusName(reading.status)}, ${percent?.let { "$it%" } ?: "level unknown"}", SOURCE),
            reading.tenthsOfCelsius
                ?.let { Field.ok(LABELS[1], formatTemperature(it), SOURCE) }
                ?: Field.unavailable(LABELS[1], SOURCE, "Not reported by the device"),
        )
    }

    private companion object {
        const val SOURCE = "ACTION_BATTERY_CHANGED"
        val LABELS = listOf("Battery", "Battery temperature")
    }
}

interface PowerAdapter {
    /** Null when the PowerManager service is missing. API 29+ only. */
    fun thermalStatus(): Int?

    /** Null when the PowerManager service is missing. */
    fun isPowerSaveMode(): Boolean?
}

class PowerSource(private val adapter: PowerAdapter, private val sdkInt: Int) : FieldSource {
    override val group = Group.PHONE

    override fun read(): List<Field> = listOf(
        if (sdkInt < THERMAL_MIN_SDK) {
            Field.notSupported("Thermal status", THERMAL_SOURCE, "Requires Android 10 (API 29), this is API $sdkInt")
        } else {
            guarded("Thermal status", THERMAL_SOURCE) {
                adapter.thermalStatus()
                    ?.let { Field.ok("Thermal status", thermalStatusName(it), THERMAL_SOURCE) }
                    ?: Field.unavailable("Thermal status", THERMAL_SOURCE, "PowerManager service missing")
            }
        },
        guarded("Power save mode", POWER_SAVE_SOURCE) {
            adapter.isPowerSaveMode()
                ?.let { Field.ok("Power save mode", if (it) "On" else "Off", POWER_SAVE_SOURCE) }
                ?: Field.unavailable("Power save mode", POWER_SAVE_SOURCE, "PowerManager service missing")
        },
    )

    private companion object {
        const val THERMAL_MIN_SDK = 29
        const val THERMAL_SOURCE = "PowerManager.getCurrentThermalStatus"
        const val POWER_SAVE_SOURCE = "PowerManager.isPowerSaveMode"
    }
}

interface BluetoothAdapterAccess {
    /** Adapter state, or null when the phone has no Bluetooth adapter. May throw SecurityException. */
    fun state(): Int?
}

/**
 * BluetoothAdapter.getState has no permission requirement in the SDK, so BLUETOOTH_CONNECT is not declared.
 * If a vendor build throws SecurityException anyway, the field reports PERMISSION_MISSING.
 */
class BluetoothSource(private val adapter: BluetoothAdapterAccess) : FieldSource {
    override val group = Group.PHONE

    override fun read(): List<Field> = listOf(
        guarded("Bluetooth", SOURCE) {
            adapter.state()
                ?.let { Field.ok("Bluetooth", bluetoothStateName(it), SOURCE) }
                ?: Field.unavailable("Bluetooth", SOURCE, "No Bluetooth adapter")
        },
    )

    private companion object {
        const val SOURCE = "BluetoothAdapter.getState"
    }
}

/** Wi-Fi link of the connected network. Frequency and speed are -1 when the platform does not know them. */
data class WifiLink(val frequencyMhz: Int, val linkSpeedMbps: Int, val standard: Int?)

interface WifiAdapter {
    /** Null when the Wi-Fi service is missing. Needs ACCESS_WIFI_STATE. */
    fun isEnabled(): Boolean?

    /** Link of the connected Wi-Fi network, null when not connected to Wi-Fi. Needs ACCESS_NETWORK_STATE. */
    fun connectedLink(): WifiLink?
}

/**
 * Wi-Fi state, band and link speed. These matter for wireless Android Auto, which streams over a
 * 5 GHz Wi-Fi link: a 2.4 GHz band or a low link speed explains stutter.
 */
class WifiSource(private val adapter: WifiAdapter) : FieldSource {
    override val group = Group.PHONE

    override fun read(): List<Field> {
        val enabled = try {
            adapter.isEnabled()
        } catch (e: SecurityException) {
            return LABELS.map { permissionField(it, e) }
        } catch (e: RuntimeException) {
            return LABELS.map { Field.unavailable(it, STATE_SOURCE, "${e::class.simpleName}: ${e.message}") }
        } ?: return LABELS.map { Field.unavailable(it, STATE_SOURCE, "Wi-Fi service missing") }

        if (!enabled) return listOf(Field.ok(LABELS[0], "Off", STATE_SOURCE)) + notConnected("Wi-Fi is off")

        val link = try {
            adapter.connectedLink()
        } catch (e: SecurityException) {
            return listOf(Field.ok(LABELS[0], "On", STATE_SOURCE)) + LABELS.drop(1).map { permissionField(it, e) }
        } catch (e: RuntimeException) {
            return listOf(Field.ok(LABELS[0], "On", STATE_SOURCE)) +
                LABELS.drop(1).map { Field.unavailable(it, LINK_SOURCE, "${e::class.simpleName}: ${e.message}") }
        } ?: return listOf(Field.ok(LABELS[0], "On, not connected", STATE_SOURCE)) + notConnected("Not connected to Wi-Fi")

        return listOf(
            Field.ok(LABELS[0], "On, connected", STATE_SOURCE),
            link.frequencyMhz.takeIf { it > 0 }
                ?.let { Field.ok(LABELS[1], wifiBand(it) ?: "Unknown band ($it MHz)", LINK_SOURCE) }
                ?: Field.unavailable(LABELS[1], LINK_SOURCE, "Frequency not reported"),
            link.linkSpeedMbps.takeIf { it > 0 }
                ?.let { Field.ok(LABELS[2], speedText(it, link.standard), LINK_SOURCE) }
                ?: Field.unavailable(LABELS[2], LINK_SOURCE, "Link speed not reported"),
        )
    }

    private fun speedText(mbps: Int, standard: Int?): String {
        val name = standard?.let(::wifiStandardName)
        return if (name == null) "$mbps Mbps" else "$mbps Mbps, $name"
    }

    private fun notConnected(reason: String) = LABELS.drop(1).map { Field.unavailable(it, LINK_SOURCE, reason) }

    private fun permissionField(label: String, e: SecurityException) =
        Field(label, null, STATE_SOURCE, Status.PERMISSION_MISSING, "SecurityException: ${e.message}")

    private companion object {
        const val STATE_SOURCE = "WifiManager.isWifiEnabled"
        const val LINK_SOURCE = "WifiInfo.getFrequency, getLinkSpeed, getWifiStandard"
        val LABELS = listOf("Wi-Fi", "Wi-Fi band", "Wi-Fi link speed")
    }
}

data class MemoryReading(val availBytes: Long, val totalBytes: Long, val lowMemory: Boolean)

interface MemoryAdapter {
    /** Null when the ActivityManager service is missing. */
    fun read(): MemoryReading?
}

class MemorySource(private val adapter: MemoryAdapter) : FieldSource {
    override val group = Group.PHONE

    override fun read(): List<Field> {
        val reading = try {
            adapter.read()
        } catch (e: RuntimeException) {
            null
        } ?: return LABELS.map { Field.unavailable(it, SOURCE, "ActivityManager service missing") }
        return listOf(
            Field.ok(LABELS[0], "${formatBytes(reading.availBytes)} of ${formatBytes(reading.totalBytes)}", SOURCE),
            Field.ok(LABELS[1], if (reading.lowMemory) "Yes" else "No", SOURCE),
        )
    }

    private companion object {
        const val SOURCE = "ActivityManager.getMemoryInfo"
        val LABELS = listOf("Free memory", "Low memory")
    }
}
