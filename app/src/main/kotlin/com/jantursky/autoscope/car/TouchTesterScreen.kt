package com.jantursky.autoscope.car

import android.graphics.Canvas
import androidx.car.app.CarContext
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/**
 * The touch tester: draws every SurfaceCallback gesture on the map surface and lists the TOUCH readings
 * next to it.
 *
 * Unverified on a head unit: the library documents the surface for MapWithContentTemplate, but whether a
 * POI app gets onClick/onScroll/onFling/onScale here is not proven. Without a surface the Touch screen
 * switches to the tap latency probe.
 */
class TouchTesterScreen(carContext: CarContext, deps: SessionDeps) :
    MapSurfaceScreen(carContext, deps, "Touch tester", FRAME_MS) {

    private val recorder = deps.sources.touch
    private val renderer = TouchRenderer(recorder, { carContext.isDarkMode }, { surfaceState.visibleArea })

    /** Every TOUCH value, live counters included: following the touch events is what the tester is for. */
    override fun visible(snapshot: Snapshot): Any = snapshot.fields(Group.TOUCH)

    override fun build(snapshot: Snapshot): Template {
        val rows = ViewRows.rows(CarView.TOUCH, snapshot, rowContext()).take(deps.listLimit())
        val content = ListTemplate.Builder()
            .setSingleList(Templates.itemList(rows, ::onAction))
            .setHeader(Templates.header("Touch tester"))
            .build()
        return mapTemplate(content)
    }

    override fun paint(canvas: Canvas, dpi: Int) = renderer.render(canvas, dpi)

    override fun onGesture(record: (TouchRecorder) -> Unit) {
        record(recorder)
        requestDraw()
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
