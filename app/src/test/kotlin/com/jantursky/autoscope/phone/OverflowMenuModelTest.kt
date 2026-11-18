package com.jantursky.autoscope.phone

import com.jantursky.autoscope.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverflowMenuModelTest {
    private fun section(kind: MenuSectionKind, millis: Long = 1_000L, theme: ThemeMode = ThemeMode.SYSTEM) =
        menuSections(millis, theme).first { it.kind == kind }

    @Test fun sectionsAreSnapshotIntervalTheme() {
        assertEquals(
            listOf(MenuSectionKind.SNAPSHOT, MenuSectionKind.INTERVAL, MenuSectionKind.THEME),
            menuSections(1_000L, ThemeMode.SYSTEM).map { it.kind },
        )
    }

    @Test fun intervalEntriesMapToPresetsAndCheckTheCurrentOne() {
        val entries = section(MenuSectionKind.INTERVAL, millis = 2_000L).entries
        assertEquals(REFRESH_CHOICES_MS, entries.map { (it.action as MenuAction.Interval).millis })
        assertEquals(listOf(false, false, true, false, false), entries.map { it.checked })
    }

    @Test fun outOfListIntervalChecksClosestPreset() {
        val checked = section(MenuSectionKind.INTERVAL, millis = 2_300L).entries.single { it.checked }
        assertEquals(MenuAction.Interval(2_000L), checked.action)
    }

    @Test fun themeEntriesCheckTheStoredMode() {
        val entries = section(MenuSectionKind.THEME, theme = ThemeMode.DARK).entries
        assertEquals(ThemeMode.entries.map { MenuAction.Theme(it) }, entries.map { it.action })
        assertEquals(MenuAction.Theme(ThemeMode.DARK), entries.single { it.checked }.action)
    }

    @Test fun snapshotSectionHasSaveThenCopyWithoutChecks() {
        assertEquals(
            listOf(MenuEntry(MenuAction.Save, false), MenuEntry(MenuAction.Copy, false)),
            section(MenuSectionKind.SNAPSHOT).entries,
        )
    }
}
