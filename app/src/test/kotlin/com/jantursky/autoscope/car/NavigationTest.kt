package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    private val root = 1

    @Test
    fun `four tabs with More last and every other view reachable from More`() {
        assertEquals(listOf(CarView.OVERVIEW, CarView.SCREEN, CarView.HARDWARE, CarView.MORE), CarView.TABS)
        assertEquals(CarView.entries.toSet(), (CarView.TABS + CarView.MORE_ENTRIES).toSet())
    }

    @Test
    fun `More has one large navigation row per entry and fits the smallest list limit`() {
        val rows = ViewRows.rows(CarView.MORE, Snapshot.EMPTY, RowContext(8, emptyList(), null))

        assertEquals(listOf("Touch", "Visual", "Sensors", "Network", "Permissions", "Log"), rows.map { it.title })
        assertEquals(CarView.MORE_ENTRIES.map { RowAction.Open(it) }, rows.map { it.action })
        // ConstraintManager guarantees at least 6 list rows, so More never needs a Next page row.
        val page = CarRows.page(rows, limit = 6, index = 0, canGoDeeper = true)
        assertEquals(1, page.count)
        assertEquals(rows, page.rows)
    }

    @Test
    fun `a blocked Visual keeps its row and title but cannot be opened`() {
        val open = ViewRows.moreRows(null)
        val blocked = ViewRows.moreRows("MapWithContentTemplate needs Car App API 7, host has 6")

        assertEquals(open.map { it.title }, blocked.map { it.title })
        val visual = blocked.single { it.title == "Visual" }
        assertNull(visual.action)
        assertTrue(visual.lines.first().startsWith("Not available: MapWithContentTemplate"))
        assertEquals(5, blocked.count { it.action != null })
    }

    @Test
    fun `every More entry opens at depth 2 from the tab root`() {
        CarView.MORE_ENTRIES.forEach { assertTrue(it.name, Navigation.mayPush(it.kind, root)) }
    }

    @Test
    fun `list pages stop at depth 4 so the fifth step is never a list`() {
        var depth = root + 1 // a list entry of More, or a tab view of the older menu
        var pages = 1
        while (Navigation.mayPush(ViewKind.LIST, depth)) {
            depth++
            pages++
        }
        assertEquals(Navigation.MAX_LIST_DEPTH, depth)
        assertEquals(3, pages)
        assertFalse(Navigation.mayPush(ViewKind.LIST, Navigation.MAX_LIST_DEPTH))
        assertTrue(Navigation.mayPush(ViewKind.PANE, Navigation.MAX_LIST_DEPTH))
        assertFalse(Navigation.mayPush(ViewKind.MAP, Navigation.MAX_DEPTH))
    }

    @Test
    fun `the deepest paths stay within five screens`() {
        // Tabs: root, Touch pane, touch tester.
        assertTrue(Navigation.mayPush(CarView.TOUCH.kind, root))
        assertTrue(Navigation.mayPush(ViewKind.MAP, root + 1))
        // Older menu (no tabs, no map templates below API 7): root, More, Sensors, Sensors page 2.
        assertTrue(Navigation.mayPush(CarView.MORE.kind, root))
        assertTrue(Navigation.mayPush(CarView.SENSORS.kind, root + 1))
        assertTrue(Navigation.mayPush(ViewKind.LIST, root + 2))
        assertFalse(Navigation.mayPush(ViewKind.LIST, root + 3))
    }

    @Test
    fun `only the screen that holds the surface callback clears it`() {
        val ownership = SurfaceOwnership()
        val tester = Any()
        val visual = Any()

        ownership.claim(tester)
        // ScreenManager starts the pushed screen before it stops the one below.
        ownership.claim(visual)
        assertFalse(ownership.release(tester))
        assertTrue(ownership.release(visual))
        assertFalse(ownership.release(visual))
    }

    @Test
    fun `map screens need API 7 and a surface that arrives`() {
        val clock = FakeClock()
        val surface = SurfaceState(clock.millis)

        assertEquals("MapWithContentTemplate needs Car App API 7, host has 6", MapSurface.blockedReason(6, surface))
        assertNull(MapSurface.blockedReason(7, surface))

        surface.surfaceRequested()
        clock.now += SurfaceState.SURFACE_TIMEOUT_MS
        assertEquals("the host provided no map surface", MapSurface.blockedReason(7, surface))
    }
}
