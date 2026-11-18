package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status

internal const val NO_SESSION = "No car session"

/** Runs [read] and turns any exception into a field status, so one failing call never hides the others. */
internal inline fun guarded(label: String, source: String, read: () -> Field): Field =
    try {
        read()
    } catch (e: SecurityException) {
        Field(label, null, source, Status.PERMISSION_MISSING, "SecurityException: ${e.message}")
    } catch (e: Exception) {
        Field.unavailable(label, source, "${e::class.simpleName}: ${e.message}")
    }

/** Same labels in every state keeps the car rows stable, so a refresh is not counted as a new step. */
internal fun List<String>.allUnavailable(source: String, reason: String): List<Field> =
    map { Field.unavailable(it, source, reason) }

internal fun List<String>.allNotSupported(source: String, reason: String): List<Field> =
    map { Field.notSupported(it, source, reason) }

internal fun needsLevel(what: String, required: Int, actual: Int) =
    "$what needs Car App API $required, host has $actual"
