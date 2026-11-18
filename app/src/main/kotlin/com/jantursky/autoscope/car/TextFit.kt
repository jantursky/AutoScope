package com.jantursky.autoscope.car

/** Width of a text in pixels with the current paint; Paint::measureText on the surface. */
fun interface TextMeasure {
    fun width(text: String): Float
}

/** Fits text drawn on the surface into a width, as the canvas does not clip or ellipsize by itself. */
object TextFit {
    const val ELLIPSIS = "…"

    /**
     * [text] when it fits [maxWidth], else its longest start that fits with [ELLIPSIS] appended, or an
     * empty string when not even the ellipsis fits.
     */
    fun fit(text: String, maxWidth: Float, measure: TextMeasure): String {
        if (measure.width(text) <= maxWidth) return text
        if (measure.width(ELLIPSIS) > maxWidth) return ""
        fun cut(length: Int) = text.take(length).trimEnd() + ELLIPSIS
        var low = 0
        var high = text.length - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (measure.width(cut(mid)) <= maxWidth) low = mid else high = mid - 1
        }
        return cut(low)
    }
}
