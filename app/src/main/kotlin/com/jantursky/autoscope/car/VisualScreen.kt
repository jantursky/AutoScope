package com.jantursky.autoscope.car

import android.graphics.Canvas
import androidx.car.app.CarContext
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/**
 * The Visual dashboard on the map surface (see [VisualRenderer]) with the same three readings as text in
 * the content pane. The surface redraws on every sensor update and on every snapshot tick, at most
 * [FRAME_MS] apart; the pane follows the list rules and refreshes only for non-live changes or on
 * "Refresh values".
 *
 * Untested on a real head unit. On the DHU the compass, accelerometer and speed listeners report only
 * what the DHU simulates, so all four tiles may show "no data" unless sensor values are fed in.
 */
class VisualScreen(carContext: CarContext, deps: SessionDeps) :
    MapSurfaceScreen(carContext, deps, "Visual", FRAME_MS) {

    private val samples = deps.sources.samples
    private val repository = deps.container.repository
    private val renderer = VisualRenderer(::model, samples::now, { carContext.isDarkMode }, { surfaceState.visibleArea })

    // Every snapshot differs from the never set "shown" value, so this redraws once per tick: field
    // statuses change and the speed history moves on even when the car reports nothing new.
    private val ticks = SnapshotWatcher(repository.snapshot, { it }) { requestDraw() }

    init {
        lifecycle.addObserver(ticks)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> samples.onChange = ::requestDraw
                Lifecycle.Event.ON_STOP -> samples.onChange = null
                else -> Unit
            }
        })
    }

    override fun visible(snapshot: Snapshot): Any = CarView.VISUAL.visible(snapshot)

    override fun build(snapshot: Snapshot): Template {
        // page() turns no fields into a "No data" row: a Pane without rows fails to build.
        val rows = CarRows.page(ViewRows.rows(CarView.VISUAL, snapshot, rowContext()), deps.paneLimit(), 0, false).rows
        val pane = Pane.Builder().apply { rows.forEach { addRow(Templates.paneRow(it)) } }
            .addAction(refreshAction())
            .build()
        requestDraw()
        return mapTemplate(PaneTemplate.Builder(pane).setHeader(Templates.header(CarView.VISUAL.title)).build())
    }

    override fun paint(canvas: Canvas, dpi: Int) = renderer.render(canvas, dpi)

    private fun model(): VisualModel = VisualModel.build(
        repository.snapshot.value.fields(Group.HARDWARE),
        samples.heading,
        samples.force,
        samples.speedMetersPerSecond,
        samples.speedHistory.window(samples.now()),
    )

    private companion object {
        const val FRAME_MS = 200L
    }
}
