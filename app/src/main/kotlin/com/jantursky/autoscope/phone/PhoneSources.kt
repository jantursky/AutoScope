package com.jantursky.autoscope.phone

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.jantursky.autoscope.core.FieldSource

/**
 * Field sources that need only the phone. Call on the main thread (starts the car connection observer).
 *
 * Permissions: ACCESS_WIFI_STATE and ACCESS_NETWORK_STATE (Wi-Fi); none for the rest.
 */
object PhoneSources {
    fun create(context: Context): List<FieldSource> {
        val app = context.applicationContext
        return listOf(
            CarConnectionSource(CarConnectionMonitor.shared(app).tracker, SystemClock::elapsedRealtime),
            HostSource(AndroidHostEnvironment(app)),
            BatterySource(AndroidBatteryAdapter(app)),
            PowerSource(AndroidPowerAdapter(app), Build.VERSION.SDK_INT),
            BluetoothSource(AndroidBluetoothAdapter(app)),
            WifiSource(AndroidWifiAdapter(app)),
            MemorySource(AndroidMemoryAdapter(app)),
        )
    }
}
