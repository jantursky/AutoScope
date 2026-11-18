package com.jantursky.autoscope.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CarFigureTest {

    private val eps = 0.001f
    private val center = Point(100f, 100f)

    private fun assertPoint(x: Float, y: Float, p: Point) {
        assertEquals(x, p.x, eps)
        assertEquals(y, p.y, eps)
    }

    @Test
    fun `the front points up at heading 0 and turns clockwise with the heading`() {
        val front = Point(0f, -1f)
        assertPoint(100f, 90f, CarFigure.place(front, center, 10f, 0f))
        assertPoint(110f, 100f, CarFigure.place(front, center, 10f, 90f))
        assertPoint(100f, 110f, CarFigure.place(front, center, 10f, 180f))
        assertPoint(90f, 100f, CarFigure.place(front, center, 10f, 270f))
    }

    @Test
    fun `the figure fits its box at any heading`() {
        val box = Box(0f, 0f, 200f, 120f)
        val scale = CarFigure.scaleToFit(box)
        val parts = listOf(CarFigure.body) + CarFigure.wheels + CarFigure.mirrors
        listOf(0f, 33f, 90f, 145f, 270f).forEach { heading ->
            parts.flatMap { CarFigure.place(it, Point(100f, 60f), scale, heading) }.forEach {
                assertTrue("$it at $heading", it.x >= -eps && it.x <= 200f + eps && it.y >= -eps && it.y <= 120f + eps)
            }
        }
    }

    @Test
    fun `the body is a closed outline inside 1 x 2 car units with four wheels and two mirrors`() {
        assertTrue(CarFigure.body.all { it.x in -0.5f..0.5f && it.y in -1f..1f })
        assertEquals(4, CarFigure.wheels.size)
        assertEquals(2, CarFigure.mirrors.size)
        assertTrue(CarFigure.wheels.take(2).all { wheel -> wheel.all { it.y < 0f } })
    }

    @Test
    fun `rounded rectangle corners stay on their radius`() {
        val r = CarFigure.roundedRect(0f, 0f, 10f, 20f, radius = 2f, segments = 4)
        assertEquals(20, r.size)
        assertPoint(8f, 0f, r.first())
        assertPoint(2f, 0f, r.last())
        r.take(5).forEach { assertEquals(2f, hypot(it.x - 8f, it.y - 2f), eps) }
    }

    @Test
    fun `force dot moves right and forwards and stays on the body for large forces`() {
        assertPoint(0f, 0f, CarFigure.forcePoint(Force(0f, 0f)))
        assertPoint(0.2f, 0f, CarFigure.forcePoint(Force(0.5f, 0f)))
        assertPoint(0f, -0.8f, CarFigure.forcePoint(Force(0f, 1f)))
        assertPoint(0f, 0.8f, CarFigure.forcePoint(Force(0f, -3f)))
    }
}
