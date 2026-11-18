package com.jantursky.autoscope.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.core.Snapshot

/**
 * The first screen. From Car App API 6 a TabTemplate with the four [CarView.TABS]; on older hosts a list
 * of the same views, each pushed as a [ViewScreen].
 */
class RootScreen(carContext: CarContext, deps: SessionDeps) : BaseScreen(carContext, deps, "Root") {

    private val tabs = deps.ports.apiLevel >= CarAppApiLevels.LEVEL_6
    private var active = CarView.OVERVIEW

    override fun visible(snapshot: Snapshot): Any = if (tabs) active.visible(snapshot) else Unit

    override fun build(snapshot: Snapshot): Template = if (tabs) tabTemplate(snapshot) else menu()

    // TabTemplate and Tab are @RequiresCarApi(6), checked in [tabs].
    private fun tabTemplate(snapshot: Snapshot): Template {
        val builder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                active = CarView.valueOf(tabContentId)
                deps.container.eventLog.add("Tab selected: ${active.title}")
                refresh()
            }
        })
            .setHeaderAction(Action.APP_ICON)
            .setActiveTabContentId(active.name)
            .setTabContents(TabContents.Builder(content(snapshot)).build())
        CarView.TABS.forEach { view ->
            builder.addTab(
                Tab.Builder().setTitle(view.title).setContentId(view.name).setIcon(deps.tabIcons.getValue(view)).build(),
            )
        }
        return builder.build()
    }

    private fun content(snapshot: Snapshot): Template =
        if (active == CarView.TOUCH) {
            Templates.tabPane(touchPane(snapshot))
        } else {
            Templates.tabList(Templates.itemList(listPage(active, snapshot, 0).rows, ::onAction))
        }

    override fun onAction(action: RowAction) {
        if (action == RowAction.NEXT_PAGE) {
            if (canPushList()) screenManager.push(ViewScreen(carContext, deps, active, 1))
        } else {
            super.onAction(action)
        }
    }

    private fun menu(): Template {
        val list = ItemList.Builder()
        CarView.TABS.forEach { view ->
            list.addItem(
                Row.Builder().setTitle(view.title).setBrowsable(true)
                    .setOnClickListener { screenManager.push(ViewScreen(carContext, deps, view, 0)) }
                    .build(),
            )
        }
        @Suppress("DEPRECATION") // ListTemplate.Builder.setTitle; Header needs Car App API 7.
        return androidx.car.app.model.ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("AutoScope diagnostic tool, Car App API ${deps.ports.apiLevel}")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
