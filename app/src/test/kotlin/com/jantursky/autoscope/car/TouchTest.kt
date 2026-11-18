package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchTest {

    private val clock = FakeClock()
    private val recorder = TouchRecorder(clock.millis)
    private val surface = SurfaceState(clock.millis)

    private fun read(ports: CarPorts?) = TouchSource({ ports }, surface, recorder).read()

    @Test
    fun `recorder counts per type and keeps a bounded trail`() {
        repeat(12) { recorder.click(it.toFloat(), 0f) }
        recorder.scroll(5f, -5f)

        val stats = recorder.stats()
        assertEquals(12, stats.counts[TouchKind.CLICK])
        assertEquals(1, stats.counts[TouchKind.SCROLL])
        assertEquals(TouchRecorder.TRAIL_SIZE, stats.trail.size)
        // The scroll moves the cursor from the last click (11, 0) against the scroll distance.
        assertEquals(6f, stats.last!!.x)
        assertEquals(5f, stats.last!!.y)
    }

    @Test
    fun `events per second count only the last second`() {
        recorder.click(1f, 1f)
        clock.now += 1_500
        recorder.click(2f, 2f)
        recorder.fling(100f, 0f)

        assertEquals(2, recorder.stats().eventsPerSecond)
    }

    @Test
    fun `scale with unknown focus keeps the cursor`() {
        recorder.click(10f, 20f)
        recorder.scale(-1f, -1f, 1.5f)

        val last = recorder.stats().last!!
        assertEquals(10f, last.x)
        assertEquals(1.5f, last.scale)
    }

    @Test
    fun `tap latency is the time from tap to the next template`() {
        recorder.tapStarted()
        clock.now += 42
        recorder.templateServed()
        clock.now += 1_000
        recorder.templateServed()

        assertEquals(42L, recorder.stats().tapLatencyMillis)
    }

    @Test
    fun `no session makes every field unavailable`() {
        assertAll(read(null), Status.UNAVAILABLE, NO_SESSION)
    }

    @Test
    fun `hosts below API 7 cannot run the tester but report tap latency`() {
        val fields = read(fakePorts(apiLevel = 6))

        assertEquals(Status.NOT_SUPPORTED_BY_HOST, fields.byLabel("Touch tester").status)
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Tap latency").status)
        assertEquals("MapWithContentTemplate needs Car App API 7, host has 6", MapSurface.blockedReason(6, surface))

        recorder.tapStarted()
        clock.now += 30
        recorder.templateServed()
        assertEquals("30 ms", read(fakePorts(apiLevel = 6)).byLabel("Tap latency").value)
    }

    @Test
    fun `a surface that never arrives marks the tester unavailable`() {
        surface.surfaceRequested()
        assertEquals("waiting for the surface", read(fakePorts()).byLabel("Touch tester").value)

        clock.now += SurfaceState.SURFACE_TIMEOUT_MS
        val fields = read(fakePorts())
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Touch tester").status)
        assertEquals("the host provided no map surface", MapSurface.blockedReason(8, surface))
    }

    @Test
    fun `an available surface reports size and counters`() {
        surface.surfaceRequested()
        surface.surfaceAvailable(SurfaceSize(800, 480, 160))
        recorder.click(5f, 5f)

        val fields = read(fakePorts())
        assertEquals("surface 800x480, 160 dpi", fields.byLabel("Touch tester").value)
        assertEquals("1", fields.byLabel("Clicks").value)
        assertEquals("click at (5, 5)", fields.byLabel("Last event").value)
        assertNull(MapSurface.blockedReason(8, surface))
    }

    @Test
    fun `tester counters are not supported below API 7`() {
        val fields = TouchSource({ fakePorts(apiLevel = 4) }, surface, recorder).read()
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, fields.byLabel("Clicks").status)
    }

    @Test
    fun `frame throttle allows one frame per interval and coalesces requests`() {
        val throttle = FrameThrottle(100)

        assertEquals(0L, throttle.request(1_000))
        assertNull(throttle.request(1_010))
        throttle.frameDrawn(1_000)
        assertEquals(60L, throttle.request(1_040))
        throttle.frameDrawn(1_100)
        assertEquals(0L, throttle.request(1_300))
    }
}
