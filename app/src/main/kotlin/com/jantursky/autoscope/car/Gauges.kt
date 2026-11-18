package com.jantursky.autoscope.car

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class Point(val x: Float, val y: Float)

/** A rectangle in surface pixels with float edges, for drawing. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class Circle(val cx: Float, val cy: Float, val radius: Float)

/**
 * Geometry of the Visual screen's gauges, free of android.graphics so it can be tested on the JVM. Screen
 * coordinates: x grows to the right, y grows downwards.
 */
object Gauges {
    const val STANDARD_GRAVITY = 9.81f

    /** The speed arc starts at the lower left (canvas degrees, clockwise from 3 o'clock) and spans 270°. */
    const val SPEED_START_DEGREES = 135f
    const val SPEED_SWEEP_DEGREES = 270f
    const val SPEED_MAX_KMH = 200f

    /** The sparkline scale never drops below this, so standing still draws a flat line at the bottom. */
    const val SPARKLINE_MIN_SCALE_KMH = 10f

    /** Four equal tiles inside [area], row by row, with [gap] between them and around them. */
    fun grid(area: Box, gap: Float): List<Box> {
        val w = ((area.width - 3 * gap) / 2).coerceAtLeast(0f)
        val h = ((area.height - 3 * gap) / 2).coerceAtLeast(0f)
        return (0..1).flatMap { row ->
            (0..1).map { col ->
                val left = area.left + gap + col * (w + gap)
                val top = area.top + gap + row * (h + gap)
                Box(left, top, left + w, top + h)
            }
        }
    }

    /** The largest circle in [tile] between a header and a footer text line, with [padding] around it. */
    fun dial(tile: Box, header: Float, footer: Float, padding: Float): Circle {
        val top = tile.top + header
        val bottom = tile.bottom - footer
        val radius = (min(tile.width, bottom - top) / 2 - padding).coerceAtLeast(0f)
        return Circle((tile.left + tile.right) / 2, (top + bottom) / 2, radius)
    }

    /** Tip of a compass needle: 0° (north) points up, 90° (east) to the right. */
    fun needleTip(dial: Circle, headingDegrees: Float): Point {
        val rad = Math.toRadians(headingDegrees.toDouble())
        return Point(
            (dial.cx + dial.radius * sin(rad)).toFloat(),
            (dial.cy - dial.radius * cos(rad)).toFloat(),
        )
    }

    /**
     * Position of the g-force dot: x to the right, y upwards, the dial edge at [fullScale] m/s² (1 g by
     * default). Larger forces stay on the edge in their direction.
     */
    fun bubble(dial: Circle, force: Force, fullScale: Float = STANDARD_GRAVITY): Point {
        var dx = force.x / fullScale * dial.radius
        var dy = -force.y / fullScale * dial.radius
        val length = hypot(dx, dy)
        if (length > dial.radius && length > 0f) {
            dx = dx / length * dial.radius
            dy = dy / length * dial.radius
        }
        return Point(dial.cx + dx, dial.cy + dy)
    }

    fun kmh(metersPerSecond: Float): Float = metersPerSecond * 3.6f

    /** How far the speed arc is filled, in degrees, for [kmh]; reversing counts as its magnitude. */
    fun speedSweep(kmh: Float): Float = (abs(kmh) / SPEED_MAX_KMH).coerceIn(0f, 1f) * SPEED_SWEEP_DEGREES

    /**
     * Polyline of [samples] (oldest first) over the last [spanMillis] before [nowMillis] in [box]. The
     * newest value is held until now, because the car may report only when the value changes.
     */
    fun sparkline(samples: List<Sample>, nowMillis: Long, spanMillis: Long, box: Box): List<Point> {
        if (samples.isEmpty()) return emptyList()
        val scale = maxOf(samples.maxOf { abs(it.value) }, SPARKLINE_MIN_SCALE_KMH)
        val from = nowMillis - spanMillis
        fun point(at: Long, value: Float): Point {
            val x = box.left + ((at - from).toFloat() / spanMillis).coerceIn(0f, 1f) * box.width
            val y = box.bottom - (abs(value) / scale) * box.height
            return Point(x, y)
        }
        val points = samples.map { point(it.atMillis, it.value) }
        val last = samples.last()
        return if (last.atMillis < nowMillis) points + point(nowMillis, last.value) else points
    }
}
