package com.jantursky.autoscope.phone

import android.content.Context
import com.jantursky.autoscope.core.DiagnosticsRepository
import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.ReportStore

/**
 * Saves a report on every change of connection type, so the state around a disconnect is kept
 * without the user having to press anything. The first value only sets the baseline.
 * Reports are written to the app's files directory and never leave the device.
 */
class ConnectionSnapshotter(
    monitor: CarConnectionMonitor,
    private val repository: DiagnosticsRepository,
    private val reports: ReportStore,
    private val eventLog: EventLog,
) {
    private var lastType: Int? = monitor.tracker.current()?.type

    constructor(
        context: Context,
        repository: DiagnosticsRepository,
        reports: ReportStore,
        eventLog: EventLog,
    ) : this(CarConnectionMonitor.shared(context), repository, reports, eventLog)

    init {
        monitor.addListener(::onType)
    }

    private fun onType(type: Int) {
        val previous = lastType
        lastType = type
        if (previous == null || previous == type) return
        val reason = "Connection: ${connectionTypeName(type)}"
        eventLog.add("${connectionTypeName(previous)} -> ${connectionTypeName(type)}")
        reports.save(repository.refresh(), reason)
    }
}
