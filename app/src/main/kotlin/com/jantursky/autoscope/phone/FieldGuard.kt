package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status

/**
 * Runs one platform read and turns any failure into a status, so a field source never throws.
 * A SecurityException means a permission is missing; the platform does not say which one.
 */
internal inline fun guarded(label: String, source: String, read: () -> Field): Field =
    try {
        read()
    } catch (e: SecurityException) {
        Field(label, null, source, Status.PERMISSION_MISSING, "SecurityException: ${e.message}")
    } catch (e: RuntimeException) {
        Field.unavailable(label, source, "${e::class.simpleName}: ${e.message}")
    }
