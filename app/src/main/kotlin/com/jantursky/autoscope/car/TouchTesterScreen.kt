package com.jantursky.autoscope.car

import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/**
 * The touch tester: a MapWithContentTemplate (Car App API 7, manifest permission
 * androidx.car.app.MAP_TEMPLATES) whose map surface comes through AppManager.setSurfaceCallback
 * (manifest permission androidx.car.app.ACCESS_SURFACE). Only pushed when the host level is 7 or more.
 *
 * Unverified on a head unit: the library documents the surface for MapWithContentTemplate, but whether a
 * POI app gets onClick/onScroll/onFling/onScale here is not proven. The map action strip carries
 * Action.PAN, without which the host sends no gesture callbacks (MapController javadoc). If no surface
 * arrives within [SurfaceState.SURFACE_TIMEOUT_MS], the TOUCH group says so and the Touch tab switches
 * to the tap latency probe.
 */
class TouchTesterScreen(carContext: CarContext, deps: SessionDeps) : BaseScreen(carContext, deps, "Touch tester") {

    private val surfaceState = deps.sources.surface
    private val recorder = deps.sources.touch
    private val renderer = TouchRenderer(recorder, { carContext.isDarkMode }, { surfaceState.visibleArea })

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            log("surface available ${surfaceContainer.width}x${surfaceContainer.height} @${surfaceContainer.dpi}")
            surfaceState.surfaceAvailable(SurfaceSize(surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi))
            surfaceContainer.surface?.let { renderer.attach(it, surfaceContainer.dpi) }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            log("surface destroyed")
            surfaceState.surfaceDestroyed()
            renderer.detach()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            surfaceState.visibleArea = visibleArea.toArea()
            renderer.requestDraw()
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            surfaceState.stableArea = stableArea.toArea()
        }

        override fun onClick(x: Float, y: Float) = record { recorder.click(x, y) }

        override fun onScroll(distanceX: Float, distanceY: Float) = record { recorder.scroll(distanceX, distanceY) }

        override fun onFling(velocityX: Float, velocityY: Float) = record { recorder.fling(velocityX, velocityY) }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) =
            record { recorder.scale(focusX, focusY, scaleFactor) }
    }

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    surfaceState.testerOpened()
                    setSurfaceCallback(callback)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    setSurfaceCallback(null)
                    renderer.detach()
                }
                else -> Unit
            }
        })
    }

    /** Every TOUCH value, live counters included: following the touch events is what the tester is for. */
    override fun visible(snapshot: Snapshot): Any = snapshot.fields(Group.TOUCH)

    override fun build(snapshot: Snapshot): Template {
        val rows = ViewRows.rows(CarView.TOUCH, snapshot, deps.ports.apiLevel, emptyList()).take(deps.listLimit())
        val content = ListTemplate.Builder()
            .setSingleList(Templates.itemList(rows, ::onAction))
            .setHeader(Templates.header("Touch tester"))
            .build()
        val mapActions = ActionStrip.Builder().addAction(Action.PAN).build()
        return MapWithContentTemplate.Builder()
            .setContentTemplate(content)
            .setMapController(MapController.Builder().setMapActionStrip(mapActions).build())
            .build()
    }

    private fun setSurfaceCallback(callback: SurfaceCallback?) {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
        } catch (e: Exception) {
            // SecurityException without ACCESS_SURFACE, HostException if the host call fails.
            log("setSurfaceCallback failed: ${e::class.simpleName}: ${e.message}")
        }
    }

    private inline fun record(event: () -> Unit) {
        event()
        renderer.requestDraw()
    }

    private fun log(message: String) = deps.container.eventLog.add("Touch tester $message")

    private fun Rect.toArea() = Area(left, top, right, bottom)
}
