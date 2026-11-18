package com.jantursky.autoscope.car

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.jantursky.autoscope.core.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Calls [onChange] while the screen is started and the part of the snapshot it shows ([visible])
 * differs from what its last template showed. The repository publishes at most once per timer tick, so
 * this never asks for more than one refresh per tick.
 */
class SnapshotWatcher(
    private val snapshot: StateFlow<Snapshot>,
    private val visible: (Snapshot) -> Any,
    private val onChange: () -> Unit,
) : LifecycleEventObserver {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var job: Job? = null
    private var shown: Any? = null

    /** Remembers what the template being built shows. Call from onGetTemplate. */
    fun shown(current: Snapshot) {
        shown = visible(current)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> job = scope.launch {
                snapshot.collect { if (visible(it) != shown) onChange() }
            }
            Lifecycle.Event.ON_STOP -> {
                job?.cancel()
                job = null
            }
            Lifecycle.Event.ON_DESTROY -> scope.cancel()
            else -> Unit
        }
    }
}
