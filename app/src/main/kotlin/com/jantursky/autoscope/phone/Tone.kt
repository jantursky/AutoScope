package com.jantursky.autoscope.phone

import android.content.res.ColorStateList
import android.widget.TextView
import com.jantursky.autoscope.R

/** How a verdict or a quality chip is colored. [IDLE] is calm grey: nothing to judge yet. */
enum class Tone { GOOD, WARNING, BAD, IDLE }

/** Pill look of [tone]: tinted [R.drawable.chip] background and a matching text color. */
internal fun TextView.applyTone(tone: Tone) {
    val (background, text) = when (tone) {
        Tone.GOOD -> R.color.tone_good_bg to R.color.tone_good_text
        Tone.WARNING -> R.color.tone_warning_bg to R.color.tone_warning_text
        Tone.BAD -> R.color.tone_bad_bg to R.color.tone_bad_text
        Tone.IDLE -> R.color.pill_disconnected_bg to R.color.pill_disconnected_text
    }
    setBackgroundResource(R.drawable.chip)
    backgroundTintList = ColorStateList.valueOf(context.color(background))
    setTextColor(context.color(text))
    setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
}
