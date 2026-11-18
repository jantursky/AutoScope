package com.jantursky.autoscope.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val snapshot = Snapshot(
        mapOf(
            Group.HOST to listOf(Field.ok("Quote \"x\"", "line1\nline2", "Build")),
            Group.HARDWARE to listOf(Field.permissionMissing("Speed", "CarInfo", "CAR_SPEED")),
        ),
    )

    @Test
    fun `json escapes special characters and keeps status`() {
        val json = ReportFormat.json(snapshot, 0, "manual")

        assertTrue(json.contains("\"label\":\"Quote \\\"x\\\"\""))
        assertTrue(json.contains("\"value\":\"line1\\nline2\""))
        assertTrue(json.contains("\"status\":\"PERMISSION_MISSING\""))
        assertTrue(json.contains("\"value\":null"))
    }

    @Test
    fun `text report lists groups with titles`() {
        val text = ReportFormat.text(snapshot, 0, "manual")

        assertTrue(text.contains("== Host and library =="))
        assertTrue(text.contains("Speed: PERMISSION_MISSING: Missing permission: CAR_SPEED  [CarInfo]"))
    }

    @Test
    fun `store writes json and text twins and lists newest first`() {
        var now = Instant.parse("2026-01-01T10:00:00Z")
        val store = ReportStore(tmp.root, object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        })

        val first = store.save(snapshot, "Mark this moment")
        now = now.plusSeconds(5)
        val second = store.save(snapshot, "Connection changed")

        assertTrue(first.json.exists() && first.text.exists())
        assertEquals(listOf(second.id, first.id), store.list().map { it.id })
        assertTrue(first.id.endsWith("mark-this-moment"))
    }

    @Test
    fun `paginate splits by host limit and never returns zero size pages`() {
        assertEquals(listOf(listOf(1, 2), listOf(3)), paginate(listOf(1, 2, 3), 2))
        assertEquals(3, paginate(listOf(1, 2, 3), 0).size)
        assertEquals(listOf(emptyList<Int>()), paginate(emptyList<Int>(), 5))
    }

    @Test
    fun `event log is bounded and newest first`() {
        val log = EventLog(capacity = 2)
        log.add("a"); log.add("b"); log.add("c")

        assertEquals(listOf("c", "b"), log.read().map { it.value })
    }
}
