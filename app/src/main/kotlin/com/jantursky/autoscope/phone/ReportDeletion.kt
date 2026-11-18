package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.SavedReport

/**
 * Removes both files of [report]. A file that is already gone counts as removed. Files shared through the
 * FileProvider need no extra step: the grant only gives other apps read access to the path.
 */
internal fun deleteReport(report: SavedReport): Boolean =
    listOf(report.json, report.text).map { !it.exists() || it.delete() }.all { it }

/** Number of reports in [reports] that were removed completely. */
internal fun deleteReports(reports: List<SavedReport>): Int = reports.count(::deleteReport)
