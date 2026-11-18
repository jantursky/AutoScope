package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import java.util.Locale

/**
 * Draws the touch tester onto the host surface: a crosshair at the last event, the trail of the last
 * events, counters per type and events per second. It draws only when an event arrives, at most every
 * [FRAME_MS], never in a loop. Main thread only.
 */
class TouchRenderer(
    private val recorder: TouchRecorder,
    private val darkMode: () -> Boolean,
    private val visibleArea: () -> Area?,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val throttle = FrameThrottle(FRAME_MS)
    private val drawRunnable = Runnable { draw() }
    private var surface: Surface? = null
    private var dpi = 160

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

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
            render(canvas, recorder.stats())
        } finally {
            target.unlockCanvasAndPost(canvas)
        }
    }

    private fun render(canvas: Canvas, stats: TouchStats) {
        val dark = darkMode()
        val foreground = if (dark) Color.WHITE else Color.BLACK
        canvas.drawColor(if (dark) Color.BLACK else Color.WHITE)
        val px = dpi / 160f
        line.strokeWidth = 3 * px

        line.color = Color.GRAY
        stats.trail.zipWithNext { a, b -> canvas.drawLine(a.x, a.y, b.x, b.y, line) }
        dot.color = Color.GRAY
        stats.trail.forEach { canvas.drawCircle(it.x, it.y, 6 * px, dot) }

        stats.last?.let {
            line.color = Color.RED
            val arm = 24 * px
            canvas.drawLine(it.x - arm, it.y, it.x + arm, it.y, line)
            canvas.drawLine(it.x, it.y - arm, it.x, it.y + arm, line)
        }

        // Text on a map surface should be at least 24 dp (car app quality guidelines). It starts inside the
        // visible area, which the host keeps free of its own UI.
        val area = visibleArea()?.takeUnless { it.isEmpty }
        val left = (area?.left ?: 0) + 16 * px
        val top = (area?.top ?: 0) + 40 * px
        text.color = foreground
        text.textSize = 24 * px
        val lines = listOf(
            TouchKind.entries.joinToString("  ") { "${it.name.lowercase()} ${stats.counts.getValue(it)}" },
            String.format(Locale.US, "%d events/s", stats.eventsPerSecond),
            stats.last?.let(TouchSource::describe) ?: "Touch, drag, fling or pinch the map",
        )
        lines.forEachIndexed { i, s -> canvas.drawText(s, left, top + i * 32 * px, text) }
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
