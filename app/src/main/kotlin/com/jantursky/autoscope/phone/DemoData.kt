package com.jantursky.autoscope.phone

import android.bluetooth.BluetoothAdapter
import android.content.res.Configuration
import android.net.wifi.ScanResult
import android.os.BatteryManager
import androidx.car.app.connection.CarConnection
import androidx.car.app.hardware.common.CarUnit
import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.car.CarHostSource
import com.jantursky.autoscope.car.CarPorts
import com.jantursky.autoscope.car.ConstraintPort
import com.jantursky.autoscope.car.ConstraintReadings
import com.jantursky.autoscope.car.ConstraintsSource
import com.jantursky.autoscope.car.DisplayInfo
import com.jantursky.autoscope.car.HardwareFeature
import com.jantursky.autoscope.car.HostIdentity
import com.jantursky.autoscope.car.HostPort
import com.jantursky.autoscope.car.ScreenSource
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import java.time.ZoneId

/**
 * A fixed fake report of a running car session, for store screenshots (`--ez demo true`). Values go
 * through the real sources and formatters where they can run without a device, so every card looks
 * like it does with live data. Nothing here touches the live tracker, the repository or saved reports.
 */
object DemoData {
    private const val NOW = 10_000_000L
    private const val API_LEVEL = 8
    private const val MINUTE = 60_000L

    private val tracker = ConnectionTracker().apply {
        onType(CarConnection.CONNECTION_TYPE_NOT_CONNECTED, NOW - 100 * MINUTE)
        onType(CarConnection.CONNECTION_TYPE_PROJECTION, NOW - 80 * MINUTE)
        onType(CarConnection.CONNECTION_TYPE_NOT_CONNECTED, NOW - 47 * MINUTE)
        onType(CarConnection.CONNECTION_TYPE_PROJECTION, NOW - 42 * MINUTE - 18_000)
    }

    private val display = DisplayInfo(
        widthPx = 1280, heightPx = 720, densityDpi = 160, density = 1f, scaledDensity = 1f,
        widthDp = 1280, heightDp = 720, smallestWidthDp = 720,
        orientation = Configuration.ORIENTATION_LANDSCAPE, uiModeNight = Configuration.UI_MODE_NIGHT_NO,
        isDarkMode = false, fontScale = 1f, locale = "en_US", refreshRateHz = 60f,
    )

    private val constraintPort = object : ConstraintPort {
        override fun contentLimit(type: Int) = when (type) {
            0 -> 12
            1 -> 6
            2 -> 6
            3 -> 5
            else -> 4
        }

        override fun isAppDrivenRefreshEnabled() = true
    }

    val snapshot: Snapshot = Snapshot(
        mapOf(
            Group.CONNECTION to CarConnectionSource(tracker) { NOW }.read(),
            Group.HOST to hostFields(),
            Group.SCREEN to screenFields(),
            Group.CONSTRAINTS to constraintFields(),
            Group.HARDWARE to hardwareFields(),
            Group.PERMISSIONS to PermissionsSource { true }.read(),
            Group.TOUCH to touchFields(),
            Group.PERFORMANCE to performanceFields(),
            Group.LIFECYCLE to lifecycleFields(),
            Group.PHONE to phoneFields(),
        ),
    )

    val telemetry = CarTelemetry(
        make = Reading.of("Demo Motors"),
        model = Reading.of("Model E"),
        year = Reading.of(2024),
        headingDegrees = Reading.of(40f),
        speedMetersPerSecond = Reading.of(14.4f),
        fuelFraction = Reading.of(0f),
        batteryFraction = Reading.of(0.68f),
        rangeMeters = Reading.of(310_000f),
        chargePortOpen = Reading.of(false),
        chargePortConnected = Reading.of(false),
        gForceX = Reading.of(0.03f),
        gForceY = Reading.of(0.02f),
    )

    /** The Connection tab history, laid out against the fixed clock above. */
    fun timeline(): ConnectionTimeline =
        connectionTimeline(tracker.history(), NOW, System.currentTimeMillis(), ZoneId.systemDefault())

    private fun hostFields(): List<Field> {
        val environment = object : HostEnvironment {
            override fun carAppLibraryVersion() = "1.7.0"
            override fun packageVersion(packageName: String) = AppVersion("18.0.664264", 181064264)
            override val device = DeviceInfo("15", 35, "Google", "Pixel 9")
        }
        val ports = CarPorts(
            API_LEVEL,
            object : HostPort {
                override fun hostInfo() = HostIdentity(HostSource.ANDROID_AUTO_PACKAGE, 10142)
                override fun libraryLatestApiLevel() = API_LEVEL
            },
            { display },
            constraintPort,
            { true },
        )
        return HostSource(environment).read() + CarHostSource { ports }.read()
    }

    private fun screenFields(): List<Field> = ScreenSource.displayFields(display) + listOf(
        Field.ok("Visible area", "(0, 0)-(1280, 720) 1280x720", "SurfaceCallback.onVisibleAreaChanged"),
        Field.ok("Stable area", "(120, 40)-(1160, 680) 1040x640", "SurfaceCallback.onStableAreaChanged"),
    )

