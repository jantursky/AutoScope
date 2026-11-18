package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiQualityTest {
    private fun rate(band: String?, speed: String?) = wifiQuality(
        band?.let { Field.ok("Wi-Fi band", it, "s") },
        speed?.let { Field.ok("Wi-Fi link speed", it, "s") },
    )

    @Test fun fastFiveGhzIsGood() {
        val q = rate("5 GHz", "433 Mbps, Wi-Fi 5 (802.11ac)")
        assertEquals(Tone.GOOD, q.tone)
        assertEquals("5 GHz · 433 Mbps", q.text)
    }

    @Test fun sixGhzFollowsTheSameThresholds() {
        assertEquals(Tone.GOOD, rate("6 GHz", "1200 Mbps").tone)
        assertEquals(Tone.WARNING, rate("6 GHz", "150 Mbps").tone)
    }

    @Test fun fiveGhzBoundaries() {
        assertEquals(Tone.GOOD, rate("5 GHz", "200 Mbps").tone)
        assertEquals(Tone.WARNING, rate("5 GHz", "199 Mbps").tone)
        assertEquals(Tone.WARNING, rate("5 GHz", "100 Mbps").tone)
        assertEquals(Tone.BAD, rate("5 GHz", "99 Mbps").tone)
    }

    @Test fun twoPointFourIsNeverGoodAndHintsAtFiveGhz() {
        val fast = rate("2.4 GHz", "144 Mbps")
        assertEquals(Tone.WARNING, fast.tone)
        assertEquals("Wireless Android Auto needs 5 GHz", fast.hint)
        assertEquals(Tone.BAD, rate("2.4 GHz", "72 Mbps").tone)
        assertEquals(Tone.WARNING, rate("2.4 GHz", "100 Mbps").tone)
    }

    @Test fun missingSpeedRatesTheBandAlone() {
        assertEquals(WifiQuality(Tone.GOOD, "5 GHz", "Fast enough for wireless Android Auto"), rate("5 GHz", null))
        assertEquals(Tone.WARNING, rate("2.4 GHz", null).tone)
    }

    @Test fun slowFiveGhzHintsAtStutter() {
        assertEquals("A slow link can make wireless Android Auto stutter", rate("5 GHz", "60 Mbps").hint)
    }

    @Test fun unparsableSpeedIsIgnored() {
        assertEquals("5 GHz", rate("5 GHz", "fast").text)
    }

    @Test fun unknownBandAndMissingFieldsAreGrey() {
        assertEquals(Tone.IDLE, rate("Unknown band (3000 MHz)", "300 Mbps").tone)
        assertEquals(WifiQuality(Tone.IDLE, "Wi-Fi unknown", "No Wi-Fi link to rate right now"), wifiQuality(null, null))
    }

    @Test fun notOnWifiShowsTheReason() {
        val band = Field.unavailable("Wi-Fi band", "s", "Not connected to Wi-Fi")
        assertEquals("Not connected to Wi-Fi", wifiQuality(band, null).text)
        assertEquals(Tone.IDLE, wifiQuality(band, null).tone)
    }

    @Test fun permissionProblemIsNamedWithoutTheException() {
        val band = Field.permissionMissing("Wi-Fi band", "s", "ACCESS_WIFI_STATE")
        assertEquals("Wi-Fi permission missing", wifiQuality(band, null).text)
    }

    @Test fun readsTheFieldsFromThePhoneGroup() {
        val snapshot = Snapshot(mapOf(Group.PHONE to listOf(Field.ok("Wi-Fi band", "5 GHz", "s"), Field.ok("Wi-Fi link speed", "300 Mbps", "s"))))
        assertEquals("5 GHz · 300 Mbps", wifiQuality(snapshot).text)
    }
}
