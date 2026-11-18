package com.jantursky.autoscope.phone

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi

/** Platform-backed implementations of the adapter interfaces. Thin on purpose: logic lives in the sources. */

internal class AndroidHostEnvironment(private val context: Context) : HostEnvironment {
    // The library generates this string resource at build time (AppInfo.create reads the same one).
    override fun carAppLibraryVersion(): String? =
        context.getString(androidx.car.app.R.string.car_app_library_version)

    override fun packageVersion(packageName: String): AppVersion? =
        try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            AppVersion(info.versionName, info.longVersionCode)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }

    override val device = DeviceInfo(Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL)
}

internal class AndroidBatteryAdapter(private val context: Context) : BatteryAdapter {
    override fun read(): BatteryReading? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return BatteryReading(
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            tenthsOfCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
        )
    }
}

internal class AndroidPowerAdapter(context: Context) : PowerAdapter {
    private val power = context.getSystemService(PowerManager::class.java)

    // PowerSource only calls this on API 29+; the check also satisfies lint.
    override fun thermalStatus(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus else null

    override fun isPowerSaveMode(): Boolean? = power?.isPowerSaveMode
}

internal class AndroidBluetoothAdapter(context: Context) : BluetoothAdapterAccess {
    private val manager = context.getSystemService(BluetoothManager::class.java)

    override fun state(): Int? = manager?.adapter?.state
}

internal class AndroidWifiAdapter(context: Context) : WifiAdapter {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun isEnabled(): Boolean? = wifi?.isWifiEnabled

    override fun connectedLink(): WifiLink? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) transportWifiInfo() else legacyWifiInfo()
        info ?: return null
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) info.wifiStandard else null
        return WifiLink(info.frequency, info.linkSpeed, standard)
    }

    // API 29+: the WifiInfo of the Wi-Fi network, found through its NetworkCapabilities.
    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION") // allNetworks is deprecated in API 31; the active network may be cellular.
    private fun transportWifiInfo(): WifiInfo? {
        val cm = connectivity ?: return null
        return cm.allNetworks.firstNotNullOfOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@firstNotNullOfOrNull null
            caps.transportInfo as? WifiInfo
        }
    }

    // API 28 has no transport info; WifiManager.connectionInfo reports -1 values when not connected.
    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? =
        wifi?.connectionInfo?.takeIf { it.networkId != -1 }
}

internal class AndroidMemoryAdapter(context: Context) : MemoryAdapter {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    override fun read(): MemoryReading? {
        val manager = activityManager ?: return null
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return MemoryReading(info.availMem, info.totalMem, info.lowMemory)
    }
}

internal class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isGranted(permission: String) =
        context.packageManager.checkPermission(permission, context.packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
