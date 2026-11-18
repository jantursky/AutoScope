package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.jantursky.autoscope.R
import com.jantursky.autoscope.core.SavedReport
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The Reports tab: a hint with "Delete all" above the saved reports. Tap shares a report, long-press deletes it. */
internal class ReportsPageView(
    context: Context,
    private val onShare: (SavedReport) -> Unit,
    private val onDelete: (SavedReport) -> Unit,
    private val onDeleteAll: () -> Unit,
) : LinearLayout(context) {
    private val topBar = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), 0, 0, 0)
    }
    private val list = context.card()

    init {
        orientation = VERTICAL
        topBar.addView(
            context.label(12f, R.color.text_tertiary).apply { setText(R.string.reports_hint) },
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        topBar.addView(pillButton(R.string.delete_all, null) { onDeleteAll() }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(48)))
        addView(topBar)
        addView(list)
    }

    fun bind(reports: List<SavedReport>) {
        list.removeAllViews()
        topBar.visibility = if (reports.isEmpty()) GONE else VISIBLE
        if (reports.isEmpty()) {
            list.addView(
                context.label(13f, R.color.text_tertiary).apply {
                    setText(R.string.no_saved_reports)
                    gravity = Gravity.CENTER
                    setPadding(context.dp(16), context.dp(24), context.dp(16), context.dp(24))
                },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            return
        }
        val now = Instant.now()
        reports.forEachIndexed { index, report ->
            if (index > 0) list.addView(context.divider())
            val label = savedReportLabel(report.id, now, ZoneId.systemDefault(), Locale.getDefault())
            list.addView(row(report, label), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(56)))
        }
    }

    private fun row(report: SavedReport, text: SavedReportLabel) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(context.label(14f, R.color.text_primary).apply { this.text = text.title })
            if (text.whenText.isNotEmpty()) addView(context.label(12f, R.color.text_secondary).apply { this.text = text.whenText })
        }
        addView(titles, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            pillButton(R.string.share, R.drawable.ic_share) { onShare(report) }.apply {
                // A clickable child would swallow the long press that belongs to the row.
                setOnLongClickListener { onDelete(report); true }
            },
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(48)),
        )
        tappable(R.drawable.tab_ripple) { onShare(report) }
        setOnLongClickListener { onDelete(report); true }
        // After tappable(): setting a background resets the padding to the drawable's own.
        setPadding(context.dp(16), 0, context.dp(8), 0)
    }

    private fun pillButton(label: Int, icon: Int?, onClick: () -> Unit): TextView = TextView(context).apply {
        setText(label)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(context.color(R.color.accent))
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = context.dp(6)
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon ?: 0, 0, 0, 0)
        tappable(R.drawable.pill_tonal, onClick)
        // After tappable(): setting a background resets the padding to the drawable's own.
        setPadding(context.dp(12), 0, context.dp(14), 0)
    }
}
