package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group

/** Connection group: type, time connected and reconnects, from [CarConnection][androidx.car.app.connection.CarConnection]. */
class CarConnectionSource(
    private val tracker: ConnectionTracker,
    private val clock: () -> Long,
) : FieldSource {
    override val group = Group.CONNECTION

    override fun read(): List<Field> {
        val state = tracker.current()
            ?: return LABELS.map { Field.unavailable(it, SOURCE, "No connection value received yet") }
        val connectedFor = state.connectedSince?.let { formatDuration(clock() - it) } ?: "-"
        return listOf(
            Field.ok(LABELS[0], connectionTypeName(state.type), SOURCE),
            Field.ok(LABELS[1], connectedFor, "$SOURCE (since last transition to connected)"),
            Field.ok(LABELS[2], state.reconnects, "$SOURCE (this process session)"),
        )
    }

    private companion object {
        const val SOURCE = "CarConnection.getType"
        val LABELS = listOf("Connection type", "Time connected", "Reconnects")
    }
}
