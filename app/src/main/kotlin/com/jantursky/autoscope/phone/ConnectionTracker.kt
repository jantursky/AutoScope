package com.jantursky.autoscope.phone

/** What the car connection has been doing during this process session. */
data class ConnectionState(
    val type: Int,
    /** Clock time of the last transition to a connected type, null while disconnected. */
    val connectedSince: Long?,
    /** Connections after the first one in this session. */
    val reconnects: Int,
)

/**
 * Reduces connection type events into [ConnectionState]. Thread safe: written on the main thread,
 * read from the ticker.
 */
class ConnectionTracker {
    private var state: ConnectionState? = null
    private var connections = 0
    private val lock = Any()

    fun current(): ConnectionState? = synchronized(lock) { state }

    /** [now] is a monotonic clock reading in milliseconds. */
    fun onType(type: Int, now: Long) {
        synchronized(lock) {
            val previous = state
            if (previous != null && previous.type == type) return
            val becameConnected = isConnected(type) && (previous == null || !isConnected(previous.type))
            if (becameConnected) connections++
            // A switch between connected types also restarts the timer.
            val since = if (isConnected(type)) now else null
            state = ConnectionState(type, since, (connections - 1).coerceAtLeast(0))
        }
    }
}
