package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Paint
import java.util.Locale

/**
 * Paints the touch tester: a crosshair at the last event, the trail of the last events, counters per type
 * and events per second. [SurfacePainter] decides when.
 */
class TouchRenderer(
    private val recorder: TouchRecorder,
    private val darkMode: () -> Boolean,
    private val visibleArea: () -> Area?,
) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(canvas: Canvas, dpi: Int) {
        val stats = recorder.stats()
        val palette = SurfacePalette.of(darkMode())
        canvas.drawColor(palette.background)
        val px = dpi / 160f
        line.strokeWidth = 3 * px

        line.color = palette.dim
        stats.trail.zipWithNext { a, b -> canvas.drawLine(a.x, a.y, b.x, b.y, line) }
        dot.color = palette.dim
        stats.trail.forEach { canvas.drawCircle(it.x, it.y, 6 * px, dot) }

        stats.last?.let {
            line.color = palette.accent
            val arm = 24 * px
            canvas.drawLine(it.x - arm, it.y, it.x + arm, it.y, line)
            canvas.drawLine(it.x, it.y - arm, it.x, it.y + arm, line)
        }

        // Text on a map surface should be at least 24 dp (car app quality guidelines). It starts inside the
        // visible area, which the host keeps free of its own UI.
        val area = visibleArea()?.takeUnless { it.isEmpty }
        val left = (area?.left ?: 0) + 16 * px
        val top = (area?.top ?: 0) + 40 * px
        text.color = palette.foreground
        text.textSize = 24 * px
        val lines = listOf(
            TouchKind.entries.joinToString("  ") { "${it.name.lowercase()} ${stats.counts.getValue(it)}" },
            String.format(Locale.US, "%d events/s", stats.eventsPerSecond),
            stats.last?.let(TouchSource::describe) ?: "Touch, drag, fling or pinch the map",
        )
        lines.forEachIndexed { i, s -> canvas.drawText(s, left, top + i * 32 * px, text) }
    }
}
