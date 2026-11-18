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

    /** Which map surface screen holds the AppManager SurfaceCallback. */
    val surfaceOwnership = SurfaceOwnership()

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

    protected fun rowContext() = RowContext(
        deps.ports.apiLevel,
        deps.missingPermissions(),
        MapSurface.blockedReason(deps.ports.apiLevel, deps.sources.surface),
    )

    /** Whether a further page of a list may be pushed; the fifth step of a task must not be a list. */
    protected fun canPushList(): Boolean = Navigation.mayPush(ViewKind.LIST, screenManager.stackSize)

    protected open fun onAction(action: RowAction) {
        when (action) {
            RowAction.Refresh -> refreshNow()
            RowAction.Mark -> mark()
            RowAction.GrantPermissions -> grantPermissions()
            is RowAction.Open -> open(action.view)
            RowAction.NextPage -> Unit
        }
    }

    /** Pushes the screen of [view] when the depth rules allow it; page 1 for a list. */
    protected fun open(view: CarView) {
        if (!Navigation.mayPush(view.kind, screenManager.stackSize)) return
        deps.container.eventLog.add("Open ${view.title}")
        screenManager.push(if (view == CarView.VISUAL) VisualScreen(carContext, deps) else ViewScreen(carContext, deps, view, 0))
    }

    /**
     * Rows of a list view, paged by the host list limit. Data pages start with "Refresh values"; the More
     * list only navigates and fits the smallest list limit (6) without paging.
     */
    protected fun listPage(view: CarView, snapshot: Snapshot, pageIndex: Int): Page {
        val rows = ViewRows.rows(view, snapshot, rowContext())
        return if (view == CarView.MORE) {
            CarRows.page(rows, deps.listLimit(), pageIndex, canPushList())
        } else {
            CarRows.refreshablePage(rows, deps.listLimit(), pageIndex, canPushList())
        }
    }

    /**
     * The Touch view: the TOUCH readings (as many as the pane limit allows) and two large actions. The first
     * opens the touch tester when the host can show it, otherwise it measures the row tap latency. The second
     * refreshes the values, as pane rows cannot be clickable.
     */
    protected fun touchPane(snapshot: Snapshot): Pane {
        val context = rowContext()
        val rows = ViewRows.rows(CarView.TOUCH, snapshot, context).take(deps.paneLimit())
        val action = if (context.mapBlockedReason == null) {
            Action.Builder().setTitle("Open touch tester").setOnClickListener {
                if (Navigation.mayPush(ViewKind.MAP, screenManager.stackSize)) {
                    screenManager.push(TouchTesterScreen(carContext, deps))
                }
            }.build()
        } else {
            Action.Builder().setTitle(TouchSource.MEASURE_ACTION).setOnClickListener {
                deps.sources.touch.tapStarted()
                refresh()
            }.build()
        }
        return Pane.Builder().apply { rows.forEach { addRow(Templates.paneRow(it)) } }
            .addAction(action)
            .addAction(refreshAction())
            .build()
    }

    /** "Refresh values" as a pane action, as pane rows cannot be clickable. */
    protected fun refreshAction(): Action =
        Action.Builder().setTitle(CarRows.REFRESH_TITLE).setOnClickListener { refreshNow() }.build()

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
}
