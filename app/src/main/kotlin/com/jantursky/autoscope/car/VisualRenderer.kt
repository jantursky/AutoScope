package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale
import kotlin.math.abs

/**
 * Paints the Visual dashboard: compass, g-force bubble, speed arc and the speed of the last minute, in
 * four tiles inside the visible area. A tile without data is drawn dim with "no data" and the reason.
 * The geometry comes from [Gauges]; this class only strokes it. [SurfacePainter] decides when.
 */
class VisualRenderer(
    private val model: () -> VisualModel,
    private val clock: () -> Long,
    private val darkMode: () -> Boolean,
    private val visibleArea: () -> Area?,
) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    private var px = 1f
    private var foreground = Color.WHITE
    private var dim = Color.GRAY

    fun render(canvas: Canvas, dpi: Int) {
        val m = model()
        val dark = darkMode()
        px = dpi / 160f
        foreground = if (dark) Color.WHITE else Color.BLACK
        dim = if (dark) Color.argb(0x66, 0xFF, 0xFF, 0xFF) else Color.argb(0x66, 0, 0, 0)
        canvas.drawColor(if (dark) Color.BLACK else Color.WHITE)

        val area = visibleArea()?.takeUnless { it.isEmpty }
            ?.let { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            ?: Box(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val tiles = Gauges.grid(area, 12 * px)
        tile(canvas, tiles[0], "Compass", m.heading, ::compass)
        tile(canvas, tiles[1], "G-force", m.force, ::bubble)
        tile(canvas, tiles[2], "Speed", m.speedKmh, ::speed)
        tile(canvas, tiles[3], "Speed, last minute", m.speedHistory, ::history)
    }

    /** Frame and title of a tile, then [draw] for data (which returns the footer text) or the no data state. */
    private fun <T> tile(canvas: Canvas, box: Box, title: String, tile: Tile<T>, draw: (Canvas, Box, T) -> String) {
        // Text on a map surface should be at least 24 dp (car app quality guidelines).
        text.textSize = 24 * px
        val pad = 12 * px
        val color = if (tile is Tile.Shown) foreground else dim
        stroke.color = color
        stroke.strokeWidth = 2 * px
        canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, 12 * px, 12 * px, stroke)
        text.color = color
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(title, box.left + pad, box.top + pad + text.textSize, text)

        val footer = when (tile) {
            is Tile.Shown -> draw(canvas, box, tile.value)
            is Tile.NoData -> {
                text.textAlign = Paint.Align.CENTER
                val cx = (box.left + box.right) / 2
                val cy = (box.top + box.bottom) / 2
                canvas.drawText("no data", cx, cy, text)
                canvas.drawText(tile.reason, cx, cy + text.textSize * 1.3f, text)
                return
            }
        }
        text.color = foreground
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(footer, box.left + pad, box.bottom - pad, text)
    }

    private fun dial(box: Box): Circle = Gauges.dial(box, header = 48 * px, footer = 48 * px, padding = 8 * px)

    private fun compass(canvas: Canvas, box: Box, heading: Float): String {
        val d = dial(box)
        stroke.color = foreground
        stroke.strokeWidth = 2 * px
        canvas.drawCircle(d.cx, d.cy, d.radius, stroke)
        val north = Gauges.needleTip(d.copy(radius = d.radius * 0.8f), 0f)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("N", north.x, north.y + text.textSize / 2, text)
        val tip = Gauges.needleTip(d, heading)
        stroke.color = Color.RED
        stroke.strokeWidth = 5 * px
        canvas.drawLine(d.cx, d.cy, tip.x, tip.y, stroke)
        return String.format(Locale.US, "%.0f°", heading)
    }

    private fun bubble(canvas: Canvas, box: Box, force: Force): String {
        val d = dial(box)
        stroke.color = foreground
        stroke.strokeWidth = 2 * px
        canvas.drawCircle(d.cx, d.cy, d.radius, stroke)
        canvas.drawCircle(d.cx, d.cy, d.radius / 2, stroke)
        canvas.drawLine(d.cx - d.radius, d.cy, d.cx + d.radius, d.cy, stroke)
        canvas.drawLine(d.cx, d.cy - d.radius, d.cx, d.cy + d.radius, stroke)
        val dot = Gauges.bubble(d, force)
        fill.color = Color.RED
        canvas.drawCircle(dot.x, dot.y, 10 * px, fill)
        return String.format(Locale.US, "x %.1f  y %.1f m/s²", force.x, force.y)
    }

    private fun speed(canvas: Canvas, box: Box, kmh: Float): String {
        val d = dial(box)
        oval.set(d.cx - d.radius, d.cy - d.radius, d.cx + d.radius, d.cy + d.radius)
        stroke.strokeWidth = 10 * px
        stroke.color = dim
        canvas.drawArc(oval, Gauges.SPEED_START_DEGREES, Gauges.SPEED_SWEEP_DEGREES, false, stroke)
        stroke.color = Color.RED
        canvas.drawArc(oval, Gauges.SPEED_START_DEGREES, Gauges.speedSweep(kmh), false, stroke)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "%.0f", kmh), d.cx, d.cy + text.textSize / 2, text)
        return "km/h, raw speed, scale ${Gauges.SPEED_MAX_KMH.toInt()}"
    }

    private fun history(canvas: Canvas, box: Box, samples: List<Sample>): String {
        val pad = 16 * px
        val chart = Box(box.left + pad, box.top + 56 * px, box.right - pad, box.bottom - 56 * px)
        stroke.color = dim
        stroke.strokeWidth = 2 * px
        canvas.drawLine(chart.left, chart.bottom, chart.right, chart.bottom, stroke)
        val points = Gauges.sparkline(samples, clock(), SensorSamples.HISTORY_SPAN_MS, chart)
        path.reset()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        stroke.color = Color.RED
        stroke.strokeWidth = 4 * px
        canvas.drawPath(path, stroke)
        return String.format(Locale.US, "max %.0f km/h", samples.maxOf { abs(it.value) })
    }
}
