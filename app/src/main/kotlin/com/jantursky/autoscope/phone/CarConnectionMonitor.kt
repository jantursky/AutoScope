package com.jantursky.autoscope.phone

import android.content.Context
import android.os.SystemClock
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/** Source of car connection type values. Exists so the monitor can be tested without LiveData. */
fun interface ConnectionTypeFeed {
    fun observe(listener: (Int) -> Unit)
}

/** Observes [CarConnection.getType] forever. Must be created on the main thread (LiveData rule). */
private class CarConnectionFeed(context: Context) : ConnectionTypeFeed {
    private val connection = CarConnection(context)

    override fun observe(listener: (Int) -> Unit) {
        connection.type.observeForever(Observer { listener(it) })
    }
}

/**
 * One observer of the car connection for the whole process, shared by the field source and the
 * snapshotter. The tracker is updated before listeners run, so a listener always sees the new state.
 */
class CarConnectionMonitor(feed: ConnectionTypeFeed, private val clock: () -> Long) {
    val tracker = ConnectionTracker()
    private val listeners = mutableListOf<(Int) -> Unit>()

    init {
        feed.observe { type ->
            tracker.onType(type, clock())
            listeners.toList().forEach { it(type) }
        }
    }

    /** Main thread only, like the feed itself. */
    fun addListener(listener: (Int) -> Unit) {
        listeners += listener
    }

    companion object {
        private var instance: CarConnectionMonitor? = null

        /** Main thread only. Application.onCreate is the first caller. */
        fun shared(context: Context): CarConnectionMonitor =
            instance ?: CarConnectionMonitor(CarConnectionFeed(context.applicationContext), SystemClock::elapsedRealtime)
                .also { instance = it }
    }
}
