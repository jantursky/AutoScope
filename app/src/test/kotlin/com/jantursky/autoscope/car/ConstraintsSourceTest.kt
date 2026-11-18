package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintsSourceTest {

    private fun source(ports: CarPorts?) =
        ConstraintsSource({ ports }) { ports?.let { ConstraintReadings.read(it.constraints, it.apiLevel) } ?: ConstraintReadings.NONE }

    @Test
    fun `reads all five limits and app driven refresh`() {
        val fields = source(fakePorts()).read()

        assertEquals("12 (library minimum 6)", fields.byLabel("List limit").value)
        assertEquals("4 (library minimum 4)", fields.byLabel("Pane limit").value)
        assertEquals("true", fields.byLabel("App driven refresh").value)
    }

    @Test
    fun `documented limits are marked as documentation`() {
        val fields = source(null).read()
        val steps = fields.byLabel("Max templates per task")

        assertEquals(Status.OK, steps.status)
        assertTrue(steps.source.startsWith("documentation"))
        assertEquals(Status.UNAVAILABLE, fields.byLabel("List limit").status)
    }

    @Test
    fun `old hosts get not supported instead of a call`() {
        val constraints = FakeConstraints(failure = IllegalStateException("must not be called"))

        val level1 = source(fakePorts(apiLevel = 1, constraints = constraints)).read()
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, level1.byLabel("List limit").status)

        val level5 = source(fakePorts(apiLevel = 5, constraints = FakeConstraints())).read()
        assertEquals(Status.OK, level5.byLabel("List limit").status)
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, level5.byLabel("App driven refresh").status)
    }

    @Test
    fun `a throwing host becomes unavailable and paging falls back to the library minimum`() {
        val ports = fakePorts(constraints = FakeConstraints(failure = RuntimeException("host died")))
        val readings = ConstraintReadings.read(ports.constraints, ports.apiLevel)

        assertEquals(Status.UNAVAILABLE, readings.fields.first().status)
        assertEquals(6, readings.limit(ContentLimit.LIST))
        assertEquals(4, readings.limit(ContentLimit.PANE))
    }
}
