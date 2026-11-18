package com.jantursky.autoscope.phone

import android.bluetooth.BluetoothAdapter
import android.net.wifi.ScanResult
import android.os.BatteryManager
import android.os.PowerManager
import androidx.car.app.connection.CarConnection
import java.util.Locale

/** Pure formatting and mapping functions. No platform calls, only platform constants. */

fun connectionTypeName(type: Int): String = when (type) {
    CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "Not connected"
    CarConnection.CONNECTION_TYPE_NATIVE -> "Native (Android Automotive OS)"
    CarConnection.CONNECTION_TYPE_PROJECTION -> "Projection (Android Auto)"
    else -> "Unknown ($type)"
}

fun isConnected(type: Int): Boolean = type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED

/** "45s", "2m 05s", "1h 02m 03s". */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.ROOT, "%dm %02ds", minutes, seconds)
        else -> "${seconds}s"
    }
}

/** Band for a Wi-Fi centre frequency in MHz, or null when it fits no known band. */
fun wifiBand(frequencyMhz: Int): String? = when (frequencyMhz) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> null
}

fun wifiStandardName(standard: Int): String? = when (standard) {
    ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
    ScanResult.WIFI_STANDARD_11AD -> "802.11ad"
    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
    else -> null
}

fun batteryStatusName(status: Int): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    else -> "Unknown"
}

fun thermalStatusName(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "None"
    PowerManager.THERMAL_STATUS_LIGHT -> "Light"
    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
    PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
    else -> "Unknown ($status)"
}

fun bluetoothStateName(state: Int): String = when (state) {
    BluetoothAdapter.STATE_OFF -> "Off"
    BluetoothAdapter.STATE_TURNING_ON -> "Turning on"
    BluetoothAdapter.STATE_ON -> "On"
    BluetoothAdapter.STATE_TURNING_OFF -> "Turning off"
    else -> "Unknown ($state)"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= GIB -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / GIB)
    else -> String.format(Locale.ROOT, "%d MB", bytes / MIB)
}

fun formatTemperature(tenthsOfCelsius: Int): String =
    String.format(Locale.ROOT, "%.1f °C", tenthsOfCelsius / 10.0)

private const val MIB = 1024L * 1024
private const val GIB = 1024L * MIB
