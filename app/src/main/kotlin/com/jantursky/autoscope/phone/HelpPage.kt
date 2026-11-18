package com.jantursky.autoscope.phone

import android.content.Context
import android.widget.LinearLayout
import com.jantursky.autoscope.R

private val STEPS = listOf(R.string.help_step_1, R.string.help_step_2, R.string.help_step_3)

/** Card with the numbered setup steps and a first-time note. */
internal fun Context.helpPage(): LinearLayout = card().apply {
    STEPS.forEachIndexed { index, step ->
        if (index > 0) addView(divider())
        addView(helpText("${index + 1}. ${getString(step)}", R.color.text_secondary))
    }
    addView(divider())
    addView(helpText(getString(R.string.help_first_time), R.color.text_tertiary))
}

private fun Context.helpText(text: String, color: Int) = label(14f, color).apply {
    this.text = text
    setPadding(dp(16), dp(12), dp(16), dp(12))
}
