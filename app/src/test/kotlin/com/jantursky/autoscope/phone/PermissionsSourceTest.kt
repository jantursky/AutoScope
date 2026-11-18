package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsSourceTest {
    @Test fun grantedPermissionsAreOk() {
        val fields = PermissionsSource { true }.read()
        assertEquals(8, fields.size)
        assertTrue(fields.all { it.status == Status.OK && it.value == "Granted" })
    }

    @Test fun missingRuntimePermissionTellsWhatToDo() {
        val speed = PermissionsSource { false }.read().first { it.label == "Car speed" }
        assertEquals(Status.PERMISSION_MISSING, speed.status)
        assertTrue(speed.reason!!.contains("Grant permissions"))
        assertTrue(speed.source.contains("CAR_SPEED"))
    }

    @Test fun missingInstallPermissionIsExplained() {
        val surface = PermissionsSource { false }.read().first { it.label == "Map surface" }
        assertTrue(surface.reason!!.contains("manifest"))
    }

    @Test fun neverThrows() {
        val fields = PermissionsSource { throw IllegalStateException("boom") }.read()
        assertTrue(fields.all { it.status == Status.UNAVAILABLE })
    }

    @Test fun belongsToPermissionsGroup() {
        assertEquals(Group.PERMISSIONS, PermissionsSource { true }.group)
    }
}
