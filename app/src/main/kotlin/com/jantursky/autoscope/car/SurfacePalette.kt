package com.jantursky.autoscope.car

/** Colours of the map surface drawings (ARGB), matching the host's dark or light car UI. */
data class SurfacePalette(
    val background: Int,
    val foreground: Int,
    /** Missing data and inactive parts. */
    val dim: Int,
    /** Tile frames and scales. */
    val line: Int,
    /** Needles, the speed arc and other live values. */
    val accent: Int,
    /** The lit charge port. */
    val charge: Int,
) {
    companion object {
        val DARK = SurfacePalette(
            background = 0xFF131215.toInt(),
            foreground = 0xFFECEAF0.toInt(),
            dim = 0x80ECEAF0.toInt(),
            line = 0xFFB4B1BA.toInt(),
            accent = 0xFFFF6B5E.toInt(),
            charge = 0xFF5BD68A.toInt(),
        )
        val LIGHT = SurfacePalette(
            background = 0xFFF1EFF3.toInt(),
            foreground = 0xFF1B1A1E.toInt(),
            dim = 0x801B1A1E.toInt(),
            line = 0xFF5E5B64.toInt(),
            accent = 0xFFC8352A.toInt(),
            charge = 0xFF1E7F45.toInt(),
        )

        fun of(darkMode: Boolean) = if (darkMode) DARK else LIGHT
    }
}
