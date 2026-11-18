package com.jantursky.autoscope.phone

import com.jantursky.autoscope.Settings

/** Preset refresh intervals offered in the settings spinner, all within the range [Settings] accepts. */
val REFRESH_CHOICES_MS: List<Long> = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L)

/** Index of the preset closest to [millis], so an out-of-list stored value still selects something sensible. */
fun closestRefreshChoice(millis: Long): Int =
    REFRESH_CHOICES_MS.indices.minBy { kotlin.math.abs(REFRESH_CHOICES_MS[it] - millis) }
