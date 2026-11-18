package com.jantursky.autoscope.car

import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import java.util.Locale

/** TOUCH group: state of the map surface touch tester, its counters and the row tap latency. */
class TouchSource(
    private val ports: () -> CarPorts?,
    private val surface: SurfaceState,
    private val recorder: TouchRecorder,
) : FieldSource {
    override val group = Group.TOUCH

    override fun read(): List<Field> {
        val p = ports() ?: return LABELS.allUnavailable(SOURCE_SURFACE, NO_SESSION)
        val stats = recorder.stats()
        if (p.apiLevel < CarAppApiLevels.LEVEL_7) {
            val reason = needsLevel("MapWithContentTemplate", 7, p.apiLevel)
            return listOf(latencyField(stats)) + TESTER_LABELS.allNotSupported(SOURCE_SURFACE, reason)
        }
        if (surface.surfaceMissing()) {
            val reason = "Map screen opened, but the host provided no surface within " +
                "${SurfaceState.SURFACE_TIMEOUT_MS / 1000} s"
            return listOf(latencyField(stats)) + TESTER_LABELS.allUnavailable(SOURCE_SURFACE, reason)
        }
        val latency = Field.unavailable(TAP_LATENCY, SOURCE_LATENCY, "Measured only when the touch tester cannot run")
        return listOf(latency, testerField()) + counterFields(stats, p.apiLevel)
    }

    private fun latencyField(stats: TouchStats): Field = stats.tapLatencyMillis
        ?.let { Field.ok(TAP_LATENCY, "$it ms", SOURCE_LATENCY) }
        ?: Field.unavailable(TAP_LATENCY, SOURCE_LATENCY, "Tap \"$MEASURE_ACTION\" on the Touch screen")

    private fun testerField(): Field {
        val size = surface.size
        return when {
            size != null -> Field.ok(TESTER, "surface ${size.width}x${size.height}, ${size.dpi} dpi", SOURCE_SURFACE)
            surface.requestedAtMillis == null -> Field.ok(TESTER, "ready, not opened yet", SOURCE_SURFACE)
            surface.everAvailable -> Field.ok(TESTER, "surface released", SOURCE_SURFACE)
            else -> Field.ok(TESTER, "waiting for the surface", SOURCE_SURFACE)
        }
    }

    private fun counterFields(stats: TouchStats, apiLevel: Int): List<Field> {
        val clicks = if (apiLevel < CarAppApiLevels.LEVEL_5) {
            Field.notSupported(CLICKS, "SurfaceCallback.onClick", needsLevel("onClick", 5, apiLevel))
        } else {
            Field.ok(CLICKS, stats.counts.getValue(TouchKind.CLICK), "SurfaceCallback.onClick")
        }
        return listOf(
            clicks,
            Field.ok(SCROLLS, stats.counts.getValue(TouchKind.SCROLL), "SurfaceCallback.onScroll"),
            Field.ok(FLINGS, stats.counts.getValue(TouchKind.FLING), "SurfaceCallback.onFling"),
            Field.ok(SCALES, stats.counts.getValue(TouchKind.SCALE), "SurfaceCallback.onScale"),
            Field.ok(RATE, stats.eventsPerSecond, SOURCE_SURFACE),
            stats.last?.let { Field.ok(LAST, describe(it), SOURCE_SURFACE) }
                ?: Field.unavailable(LAST, SOURCE_SURFACE, "No touch event yet"),
        )
    }

    companion object {
        const val MEASURE_ACTION = "Measure tap latency"

        private const val SOURCE_SURFACE = "SurfaceCallback (MapWithContentTemplate)"
        private const val SOURCE_LATENCY = "Row tap to next onGetTemplate"
        private const val TAP_LATENCY = "Tap latency"
        private const val TESTER = "Touch tester"
        private const val CLICKS = "Clicks"
        private const val SCROLLS = "Scrolls"
        private const val FLINGS = "Flings"
        private const val SCALES = "Scales"
        private const val RATE = "Events per second"
        private const val LAST = "Last event"
        private val TESTER_LABELS = listOf(TESTER, CLICKS, SCROLLS, FLINGS, SCALES, RATE, LAST)
        private val LABELS = listOf(TAP_LATENCY) + TESTER_LABELS

        fun describe(e: TouchEvent): String {
            val at = String.format(Locale.US, "(%.0f, %.0f)", e.x, e.y)
            return when (e.kind) {
                TouchKind.CLICK -> "click at $at"
                TouchKind.SCROLL -> String.format(Locale.US, "scroll by %.0f, %.0f to %s", -e.dx, -e.dy, at)
                TouchKind.FLING -> String.format(Locale.US, "fling %.0f, %.0f px/s", e.dx, e.dy)
                TouchKind.SCALE -> String.format(Locale.US, "scale x%.2f at %s", e.scale, at)
            }
        }
    }
}
