package com.jantursky.autoscope.car

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveFieldsTest {

    private val clock = FakeClock()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun overview(timeConnected: String, heap: String, thermal: String = "none") = Snapshot(
        mapOf(
            Group.CONNECTION to listOf(Field.ok("Connection type", "Projection", "t"), Field.ok("Time connected", timeConnected, "t")),
            Group.PHONE to listOf(Field.ok("Thermal status", thermal, "t")),
            Group.PERFORMANCE to listOf(Field.ok("Session to first template", "250 ms", "t"), Field.ok("Java heap", heap, "t")),
        ),
    )

    private fun log(vararg messages: String) =
        Snapshot(mapOf(Group.LIFECYCLE to messages.mapIndexed { i, m -> Field.ok("10:00:0$i.000", m, "log") }))

    @Test
    fun `timers, memory, sensors, counters and the log are live, settings of the car are not`() {
        assertTrue(LiveFields.isLive(Group.CONNECTION, Field.ok("Time connected", "1 s", "t")))
        assertTrue(LiveFields.isLive(Group.PERFORMANCE, Field.ok("Templates in the last minute", 3, "t")))
        assertTrue(LiveFields.isLive(Group.HARDWARE, Field.ok("Raw speed", "1 m/s", "t")))
        assertTrue(LiveFields.isLive(Group.TOUCH, Field.ok("Clicks", 2, "t")))
        assertTrue(LiveFields.isLive(Group.LIFECYCLE, Field.ok("10:00:00.000", "Session ON_CREATE", "log")))

        assertFalse(LiveFields.isLive(Group.PERFORMANCE, Field.ok("Session to first template", "250 ms", "t")))
        assertFalse(LiveFields.isLive(Group.HARDWARE, Field.ok("Speed display unit", "km/h", "t")))
        assertFalse(LiveFields.isLive(Group.HOST, Field.ok("Car App Library version", "1.7.0", "t")))
    }

    @Test
    fun `every car side live label is one the car sources produce`() {
        val performance = PerformanceSource({ fakePorts() }, PerformanceTracker(clock.millis), MemoryPort { MemoryReading(1L shl 20, 2L shl 20, 3L shl 20, 4_096) }).read()
        val touch = TouchSource({ fakePorts() }, SurfaceState(clock.millis), TouchRecorder(clock.millis)).read()
        val carLabels = (performance + touch).map { it.label } + HardwareFeature.entries.flatMap { it.labels }
        val phoneLabels = setOf("Time connected", "Battery", "Battery temperature", "Free memory", "Wi-Fi link speed")

        assertEquals(emptySet<String>(), LiveFields.LABELS - phoneLabels - carLabels.toSet())
    }

    @Test
    fun `a view ignores live values but not their status, labels or other values`() {
        val shown = CarView.OVERVIEW.visible(overview("1 s", "10 MiB"))

        assertEquals(shown, CarView.OVERVIEW.visible(overview("2 s", "11 MiB")))
        assertNotEquals(shown, CarView.OVERVIEW.visible(overview("1 s", "10 MiB", thermal = "severe")))

        val heapFailed = overview("1 s", "10 MiB").let {
            Snapshot(it.groups + (Group.PERFORMANCE to listOf(Field.unavailable("Java heap", "t", "boom"))))
        }
        assertNotEquals(shown, CarView.OVERVIEW.visible(heapFailed))

        val fewer = Snapshot(overview("1 s", "10 MiB").groups - Group.PHONE)
        assertNotEquals(shown, CarView.OVERVIEW.visible(fewer))
    }

    @Test
    fun `the log view does not follow new entries`() {
        assertEquals(CarView.LOG.visible(log("a")), CarView.LOG.visible(log("b", "a")))
    }

    @Test
    fun `the watcher refreshes on a structural change only`() {
        val flow = MutableStateFlow(overview("1 s", "10 MiB"))
        var changes = 0
        val watcher = SnapshotWatcher(flow, CarView.OVERVIEW::visible) { changes++ }
        watcher.shown(flow.value)
        watcher.onStateChanged(OWNER, Lifecycle.Event.ON_START)

        flow.value = overview("2 s", "11 MiB")
        flow.value = overview("3 s", "12 MiB")
        assertEquals(0, changes)

        flow.value = overview("4 s", "12 MiB", thermal = "severe")
        assertEquals(1, changes)

        watcher.onStateChanged(OWNER, Lifecycle.Event.ON_STOP)
        flow.value = overview("5 s", "12 MiB", thermal = "none")
        assertEquals(1, changes)
        watcher.onStateChanged(OWNER, Lifecycle.Event.ON_DESTROY)
    }

    @Test
    fun `list pages start with the refresh row and keep count and titles across refreshes`() {
        val before = ViewRows.rows(CarView.OVERVIEW, overview("1 s", "10 MiB"), 8, emptyList())
        val after = ViewRows.rows(CarView.OVERVIEW, overview("2 s", "11 MiB", thermal = "light"), 8, emptyList())

        for (index in 0..1) {
            val first = CarRows.refreshablePage(before, limit = 6, index = index, canGoDeeper = true)
            val second = CarRows.refreshablePage(after, limit = 6, index = index, canGoDeeper = true)
            assertEquals(CarRows.REFRESH, first.rows.first())
            assertTrue(first.rows.size <= 6)
            assertEquals(first.rows.map { it.title }, second.rows.map { it.title })
        }
    }

    @Test
    fun `a growing log keeps the page title`() {
        val small = CarRows.refreshablePage(CarRows.logRows(log("a").fields(Group.LIFECYCLE)), 6, 0, true)
        val large = CarRows.refreshablePage(CarRows.logRows(log("a", "b", "c", "d", "e", "f", "g").fields(Group.LIFECYCLE)), 6, 0, true)

        assertNotEquals(small.count, large.count)
        assertEquals(CarRows.pageTitle(CarView.LOG, small), CarRows.pageTitle(CarView.LOG, large))
        assertEquals("Log", CarRows.pageTitle(CarView.LOG, large))
        assertEquals("Screen 2/3", CarRows.pageTitle(CarView.SCREEN, Page(emptyList(), 1, 3)))
    }

    @Test
    fun `live fields keep their label, status and source in the comparison`() {
        val structure = LiveFields.structure(Group.PERFORMANCE, listOf(Field.ok("Java heap", "10 MiB", "Runtime")))
        assertEquals(listOf(Field("Java heap", null, "Runtime", Status.OK, null)), structure)
    }

    private companion object {
        val OWNER = object : LifecycleOwner {
            override fun getLifecycle(): Lifecycle = throw UnsupportedOperationException()
        }
    }
}
