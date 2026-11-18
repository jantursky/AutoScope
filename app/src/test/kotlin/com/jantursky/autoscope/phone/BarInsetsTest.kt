package com.jantursky.autoscope.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class BarInsetsTest {
    @Test fun maxIsPerSide() {
        val bars = BarInsets(0, 80, 0, 120)
        val cutout = BarInsets(40, 100, 0, 0)
        assertEquals(BarInsets(40, 100, 0, 120), bars.max(cutout))
    }

    @Test fun noneIsNeutral() {
        val bars = BarInsets(1, 2, 3, 4)
        assertEquals(bars, bars.max(BarInsets.NONE))
    }
}
