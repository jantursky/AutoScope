package com.jantursky.autoscope.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRepositoryTest {

    private class FakeSource(override val group: Group, var fields: List<Field>) : FieldSource {
        override fun read() = fields
    }

    private class ThrowingSource(override val group: Group, val error: Exception) : FieldSource {
        override fun read(): List<Field> = throw error
    }

    @Test
    fun `groups fields from every source in group order`() {
        val repo = DiagnosticsRepository(
            listOf(
                FakeSource(Group.PHONE, listOf(Field.ok("Model", "Pixel", "Build.MODEL"))),
                FakeSource(Group.CONNECTION, listOf(Field.ok("Type", "Projection", "CarConnection"))),
            ),
        )

        val snapshot = repo.refresh()

        assertEquals(listOf(Group.CONNECTION, Group.PHONE), snapshot.groups.keys.toList())
        assertEquals("Pixel", snapshot.fields(Group.PHONE).single().value)
    }

    @Test
    fun `source that throws becomes an UNAVAILABLE field, not an exception`() {
        val repo = DiagnosticsRepository(
            listOf(ThrowingSource(Group.HARDWARE, IllegalStateException("host gone"))),
        )

        val field = repo.refresh().fields(Group.HARDWARE).single()

        assertEquals(Status.UNAVAILABLE, field.status)
        assertTrue(field.reason!!.contains("host gone"))
    }

    @Test
    fun `missing permission and unsupported host are plain statuses`() {
        val repo = DiagnosticsRepository(
            listOf(
                FakeSource(
                    Group.HARDWARE,
                    listOf(
                        Field.permissionMissing("Speed", "CarInfo.addSpeedListener", "com.google.android.gms.permission.CAR_SPEED"),
                        Field.notSupported("Toll card", "CarInfo.addTollListener", "Host API level 2 < 3"),
                    ),
                ),
            ),
        )

        val fields = repo.refresh().fields(Group.HARDWARE)

        assertEquals(Status.PERMISSION_MISSING, fields[0].status)
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, fields[1].status)
        assertNull(fields[0].value)
        assertEquals("PERMISSION_MISSING: Missing permission: com.google.android.gms.permission.CAR_SPEED", fields[0].rowText())
    }

    @Test
    fun `StateFlow value only changes when data changes`() {
        val source = FakeSource(Group.SCREEN, listOf(Field.ok("Width", 1280, "Surface")))
        val repo = DiagnosticsRepository(listOf(source))

        val first = repo.refresh()
        val second = repo.refresh()
        assertEquals(first, second)

        source.fields = listOf(Field.ok("Width", 1920, "Surface"))
        assertEquals("1920", repo.refresh().fields(Group.SCREEN).single().value)
        assertEquals(repo.snapshot.value, repo.refresh())
    }

}
