package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status

/** Wi-Fi chip: [text] like "5 GHz · 433 Mbps", colored by [tone], with a one-line [hint]. */
data class WifiQuality(val tone: Tone, val text: String, val hint: String)

private const val BAND_LABEL = "Wi-Fi band"
private const val SPEED_LABEL = "Wi-Fi link speed"

/** Link speed from which a 5 or 6 GHz link is good, and the floor for fair on any band, in Mbps. */
internal const val GOOD_MBPS = 200
internal const val FAIR_MBPS = 100

private val SPEED = Regex("""^(\d+) Mbps""")

/**
 * Rates the phone's Wi-Fi from the PHONE group. 5 and 6 GHz: >= 200 Mbps good, >= 100 fair, below poor.
 * 2.4 GHz: >= 100 fair, below poor (wireless Android Auto needs 5 GHz, so never good). A missing speed
 * rates the band alone: 5 and 6 GHz good, 2.4 GHz fair. No band means nothing to rate.
 */
fun wifiQuality(snapshot: Snapshot): WifiQuality {
    val phone = snapshot.fields(Group.PHONE)
    return wifiQuality(phone.firstOrNull { it.label == BAND_LABEL }, phone.firstOrNull { it.label == SPEED_LABEL })
}

internal fun wifiQuality(band: Field?, speed: Field?): WifiQuality {
    val value = band?.takeIf { it.status == Status.OK }?.value
    val fast = when {
        value == null -> return unrated(band)
        value.startsWith("2.4 GHz") -> false
        value.startsWith("5 GHz") || value.startsWith("6 GHz") -> true
        else -> return unrated(band)
    }
    val mbps = speed?.takeIf { it.status == Status.OK }?.value?.let { SPEED.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    val tone = when {
        mbps == null -> if (fast) Tone.GOOD else Tone.WARNING
        fast && mbps >= GOOD_MBPS -> Tone.GOOD
        mbps >= FAIR_MBPS -> Tone.WARNING
        else -> Tone.BAD
    }
    val bandText = value.substringBefore(" GHz") + " GHz"
    val text = if (mbps == null) bandText else "$bandText · $mbps Mbps"
    val hint = when {
        !fast -> "Wireless Android Auto needs 5 GHz"
        tone == Tone.GOOD -> "Fast enough for wireless Android Auto"
        else -> "A slow link can make wireless Android Auto stutter"
    }
    return WifiQuality(tone, text, hint)
}

private fun unrated(band: Field?): WifiQuality {
    val text = when {
        band == null -> "Wi-Fi unknown"
        band.status == Status.PERMISSION_MISSING -> "Wi-Fi permission missing"
        band.status == Status.OK -> band.value ?: "Wi-Fi unknown"
        else -> band.reason ?: "Wi-Fi unknown"
    }
    return WifiQuality(Tone.IDLE, text, "No Wi-Fi link to rate right now")
}
