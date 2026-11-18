package com.jantursky.autoscope.core

/** Why a field has, or has no, value. Shown next to every value in the UI and the report. */
enum class Status {
    OK,
    UNAVAILABLE,
    PERMISSION_MISSING,
    NOT_SUPPORTED_BY_HOST,
}

/** Diagnostic groups, in display order. */
enum class Group(val title: String) {
    CONNECTION("Connection"),
    HOST("Host and library"),
    SCREEN("Screen"),
    CONSTRAINTS("Template constraints"),
    HARDWARE("Car hardware"),
    TOUCH("Touch"),
    PERFORMANCE("Performance"),
    LIFECYCLE("Lifecycle log"),
    PHONE("Phone"),
}

/**
 * One reading. [source] names the API it came from. [value] is null unless [status] is [Status.OK]
 * (a few OK fields may also carry no value, for example an empty log). [reason] explains any other status.
 */
data class Field(
    val label: String,
    val value: String?,
    val source: String,
    val status: Status = Status.OK,
    val reason: String? = null,
) {
    companion object {
        fun ok(label: String, value: Any?, source: String) = Field(label, value.toString(), source)

        fun unavailable(label: String, source: String, reason: String) =
            Field(label, null, source, Status.UNAVAILABLE, reason)

        fun permissionMissing(label: String, source: String, permission: String) =
            Field(label, null, source, Status.PERMISSION_MISSING, "Missing permission: $permission")

        fun notSupported(label: String, source: String, reason: String) =
            Field(label, null, source, Status.NOT_SUPPORTED_BY_HOST, reason)
    }
}

/** Immutable state of every group at one moment. */
data class Snapshot(val groups: Map<Group, List<Field>>) {
    fun fields(group: Group): List<Field> = groups[group].orEmpty()

    companion object {
        val EMPTY = Snapshot(emptyMap())
    }
}

/**
 * A producer of fields for one group. [read] must be cheap and must not block; listener based sources
 * keep their latest value and return it here.
 */
interface FieldSource {
    val group: Group
    fun read(): List<Field>
}
