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
        assertEquals(listOf("Host and library", "Phone", "Changes", "Reports", "Help"), tabs.map { it.title })
    }

    @Test fun withoutDataOnlyFixedPagesExist() {
        assertEquals(listOf("Changes", "Reports", "Help"), tabModels(emptyList()).map { it.title })
    }

    @Test fun badgeCountsProblemsButNotWaitingForCar() {
        val tabs = tabModels(groups(Group.HARDWARE to listOf(ok, broken, waiting, broken)))
        assertEquals(2, tabs.first().badge)
        assertEquals(0, tabs.last().badge)
    }

    @Test fun resolvesStoredTab() {
        val tabs = tabModels(groups(Group.HOST to listOf(ok)))
        assertEquals(TabId.Help, resolveTab(tabs, "help").id)
        assertEquals(TabId.OfGroup(Group.HOST), resolveTab(tabs, "group:HOST").id)
    }

    @Test fun fallsBackToFirstTabWhenGroupDisappearsOrKeyIsUnknown() {
        val tabs = tabModels(groups(Group.HOST to listOf(ok)))
        assertEquals(TabId.OfGroup(Group.HOST), resolveTab(tabs, "group:TOUCH").id)
        assertEquals(TabId.OfGroup(Group.HOST), resolveTab(tabs, null).id)
        assertEquals(TabId.Changes, resolveTab(tabModels(emptyList()), "group:HOST").id)
    }
}
