package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status

enum class PillKind { CONNECTED, DISCONNECTED }

data class ConnectionPill(val text: String, val kind: PillKind)

internal const val CONNECTION_TYPE_LABEL = "Connection type"

/** Header pill derived from the "Connection type" field, for example "Projection" or "Not connected". */
fun connectionPill(snapshot: Snapshot): ConnectionPill {
    val field = snapshot.fields(Group.CONNECTION).firstOrNull { it.label == CONNECTION_TYPE_LABEL }
    val value = field?.value.takeIf { field?.status == Status.OK }
        ?: return ConnectionPill("Not connected", PillKind.DISCONNECTED)
    return when {
        value.startsWith("Not connected") -> ConnectionPill("Not connected", PillKind.DISCONNECTED)
        value.startsWith("Projection") -> ConnectionPill("Projection", PillKind.CONNECTED)
        value.startsWith("Native") -> ConnectionPill("Native", PillKind.CONNECTED)
        else -> ConnectionPill(value, PillKind.DISCONNECTED)
    }
}
