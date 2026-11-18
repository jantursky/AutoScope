package com.jantursky.autoscope.car

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A stylised top-down car as plain polygons, free of any graphics API so every canvas can draw it.
 *
 * Car units: the centre of the car is (0, 0), x grows to the right, y grows towards the rear, so the
 * front points up. The body is 1 wide and 2 long. [place] scales the points, turns them clockwise by a
 * heading (0 = north = up, 90 = east = right) and moves them to a centre in screen coordinates.
 */
object CarFigure {
    private const val HALF_WIDTH = 0.5f
    private const val HALF_LENGTH = 1f

    val body: List<Point> = roundedRect(-HALF_WIDTH, -HALF_LENGTH, HALF_WIDTH, HALF_LENGTH, radius = 0.3f)

    val windshield: List<Point> = listOf(Point(-0.4f, -0.5f), Point(0.4f, -0.5f), Point(0.34f, -0.2f), Point(-0.34f, -0.2f))

    val rearWindow: List<Point> = listOf(Point(-0.34f, 0.55f), Point(0.34f, 0.55f), Point(0.38f, 0.75f), Point(-0.38f, 0.75f))

    /** Front left, front right, rear left, rear right; they stick out of the body a little. */
    val wheels: List<List<Point>> = listOf(-0.6f, 0.6f).flatMap { y ->
        listOf(-1f, 1f).map { side -> rect(side * 0.6f, y - 0.18f, side * 0.42f, y + 0.18f) }
    }

    /** Left and right side mirrors. */
    val mirrors: List<List<Point>> = listOf(-1f, 1f).map { side -> rect(side * 0.5f, -0.4f, side * 0.66f, -0.32f) }

    /** Where the charge port glyph sits: the left rear flank. */
    val chargePort = Point(-0.32f, 0.35f)

    /** Distance of the farthest figure point from the centre, so a figure fits a box at any heading. */
    val EXTENT: Float = (body + wheels.flatten() + mirrors.flatten()).maxOf { hypot(it.x, it.y) }

    /** Scale (pixels per car unit) at which the figure fits [box] whatever its heading. */
    fun scaleToFit(box: Box): Float = (min(box.width, box.height) / 2 / EXTENT).coerceAtLeast(0f)

    /** [points] scaled by [scale], turned clockwise by [headingDegrees] and moved to [center]. */
    fun place(points: List<Point>, center: Point, scale: Float, headingDegrees: Float): List<Point> =
        points.map { place(it, center, scale, headingDegrees) }

    fun place(point: Point, center: Point, scale: Float, headingDegrees: Float): Point {
        val rad = Math.toRadians(headingDegrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return Point(
            center.x + (point.x * c - point.y * s) * scale,
            center.y + (point.x * s + point.y * c) * scale,
        )
    }

    /**
     * Where a g-force dot sits on the body, in car units: right for positive x, forwards (up) for positive
     * y, 1 g at [HALF_WIDTH] sideways and 0.8 lengthwise. Larger forces stay on that edge in their direction.
     */
    fun forcePoint(force: Force): Point {
        val length = hypot(force.x, force.y)
        val k = if (length > 1f) 1f / length else 1f
        return Point(force.x * k * HALF_WIDTH * 0.8f, -force.y * k * HALF_LENGTH * 0.8f)
    }

    fun rect(left: Float, top: Float, right: Float, bottom: Float): List<Point> =
        listOf(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom))

    /** A rectangle with quarter circle corners of [radius], each drawn with [segments] straight lines. */
    fun roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, segments: Int = 6): List<Point> {
        val corners = listOf(
            Point(right - radius, top + radius) to -90.0,
            Point(right - radius, bottom - radius) to 0.0,
            Point(left + radius, bottom - radius) to 90.0,
            Point(left + radius, top + radius) to 180.0,
        )
        return corners.flatMap { (c, start) ->
            (0..segments).map { i ->
                val rad = Math.toRadians(start + 90.0 * i / segments)
                Point(c.x + radius * cos(rad).toFloat(), c.y + radius * sin(rad).toFloat())
            }
        }
    }
}
