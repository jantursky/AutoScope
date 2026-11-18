package com.jantursky.autoscope.car

import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import java.util.concurrent.ConcurrentHashMap

/**
 * Latest result per hardware item. Listener callbacks write here (any thread), the source reads here;
 * nothing else happens in a callback.
 */
class HardwareState {
    private val readings = ConcurrentHashMap<HardwareFeature, List<Field>>()
    private val failures = ConcurrentHashMap<HardwareFeature, String>()

    fun store(feature: HardwareFeature, fields: List<Field>) {
        failures.remove(feature)
        readings[feature] = fields
    }

    /** Registration or fetch failed with [reason]. */
    fun fail(feature: HardwareFeature, reason: String) {
        failures[feature] = reason
    }

    fun reading(feature: HardwareFeature): List<Field>? = readings[feature]

    fun failure(feature: HardwareFeature): String? = failures[feature]

    fun clear() {
        readings.clear()
        failures.clear()
    }
}

/** HARDWARE group. Decides from the API level and permissions why an item has no value. */
class HardwareSource(
    private val ports: () -> CarPorts?,
    private val state: HardwareState,
) : FieldSource {
    override val group = Group.HARDWARE

    override fun read(): List<Field> {
        val p = ports()
        return HardwareFeature.entries.flatMap { feature -> fields(feature, p) }
    }

    private fun fields(feature: HardwareFeature, p: CarPorts?): List<Field> {
        val labels = feature.labels
        val permission = feature.permission
        return when {
            p == null -> labels.allUnavailable(feature.source, NO_SESSION)
            p.apiLevel < CarAppApiLevels.LEVEL_3 ->
                labels.allNotSupported(feature.source, needsLevel("CarHardwareManager", 3, p.apiLevel))
            permission != null && !granted(p, permission) ->
                labels.map { Field.permissionMissing(it, feature.source, permission) }
            else -> state.failure(feature)?.let { labels.allUnavailable(feature.source, it) }
                ?: state.reading(feature)
                ?: labels.allUnavailable(feature.source, "No value from the car yet")
        }
    }

    private fun granted(p: CarPorts, permission: String) =
        try {
            p.permissions.isGranted(permission)
        } catch (e: Exception) {
            false
        }

    companion object {
        /** Whether [feature] may be registered now; the same rules [read] reports. */
        fun mayRegister(feature: HardwareFeature, apiLevel: Int, permissions: PermissionPort): Boolean =
            apiLevel >= CarAppApiLevels.LEVEL_3 &&
                (feature.permission == null || permissions.isGranted(feature.permission))
    }
}
