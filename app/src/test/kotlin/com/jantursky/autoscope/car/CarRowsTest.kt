package com.jantursky.autoscope.car

import androidx.car.app.hardware.common.CarValue
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRowsTest {

    private fun rows(n: Int) = (1..n).map { UiRow("Row $it", listOf("v")) }

    @Test
    fun `short text stays on one line`() {
        assertEquals(listOf("42 (OK)"), CarRows.splitText("42 (OK)"))
    }

    @Test
    fun `long text wraps at spaces into two lines and is cut with an ellipsis`() {
        val text = (1..40).joinToString(" ") { "word$it" }
        val lines = CarRows.splitText(text, maxLine = 20)

        assertEquals(2, lines.size)
        assertTrue(lines.all { it.length <= 20 })
        assertEquals("word1 word2 word3", lines[0])
        assertTrue(lines[1].endsWith("…"))
    }

    @Test
    fun `a word longer than a line is cut hard`() {
        val lines = CarRows.splitText("x".repeat(50), maxLine = 20)
        assertEquals("x".repeat(20), lines[0])
        assertEquals(20, lines[1].length)
    }

    @Test
    fun `empty text becomes a dash`() {
        assertEquals(listOf("-"), CarRows.splitText("  "))
    }

    @Test
    fun `rows that fit the limit make one page without navigation`() {
        val page = CarRows.page(rows(6), limit = 6, index = 0, canGoDeeper = true)
        assertEquals(1, page.count)
        assertEquals(6, page.rows.size)
    }

    @Test
    fun `longer content gets a next page row on every page but the last`() {
        val all = rows(12)
        val first = CarRows.page(all, limit = 6, index = 0, canGoDeeper = true)
        val last = CarRows.page(all, limit = 6, index = 2, canGoDeeper = true)

        assertEquals(3, first.count)
        assertEquals(6, first.rows.size)
        assertEquals(RowAction.NextPage, first.rows.last().action)
        assertEquals("Page 2 of 3", first.rows.last().lines.single())
        assertEquals(listOf("Row 11", "Row 12"), last.rows.map { it.title })
    }

    @Test
    fun `at the depth limit the last row says how many rows remain`() {
        val page = CarRows.page(rows(12), limit = 6, index = 0, canGoDeeper = false)
        val tail = page.rows.last()

        assertEquals(null, tail.action)
        assertTrue(tail.lines.single().startsWith("7 more"))
    }

    @Test
    fun `a bad host limit still shows one row per page`() {
        val page = CarRows.page(rows(3), limit = 0, index = 0, canGoDeeper = true)
        assertEquals(listOf("Row 1", "Next page"), page.rows.map { it.title })
    }

    @Test
    fun `an empty view shows a no data row`() {
        assertEquals("No data", CarRows.page(emptyList(), 6, 0, true).rows.single().title)
    }

    @Test
    fun `log rows have stable titles so new entries refresh instead of adding a step`() {
        val first = CarRows.logRows(listOf(Field.ok("10:00:00.000", "Session ON_CREATE", "log")))
        val second = CarRows.logRows(
            listOf(Field.ok("10:00:01.000", "Screen Root ON_START", "log"), Field.ok("10:00:00.000", "Session ON_CREATE", "log")),
        )

        assertEquals("#1", first.single().title)
        assertEquals(first.single().title, second.first().title)
        assertEquals("10:00:01.000 Screen Root ON_START", second.first().lines.single())
    }

    @Test
    fun `overview starts with the diagnostic marking and the mark row`() {
        val snapshot = Snapshot(mapOf(Group.HOST to listOf(Field.ok("Host package", "gearhead", "test"))))
        val rows = ViewRows.rows(CarView.OVERVIEW, snapshot, RowContext(8, emptyList(), null))

        assertEquals("AutoScope diagnostic tool", rows[0].title)
        assertEquals("Car App API level 8 (host)", rows[0].lines.single())
        assertEquals(RowAction.Mark, rows[1].action)
        assertEquals("gearhead (OK)", rows[2].lines.single())
    }

    @Test
    fun `hardware view lists missing permissions in its grant row`() {
        val rows = ViewRows.rows(
            CarView.HARDWARE,
            Snapshot.EMPTY,
            RowContext(8, listOf(CarPermissions.CAR_SPEED, CarPermissions.FINE_LOCATION), null),
        )

        assertEquals(RowAction.GrantPermissions, rows.single().action)
        assertEquals("Missing: CAR_SPEED, ACCESS_FINE_LOCATION", rows.single().lines.single())
    }

    @Test
    fun `tabs fit the TabTemplate limit and a view changes only with its own groups`() {
        assertTrue(CarView.TABS.size in 2..4)
        assertEquals(CarView.TABS.size, CarView.TABS.map { it.title.first() }.toSet().size)
        val before = Snapshot(mapOf(Group.HARDWARE to listOf(Field.ok("Make", "A", "t"))))
        val otherGroup = Snapshot(before.groups + (Group.SCREEN to listOf(Field.ok("Width", "1", "t"))))
        val ownGroup = Snapshot(mapOf(Group.HARDWARE to listOf(Field.ok("Make", "B", "t"))))

        assertEquals(CarView.HARDWARE.visible(before), CarView.HARDWARE.visible(otherGroup))
        assertNotEquals(CarView.HARDWARE.visible(before), CarView.HARDWARE.visible(ownGroup))
    }

    @Test
    fun `row text leads with what is missing and drops package prefixes`() {
        assertEquals("42 (OK)", CarRows.fieldText(Field.ok("Speed", 42, "t")))
        assertEquals(
            "Not implemented by this car/host",
            CarRows.fieldText(CarValues.noValue(CarValue.STATUS_UNIMPLEMENTED).field("Compass", "t")),
        )
        assertEquals("No value yet", CarRows.fieldText(CarValues.noValue(CarValue.STATUS_UNAVAILABLE).field("Compass", "t")))
        assertEquals(
            "Missing permission: CAR_SPEED",
            CarRows.fieldText(Field.permissionMissing("Raw speed", "t", CarPermissions.CAR_SPEED)),
        )
        assertEquals(
            "Not granted: SecurityException: needs ACCESS_FINE_LOCATION",
            CarRows.fieldText(Field("Location", null, "t", Status.PERMISSION_MISSING, "SecurityException: needs android.permission.ACCESS_FINE_LOCATION")),
        )
        assertEquals(
            "Not supported: onClick needs Car App API 5, host has 4",
            CarRows.fieldText(Field.notSupported("Clicks", "t", needsLevel("onClick", 5, 4))),
        )
        assertEquals("No value", CarRows.fieldText(Field("X", null, "t", Status.UNAVAILABLE)))
    }

    @Test
    fun `hardware, sensor and permission rows fit two lines without losing their start`() {
        val ports = fakePorts(apiLevel = 8, permissions = FakePermissions(mutableSetOf()))
        val hardware = HardwareSource({ ports }, HardwareState()).read() +
            HardwareSource({ fakePorts(apiLevel = 2) }, HardwareState()).read()
        val permissions = Field(
            "Car speed", null, "t", Status.PERMISSION_MISSING,
            "Tap Grant permissions on the Hardware tab in the car, or grant it in phone Settings",
        )
        (hardware + permissions).forEach { field ->
            val lines = CarRows.fieldRow(field).lines
            assertTrue("${field.label}: $lines", lines.size <= CarRows.MAX_LINES && lines.all { it.length <= CarRows.MAX_LINE })
            assertTrue("${field.label}: $lines", lines.none { it.endsWith("…") })
            assertTrue(lines.first().startsWith("No") || lines.first().startsWith("Not") || lines.first().startsWith("Missing"))
        }
    }

    @Test
    fun `map pane rows wrap earlier and keep the reason readable`() {
        val row = CarRows.fieldRow(CarValues.noValue(CarValue.STATUS_UNIMPLEMENTED).field("Compass", "t"), CarRows.MAP_PANE_LINE)
        assertEquals(listOf("Not implemented by this", "car/host"), row.lines)
    }
}
