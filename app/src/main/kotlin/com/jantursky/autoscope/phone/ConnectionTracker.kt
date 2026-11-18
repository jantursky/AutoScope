package com.jantursky.autoscope.phone

/** What the car connection has been doing during this process session. */
data class ConnectionState(
    val type: Int,
    /** Clock time of the last transition to a connected type, null while disconnected. */
    val connectedSince: Long?,
    /** Connections after the first one in this session. */
    val reconnects: Int,
)

/** A change of connection type; [at] is the monotonic clock reading of the change. */
data class ConnectionEvent(val type: Int, val at: Long)

/** Most recent changes kept for the Connection tab timeline. */
private const val HISTORY_LIMIT = 50

/**
 * Reduces connection type events into [ConnectionState]. Thread safe: written on the main thread,
 * read from the ticker.
 */
class ConnectionTracker {
    private var state: ConnectionState? = null
    private var connections = 0
    private val events = ArrayDeque<ConnectionEvent>()
    private val lock = Any()

    fun current(): ConnectionState? = synchronized(lock) { state }

    /** Changes in this process session, oldest first, the first one being the baseline. */
    fun history(): List<ConnectionEvent> = synchronized(lock) { events.toList() }

    /** [now] is a monotonic clock reading in milliseconds. */
    fun onType(type: Int, now: Long) {
        synchronized(lock) {
            val previous = state
            if (previous != null && previous.type == type) return
            events.addLast(ConnectionEvent(type, now))
            if (events.size > HISTORY_LIMIT) events.removeFirst()
            val becameConnected = isConnected(type) && (previous == null || !isConnected(previous.type))
            if (becameConnected) connections++
            // A switch between connected types also restarts the timer.
            val since = if (isConnected(type)) now else null
            state = ConnectionState(type, since, (connections - 1).coerceAtLeast(0))
        }
    }
}
