package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAndSourcesTest {

    private val clock = FakeClock()
    private val memory = MemoryPort { MemoryReading(10L shl 20, 256L shl 20, 4L shl 20, 51_200) }

    @Test
    fun `tracker measures first template, invalidate latency and refreshes per minute`() {
        val tracker = PerformanceTracker(clock.millis)
        tracker.sessionCreated()
        clock.now += 250
        tracker.templateServed()
        tracker.invalidateRequested()
        clock.now += 20
        tracker.templateServed()
        clock.now += 61_000
        tracker.templateServed()

        val reading = tracker.reading()
        assertEquals(250L, reading.sessionToFirstTemplateMillis)
        assertEquals(20L, reading.invalidateToTemplateMillis)
        assertEquals(1, reading.templatesLastMinute)
    }

    @Test
    fun `performance reports memory without a session and timing with one`() {
        val tracker = PerformanceTracker(clock.millis)
        val unbound = PerformanceSource({ null }, tracker, memory).read()
        assertEquals(Status.UNAVAILABLE, unbound.byLabel("Session to first template").status)
        assertEquals("10 of 256 MiB", unbound.byLabel("Java heap").value)
        assertEquals("50 MiB", unbound.byLabel("Process PSS").value)

        val bound = PerformanceSource({ fakePorts() }, tracker, memory).read()
        assertEquals("No template served yet", bound.byLabel("Session to first template").reason)
        assertEquals("0", bound.byLabel("Templates in the last minute").value)
    }

    @Test
    fun `failing memory read becomes unavailable`() {
        val fields = PerformanceSource({ null }, PerformanceTracker(clock.millis), MemoryPort { error("no") }).read()
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Native heap").status)
    }

    @Test
    fun `car sources are unavailable until bound and again after unbind`() {
        val log = EventLog()
        val sources = CarSources(log, memory, clock.millis)
        val sessionDependent = sources.sources.filterNot { it is PerformanceSource || it is ConstraintsSource }

        sessionDependent.forEach { assertAll(it.read(), Status.UNAVAILABLE, NO_SESSION) }

        sources.bind(fakePorts())
        assertEquals("8", sources.sources.first().read().first().value)
        assertEquals(12, sources.constraints.limit(ContentLimit.LIST))

        sources.unbind()
        sessionDependent.forEach { assertAll(it.read(), Status.UNAVAILABLE, NO_SESSION) }
        assertTrue(log.read().any { it.value == "Car sources unbound" })
    }
}
