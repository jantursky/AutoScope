package com.jantursky.autoscope.phone

import com.jantursky.autoscope.car.Area
import com.jantursky.autoscope.car.ScreenSource
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPreviewTest {
    private fun snapshot(vararg fields: Field) = Snapshot(mapOf(Group.SCREEN to fields.toList()))

    // Uses the car side's own formatting, so a change of the value formats fails here.
    private fun display(widthPx: Int = 1280, heightPx: Int = 720) = ScreenSource.displayFields(
        com.jantursky.autoscope.car.DisplayInfo(
            widthPx = widthPx, heightPx = heightPx, densityDpi = 160, density = 1f, scaledDensity = 1f,
            widthDp = widthPx, heightDp = heightPx, smallestWidthDp = heightPx, orientation = 2,
            refreshRateHz = 60f, isDarkMode = false, uiModeNight = 1, fontScale = 1f, locale = "en-US",
        ),
    )

    @Test fun parsesRealDisplayFields() {
        val ready = screenPreview(snapshot(*display().toTypedArray())) as ScreenPreview.Ready
        assertEquals("1280 x 720 px", ready.pxText)
        assertEquals("1280 x 720 dp", ready.dpText)
        assertEquals("160 dpi", ready.dpiText)
        assertNull(ready.visible)
    }

    @Test fun drawsAreasFromTheCarSideFormat() {
        val area = Area(0, 72, 640, 720)
        val fields = display() + listOf(Field.ok("Visible area", area, "s"), Field.ok("Stable area", Area(100, 0, 1180, 720), "s"))
        val ready = screenPreview(snapshot(*fields.toTypedArray())) as ScreenPreview.Ready
        assertEquals(Fractions(0f, 0.1f, 0.5f, 1f), ready.visible)
        assertEquals(Fractions(100f / 1280, 0f, 1180f / 1280, 1f), ready.stable)
    }

    @Test fun areasOutsideTheDisplayAreClamped() {
        val fields = display() + Field.ok("Visible area", Area(-50, -10, 5000, 720), "s")
        val ready = screenPreview(snapshot(*fields.toTypedArray())) as ScreenPreview.Ready
        assertEquals(Fractions(0f, 0f, 1f, 1f), ready.visible)
    }

    @Test fun unavailableAreasDrawNothing() {
        val fields = display() + Field.unavailable("Visible area", "s", "Host reported an empty area, which means unknown")
        assertNull((screenPreview(snapshot(*fields.toTypedArray())) as ScreenPreview.Ready).visible)
    }

    @Test fun noCarSessionIsDimmedWithThatReason() {
        val fields = arrayOf(Field.unavailable("Width", "s", NO_CAR_SESSION), Field.unavailable("Height", "s", NO_CAR_SESSION))
        assertEquals(ScreenPreview.Empty(NO_CAR_SESSION), screenPreview(snapshot(*fields)))
        assertEquals(ScreenPreview.Empty(NO_CAR_SESSION), screenPreview(Snapshot.EMPTY))
    }

    @Test fun unparsableValuesDegradeToNoPreview() {
        val garbage = screenPreview(snapshot(Field.ok("Width", "wide", "s"), Field.ok("Height", "720 px", "s")))
        assertEquals(ScreenPreview.Empty("No preview available"), garbage)
        assertTrue(screenPreview(snapshot(Field.ok("Width", "0 px", "s"), Field.ok("Height", "720 px", "s"))) is ScreenPreview.Empty)
    }

    @Test fun unparsableAreaOrDpKeepsTheRestOfThePreview() {
        val fields = display().filterNot { it.label == "Width dp" } + Field.ok("Visible area", "somewhere", "s")
        val ready = screenPreview(snapshot(*fields.toTypedArray())) as ScreenPreview.Ready
        assertNull(ready.dpText)
        assertNull(ready.visible)
    }
}
