package com.jantursky.autoscope.car

import androidx.car.app.versioning.CarAppApiLevels

/** A rectangle in surface pixels, kept free of android.graphics so it can be tested on the JVM. */
data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    override fun toString() = "($left, $top)-($right, $bottom) ${right - left}x${bottom - top}"
}

data class SurfaceSize(val width: Int, val height: Int, val dpi: Int)

/**
 * What the host told the SurfaceCallback of the map surface screens (touch tester and Visual). Written
 * on the main thread by the callback, read by the sources; every field is volatile because the reads may
 * come from another thread.
 */
class SurfaceState(private val clock: () -> Long) {
    @Volatile var requestedAtMillis: Long? = null
        private set
    @Volatile var everAvailable = false
        private set
    @Volatile var size: SurfaceSize? = null
        private set
    @Volatile var visibleArea: Area? = null
    @Volatile var stableArea: Area? = null

    /** A map surface screen was opened and asked the host for the surface. */
    fun surfaceRequested() {
        if (requestedAtMillis == null) requestedAtMillis = clock()
    }

    fun surfaceAvailable(size: SurfaceSize) {
        this.size = size
        everAvailable = true
    }

    fun surfaceDestroyed() {
        size = null
    }

    /** True once a map surface screen has waited [SURFACE_TIMEOUT_MS] without the host providing a surface. */
    fun surfaceMissing(): Boolean {
        val requested = requestedAtMillis ?: return false
        return !everAvailable && clock() - requested >= SURFACE_TIMEOUT_MS
    }

    fun reset() {
        requestedAtMillis = null
        everAvailable = false
        size = null
        visibleArea = null
        stableArea = null
    }

    companion object {
        const val SURFACE_TIMEOUT_MS = 3_000L
    }
}

object MapSurface {
    /**
     * Why the map surface screens cannot open, or null when they can: they need MapWithContentTemplate
     * (Car App API 7) and a host that actually provides the surface.
     */
    fun blockedReason(apiLevel: Int, surface: SurfaceState): String? = when {
        apiLevel < CarAppApiLevels.LEVEL_7 -> needsLevel("MapWithContentTemplate", 7, apiLevel)
        surface.surfaceMissing() -> "the host provided no map surface"
        else -> null
    }
}

/**
 * Which screen holds the single AppManager SurfaceCallback. ScreenManager starts a pushed screen before
 * it stops the one below (and starts the new top before stopping a popped one), so a stopping screen
 * must not clear the callback another screen has just set.
 */
class SurfaceOwnership {
    private var owner: Any? = null

    fun claim(screen: Any) {
        owner = screen
    }

    /** Returns true when [screen] held the callback, which the caller then clears. */
    fun release(screen: Any): Boolean {
        if (owner !== screen) return false
        owner = null
        return true
    }
}
