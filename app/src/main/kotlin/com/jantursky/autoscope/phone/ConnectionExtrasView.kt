package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/** Above the Connection group card: the connection timeline and the Wi-Fi chip. */
internal class ConnectionExtrasView(context: Context) : LinearLayout(context) {
    private val bar = TimelineBarView(context)
    private val rows = RowListView(context)
    private val empty = context.label(13f, R.color.text_tertiary).apply {
        setText(R.string.timeline_empty)
        gravity = Gravity.CENTER
        setPadding(context.dp(16), context.dp(24), context.dp(16), context.dp(24))
    }
    private val wifi = WifiChipView(context, withNote = true)

    init {
        orientation = VERTICAL
        addView(context.heading(R.string.timeline_title))
        addView(context.card().apply {
            addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(12)).apply {
                setMargins(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
            })
            addView(rows)
            addView(empty)
        })
        addView(context.card().apply { addView(wifi) })
    }

    fun bind(timeline: ConnectionTimeline, quality: WifiQuality) {
        val none = timeline.rows.isEmpty()
        bar.visibility = if (none) GONE else VISIBLE
        empty.visibility = if (none) VISIBLE else GONE
        bar.bind(timeline.segments)
        rows.bind(timeline.rows.mapIndexed { index, row ->
            // Newest first, so the position from the top is a stable key until a new event arrives.
            RowModel("$index", row.time, row.state, row.duration, StatusStyle.NONE)
        })
        wifi.bind(quality)
    }
}
