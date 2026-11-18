package com.jantursky.autoscope.car

import android.content.res.Configuration
import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import java.util.Locale

/** SCREEN group: the car display as seen through the CarContext resources, plus the surface areas. */
class ScreenSource(
    private val ports: () -> CarPorts?,
    private val surface: SurfaceState,
) : FieldSource {
    override val group = Group.SCREEN

    override fun read(): List<Field> {
        val p = ports() ?: return LABELS.allUnavailable(SOURCE_METRICS, NO_SESSION)
        val info = try {
            p.display.read()
        } catch (e: Exception) {
            return DISPLAY_LABELS.allUnavailable(SOURCE_METRICS, "${e::class.simpleName}: ${e.message}") +
                areaFields(p.apiLevel)
        }
        return displayFields(info) + areaFields(p.apiLevel)
    }

    private fun areaFields(apiLevel: Int): List<Field> = listOf(
        areaField(VISIBLE_AREA, "SurfaceCallback.onVisibleAreaChanged", apiLevel, surface.visibleArea),
        areaField(STABLE_AREA, "SurfaceCallback.onStableAreaChanged", apiLevel, surface.stableArea),
    )

    private fun areaField(label: String, source: String, apiLevel: Int, area: Area?): Field = when {
        apiLevel < CarAppApiLevels.LEVEL_7 ->
            Field.notSupported(label, source, needsLevel("The map surface (MapWithContentTemplate)", 7, apiLevel))
        area == null ->
            Field.unavailable(label, source, "Known only after the touch tester (map surface) has been opened")
        area.isEmpty -> Field.unavailable(label, source, "Host reported an empty area, which means unknown")
        else -> Field.ok(label, area, source)
    }

    companion object {
        private const val SOURCE_METRICS = "CarContext.getResources().getDisplayMetrics"
        private const val SOURCE_CONFIG = "CarContext.getResources().getConfiguration"
        private const val VISIBLE_AREA = "Visible area"
        private const val STABLE_AREA = "Stable area"

        private val DISPLAY_LABELS = listOf(
            "Width", "Height", "Density dpi", "Density", "Scaled density", "Width dp", "Height dp",
            "Smallest width dp", "Aspect ratio", "Orientation", "Refresh rate", "Dark mode (CarContext)",
            "UI mode night", "Font scale", "Locale",
        )
        private val LABELS = DISPLAY_LABELS + listOf(VISIBLE_AREA, STABLE_AREA)

        /** Pure conversion, in the order of [DISPLAY_LABELS]. */
        fun displayFields(info: DisplayInfo): List<Field> = listOf(
            Field.ok("Width", "${info.widthPx} px", SOURCE_METRICS),
            Field.ok("Height", "${info.heightPx} px", SOURCE_METRICS),
            Field.ok("Density dpi", info.densityDpi, SOURCE_METRICS),
            Field.ok("Density", format(info.density), SOURCE_METRICS),
            // DisplayMetrics.scaledDensity is deprecated since Android 14 but still reported; it is what text uses.
            Field.ok("Scaled density", format(info.scaledDensity), SOURCE_METRICS),
            Field.ok("Width dp", info.widthDp, SOURCE_CONFIG),
            Field.ok("Height dp", info.heightDp, SOURCE_CONFIG),
            Field.ok("Smallest width dp", info.smallestWidthDp, SOURCE_CONFIG),
            aspectRatio(info),
            Field.ok("Orientation", orientationName(info.orientation), SOURCE_CONFIG),
            refreshRate(info.refreshRateHz),
            Field.ok("Dark mode (CarContext)", info.isDarkMode, "CarContext.isDarkMode"),
            Field.ok("UI mode night", nightName(info.uiModeNight), SOURCE_CONFIG),
            Field.ok("Font scale", format(info.fontScale), SOURCE_CONFIG),
            Field.ok("Locale", info.locale, SOURCE_CONFIG),
        )

        private fun aspectRatio(info: DisplayInfo): Field {
            val short = minOf(info.widthPx, info.heightPx)
            return if (short <= 0) {
                Field.unavailable("Aspect ratio", SOURCE_METRICS, "Display size is zero")
            } else {
                Field.ok("Aspect ratio", "${format(maxOf(info.widthPx, info.heightPx).toFloat() / short)}:1", SOURCE_METRICS)
            }
        }

        private fun refreshRate(hz: Float?): Field {
            // The CarContext display is the virtual display the library creates for the car screen, so this
            // is the virtual display's rate, not a measured panel rate.
            val source = "CarContext.getDisplay().getRefreshRate (virtual display)"
            return if (hz == null || hz <= 0f) {
                Field.unavailable("Refresh rate", source, "Context.getDisplay needs Android 11 or the display is unknown")
            } else {
                Field.ok("Refresh rate", "${format(hz)} Hz", source)
            }
        }

        private fun orientationName(value: Int) = when (value) {
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            else -> "undefined ($value)"
        }

        private fun nightName(value: Int) = when (value) {
            Configuration.UI_MODE_NIGHT_NO -> "no"
            Configuration.UI_MODE_NIGHT_YES -> "yes"
            else -> "undefined ($value)"
        }

        private fun format(value: Float) = String.format(Locale.US, "%.2f", value)
    }
}
