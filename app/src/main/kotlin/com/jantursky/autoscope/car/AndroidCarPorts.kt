package com.jantursky.autoscope.car

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.versioning.CarAppApiLevels

/** Thin adapters from a CarContext to the ports. Only call after Session.onCreateScreen (handshake done). */
object AndroidCarPorts {

    fun create(carContext: CarContext): CarPorts = CarPorts(
        apiLevel = carContext.carAppApiLevel,
        host = host(carContext),
        display = display(carContext),
        constraints = constraints(carContext),
        permissions = PermissionPort { carContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED },
    )

    private fun host(carContext: CarContext) = object : HostPort {
        override fun hostInfo() = carContext.hostInfo?.let { HostIdentity(it.packageName, it.uid) }

        override fun libraryLatestApiLevel() = CarAppApiLevels.getLatest()
    }

    @Suppress("DEPRECATION") // DisplayMetrics.scaledDensity, still the factor text is scaled with.
    private fun display(carContext: CarContext) = DisplayPort {
        val metrics = carContext.resources.displayMetrics
        val config = carContext.resources.configuration
        DisplayInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            density = metrics.density,
            scaledDensity = metrics.scaledDensity,
            widthDp = config.screenWidthDp,
            heightDp = config.screenHeightDp,
            smallestWidthDp = config.smallestScreenWidthDp,
            orientation = config.orientation,
            uiModeNight = config.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            isDarkMode = carContext.isDarkMode,
            fontScale = config.fontScale,
            locale = config.locales.toLanguageTags(),
            refreshRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) carContext.display?.refreshRate else null,
        )
    }

    // ConstraintManager is @RequiresCarApi(2); ConstraintReadings checks the level before calling.
    private fun constraints(carContext: CarContext) = object : ConstraintPort {
        private val manager by lazy { carContext.getCarService(ConstraintManager::class.java) }

        override fun contentLimit(type: Int) = manager.getContentLimit(type)

        override fun isAppDrivenRefreshEnabled() = manager.isAppDrivenRefreshEnabled
    }
}

/** Process memory. Debug.getPss walks the process maps and is slow, so it is sampled at most every 10 s. */
class AndroidMemoryPort : MemoryPort {
    @Volatile private var pssKb: Long? = null
    @Volatile private var sampledAt = Long.MIN_VALUE

    override fun read(): MemoryReading {
        val now = SystemClock.elapsedRealtime()
        if (pssKb == null || now - sampledAt >= PSS_INTERVAL_MS) {
            pssKb = Debug.getPss()
            sampledAt = now
        }
        val runtime = Runtime.getRuntime()
        return MemoryReading(
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMaxBytes = runtime.maxMemory(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            pssKb = pssKb,
        )
    }

    private companion object {
        const val PSS_INTERVAL_MS = 10_000L
    }
}
