package com.jantursky.autoscope.car

import androidx.car.app.versioning.CarAppApiLevels
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status
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
        if (p == null) return labels.allUnavailable(feature.source, NO_SESSION)
        val missing = HardwareGate.block(feature, p.apiLevel, p.permissions)
            ?: state.failure(feature)?.let { NoValue(Status.UNAVAILABLE, it) }
        return missing?.let { m -> labels.map { m.field(it, feature.source) } }
            ?: state.reading(feature)
            ?: labels.allUnavailable(feature.source, NO_VALUE_YET)
    }
}

/** Why a value is missing, in the words a car row or a drawing shows. */
data class NoValue(val status: Status, val reason: String) {
    fun field(label: String, source: String) = Field(label, null, source, status, reason)

    fun <T> reading(): Reading<T> = Reading.missing(status, reason)
}

/** The API level and permission rules for registering a hardware item; fields and telemetry share them. */
object HardwareGate {

    /** Why [feature] cannot deliver values in this session, or null when it may be registered. */
    fun block(feature: HardwareFeature, apiLevel: Int, permissions: PermissionPort): NoValue? {
        val permission = feature.permission
        return when {
            apiLevel < CarAppApiLevels.LEVEL_3 ->
                NoValue(Status.NOT_SUPPORTED_BY_HOST, needsLevel("CarHardwareManager", 3, apiLevel))
            permission != null && !granted(permissions, permission) ->
                NoValue(Status.PERMISSION_MISSING, "Missing permission: ${CarPermissions.shortName(permission)}")
            else -> null
        }
    }

    private fun granted(permissions: PermissionPort, permission: String) =
        try {
            permissions.isGranted(permission)
        } catch (e: Exception) {
            false
        }
}
