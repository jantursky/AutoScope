package com.jantursky.autoscope.car

import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group

/** One content limit type, with the minimum the library's integers.xml (1.7.0) guarantees. */
enum class ContentLimit(val type: Int, val label: String, val libraryMinimum: Int) {
    LIST(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, "List limit", 6),
    GRID(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, "Grid limit", 6),
    PLACE_LIST(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST, "Place list limit", 6),
    ROUTE_LIST(ConstraintManager.CONTENT_LIMIT_TYPE_ROUTE_LIST, "Route list limit", 3),
    PANE(ConstraintManager.CONTENT_LIMIT_TYPE_PANE, "Pane limit", 4),
}

/**
 * The host constraints, read once per bind and per configuration change. getContentLimit is a
 * synchronous call into the host, too costly for every timer tick.
 */
data class ConstraintReadings(val fields: List<Field>, val limits: Map<ContentLimit, Int>) {

    /** The host limit, or the library minimum when the host gave none. */
    fun limit(type: ContentLimit): Int = limits[type] ?: type.libraryMinimum

    companion object {
        val NONE = ConstraintReadings(emptyList(), emptyMap())

        private const val SOURCE_LIMIT = "ConstraintManager.getContentLimit"
        private const val SOURCE_REFRESH = "ConstraintManager.isAppDrivenRefreshEnabled"
        private const val APP_DRIVEN_REFRESH = "App driven refresh"

        fun read(port: ConstraintPort, apiLevel: Int): ConstraintReadings {
            val limits = mutableMapOf<ContentLimit, Int>()
            val limitFields = ContentLimit.entries.map { type ->
                limitField(apiLevel, type) { port.contentLimit(type.type).also { limits[type] = it } }
            }
            val refresh = when {
                apiLevel < CarAppApiLevels.LEVEL_6 ->
                    Field.notSupported(APP_DRIVEN_REFRESH, SOURCE_REFRESH, needsLevel("isAppDrivenRefreshEnabled", 6, apiLevel))
                else -> guarded(APP_DRIVEN_REFRESH, SOURCE_REFRESH) {
                    Field.ok(APP_DRIVEN_REFRESH, port.isAppDrivenRefreshEnabled(), SOURCE_REFRESH)
                }
            }
            return ConstraintReadings(limitFields + refresh, limits)
        }

        private fun limitField(apiLevel: Int, type: ContentLimit, read: () -> Int): Field = when {
            apiLevel < CarAppApiLevels.LEVEL_2 ->
                Field.notSupported(type.label, SOURCE_LIMIT, needsLevel("ConstraintManager", 2, apiLevel))
            else -> guarded(type.label, SOURCE_LIMIT) {
                // The library silently returns its own default when the host call fails, so a value equal
                // to the minimum may be either.
                Field.ok(type.label, "${read()} (library minimum ${type.libraryMinimum})", SOURCE_LIMIT)
            }
        }

        fun labels(): List<String> = ContentLimit.entries.map { it.label } + APP_DRIVEN_REFRESH
    }
}

/** CONSTRAINTS group: host limits read at bind time plus documented limits that are never measured. */
class ConstraintsSource(
    private val ports: () -> CarPorts?,
    private val readings: () -> ConstraintReadings,
) : FieldSource {
    override val group = Group.CONSTRAINTS

    override fun read(): List<Field> {
        val hostFields = if (ports() == null) {
            ConstraintReadings.labels().allUnavailable("ConstraintManager", NO_SESSION)
        } else {
            readings().fields
        }
        return hostFields + DOCUMENTED
    }

    private companion object {
        const val TEMPLATE_RESTRICTIONS =
            "documentation: developer.android.com/training/cars/apps/library/template-restrictions"
        const val TASK_FLOWS = "documentation: developer.android.com/design/ui/cars/guides/ux-requirements/plan-task-flows"

        val DOCUMENTED = listOf(
            Field.ok("Max templates per task", "5 (more only with adaptive task limits)", TEMPLATE_RESTRICTIONS),
            Field.ok("Last of 5 templates", "Navigation, Pane, Message (not List or Grid)", TEMPLATE_RESTRICTIONS),
            Field.ok(
                "Refresh rate limit",
                "No number documented: the host throttles refreshes and shows only the latest",
                TASK_FLOWS,
            ),
        )
    }
}
