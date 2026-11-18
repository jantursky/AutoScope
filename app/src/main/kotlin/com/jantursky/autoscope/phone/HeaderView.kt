package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/** App icon, title, subtitle and the connection status pill. */
internal class HeaderView(context: Context) : LinearLayout(context) {
    private val pill = context.label(12f, R.color.pill_disconnected_text, bold = true).apply {
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
    }

    init {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(24))
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_launcher) }, LayoutParams(context.dp(40), context.dp(40)))
        val titles = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(context.label(20f, R.color.text_primary, bold = true).apply { setText(R.string.app_name) })
            addView(context.label(12f, R.color.text_secondary).apply { setText(R.string.app_subtitle) })
        }
        addView(titles, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(12) })
        addView(pill, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bind(ConnectionPill("Not connected", PillKind.DISCONNECTED))
    }

    fun bind(state: ConnectionPill) {
        pill.setTextIfChanged(state.text)
        val connected = state.kind == PillKind.CONNECTED
        pill.setBackgroundResource(if (connected) R.drawable.pill_connected else R.drawable.pill_disconnected)
        pill.setTextColor(context.color(if (connected) R.color.pill_connected_text else R.color.pill_disconnected_text))
    }
}
