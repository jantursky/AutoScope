package com.jantursky.autoscope.car

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFitTest {

    /** Every character 10 px wide. */
    private val measure = TextMeasure { it.length * 10f }

    @Test
    fun `text that fits stays as it is`() {
        assertEquals("Speed", TextFit.fit("Speed", 50f, measure))
    }

    @Test
    fun `longer text keeps the longest start that fits with the ellipsis`() {
        assertEquals("Speed, la…", TextFit.fit("Speed, last minute", 100f, measure))
        assertEquals("Speed,…", TextFit.fit("Speed, last minute", 75f, measure))
    }

    @Test
    fun `spaces before the ellipsis are dropped`() {
        assertEquals("ab…", TextFit.fit("ab cdef", 40f, measure))
    }

    @Test
    fun `no room for the ellipsis gives an empty text`() {
        assertEquals("", TextFit.fit("Speed", 5f, measure))
        assertEquals("…", TextFit.fit("Speed", 10f, measure))
    }
}
