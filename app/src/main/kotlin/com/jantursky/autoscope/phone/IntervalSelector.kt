package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

private val INTERVAL_LABELS = listOf(
    R.string.interval_500, R.string.interval_1000, R.string.interval_2000,
    R.string.interval_5000, R.string.interval_10000,
)

/** Segmented choice over [REFRESH_CHOICES_MS]; [selectedIndex] is an index into that list. */
internal class IntervalSelector(
    context: Context,
    selectedIndex: Int,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    init {
        setBackgroundResource(R.drawable.segment_track)
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        INTERVAL_LABELS.forEachIndexed { index, label ->
            addView(
                context.label(14f, R.color.text_secondary, bold = true).apply {
                    setText(label)
                    gravity = Gravity.CENTER
                    minHeight = context.dp(48)
                    setTextColor(context.getColorStateList(R.color.segment_text))
                    setBackgroundResource(R.drawable.segment_background)
                    isSelected = index == selectedIndex
                    setOnClickListener { select(index) }
                },
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun select(index: Int) {
        for (i in 0 until childCount) getChildAt(i).isSelected = i == index
        onSelected(index)
    }
}
