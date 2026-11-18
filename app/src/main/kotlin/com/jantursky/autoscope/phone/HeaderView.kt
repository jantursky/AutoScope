package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.jantursky.autoscope.R
import com.jantursky.autoscope.ThemeMode

/**
 * Sticky top bar: connection pill, last update time, refresh interval dropdown, save and theme menu.
 */
internal class HeaderView(
    context: Context,
    private val intervalLabels: List<Int>,
    selectedInterval: Int,
    selectedTheme: ThemeMode,
    private val onInterval: (Int) -> Unit,
    private val onTheme: (ThemeMode) -> Unit,
    private val onSave: () -> Unit,
) : LinearLayout(context) {
    private val pill = context.label(12f, R.color.pill_disconnected_text, bold = true).apply {
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
    }
    private val updated = context.label(12f, R.color.text_tertiary).apply {
        maxLines = 1
        fontFeatureSettings = "tnum"
    }
    private val intervalButton = context.label(13f, R.color.text_primary, bold = true)
    private val saveButton = ImageView(context)
    private val themeButton = ImageView(context)
    private var interval = selectedInterval
    private var theme = selectedTheme

    init {
        orientation = VERTICAL
        setBackgroundColor(context.color(R.color.background))
        val bar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(16), 0, context.dp(8), 0)
            addView(pill, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(updated, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(12) })
            addView(intervalButton, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(48)).apply { marginEnd = context.dp(4) })
            addView(saveButton, LayoutParams(context.dp(48), context.dp(48)))
            addView(themeButton, LayoutParams(context.dp(48), context.dp(48)))
        }
        addView(bar)
        setUpIntervalButton()
        setUpSaveButton()
        setUpThemeButton()
        bind(ConnectionPill("Not connected", PillKind.DISCONNECTED), "")
    }

    private fun setUpIntervalButton() = intervalButton.apply {
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = context.dp(4)
        context.getDrawable(R.drawable.ic_chevron)?.mutate()?.let {
            it.setBounds(0, 0, context.dp(16), context.dp(16))
            setCompoundDrawablesRelative(null, null, it, null)
        }
        contentDescription = context.getString(R.string.refresh_interval)
        setText(intervalLabels[interval])
        tappable(R.drawable.pill_button) {
            menu(this, intervalLabels, interval) {
                interval = it
                setText(intervalLabels[it])
                onInterval(it)
            }
        }
        // After tappable(): setting a background resets the padding to the drawable's own.
        setPadding(context.dp(16), 0, context.dp(12), 0)
    }

    private fun setUpSaveButton() = saveButton.apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.ic_save)
        contentDescription = context.getString(R.string.save_snapshot)
        tappable(R.drawable.icon_button) { onSave() }
    }

    private fun setUpThemeButton() = themeButton.apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.ic_theme)
        contentDescription = context.getString(R.string.theme)
        tappable(R.drawable.icon_button) {
            menu(this, THEME_LABELS, theme.ordinal) {
                theme = ThemeMode.entries[it]
                onTheme(theme)
            }
        }
    }

    private fun menu(anchor: View, labels: List<Int>, checked: Int, onPick: (Int) -> Unit) {
        PopupMenu(context, anchor).apply {
            labels.forEachIndexed { index, label -> menu.add(Menu.NONE, index, index, label).isChecked = index == checked }
            menu.setGroupCheckable(Menu.NONE, true, true)
            setOnMenuItemClickListener { onPick(it.itemId); true }
            show()
        }
    }

    fun bind(state: ConnectionPill, updatedAt: String) {
        pill.setTextIfChanged(state.text)
        updated.setTextIfChanged(updatedAt)
        val connected = state.kind == PillKind.CONNECTED
        pill.setBackgroundResource(if (connected) R.drawable.pill_connected else R.drawable.pill_disconnected)
        pill.setTextColor(context.color(if (connected) R.color.pill_connected_text else R.color.pill_disconnected_text))
    }

    private companion object {
        val THEME_LABELS = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
    }
}
