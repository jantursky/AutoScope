package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.DiagnosticsRepository
import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.ReportStore
import com.jantursky.autoscope.core.Status
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val OFF = 0
private const val NATIVE = 1
private const val PROJECTION = 2

private class FakeFeed : ConnectionTypeFeed {
    private var listener: (Int) -> Unit = {}
    override fun observe(listener: (Int) -> Unit) { this.listener = listener }
    fun emit(type: Int) = listener(type)
}

class ConnectionTrackerTest {
    @Test fun reconnectsCountConnectionsAfterTheFirst() {
        val t = ConnectionTracker()
        assertNull(t.current())
        t.onType(PROJECTION, 0)
        assertEquals(0, t.current()!!.reconnects)
        t.onType(OFF, 10)
        assertNull(t.current()!!.connectedSince)
        t.onType(PROJECTION, 20)
        assertEquals(1, t.current()!!.reconnects)
        assertEquals(20L, t.current()!!.connectedSince)
    }

    @Test fun startingDisconnectedIsNotAReconnect() {
        val t = ConnectionTracker()
        t.onType(OFF, 0)
        t.onType(PROJECTION, 5)
        assertEquals(0, t.current()!!.reconnects)
    }

    @Test fun switchBetweenConnectedTypesRestartsTimerWithoutReconnect() {
        val t = ConnectionTracker()
        t.onType(PROJECTION, 0)
        t.onType(NATIVE, 50)
        assertEquals(50L, t.current()!!.connectedSince)
        assertEquals(0, t.current()!!.reconnects)
    }

    @Test fun repeatedTypeChangesNothing() {
        val t = ConnectionTracker()
        t.onType(PROJECTION, 0)
        t.onType(PROJECTION, 99)
        assertEquals(0L, t.current()!!.connectedSince)
    }
}

class ConnectionHistoryTest {
    @Test fun recordsEveryChangeButNotRepeats() {
        val t = ConnectionTracker()
        t.onType(OFF, 0)
        t.onType(OFF, 5)
        t.onType(PROJECTION, 10)
        t.onType(NATIVE, 20)
        assertEquals(listOf(ConnectionEvent(OFF, 0), ConnectionEvent(PROJECTION, 10), ConnectionEvent(NATIVE, 20)), t.history())
    }

    @Test fun keepsOnlyTheMostRecentEvents() {
        val t = ConnectionTracker()
        repeat(80) { t.onType(if (it % 2 == 0) OFF else PROJECTION, it.toLong()) }
        val history = t.history()
        assertEquals(50, history.size)
        assertEquals(30L, history.first().at)
        assertEquals(79L, history.last().at)
    }
}

class CarConnectionSourceTest {
    @Test fun noValueYetIsUnavailable() {
        val fields = CarConnectionSource(ConnectionTracker()) { 0 }.read()
        assertEquals(3, fields.size)
        assertEquals(setOf(Status.UNAVAILABLE), fields.map { it.status }.toSet())
    }

    @Test fun showsTypeDurationAndDash() {
        var now = 0L
        val tracker = ConnectionTracker()
        val source = CarConnectionSource(tracker) { now }
        tracker.onType(PROJECTION, 0)
        now = 65_000
        assertEquals(listOf("Projection (Android Auto)", "1m 05s", "0"), source.read().map { it.value })
        tracker.onType(OFF, now)
        assertEquals("-", source.read()[1].value)
    }
}

class ConnectionSnapshotterTest {
    private fun fixture(): Triple<FakeFeed, ReportStore, File> {
        val dir = Files.createTempDirectory("reports").toFile()
        return Triple(FakeFeed(), ReportStore(dir), dir)
    }

    @Test fun savesOnChangeButNotOnFirstValueOrRepeat() {
        val (feed, store, dir) = fixture()
        val monitor = CarConnectionMonitor(feed) { 0 }
        val log = EventLog()
        ConnectionSnapshotter(monitor, DiagnosticsRepository(emptyList()), store, log)

        feed.emit(OFF)
        assertEquals(0, store.list().size)
        feed.emit(OFF)
        assertEquals(0, store.list().size)
        feed.emit(PROJECTION)
        assertEquals(1, store.list().size)
        assertEquals(true, store.list().single().id.contains("connection-projection"))
        dir.deleteRecursively()
    }

    @Test fun valueDeliveredBeforeConstructionIsTheBaseline() {
        val (feed, store, dir) = fixture()
        val monitor = CarConnectionMonitor(feed) { 0 }
        feed.emit(PROJECTION)
        ConnectionSnapshotter(monitor, DiagnosticsRepository(emptyList()), store, EventLog())
        feed.emit(PROJECTION)
        assertEquals(0, store.list().size)
        feed.emit(OFF)
        assertEquals(1, store.list().size)
        dir.deleteRecursively()
    }
}
