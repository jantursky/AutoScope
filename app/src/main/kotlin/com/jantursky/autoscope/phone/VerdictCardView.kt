package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/** Compatibility card at the top of the Car tab: verdict word, one-line summary, detail rows and the Wi-Fi chip. */
internal class VerdictCardView(context: Context) : LinearLayout(context) {
    private val word = context.label(13f, R.color.text_primary, bold = true).apply { maxLines = 1 }
    private val summary = context.label(13f, R.color.text_secondary)
    private val rows = RowListView(context)
    private val wifi = WifiChipView(context, withNote = false)

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.card_background)
        clipToOutline = true
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = context.dp(8) }
        addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
                addView(word, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(summary, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(12) })
            },
        )
        addView(rows)
        addView(context.divider())
        addView(wifi)
    }

    fun bind(verdict: CarVerdict, quality: WifiQuality) {
        word.setTextIfChanged(verdict.word)
        word.applyTone(verdict.tone)
        summary.setTextIfChanged(verdict.summary)
        rows.bind(verdictRows(verdict))
        wifi.bind(quality)
    }
}
