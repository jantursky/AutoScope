package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TabModelTest {
    private fun groups(vararg entries: Pair<Group, List<Field>>) = groupModels(Snapshot(entries.toMap()))

    private val ok = Field.ok("a", 1, "s")
    private val broken = Field.unavailable("b", "s", "broken")
    private val waiting = Field.unavailable("c", "s", NO_CAR_SESSION)

    @Test fun groupTabsComeFirstInGroupOrderThenFixedPages() {
        val tabs = tabModels(groups(Group.PHONE to listOf(ok), Group.HOST to listOf(ok)))
        assertEquals(listOf("Car", "Host and library", "Phone", "Changes", "Reports", "Help"), tabs.map { it.title })
    }

    @Test fun withoutDataOnlyFixedPagesExist() {
        assertEquals(listOf("Car", "Changes", "Reports", "Help"), tabModels(emptyList()).map { it.title })
    }

    @Test fun badgeCountsProblemsButNotWaitingForCar() {
        val tabs = tabModels(groups(Group.HARDWARE to listOf(ok, broken, waiting, broken)))
        assertEquals(0, tabs.first().badge)
        assertEquals(2, tabs[1].badge)
        assertEquals(0, tabs.last().badge)
    }

    @Test fun carTabIsFirstAndExistsWithoutData() {
        assertEquals(TabId.Car, tabModels(emptyList()).first().id)
        assertEquals("car", resolveTab(tabModels(emptyList()), "car").id.key)
    }

    @Test fun resolvesStoredTab() {
        val tabs = tabModels(groups(Group.HOST to listOf(ok)))
        assertEquals(TabId.Help, resolveTab(tabs, "help").id)
        assertEquals(TabId.OfGroup(Group.HOST), resolveTab(tabs, "group:HOST").id)
    }

    @Test fun fallsBackToFirstTabWhenGroupDisappearsOrKeyIsUnknown() {
        val tabs = tabModels(groups(Group.HOST to listOf(ok)))
        assertEquals(TabId.Car, resolveTab(tabs, "group:TOUCH").id)
        assertEquals(TabId.Car, resolveTab(tabs, null).id)
        assertEquals(TabId.Car, resolveTab(tabModels(emptyList()), "group:HOST").id)
    }
}
