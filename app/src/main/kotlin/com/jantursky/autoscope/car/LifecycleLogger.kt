package com.jantursky.autoscope.car

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.jantursky.autoscope.core.EventLog

/** Writes every lifecycle event of [name] into the shared log, which adds the timestamp. */
class LifecycleLogger(private val eventLog: EventLog, private val name: String) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        eventLog.add("$name $event")
    }
}
