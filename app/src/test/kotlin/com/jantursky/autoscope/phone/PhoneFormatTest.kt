package com.jantursky.autoscope.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFormatTest {
    @Test fun bandFromFrequency() {
        assertEquals("2.4 GHz", wifiBand(2437))
        assertEquals("5 GHz", wifiBand(5180))
        assertEquals("5 GHz", wifiBand(5825))
        assertEquals("6 GHz", wifiBand(5955))
        assertEquals("6 GHz", wifiBand(7115))
        assertNull(wifiBand(-1))
        assertNull(wifiBand(60_000))
    }

    @Test fun durations() {
        assertEquals("0s", formatDuration(-5))
        assertEquals("45s", formatDuration(45_999))
        assertEquals("2m 05s", formatDuration(125_000))
        assertEquals("1h 02m 03s", formatDuration(3_723_000))
    }

    @Test fun bytes() {
        assertEquals("512 MB", formatBytes(512L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes(1536L * 1024 * 1024))
    }

    @Test fun temperature() = assertEquals("31.5 °C", formatTemperature(315))

    @Test fun names() {
        assertEquals("Projection (Android Auto)", connectionTypeName(2))
        assertEquals("Unknown (9)", connectionTypeName(9))
        assertEquals("Charging", batteryStatusName(2))
        assertEquals("Severe", thermalStatusName(3))
        assertEquals("Wi-Fi 6 (802.11ax)", wifiStandardName(6))
        assertNull(wifiStandardName(0))
    }

    @Test fun closestRefresh() {
        assertEquals(0, closestRefreshChoice(500))
        assertEquals(2, closestRefreshChoice(2_300))
        assertEquals(4, closestRefreshChoice(60_000))
    }
}
