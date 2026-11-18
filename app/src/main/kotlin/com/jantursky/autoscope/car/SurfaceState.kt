package com.jantursky.autoscope.car

/** A rectangle in surface pixels, kept free of android.graphics so it can be tested on the JVM. */
data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    override fun toString() = "($left, $top)-($right, $bottom) ${right - left}x${bottom - top}"
}

data class SurfaceSize(val width: Int, val height: Int, val dpi: Int)

/**
 * What the host told the touch tester's SurfaceCallback. Written on the main thread by the
 * callback, read by the sources; every field is volatile because the reads may come from another thread.
 */
class SurfaceState(private val clock: () -> Long) {
    @Volatile var openedAtMillis: Long? = null
        private set
    @Volatile var everAvailable = false
        private set
    @Volatile var size: SurfaceSize? = null
        private set
    @Volatile var visibleArea: Area? = null
    @Volatile var stableArea: Area? = null

    fun testerOpened() {
        if (openedAtMillis == null) openedAtMillis = clock()
    }

    fun surfaceAvailable(size: SurfaceSize) {
        this.size = size
        everAvailable = true
    }

    fun surfaceDestroyed() {
        size = null
    }

    /** True once the tester has been open for [SURFACE_TIMEOUT_MS] without the host providing a surface. */
    fun surfaceMissing(): Boolean {
        val opened = openedAtMillis ?: return false
        return !everAvailable && clock() - opened >= SURFACE_TIMEOUT_MS
    }

    fun reset() {
        openedAtMillis = null
        everAvailable = false
        size = null
        visibleArea = null
        stableArea = null
    }

    companion object {
        const val SURFACE_TIMEOUT_MS = 3_000L
    }
}
