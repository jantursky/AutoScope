package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot

/** Builds the rows of each list view. Pure, so the content of every tab is testable. */
object ViewRows {

    fun rows(view: CarView, snapshot: Snapshot, apiLevel: Int, missingPermissions: List<String>): List<UiRow> {
        val fields = view.groups.flatMap(snapshot::fields)
        return when (view) {
            CarView.OVERVIEW -> overviewHeader(apiLevel) + fields.map(CarRows::fieldRow)
            CarView.HARDWARE -> listOf(grantRow(missingPermissions)) + fields.map(CarRows::fieldRow)
            CarView.LOG -> CarRows.logRows(snapshot.fields(Group.LIFECYCLE))
            CarView.SCREEN, CarView.TOUCH -> fields.map(CarRows::fieldRow)
        }
    }

    private fun overviewHeader(apiLevel: Int) = listOf(
        UiRow("AutoScope diagnostic tool", listOf("Car App API level $apiLevel (host)")),
        UiRow("Mark this moment", listOf("Save a report of all readings now"), RowAction.MARK),
        UiRow("Lifecycle log", listOf("Session, screen and configuration events"), RowAction.OPEN_LOG),
    )

    private fun grantRow(missing: List<String>) = UiRow(
        "Grant permissions",
        CarRows.splitText(
            if (missing.isEmpty()) "All granted" else "Missing: ${missing.joinToString { CarPermissions.shortName(it) }}",
        ),
        RowAction.GRANT_PERMISSIONS,
    )
}
