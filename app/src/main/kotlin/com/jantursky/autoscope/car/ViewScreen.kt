package com.jantursky.autoscope.car

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import com.jantursky.autoscope.core.Snapshot

/** One view pushed on the stack: a further page of a tab, the log, or a view of the pre-tab menu. */
class ViewScreen(
    carContext: CarContext,
    deps: SessionDeps,
    private val view: CarView,
    private val pageIndex: Int,
) : BaseScreen(carContext, deps, "${view.title} page ${pageIndex + 1}") {

    override fun visible(snapshot: Snapshot): Any = view.visible(snapshot)

    override fun build(snapshot: Snapshot): Template {
        val api = deps.ports.apiLevel
        if (view == CarView.TOUCH) return Templates.screenPane(view.title, touchPane(snapshot), api)
        val page = listPage(view, snapshot, pageIndex)
        return Templates.screenList(CarRows.pageTitle(view, page), Templates.itemList(page.rows, ::onAction), api)
    }

    override fun onAction(action: RowAction) {
        if (action == RowAction.NEXT_PAGE) {
            if (canPushList()) screenManager.push(ViewScreen(carContext, deps, view, pageIndex + 1))
        } else {
            super.onAction(action)
        }
    }
}
