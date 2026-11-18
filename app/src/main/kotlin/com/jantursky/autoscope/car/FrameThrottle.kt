package com.jantursky.autoscope.car

/**
 * Coalesces draw requests to at most one frame per [intervalMillis]. Not thread safe; use from the main
 * thread only, where the SurfaceCallback runs.
 */
class FrameThrottle(private val intervalMillis: Long) {
    private var lastFrameAt = Long.MIN_VALUE / 2
    private var pending = false

    /**
     * Returns how long to wait before drawing, or null when a frame is already scheduled and this request
     * rides along with it.
     */
    fun request(now: Long): Long? {
        if (pending) return null
        pending = true
        return (lastFrameAt + intervalMillis - now).coerceAtLeast(0)
    }

    fun frameDrawn(now: Long) {
        pending = false
        lastFrameAt = now
    }

    fun cancel() {
        pending = false
    }
}
