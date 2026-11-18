package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale
import kotlin.math.abs

/**
 * Paints the Visual dashboard inside the visible area: the car tile ([CarPainter]) and four gauges
 * (compass, g-force bubble, speed arc, speed of the last minute). A gauge without data is drawn dim with
 * "no data" and the reason. Every text is cut to its tile. The geometry comes from [Gauges]; this class
 * only strokes it. [SurfacePainter] decides when.
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
    private val car = CarPainter()

    private var px = 1f
    private var palette = SurfacePalette.DARK

    fun render(canvas: Canvas, dpi: Int) {
        val m = model()
        px = dpi / 160f
        palette = SurfacePalette.of(darkMode())
        canvas.drawColor(palette.background)

        val area = visibleArea()?.takeUnless { it.isEmpty }
            ?.let { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            ?: Box(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val layout = Gauges.dashboard(area, 12 * px)
        frame(canvas, layout.car, (m.car.name as? Tile.Shown)?.value ?: "Car", active = true)
        car.paint(canvas, layout.car, m.car, palette, px, HEADER * px)
        val tiles = layout.gauges
        tile(canvas, tiles[0], "Compass", m.heading, ::compass)
        tile(canvas, tiles[1], "G-force", m.force, ::bubble)
        tile(canvas, tiles[2], "Speed", m.speedKmh, ::speed)
        tile(canvas, tiles[3], "Speed 60 s", m.speedHistory, ::history)
    }

    /** Frame and title of a tile, then [draw] for data (which returns the footer text) or the no data state. */
    private fun <T> tile(canvas: Canvas, box: Box, title: String, tile: Tile<T>, draw: (Canvas, Box, T) -> String) {
        frame(canvas, box, title, active = tile is Tile.Shown)
        val pad = 12 * px
        val width = box.width - 2 * pad
        val footer = when (tile) {
            is Tile.Shown -> draw(canvas, box, tile.value)
            is Tile.NoData -> {
                text.textAlign = Paint.Align.CENTER
                val cx = (box.left + box.right) / 2
                val cy = (box.top + box.bottom) / 2
                canvas.drawFitted("no data", cx, cy, width, text)
                canvas.drawFitted(tile.reason, cx, cy + text.textSize * 1.3f, width, text)
                return
            }
        }
        text.color = palette.foreground
        text.textAlign = Paint.Align.LEFT
        canvas.drawFitted(footer, box.left + pad, box.bottom - pad, width, text)
    }

    /** Rounded frame and title; leaves [text] in the tile's colour at the 24 dp size. */
    private fun frame(canvas: Canvas, box: Box, title: String, active: Boolean) {
        // Text on a map surface should be at least 24 dp (car app quality guidelines).
        text.textSize = 24 * px
        val pad = 12 * px
        stroke.color = if (active) palette.line else palette.dim
        stroke.strokeWidth = 2 * px
        canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, 12 * px, 12 * px, stroke)
        text.color = if (active) palette.foreground else palette.dim
        text.textAlign = Paint.Align.LEFT
        canvas.drawFitted(title, box.left + pad, box.top + pad + text.textSize, box.width - 2 * pad, text)
    }

    private fun dial(box: Box): Circle = Gauges.dial(box, header = HEADER * px, footer = HEADER * px, padding = 8 * px)

    private fun compass(canvas: Canvas, box: Box, heading: Float): String {
        val d = dial(box)
        stroke.color = palette.line
        stroke.strokeWidth = 2 * px
        canvas.drawCircle(d.cx, d.cy, d.radius, stroke)
        val north = Gauges.needleTip(d.copy(radius = d.radius * 0.8f), 0f)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("N", north.x, north.y + text.textSize / 2, text)
        val tip = Gauges.needleTip(d, heading)
        stroke.color = palette.accent
        stroke.strokeWidth = 5 * px
        canvas.drawLine(d.cx, d.cy, tip.x, tip.y, stroke)
        return String.format(Locale.US, "%.0f°", heading)
    }

    private fun bubble(canvas: Canvas, box: Box, force: Force): String {
        val d = dial(box)
        stroke.color = palette.line
        stroke.strokeWidth = 2 * px
        canvas.drawCircle(d.cx, d.cy, d.radius, stroke)
        canvas.drawCircle(d.cx, d.cy, d.radius / 2, stroke)
        canvas.drawLine(d.cx - d.radius, d.cy, d.cx + d.radius, d.cy, stroke)
        canvas.drawLine(d.cx, d.cy - d.radius, d.cx, d.cy + d.radius, stroke)
        val dot = Gauges.bubble(d, force)
        fill.color = palette.accent
        canvas.drawCircle(dot.x, dot.y, 10 * px, fill)
        return String.format(Locale.US, "x %.2f  y %.2f g", force.x, force.y)
    }

    private fun speed(canvas: Canvas, box: Box, kmh: Float): String {
        val d = dial(box)
        oval.set(d.cx - d.radius, d.cy - d.radius, d.cx + d.radius, d.cy + d.radius)
        stroke.strokeWidth = 10 * px
        stroke.color = palette.dim
        canvas.drawArc(oval, Gauges.SPEED_START_DEGREES, Gauges.SPEED_SWEEP_DEGREES, false, stroke)
        stroke.color = palette.accent
        canvas.drawArc(oval, Gauges.SPEED_START_DEGREES, Gauges.speedSweep(kmh), false, stroke)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US, "%.0f", kmh), d.cx, d.cy + text.textSize / 2, text)
        return "km/h, scale ${Gauges.SPEED_MAX_KMH.toInt()}"
    }

    private fun history(canvas: Canvas, box: Box, samples: List<Sample>): String {
        val pad = 16 * px
        val chart = Box(box.left + pad, box.top + 56 * px, box.right - pad, box.bottom - 56 * px)
        stroke.color = palette.dim
        stroke.strokeWidth = 2 * px
        canvas.drawLine(chart.left, chart.bottom, chart.right, chart.bottom, stroke)
        val points = Gauges.sparkline(samples, clock(), SensorSamples.HISTORY_SPAN_MS, chart)
        path.reset()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        stroke.color = palette.accent
        stroke.strokeWidth = 4 * px
        canvas.drawPath(path, stroke)
        return String.format(Locale.US, "max %.0f km/h", samples.maxOf { abs(it.value) })
    }

    private companion object {
        /** Height of a tile's title band, in dp. */
        const val HEADER = 48f
    }
}
