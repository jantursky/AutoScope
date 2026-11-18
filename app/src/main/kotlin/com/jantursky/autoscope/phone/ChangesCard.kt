package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

internal const val CHANGES_ROW_LIMIT = 40

/** Card with the fields that differ between two saved text reports; [reports] is null when fewer than two exist. */
internal fun Context.changesCard(reports: Pair<String, String>?): LinearLayout = card().apply {
    val changes = reports?.let { (older, newer) -> diffReports(older, newer) }
    if (changes == null || changes.isEmpty()) {
        addView(
            label(13f, R.color.text_tertiary).apply {
                setText(if (changes == null) R.string.changes_need_two else R.string.changes_none)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return@apply
    }
    var group: String? = null
    changes.take(CHANGES_ROW_LIMIT).forEachIndexed { index, change ->
        if (change.group != group) {
            group = change.group
            if (index > 0) addView(divider())
            addView(
                label(12f, R.color.text_secondary, bold = true).apply {
                    text = change.group
                    setPadding(dp(16), dp(12), dp(16), dp(4))
                },
            )
        }
        addView(divider())
        addView(RowView(context).apply { bind(rowFor(change, index)) })
    }
    val more = changes.size - CHANGES_ROW_LIMIT
    if (more > 0) {
        addView(divider())
        addView(
            label(12f, R.color.text_tertiary).apply {
                text = getString(R.string.changes_more, more)
                setPadding(dp(16), dp(8), dp(16), dp(8))
            },
        )
    }
}

private fun Context.valueText(change: FieldChange): String = when {
    change.old == null -> getString(R.string.change_added, change.new)
    change.new == null -> getString(R.string.change_removed, change.old)
    else -> getString(R.string.change_arrow, change.old, change.new)
}

private fun Context.rowFor(change: FieldChange, index: Int) =
    RowModel("$index:${change.label}", change.label, valueText(change), null, StatusStyle.NONE)