    private fun constraintFields(): List<Field> {
        val ports = CarPorts(API_LEVEL, object : HostPort {
            override fun hostInfo() = null
            override fun libraryLatestApiLevel() = API_LEVEL
        }, { display }, constraintPort, { true })
        return ConstraintsSource({ ports }, { ConstraintReadings.read(constraintPort, API_LEVEL) }).read()
    }

    private fun hardwareFields(): List<Field> {
        fun f(feature: HardwareFeature, vararg values: String, source: String = feature.source) =
            feature.labels.zip(values.toList()).map { (label, value) -> Field.ok(label, value, source) }
        return f(HardwareFeature.MODEL, "Demo Motors", "Model E", "2024") +
            f(HardwareFeature.ENERGY_PROFILE, "10", "2, 5") +
            f(HardwareFeature.SPEED, "14.4 m/s (51.8 km/h)", "14.4 m/s (51.8 km/h)", CarUnit.toString(CarUnit.KILOMETERS_PER_HOUR)) +
            listOf(
                Field.ok("Odometer", "18432.0 km", "${HardwareFeature.MILEAGE.source}, getOdometerMeters (kilometers)"),
                Field.ok("Odometer display unit", CarUnit.toString(CarUnit.KILOMETER), HardwareFeature.MILEAGE.source),
            ) +
            f(HardwareFeature.ENERGY_LEVEL, "68.0 %", "0.0 %", "false", "310.0 km", CarUnit.toString(CarUnit.KILOMETER)) +
            f(HardwareFeature.TOLL, "not inserted") +
            f(HardwareFeature.EV_STATUS, "false", "false") +
            f(HardwareFeature.ACCELEROMETER, "[0.3, 0.2, 9.8] m/s²") +
            f(HardwareFeature.GYROSCOPE, "[0.0, 0.0, 0.0] rad/s") +
            f(HardwareFeature.COMPASS, "[40.0, 0.0, 0.0] °") +
            f(HardwareFeature.LOCATION, "37.42200, -122.08400 ±5 m")
    }

    private fun touchFields(): List<Field> {
        val surface = "SurfaceCallback (MapWithContentTemplate)"
        return listOf(
            Field.unavailable("Tap latency", "Row tap to next onGetTemplate", "Measured only when the touch tester cannot run"),
            Field.ok("Touch tester", "surface 1280x720, 160 dpi", surface),
            Field.ok("Clicks", 3, "SurfaceCallback.onClick"),
            Field.ok("Scrolls", 12, "SurfaceCallback.onScroll"),
            Field.ok("Flings", 2, "SurfaceCallback.onFling"),
            Field.ok("Scales", 1, "SurfaceCallback.onScale"),
            Field.ok("Events per second", 0, surface),
            Field.ok("Last event", "click at (642, 318)", surface),
        )
    }

    private fun performanceFields(): List<Field> = listOf(
        Field.ok("Session to first template", "412 ms", "Screen.onGetTemplate"),
        Field.ok("Invalidate to template", "86 ms", "Screen.invalidate to onGetTemplate"),
        Field.ok("Templates in the last minute", 7, "Screen.onGetTemplate"),
        Field.ok("Java heap", "18 of 256 MiB", "Runtime.totalMemory - freeMemory"),
        Field.ok("Native heap", "14 MiB", "Debug.getNativeHeapAllocatedSize"),
        Field.ok("Process PSS", "96 MiB", "Debug.getPss (sampled every 10 s)"),
    )

    private fun lifecycleFields(): List<Field> = listOf(
        "14:36:52.104" to "Tab selected: Touch",
        "14:36:41.870" to "Open Touch tester",
        "14:36:41.652" to "Screen Touch ON_RESUME",
        "14:34:09.215" to "Hardware listeners registered: MODEL, SPEED, ENERGY_LEVEL, EV_STATUS, ACCELEROMETER, COMPASS",
        "14:34:09.190" to "Car sources bound, Car App API $API_LEVEL",
        "14:34:08.944" to "Screen Root ON_START",
        "14:34:08.731" to "Session ON_CREATE",
    ).map { (time, message) -> Field.ok(time, message, "Session/Screen lifecycle") }

    private fun phoneFields(): List<Field> {
        val battery = object : BatteryAdapter {
            override fun read() = BatteryReading(BatteryManager.BATTERY_STATUS_DISCHARGING, 82, 100, 314)
        }
        val power = object : PowerAdapter {
            override fun thermalStatus() = 0
            override fun isPowerSaveMode() = false
        }
        val bluetooth = object : BluetoothAdapterAccess {
            override fun state() = BluetoothAdapter.STATE_ON
        }
        val wifi = object : WifiAdapter {
            override fun isEnabled() = true
            override fun connectedLink() = WifiLink(5745, 866, ScanResult.WIFI_STANDARD_11AC)
        }
        val memory = object : MemoryAdapter {
            override fun read() = MemoryReading(6_800_000_000L, 12_000_000_000L, lowMemory = false)
        }
        return BatterySource(battery).read() + PowerSource(power, 35).read() + BluetoothSource(bluetooth).read() +
            WifiSource(wifi).read() + MemorySource(memory).read()
    }
}
