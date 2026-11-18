package com.jantursky.autoscope

import android.content.Context

/** Light, dark, or follow the system. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User settings: the refresh interval and the phone screen's theme choice. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("autoscope", Context.MODE_PRIVATE)

    /** Car template and data refresh interval in milliseconds. Default 1 s. */
    var refreshMillis: Long
        get() = prefs.getLong(KEY_REFRESH, DEFAULT_REFRESH_MS).coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)
        set(value) = prefs.edit().putLong(KEY_REFRESH, value.coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)).apply()

    /** Phone screen theme. The car screen follows the car host and ignores this. */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    companion object {
        const val DEFAULT_REFRESH_MS = 1_000L
        const val MIN_REFRESH_MS = 500L
        const val MAX_REFRESH_MS = 10_000L
        private const val KEY_REFRESH = "refresh_ms"
        private const val KEY_THEME = "theme_mode"
    }
}
