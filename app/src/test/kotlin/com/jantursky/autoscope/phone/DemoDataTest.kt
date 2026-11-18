package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {
    private val snapshot = DemoData.snapshot

    @Test
    fun verdictIsReady() {
        val verdict = carVerdict(snapshot, DemoData.telemetry)
        assertEquals("Ready", verdict.word)
        assertEquals(Tone.GOOD, verdict.tone)
        assertEquals(22, verdict.hardwareTotal)
        assertEquals(22, verdict.hardwareOk)
        assertTrue(verdict.missing.isEmpty())
    }

    @Test
    fun pillSaysProjection() {
        assertEquals(ConnectionPill("Projection", PillKind.CONNECTED), connectionPill(snapshot))
    }

    @Test
    fun screenPreviewIsReadyWithAreas() {
        val preview = screenPreview(snapshot) as ScreenPreview.Ready
        assertEquals(1280, preview.widthPx)
        assertEquals(720, preview.heightPx)
        assertEquals(160, preview.dpi)
        assertTrue(preview.visible != null && preview.stable != null)
    }

    @Test
    fun wifiIsGood() {
        val wifi = wifiQuality(snapshot)
        assertEquals(Tone.GOOD, wifi.tone)
        assertEquals("5 GHz · 866 Mbps", wifi.text)
    }

    @Test
    fun everyGroupHasOnlyOkFields() {
        Group.entries.forEach { group ->
            val fields = snapshot.fields(group).filterNot { it.label == "Tap latency" }
            assertTrue("$group has no fields", fields.isNotEmpty())
            assertTrue("$group has a field that is not OK", fields.all { it.status == Status.OK })
        }
        assertEquals(Group.entries.toSet(), groupModels(snapshot).map { it.group }.toSet())
    }

    @Test
    fun timelineHasHistory() {
        val timeline = DemoData.timeline()
        assertEquals(4, timeline.rows.size)
        assertEquals("now", timeline.rows.first().duration)
    }
}
