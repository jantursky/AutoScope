package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group

/** Car side of the HOST group: negotiated API level, library version and the host identity. */
class CarHostSource(private val ports: () -> CarPorts?) : FieldSource {
    override val group = Group.HOST

    override fun read(): List<Field> {
        val p = ports() ?: return LABELS.allUnavailable(SOURCE_CONTEXT, NO_SESSION)
        val host = guarded(HOST_PACKAGE, SOURCE_HOST_INFO) {
            p.host.hostInfo()?.let { Field.ok(HOST_PACKAGE, it.packageName, SOURCE_HOST_INFO) }
                ?: Field.unavailable(HOST_PACKAGE, SOURCE_HOST_INFO, HOST_INFO_NULL)
        }
        val uid = guarded(HOST_UID, SOURCE_HOST_INFO) {
            p.host.hostInfo()?.let { Field.ok(HOST_UID, it.uid, SOURCE_HOST_INFO) }
                ?: Field.unavailable(HOST_UID, SOURCE_HOST_INFO, HOST_INFO_NULL)
        }
        return listOf(
            Field.ok(API_LEVEL, p.apiLevel, SOURCE_CONTEXT),
            guarded(LIBRARY_LEVEL, SOURCE_LEVELS) { Field.ok(LIBRARY_LEVEL, p.host.libraryLatestApiLevel(), SOURCE_LEVELS) },
            guarded(LIBRARY_VERSION, SOURCE_VERSION) { Field.ok(LIBRARY_VERSION, p.host.libraryVersion(), SOURCE_VERSION) },
            host,
            uid,
        )
    }

    private companion object {
        const val API_LEVEL = "Car App API level (host)"
        const val LIBRARY_LEVEL = "Car App API level (library latest)"
        const val LIBRARY_VERSION = "Car App Library version"
        const val HOST_PACKAGE = "Host package"
        const val HOST_UID = "Host uid"
        val LABELS = listOf(API_LEVEL, LIBRARY_LEVEL, LIBRARY_VERSION, HOST_PACKAGE, HOST_UID)

        const val SOURCE_CONTEXT = "CarContext.getCarAppApiLevel"
        const val SOURCE_LEVELS = "CarAppApiLevels.getLatest"
        const val SOURCE_VERSION = "androidx.car.app R.string.car_app_library_version"
        const val SOURCE_HOST_INFO = "CarContext.getHostInfo"
        const val HOST_INFO_NULL = "getHostInfo() returned null"
    }
}
