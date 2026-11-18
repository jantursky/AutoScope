package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Paints the car tile below its title: the [CarFigure] turned by the heading (upright without one), the
 * g-force dot on the body, the charge port glyph, a north marker, the energy bar and the text lines.
 * Missing parts are dim, and their reasons are in the lines. Geometry from [Gauges] and [CarFigure].
 */
class CarPainter {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun paint(canvas: Canvas, tile: Box, car: CarTile, palette: SurfacePalette, px: Float, header: Float) {
        val lines = car.lines()
        val pad = 12 * px
        val lineHeight = 32 * px
        val layout = Gauges.carTile(tile, header, pad, lineHeight, lines.size, barWidth = 14 * px)

        figure(canvas, layout.figure, car, palette, px)
        energyBar(canvas, layout.bar, car.energy, palette, px)

        text.textSize = 24 * px
        text.textAlign = Paint.Align.LEFT
        lines.forEachIndexed { i, line ->
            text.color = if (line.dim) palette.dim else palette.foreground
            val baseline = layout.firstLineTop + (i + 1) * lineHeight - 8 * px
            canvas.drawFitted(line.text, tile.left + pad, baseline, tile.width - 2 * pad, text)
        }
    }

    private fun figure(canvas: Canvas, box: Box, car: CarTile, palette: SurfacePalette, px: Float) {
        val heading = car.heading as? Tile.Shown
        val angle = heading?.value ?: 0f
        val center = Point((box.left + box.right) / 2, (box.top + box.bottom) / 2)
        val scale = CarFigure.scaleToFit(box)
        fun polygon(points: List<Point>) = path.setPolygon(CarFigure.place(points, center, scale, angle))
        val body = if (heading != null) palette.foreground else palette.dim

        fill.color = body
        (CarFigure.wheels + CarFigure.mirrors).forEach { canvas.drawPath(polygon(it), fill) }
        fill.color = palette.background
        canvas.drawPath(polygon(CarFigure.body), fill)
        stroke.color = body
        stroke.strokeWidth = 3 * px
        canvas.drawPath(polygon(CarFigure.body), stroke)
        stroke.strokeWidth = 2 * px
        canvas.drawPath(polygon(CarFigure.windshield), stroke)
        canvas.drawPath(polygon(CarFigure.rearWindow), stroke)

        val port = CarFigure.place(CarFigure.chargePort, center, scale, angle)
        val lit = (car.chargePort as? Tile.Shown)?.value?.lit == true
        if (lit) {
            fill.color = palette.charge
            canvas.drawCircle(port.x, port.y, 0.1f * scale, fill)
        } else {
            stroke.color = palette.dim
            canvas.drawCircle(port.x, port.y, 0.1f * scale, stroke)
        }

        (car.force as? Tile.Shown)?.let {
            val dot = CarFigure.place(CarFigure.forcePoint(it.value), center, scale, angle)
            stroke.color = palette.line
            stroke.strokeWidth = 2 * px
            canvas.drawLine(center.x, center.y, dot.x, dot.y, stroke)
            fill.color = palette.accent
            canvas.drawCircle(dot.x, dot.y, 8 * px, fill)
        }

        text.textSize = 20 * px
        text.textAlign = Paint.Align.CENTER
        text.color = if (heading != null) palette.foreground else palette.dim
        canvas.drawText("N", box.left + 12 * px, box.top + text.textSize, text)
    }

    private fun energyBar(canvas: Canvas, bar: Box, energy: Tile<Energy>, palette: SurfacePalette, px: Float) {
        val radius = 4 * px
        stroke.color = if (energy is Tile.Shown) palette.line else palette.dim
        stroke.strokeWidth = 2 * px
        canvas.drawRoundRect(bar.left, bar.top, bar.right, bar.bottom, radius, radius, stroke)
        if (energy !is Tile.Shown) return
        val level = Gauges.fillFromBottom(bar, energy.value.fraction)
        fill.color = palette.foreground
        canvas.drawRoundRect(level.left, level.top, level.right, level.bottom, radius, radius, fill)
    }
}
