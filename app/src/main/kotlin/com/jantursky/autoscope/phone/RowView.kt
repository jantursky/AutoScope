package com.jantursky.autoscope.phone

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.jantursky.autoscope.R

private const val STACK_VALUE_CHARS = 28
private const val STACK_LABEL_CHARS = 24

/** One label and value row of a group card, updated in place by [bind]. */
internal class RowView(context: Context) : LinearLayout(context) {
    private val label = context.label(14f, R.color.text_secondary)
    private val dot = View(context).apply {
        setBackgroundResource(R.drawable.status_dot)
        layoutParams = LayoutParams(context.dp(8), context.dp(8)).apply { marginEnd = context.dp(8) }
    }
    private val value = context.label(15f, R.color.text_primary)
    private val reason = context.label(12f, R.color.text_tertiary)
    private val valueLine = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(dot)
        addView(value, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private val valueBlock = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(valueLine, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(reason, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private var stacked: Boolean? = null

    init {
        minimumHeight = context.dp(48)
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        addView(label)
        addView(valueBlock)
    }

    fun bind(row: RowModel) {
        val calm = row.style == StatusStyle.CALM
        // A calm row has no reading yet: the reason takes the value's place instead of a dash plus a note.
        val shownValue = if (calm) row.reason ?: row.value else row.value
        val shownReason = row.reason.takeIf { !calm }
        label.setTextIfChanged(row.label)
        value.setTextIfChanged(shownValue)
        value.setTextColor(context.color(if (row.style == StatusStyle.NONE) R.color.text_primary else R.color.text_tertiary))
        dot.visibility = when (row.style) {
            StatusStyle.NONE, StatusStyle.CALM -> View.GONE
            else -> View.VISIBLE
        }
        statusColor(row.style)?.let { dot.backgroundTintList = ColorStateList.valueOf(context.color(it)) }
        reason.visibility = if (shownReason == null) View.GONE else View.VISIBLE
        reason.setTextIfChanged(shownReason.orEmpty())
        reason.setTextColor(context.color(statusColor(row.style) ?: R.color.text_tertiary))
        applyLayout(shownValue.length > STACK_VALUE_CHARS || row.label.length > STACK_LABEL_CHARS)
    }

    private fun statusColor(style: StatusStyle): Int? = when (style) {
        StatusStyle.MUTED -> R.color.status_muted
        StatusStyle.WARNING -> R.color.status_warning
        StatusStyle.INFO -> R.color.status_info
        StatusStyle.NONE, StatusStyle.CALM -> null
    }

    /** Wide values go on their own line under the label; short ones sit right-aligned beside it. */
    private fun applyLayout(stack: Boolean) {
        if (stacked == stack) return
        stacked = stack
        val gap = context.dp(16)
        if (stack) {
            orientation = VERTICAL
            label.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            valueBlock.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = context.dp(4) }
            alignValue(Gravity.START)
        } else {
            orientation = HORIZONTAL
            label.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f)
            valueBlock.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 6f).apply { marginStart = gap }
            alignValue(Gravity.END)
        }
    }

    private fun alignValue(horizontal: Int) {
        valueLine.gravity = horizontal or Gravity.CENTER_VERTICAL
        value.gravity = horizontal
        reason.gravity = horizontal
    }
}
