package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.ReportStore
import com.jantursky.autoscope.core.Snapshot
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDeletionTest {
    private val dir: File = Files.createTempDirectory("reports").toFile()
    private val store = ReportStore(dir)

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun removesBothFiles() {
        val report = store.save(Snapshot.EMPTY, "one")
        assertTrue(deleteReport(report))
        assertFalse(report.json.exists())
        assertFalse(report.text.exists())
        assertEquals(0, store.list().size)
    }

    @Test fun anAlreadyMissingFileIsFine() {
        val report = store.save(Snapshot.EMPTY, "one")
        report.text.delete()
        assertTrue(deleteReport(report))
        assertFalse(report.json.exists())
    }

    @Test fun deletesOnlyTheGivenReports() {
        val a = store.save(Snapshot.EMPTY, "a")
        Thread.sleep(2)
        val b = store.save(Snapshot.EMPTY, "b")
        assertEquals(1, deleteReports(listOf(a)))
        assertEquals(listOf(b.id), store.list().map { it.id })
    }

    @Test fun deleteAllCountsEveryReport() {
        repeat(3) { store.save(Snapshot.EMPTY, "r$it"); Thread.sleep(2) }
        assertEquals(3, deleteReports(store.list()))
        assertEquals(0, store.list().size)
    }
}
