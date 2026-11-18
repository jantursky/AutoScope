package com.jantursky.autoscope

import android.content.Context

/** User settings. Only the refresh interval exists, as the brief allows no extra features. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("autoscope", Context.MODE_PRIVATE)

    /** Car template and data refresh interval in milliseconds. Default 1 s. */
    var refreshMillis: Long
        get() = prefs.getLong(KEY_REFRESH, DEFAULT_REFRESH_MS).coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)
        set(value) = prefs.edit().putLong(KEY_REFRESH, value.coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)).apply()

    companion object {
        const val DEFAULT_REFRESH_MS = 1_000L
        const val MIN_REFRESH_MS = 500L
        const val MAX_REFRESH_MS = 10_000L
        private const val KEY_REFRESH = "refresh_ms"
    }
}
