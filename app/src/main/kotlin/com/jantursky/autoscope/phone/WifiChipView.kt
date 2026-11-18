package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/** Wi-Fi quality chip with its hint, and optionally the note that this is the phone's network. */
internal class WifiChipView(context: Context, withNote: Boolean) : LinearLayout(context) {
    private val chip = context.label(13f, R.color.text_primary, bold = true).apply { maxLines = 1 }
    private val hint = context.label(13f, R.color.text_secondary)

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        val top = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(context.label(14f, R.color.text_secondary).apply { setText(R.string.wifi_title) }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(top)
        addView(hint, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        if (withNote) {
            addView(
                context.label(12f, R.color.text_tertiary).apply { setText(R.string.wifi_note) },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) },
            )
        }
    }

    fun bind(quality: WifiQuality) {
        chip.setTextIfChanged(quality.text)
        chip.applyTone(quality.tone)
        hint.setTextIfChanged(quality.hint)
    }
}
