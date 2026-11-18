package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status

data class AppVersion(val name: String?, val code: Long)

/** Static facts about the phone, copied from android.os.Build. */
data class DeviceInfo(val release: String, val sdkInt: Int, val manufacturer: String, val model: String)

/** Platform access for [HostSource]. */
interface HostEnvironment {
    /** Version of the androidx.car.app library this app was built with, null when unreadable. */
    fun carAppLibraryVersion(): String?

    /** Installed version of [packageName], or null when it is not installed or not visible. */
    fun packageVersion(packageName: String): AppVersion?

    val device: DeviceInfo
}

/**
 * Host group, phone part: library version, Android Auto app version and the phone itself.
 * Reading the Android Auto package needs a `<queries>` entry in the manifest on API 30+.
 */
class HostSource(private val environment: HostEnvironment) : FieldSource {
    override val group = Group.HOST

    override fun read(): List<Field> = buildList {
        add(guarded("Car App Library version", LIBRARY_SOURCE) {
            environment.carAppLibraryVersion()
                ?.let { Field.ok("Car App Library version", it, LIBRARY_SOURCE) }
                ?: Field.unavailable("Car App Library version", LIBRARY_SOURCE, "Version resource not found")
        })
        addAll(androidAutoFields())
        val device = environment.device
        add(Field.ok("Phone Android", "${device.release} (API ${device.sdkInt})", "Build.VERSION.RELEASE, SDK_INT"))
        add(Field.ok("Phone device", "${device.manufacturer} ${device.model}", "Build.MANUFACTURER, Build.MODEL"))
    }

    private fun androidAutoFields(): List<Field> {
        val labels = listOf("Android Auto version name", "Android Auto version code")
        val version = try {
            environment.packageVersion(ANDROID_AUTO_PACKAGE)
        } catch (e: SecurityException) {
            return labels.map { Field(it, null, PACKAGE_SOURCE, Status.PERMISSION_MISSING, "SecurityException: ${e.message}") }
        } catch (e: RuntimeException) {
            return labels.map { Field.unavailable(it, PACKAGE_SOURCE, "${e::class.simpleName}: ${e.message}") }
        } ?: return labels.map { Field.unavailable(it, PACKAGE_SOURCE, "Android Auto not installed") }
        return listOf(
            Field.ok(labels[0], version.name ?: "-", PACKAGE_SOURCE),
            Field.ok(labels[1], version.code, PACKAGE_SOURCE),
        )
    }

    companion object {
        const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val LIBRARY_SOURCE = "R.string.car_app_library_version (androidx.car.app)"
        private const val PACKAGE_SOURCE = "PackageManager.getPackageInfo($ANDROID_AUTO_PACKAGE)"
    }
}
