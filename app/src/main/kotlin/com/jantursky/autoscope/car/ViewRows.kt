package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/** Session state the rows depend on besides the snapshot. */
data class RowContext(
    val apiLevel: Int,
    val missingPermissions: List<String>,
    /** Why the map surface screens cannot open, null when they can (see [MapSurface.blockedReason]). */
    val mapBlockedReason: String?,
)

/** Builds the rows of each list view. Pure, so the content of every tab is testable. */
object ViewRows {

    fun rows(view: CarView, snapshot: Snapshot, context: RowContext): List<UiRow> {
        fun fields() = view.fields(snapshot).map(CarRows::fieldRow)
        return when (view) {
            CarView.OVERVIEW -> overviewHeader(context.apiLevel) + fields()
            CarView.HARDWARE, CarView.PERMISSIONS -> listOf(grantRow(context.missingPermissions)) + fields()
            CarView.MORE -> moreRows(context.mapBlockedReason)
            CarView.LOG -> CarRows.logRows(snapshot.fields(Group.LIFECYCLE))
            CarView.SCREEN, CarView.TOUCH, CarView.VISUAL, CarView.SENSORS, CarView.NETWORK -> fields()
        }
    }

    /**
     * One row per entry. An entry that cannot open keeps its row and says why, so the row count and the
     * titles stay the same on every host.
     */
    fun moreRows(mapBlockedReason: String?): List<UiRow> = CarView.MORE_ENTRIES.map { view ->
        if (view.kind == ViewKind.MAP && mapBlockedReason != null) {
            UiRow(view.title, CarRows.splitText("Not available: $mapBlockedReason"))
        } else {
            UiRow(view.title, listOf(view.summary), RowAction.Open(view))
        }
    }

    private fun overviewHeader(apiLevel: Int) = listOf(
        UiRow("AutoScope diagnostic tool", listOf("Car App API level $apiLevel (host)")),
        UiRow("Mark this moment", listOf("Save a report of all readings now"), RowAction.Mark),
    )

    /** Always present, "All granted" included, so granting does not change the row count. */
    private fun grantRow(missing: List<String>) = UiRow(
        "Grant permissions",
        CarRows.splitText(
            if (missing.isEmpty()) "All granted" else "Missing: ${missing.joinToString { CarPermissions.shortName(it) }}",
        ),
        RowAction.GrantPermissions,
    )
}
