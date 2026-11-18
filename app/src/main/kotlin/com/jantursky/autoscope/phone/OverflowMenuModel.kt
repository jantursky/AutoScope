package com.jantursky.autoscope.phone

import com.jantursky.autoscope.ThemeMode

/** What a menu entry does when tapped. */
sealed interface MenuAction {
    object Save : MenuAction
    object Copy : MenuAction
    data class Interval(val millis: Long) : MenuAction
    data class Theme(val mode: ThemeMode) : MenuAction
}

enum class MenuSectionKind { SNAPSHOT, INTERVAL, THEME }

/** One row of the overflow menu. [checked] is only meaningful for the radio sections. */
data class MenuEntry(val action: MenuAction, val checked: Boolean)

data class MenuSection(val kind: MenuSectionKind, val entries: List<MenuEntry>)

/** The menu for the stored refresh interval and theme; an out-of-list interval checks the closest preset. */
fun menuSections(refreshMillis: Long, theme: ThemeMode): List<MenuSection> {
    val interval = closestRefreshChoice(refreshMillis)
    return listOf(
        MenuSection(MenuSectionKind.SNAPSHOT, listOf(MenuEntry(MenuAction.Save, checked = false), MenuEntry(MenuAction.Copy, checked = false))),
        MenuSection(
            MenuSectionKind.INTERVAL,
            REFRESH_CHOICES_MS.mapIndexed { index, ms -> MenuEntry(MenuAction.Interval(ms), index == interval) },
        ),
        MenuSection(MenuSectionKind.THEME, ThemeMode.entries.map { MenuEntry(MenuAction.Theme(it), it == theme) }),
    )
}
