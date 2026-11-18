package com.jantursky.autoscope

import android.app.Application
import com.jantursky.autoscope.car.CarSources
import com.jantursky.autoscope.core.DiagnosticsRepository
import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.ReportStore
import com.jantursky.autoscope.phone.ConnectionSnapshotter
import com.jantursky.autoscope.phone.PhoneSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hand-wired dependencies, shared by the phone Activity and the car Session (one process).
 *
 * The ticker runs only while somebody holds it ([acquireTicker]/[releaseTicker]), so the app is idle
 * when neither the phone screen nor the car is showing.
 */
class AppContainer(app: Application) {
    val settings = Settings(app)
    val eventLog = EventLog()
    val reports = ReportStore(app.filesDir)

    /** Car side sources. They report UNAVAILABLE until a car Session binds them. */
    val carSources = CarSources(eventLog)
    private val phoneSources = PhoneSources.create(app)

    val repository = DiagnosticsRepository(phoneSources + carSources.sources + eventLog)

    /** Saves a report whenever the connection state changes. Lives as long as the process. */
    val connectionSnapshotter = ConnectionSnapshotter(app, repository, reports, eventLog)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var holders = 0

    /** Takes a snapshot now and stores it as JSON and text. Used by "Mark this moment" and the phone button. */
    fun mark(reason: String) = reports.save(repository.refresh(), reason)

    fun acquireTicker() {
        holders++
        if (ticker == null) {
            ticker = scope.launch {
                while (true) {
                    repository.refresh()
                    delay(settings.refreshMillis)
                }
            }
        }
    }

    fun releaseTicker() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            ticker?.cancel()
            ticker = null
        }
    }
}
