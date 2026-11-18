package com.jantursky.autoscope.phone

import android.content.Context
import android.content.res.Configuration
import com.jantursky.autoscope.ThemeMode

/** The night bits of [current] (a Configuration.uiMode) with the user's choice applied. */
fun uiModeFor(mode: ThemeMode, current: Int): Int {
    val night = when (mode) {
        ThemeMode.SYSTEM -> return current
        ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (current and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
}

/**
 * [base] with the night mode forced by [mode]. Works from API 28 (no AppCompat needed): the Activity
 * resolves its theme, colors and drawables from this overridden configuration, so values-night applies.
 */
internal fun themedContext(base: Context, mode: ThemeMode): Context {
    if (mode == ThemeMode.SYSTEM) return base
    val config = Configuration(base.resources.configuration)
    config.uiMode = uiModeFor(mode, config.uiMode)
    return base.createConfigurationContext(config)
}
