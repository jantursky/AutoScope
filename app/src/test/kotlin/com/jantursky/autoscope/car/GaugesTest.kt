package com.jantursky.autoscope.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GaugesTest {

    private val eps = 0.01f
    private val dial = Circle(100f, 100f, 50f)

    private fun assertPoint(x: Float, y: Float, p: Point) {
        assertEquals(x, p.x, eps)
        assertEquals(y, p.y, eps)
    }

    @Test
    fun `grid splits the area into four equal tiles with gaps`() {
        val tiles = Gauges.grid(Box(0f, 0f, 430f, 230f), gap = 10f)

        assertEquals(4, tiles.size)
        assertEquals(Box(10f, 10f, 210f, 110f), tiles[0])
        assertEquals(Box(220f, 10f, 420f, 110f), tiles[1])
        assertEquals(Box(10f, 120f, 210f, 220f), tiles[2])
        assertEquals(Box(220f, 120f, 420f, 220f), tiles[3])
    }

    @Test
    fun `a tiny area gives empty tiles instead of negative ones`() {
        Gauges.grid(Box(0f, 0f, 10f, 10f), gap = 10f).forEach {
            assertEquals(0f, it.width)
            assertEquals(0f, it.height)
        }
    }

    @Test
    fun `dial is the largest circle between header and footer`() {
        val d = Gauges.dial(Box(0f, 0f, 300f, 200f), header = 40f, footer = 40f, padding = 10f)
        assertEquals(Circle(150f, 100f, 50f), d)
    }

    @Test
    fun `compass needle points up for north, right for east, down for south`() {
        assertPoint(100f, 50f, Gauges.needleTip(dial, 0f))
        assertPoint(150f, 100f, Gauges.needleTip(dial, 90f))
        assertPoint(100f, 150f, Gauges.needleTip(dial, 180f))
        assertPoint(50f, 100f, Gauges.needleTip(dial, 270f))
    }

    @Test
    fun `bubble moves right for positive x and up for positive y`() {
        assertPoint(100f, 100f, Gauges.bubble(dial, Force(0f, 0f)))
        assertPoint(125f, 100f, Gauges.bubble(dial, Force(Gauges.STANDARD_GRAVITY / 2, 0f)))
        assertPoint(100f, 75f, Gauges.bubble(dial, Force(0f, Gauges.STANDARD_GRAVITY / 2)))
    }

    @Test
    fun `bubble stays on the edge for forces above full scale`() {
        val p = Gauges.bubble(dial, Force(30f, -30f))
        assertEquals(50f, hypot(p.x - 100f, p.y - 100f), eps)
        assertTrue(p.x > 100f && p.y > 100f)
    }

    @Test
    fun `speed arc fills linearly up to the scale maximum`() {
        assertEquals(36f, Gauges.kmh(10f), eps)
        assertEquals(0f, Gauges.speedSweep(0f), eps)
        assertEquals(135f, Gauges.speedSweep(Gauges.SPEED_MAX_KMH / 2), eps)
        assertEquals(Gauges.SPEED_SWEEP_DEGREES, Gauges.speedSweep(400f), eps)
        assertEquals(Gauges.speedSweep(20f), Gauges.speedSweep(-20f), eps)
    }

    @Test
    fun `sparkline maps time to x and value to y and holds the last value until now`() {
        val box = Box(0f, 0f, 600f, 100f)
        val samples = listOf(Sample(0, 0f), Sample(30_000, 50f))

        val points = Gauges.sparkline(samples, nowMillis = 60_000, spanMillis = 60_000, box = box)

        assertEquals(3, points.size)
        assertPoint(0f, 100f, points[0])
        assertPoint(300f, 0f, points[1])
        assertPoint(600f, 0f, points[2])
    }

    @Test
    fun `sparkline keeps a minimum scale so slow speeds stay near the bottom`() {
        val points = Gauges.sparkline(listOf(Sample(1_000, 1f)), 1_000, 60_000, Box(0f, 0f, 600f, 100f))

        assertEquals(1, points.size)
        assertEquals(100f - 100f / Gauges.SPARKLINE_MIN_SCALE_KMH, points.single().y, eps)
        assertTrue(Gauges.sparkline(emptyList(), 0, 60_000, Box(0f, 0f, 1f, 1f)).isEmpty())
    }
}
