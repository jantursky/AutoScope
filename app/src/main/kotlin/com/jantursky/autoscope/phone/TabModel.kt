package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group

/** A page of the phone screen. [key] survives recreation, so it is stored as text. */
sealed interface TabId {
    val key: String

    data class OfGroup(val group: Group) : TabId {
        override val key = "group:${group.name}"
    }

    object Car : TabId {
        override val key = "car"
    }

    object Changes : TabId {
        override val key = "changes"
    }

    object Reports : TabId {
        override val key = "reports"
    }

    object Help : TabId {
        override val key = "help"
    }
}

/** One chip: [badge] counts the fields that are a problem, 0 shows no badge. */
data class TabModel(val id: TabId, val title: String, val badge: Int)

/** The Car tab, one tab per group that has fields in group order, then the fixed pages. The car page needs no data to exist. */
fun tabModels(groups: List<GroupModel>): List<TabModel> =
    listOf(TabModel(TabId.Car, "Car", 0)) + groups.map { TabModel(TabId.OfGroup(it.group), it.group.title, it.issues) } + listOf(
        TabModel(TabId.Changes, "Changes", 0),
        TabModel(TabId.Reports, "Reports", 0),
        TabModel(TabId.Help, "Help", 0),
    )

/** The tab stored under [key], or the first one when it is unknown or its group has gone away. */
fun resolveTab(tabs: List<TabModel>, key: String?): TabModel = tabs.firstOrNull { it.id.key == key } ?: tabs.first()
