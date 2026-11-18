package com.jantursky.autoscope.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.Template
import com.jantursky.autoscope.AppContainer
import com.jantursky.autoscope.core.Snapshot

/** What every car screen of one Session shares. */
class SessionDeps(
    val container: AppContainer,
    val ports: CarPorts,
    val hardware: HardwareListeners,
    val tabIcons: Map<CarView, CarIcon>,
) {
    val sources: CarSources get() = container.carSources

    fun missingPermissions(): List<String> = CarPermissions.HARDWARE.filterNot(ports.permissions::isGranted)

    /** List limit of the host, from ConstraintManager or the library minimum. */
    fun listLimit(): Int = sources.constraints.limit(ContentLimit.LIST)

    fun paneLimit(): Int = sources.constraints.limit(ContentLimit.PANE)
}

/**
 * Logs its lifecycle, refreshes only when the snapshot part it shows has changed, records template
 * timing and handles the row actions shared by all screens. Live values ([LiveFields]) refresh only with
 * the template, so list views also offer [refreshNow].
 */
abstract class BaseScreen(carContext: CarContext, protected val deps: SessionDeps, name: String) : Screen(carContext) {

    private val watcher = SnapshotWatcher(deps.container.repository.snapshot, ::visible) { refresh() }

    init {
        lifecycle.addObserver(LifecycleLogger(deps.container.eventLog, "Screen $name"))
        lifecycle.addObserver(watcher)
    }

    /** The part of the snapshot this screen shows. */
    protected abstract fun visible(snapshot: Snapshot): Any

    protected abstract fun build(snapshot: Snapshot): Template

    final override fun onGetTemplate(): Template {
        deps.sources.performance.templateServed()
        deps.sources.touch.templateServed()
        val snapshot = deps.container.repository.snapshot.value
        watcher.shown(snapshot)
        return build(snapshot)
    }

    protected fun refresh() {
        deps.sources.performance.invalidateRequested()
        invalidate()
    }

    /** "Refresh values": reads every source now, so the live values are current, and rebuilds the template. */
    protected fun refreshNow() {
        deps.container.repository.refresh()
        refresh()
    }

    /** Lists never go deeper than [MAX_LIST_DEPTH]; the fifth step of a task must not be a list. */
    protected fun canPushList(): Boolean = screenManager.stackSize < MAX_LIST_DEPTH

    protected open fun onAction(action: RowAction) {
        when (action) {
            RowAction.REFRESH -> refreshNow()
            RowAction.MARK -> mark()
            RowAction.OPEN_LOG -> if (canPushList()) screenManager.push(ViewScreen(carContext, deps, CarView.LOG, 0))
            RowAction.GRANT_PERMISSIONS -> grantPermissions()
            RowAction.NEXT_PAGE -> Unit
        }
    }

    /** Rows of a list view, paged by the host list limit, each page starting with "Refresh values". */
    protected fun listPage(view: CarView, snapshot: Snapshot, pageIndex: Int): Page = CarRows.refreshablePage(
        ViewRows.rows(view, snapshot, deps.ports.apiLevel, deps.missingPermissions()),
        deps.listLimit(),
        pageIndex,
        canPushList(),
    )

    /**
     * The Touch view: the TOUCH readings (as many as the pane limit allows) and two large actions. The first
     * opens the touch tester when the host can show it, otherwise it measures the row tap latency. The second
     * refreshes the values, as pane rows cannot be clickable.
     */
    protected fun touchPane(snapshot: Snapshot): Pane {
        val rows = ViewRows.rows(CarView.TOUCH, snapshot, deps.ports.apiLevel, emptyList()).take(deps.paneLimit())
        val action = if (TouchSource.testerUsable(deps.ports.apiLevel, deps.sources.surface)) {
            Action.Builder().setTitle("Open touch tester").setOnClickListener {
                if (screenManager.stackSize < MAX_DEPTH) screenManager.push(TouchTesterScreen(carContext, deps))
            }.build()
        } else {
            Action.Builder().setTitle(TouchSource.MEASURE_ACTION).setOnClickListener {
                deps.sources.touch.tapStarted()
                refresh()
            }.build()
        }
        val refresh = Action.Builder().setTitle(CarRows.REFRESH_TITLE).setOnClickListener { refreshNow() }.build()
        return Pane.Builder().apply { rows.forEach { addRow(Templates.paneRow(it)) } }
            .addAction(action)
            .addAction(refresh)
            .build()
    }

    private fun mark() {
        val text = try {
            "Saved ${deps.container.mark("Mark this moment").id}"
        } catch (e: Exception) {
            "Saving failed: ${e.message}"
        }
        CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()
    }

    private fun grantPermissions() {
        val missing = deps.missingPermissions()
        if (missing.isEmpty()) {
            CarToast.makeText(carContext, "All permissions granted", CarToast.LENGTH_SHORT).show()
            return
        }
        // Opens the phone's permission dialog; the host does not show it while driving.
        carContext.requestPermissions(missing) { approved, rejected ->
            deps.container.eventLog.add("Permissions approved: $approved, rejected: $rejected")
            deps.hardware.start()
            refresh()
        }
    }

    companion object {
        const val MAX_LIST_DEPTH = 4
        const val MAX_DEPTH = 5
    }
}
