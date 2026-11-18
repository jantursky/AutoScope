package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface

/**
 * Holds the host surface of a map surface screen and draws onto it with [paint] (canvas, dpi). It draws
 * only on [requestDraw], at most one frame per [frameMillis], never in a loop and only while a surface is
 * attached. Main thread only.
 */
class SurfacePainter(frameMillis: Long, private val paint: (Canvas, Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val throttle = FrameThrottle(frameMillis)
    private val drawRunnable = Runnable { draw() }
    private var surface: Surface? = null
    private var dpi = 160

    fun attach(surface: Surface, dpi: Int) {
        // The host may call onSurfaceAvailable again with the same Surface when only its size changes.
        if (surface !== this.surface) detach()
        this.surface = surface
        this.dpi = dpi.coerceAtLeast(1)
        requestDraw()
    }

    /** Stops drawing and releases the surface, as SurfaceCallback.onSurfaceAvailable requires. */
    fun detach() {
        handler.removeCallbacks(drawRunnable)
        throttle.cancel()
        surface?.release()
        surface = null
    }

    fun requestDraw() {
        if (surface == null) return
        val delay = throttle.request(SystemClock.uptimeMillis()) ?: return
        handler.postDelayed(drawRunnable, delay)
    }

    private fun draw() {
        throttle.frameDrawn(SystemClock.uptimeMillis())
        val target = surface?.takeIf { it.isValid } ?: return
        val canvas = try {
            target.lockHardwareCanvas()
        } catch (e: Exception) {
            return
        }
        try {
            paint(canvas, dpi)
        } finally {
            target.unlockCanvasAndPost(canvas)
        }
    }
}
