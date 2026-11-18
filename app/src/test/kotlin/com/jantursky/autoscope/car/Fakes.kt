package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertTrue

class FakeHost(
    var info: HostIdentity? = HostIdentity("com.google.android.projection.gearhead", 10_123),
    var failure: Exception? = null,
) : HostPort {
    override fun hostInfo(): HostIdentity? {
        failure?.let { throw it }
        return info
    }

    override fun libraryLatestApiLevel() = 8
}

class FakeConstraints(
    var limits: Map<Int, Int> = mapOf(0 to 12, 1 to 6, 2 to 6, 3 to 3, 4 to 4),
    var failure: Exception? = null,
    var appDrivenRefresh: Boolean = true,
) : ConstraintPort {
    override fun contentLimit(type: Int): Int {
        failure?.let { throw it }
        return limits.getValue(type)
    }

    override fun isAppDrivenRefreshEnabled(): Boolean {
        failure?.let { throw it }
        return appDrivenRefresh
    }
}

class FakePermissions(val granted: MutableSet<String> = CarPermissions.HARDWARE.toMutableSet()) : PermissionPort {
    override fun isGranted(permission: String) = permission in granted
}

val DISPLAY = DisplayInfo(
    widthPx = 1920, heightPx = 1080, densityDpi = 240, density = 1.5f, scaledDensity = 1.5f,
    widthDp = 1280, heightDp = 720, smallestWidthDp = 720, orientation = 2, uiModeNight = 0x20,
    isDarkMode = true, fontScale = 1f, locale = "en-US", refreshRateHz = 60f,
)

fun fakePorts(
    apiLevel: Int = 8,
    host: HostPort = FakeHost(),
    display: DisplayPort = DisplayPort { DISPLAY },
    constraints: ConstraintPort = FakeConstraints(),
    permissions: PermissionPort = FakePermissions(),
) = CarPorts(apiLevel, host, display, constraints, permissions)

class FakeClock(var now: Long = 1_000_000) {
    val millis: () -> Long = { now }
}

fun List<Field>.byLabel(label: String): Field = single { it.label == label }

fun assertAll(fields: List<Field>, status: Status, reasonPart: String) {
    assertTrue(fields.isNotEmpty())
    fields.forEach {
        assertTrue("${it.label} is ${it.status}", it.status == status)
        assertTrue("${it.label}: ${it.reason}", it.reason.orEmpty().contains(reasonPart))
    }
}
