package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.jantursky.autoscope.R

/** Segmented choice between [labels]; [onSelected] gets the chosen index. */
internal class SegmentedControl(
    context: Context,
    @StringRes labels: List<Int>,
    selectedIndex: Int,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    init {
        setBackgroundResource(R.drawable.segment_track)
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        labels.forEachIndexed { index, label ->
            addView(
                context.label(13f, R.color.text_secondary, bold = true).apply {
                    setText(label)
                    gravity = Gravity.CENTER
                    minHeight = context.dp(40)
                    setTextColor(context.getColorStateList(R.color.segment_text))
                    isSelected = index == selectedIndex
                    tappable(R.drawable.segment_background) { select(index) }
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
