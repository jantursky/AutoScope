package com.jantursky.autoscope.car

/**
 * Everything a bound car Session lends to the sources. The Android implementations live in
 * [AndroidCarPorts]; tests pass fakes.
 */
class CarPorts(
    /** CarContext.getCarAppApiLevel, the level negotiated with the host. */
    val apiLevel: Int,
    val host: HostPort,
    val display: DisplayPort,
    val constraints: ConstraintPort,
    val permissions: PermissionPort,
)

data class HostIdentity(val packageName: String, val uid: Int)

interface HostPort {
    /** CarContext.getHostInfo, null when the host did not send it. */
    fun hostInfo(): HostIdentity?

    /** Highest Car App API level this library build implements. */
    fun libraryLatestApiLevel(): Int

    fun libraryVersion(): String
}

/** Raw display values of the car screen as the CarContext resources see them. */
data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val density: Float,
    val scaledDensity: Float,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    /** Configuration.orientation. */
    val orientation: Int,
    /** Configuration.uiMode and UI_MODE_NIGHT_MASK. */
    val uiModeNight: Int,
    val isDarkMode: Boolean,
    val fontScale: Float,
    val locale: String,
    /** Null when the platform cannot tell (Context.getDisplay needs Android 11). */
    val refreshRateHz: Float?,
)

fun interface DisplayPort {
    fun read(): DisplayInfo
}

interface ConstraintPort {
    /** ConstraintManager.getContentLimit; may throw HostException. */
    fun contentLimit(type: Int): Int

    /** ConstraintManager.isAppDrivenRefreshEnabled (Car App API 6). */
    fun isAppDrivenRefreshEnabled(): Boolean
}

fun interface PermissionPort {
    fun isGranted(permission: String): Boolean
}

data class MemoryReading(
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    /** Null until the first sample is taken. */
    val pssKb: Long?,
)

fun interface MemoryPort {
    fun read(): MemoryReading
}
