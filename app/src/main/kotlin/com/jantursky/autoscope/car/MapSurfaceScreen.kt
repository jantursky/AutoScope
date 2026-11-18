package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A screen that draws on the map surface: a MapWithContentTemplate (Car App API 7, manifest permission
 * androidx.car.app.MAP_TEMPLATES) whose surface comes through AppManager.setSurfaceCallback (manifest
 * permission androidx.car.app.ACCESS_SURFACE). Only pushed when [MapSurface.blockedReason] is null.
 *
 * The callback is set while the screen is started and cleared when it stops, through [SurfaceOwnership]
 * so only one screen holds it. Unverified on a head unit: whether the host sends onSurfaceAvailable
 * again when the callback is set anew after the app was in the background. If no surface arrives within
 * [SurfaceState.SURFACE_TIMEOUT_MS], [MapSurface.blockedReason] says so and these screens stay closed.
 */
abstract class MapSurfaceScreen(
    carContext: CarContext,
    deps: SessionDeps,
    private val name: String,
    frameMillis: Long,
) : BaseScreen(carContext, deps, name) {

    protected val surfaceState = deps.sources.surface
    private val painter = SurfacePainter(frameMillis, ::paint)

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            log("surface available ${surfaceContainer.width}x${surfaceContainer.height} @${surfaceContainer.dpi}")
            surfaceState.surfaceAvailable(SurfaceSize(surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi))
            surfaceContainer.surface?.let { painter.attach(it, surfaceContainer.dpi) }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            log("surface destroyed")
            surfaceState.surfaceDestroyed()
            painter.detach()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            surfaceState.visibleArea = visibleArea.toArea()
            painter.requestDraw()
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            surfaceState.stableArea = stableArea.toArea()
        }

        override fun onClick(x: Float, y: Float) = onGesture { it.click(x, y) }

        override fun onScroll(distanceX: Float, distanceY: Float) = onGesture { it.scroll(distanceX, distanceY) }

        override fun onFling(velocityX: Float, velocityY: Float) = onGesture { it.fling(velocityX, velocityY) }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) =
            onGesture { it.scale(focusX, focusY, scaleFactor) }
    }

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> surfaceState.surfaceRequested()
                Lifecycle.Event.ON_START -> {
                    deps.surfaceOwnership.claim(this)
                    setSurfaceCallback(callback)
                }
                Lifecycle.Event.ON_STOP -> {
                    if (deps.surfaceOwnership.release(this)) setSurfaceCallback(null)
                    painter.detach()
                }
                else -> Unit
            }
        })
    }

    /** Paints one frame; called by the painter at most once per frame interval. */
    protected abstract fun paint(canvas: Canvas, dpi: Int)

    /** A gesture on the map; [record] applies it to a touch recorder. Ignored unless overridden. */
    protected open fun onGesture(record: (TouchRecorder) -> Unit) = Unit

    protected fun requestDraw() = painter.requestDraw()

    /**
     * The map template around [content]. The map action strip carries Action.PAN, without which the host
     * sends no gesture callbacks (MapController javadoc).
     */
    protected fun mapTemplate(content: Template): Template = MapWithContentTemplate.Builder()
        .setContentTemplate(content)
        .setMapController(
            MapController.Builder().setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build()).build(),
        )
        .build()

    private fun setSurfaceCallback(callback: SurfaceCallback?) {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
        } catch (e: Exception) {
            // SecurityException without ACCESS_SURFACE, HostException if the host call fails.
            log("setSurfaceCallback failed: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun log(message: String) = deps.container.eventLog.add("$name $message")

    private fun Rect.toArea() = Area(left, top, right, bottom)
}
