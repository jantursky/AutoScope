package com.jantursky.autoscope.phone

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.jantursky.autoscope.R
import com.jantursky.autoscope.Settings

/** Sticky top bar: connection pill, last update time and the overflow menu button. */
internal class HeaderView(
    context: Context,
    private val settings: Settings,
    private val onAction: (MenuAction) -> Unit,
) : LinearLayout(context) {
    private val pill = context.label(12f, R.color.pill_disconnected_text, bold = true).apply {
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
        maxLines = 1
    }
    private val updated = context.label(12f, R.color.text_tertiary).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        fontFeatureSettings = "tnum"
    }
    private val menuButton = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.ic_more)
        contentDescription = context.getString(R.string.menu)
        tappable(R.drawable.icon_button) {
            showOverflowMenu(this, menuSections(settings.refreshMillis, settings.themeMode), onAction)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(context.color(R.color.background))
        val bar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16), 0, context.dp(8), 0)
            addView(pill, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(updated, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(12) })
            addView(menuButton, LayoutParams(context.dp(48), context.dp(48)))
        }
        addView(bar)
        bind(ConnectionPill("Not connected", PillKind.DISCONNECTED), "")
    }

    fun bind(state: ConnectionPill, updatedAt: String) {
        pill.setTextIfChanged(state.text)
        updated.setTextIfChanged(updatedAt)
        val connected = state.kind == PillKind.CONNECTED
        pill.setBackgroundResource(if (connected) R.drawable.pill_connected else R.drawable.pill_disconnected)
        pill.setTextColor(context.color(if (connected) R.color.pill_connected_text else R.color.pill_disconnected_text))
    }
}
