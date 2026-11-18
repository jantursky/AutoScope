package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status

/** Answers whether this app currently holds a permission. */
fun interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

private data class Tracked(val label: String, val permission: String, val runtime: Boolean)

private const val RUNTIME_HINT = "Tap Grant permissions on the Hardware tab or More > Permissions in the car, or grant it in phone Settings"
private const val INSTALL_HINT = "Install time permission, only declared in the manifest. Reinstall the app to get it"

private val TRACKED = listOf(
    Tracked("Car speed", "com.google.android.gms.permission.CAR_SPEED", runtime = true),
    Tracked("Car fuel", "com.google.android.gms.permission.CAR_FUEL", runtime = true),
    Tracked("Car mileage", "com.google.android.gms.permission.CAR_MILEAGE", runtime = true),
    Tracked("Location", "android.permission.ACCESS_FINE_LOCATION", runtime = true),
    Tracked("Map surface", "androidx.car.app.ACCESS_SURFACE", runtime = false),
    Tracked("Map templates", "androidx.car.app.MAP_TEMPLATES", runtime = false),
    Tracked("Wi-Fi state", "android.permission.ACCESS_WIFI_STATE", runtime = false),
    Tracked("Network state", "android.permission.ACCESS_NETWORK_STATE", runtime = false),
)

/** One field per permission the app declares or uses. */
class PermissionsSource(private val checker: PermissionChecker) : FieldSource {
    override val group = Group.PERMISSIONS

    override fun read(): List<Field> = TRACKED.map { tracked ->
        val source = "PackageManager.checkPermission(${tracked.permission})"
        guarded(tracked.label, source) {
            if (checker.isGranted(tracked.permission)) {
                Field.ok(tracked.label, "Granted", source)
            } else {
                Field(
                    tracked.label, null, source, Status.PERMISSION_MISSING,
                    if (tracked.runtime) RUNTIME_HINT else INSTALL_HINT,
                )
            }
        }
    }
}
