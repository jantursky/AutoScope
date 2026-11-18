package com.jantursky.autoscope.phone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/** Share of the row taken by the label column, so labels and values line up in every card. */
private const val LABEL_WEIGHT = 10f
private const val VALUE_WEIGHT = 10f

/**
 * One label and value row of a group card, updated in place by [bind]. The label column, the status
 * dot slot and the value column have fixed positions; a long value wraps inside its column and the
 * label stays on the first line.
 */
internal class RowView(context: Context) : LinearLayout(context) {
    private val label = context.label(14f, R.color.text_secondary).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val dot = View(context).apply { setBackgroundResource(R.drawable.status_dot) }
    private val dotSlot = LinearLayout(context).apply {
        // 6dp above the dot centers it on the first 20dp text line.
        addView(dot, LayoutParams(context.dp(8), context.dp(8)).apply { topMargin = context.dp(6) })
    }
    private val value = context.label(14f, R.color.text_primary).apply { fontFeatureSettings = "tnum" }
    private val reason = context.label(12f, R.color.text_tertiary)
    private val valueBlock = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(value, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(reason, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        orientation = HORIZONTAL
        // 12dp above and below one 20dp line gives a 44dp row with the text centered.
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, LABEL_WEIGHT).apply { marginEnd = context.dp(8) })
        addView(dotSlot, LayoutParams(context.dp(16), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(valueBlock, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, VALUE_WEIGHT))
    }

    fun bind(row: RowModel) {
        val calm = row.style == StatusStyle.CALM
        // A calm row has no reading yet: the reason takes the value's place instead of a dash plus a note.
        val shownValue = if (calm) row.reason ?: row.value else row.value
        val shownReason = row.reason.takeIf { !calm }
        label.setTextIfChanged(row.label)
        value.setTypeface(null, if (calm) Typeface.ITALIC else Typeface.NORMAL)
        value.setTextIfChanged(shownValue)
        value.setTextColor(context.color(if (row.style == StatusStyle.NONE) R.color.text_primary else R.color.text_tertiary))
        val showDot = statusColor(row.style) != null
        dot.visibility = if (showDot) View.VISIBLE else View.INVISIBLE
        statusColor(row.style)?.let { dot.backgroundTintList = ColorStateList.valueOf(context.color(it)) }
        reason.visibility = if (shownReason == null) View.GONE else View.VISIBLE
        reason.setTextIfChanged(shownReason.orEmpty())
        reason.setTextColor(context.color(statusColor(row.style) ?: R.color.text_tertiary))
    }

    private fun statusColor(style: StatusStyle): Int? = when (style) {
        StatusStyle.MUTED -> R.color.status_muted
        StatusStyle.WARNING -> R.color.status_warning
        StatusStyle.INFO -> R.color.status_info
        StatusStyle.NONE, StatusStyle.CALM -> null
    }
}
