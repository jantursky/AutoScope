package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class HostAndScreenSourceTest {

    @Test
    fun `host fields are unavailable without a session`() {
        assertAll(CarHostSource { null }.read(), Status.UNAVAILABLE, NO_SESSION)
    }

    @Test
    fun `host fields report level, library and host identity`() {
        val fields = CarHostSource { fakePorts(apiLevel = 7) }.read()

        assertEquals("7", fields.byLabel("Car App API level (host)").value)
        assertEquals("1.7.0", fields.byLabel("Car App Library version").value)
        assertEquals("10123", fields.byLabel("Host uid").value)
    }

    @Test
    fun `missing host info and a throwing host become statuses`() {
        val missing = CarHostSource { fakePorts(host = FakeHost(info = null)) }.read()
        assertEquals(Status.UNAVAILABLE, missing.byLabel("Host package").status)

        val denied = CarHostSource { fakePorts(host = FakeHost(failure = SecurityException("no"))) }.read()
        assertEquals(Status.PERMISSION_MISSING, denied.byLabel("Host package").status)
        assertEquals(Status.OK, denied.byLabel("Car App API level (host)").status)
    }

    @Test
    fun `screen fields derive aspect ratio, orientation and night mode`() {
        val fields = ScreenSource({ fakePorts() }, SurfaceState { 0 }).read()

        assertEquals("1.78:1", fields.byLabel("Aspect ratio").value)
        assertEquals("landscape", fields.byLabel("Orientation").value)
        assertEquals("yes", fields.byLabel("UI mode night").value)
        assertEquals("60.00 Hz", fields.byLabel("Refresh rate").value)
    }

    @Test
    fun `surface areas need the tester and API 7`() {
        val surface = SurfaceState { 0 }
        val waiting = ScreenSource({ fakePorts() }, surface).read()
        assertEquals(Status.UNAVAILABLE, waiting.byLabel("Visible area").status)

        surface.visibleArea = Area(0, 100, 800, 480)
        assertEquals("(0, 100)-(800, 480) 800x380", ScreenSource({ fakePorts() }, surface).read().byLabel("Visible area").value)

        val old = ScreenSource({ fakePorts(apiLevel = 6) }, surface).read()
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, old.byLabel("Visible area").status)
    }

    @Test
    fun `screen keeps its labels when the display read throws or no session exists`() {
        val ok = ScreenSource({ fakePorts() }, SurfaceState { 0 }).read().map { it.label }
        val failing = ScreenSource({ fakePorts(display = DisplayPort { error("boom") }) }, SurfaceState { 0 }).read()
        val unbound = ScreenSource({ null }, SurfaceState { 0 }).read()

        assertEquals(ok, failing.map { it.label })
        assertEquals(ok, unbound.map { it.label })
        assertEquals(Status.UNAVAILABLE, failing.byLabel("Width").status)
        assertAll(unbound, Status.UNAVAILABLE, NO_SESSION)
    }

    @Test
    fun `unknown refresh rate is unavailable`() {
        val fields = ScreenSource({ fakePorts(display = DisplayPort { DISPLAY.copy(refreshRateHz = null) }) }, SurfaceState { 0 }).read()
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Refresh rate").status)
    }
}
