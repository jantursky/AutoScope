package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.jantursky.autoscope.R

/**
 * Sticky chip row: one scrollable chip per tab and a fixed Save button. [bind] updates chips in place and
 * only rebuilds them when the set of tabs changes.
 */
internal class TabBarView(
    context: Context,
    private val onSelect: (TabId) -> Unit,
    onSave: () -> Unit,
) : LinearLayout(context) {
    private val chips = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(16), 0, context.dp(8), 0)
    }
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(context.dp(24))
        addView(chips, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    private var shown = emptyList<Pair<String, String>>()
    private var views = emptyList<ChipView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(context.color(R.color.background))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(scroll, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(saveButton(onSave), LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            .apply { marginEnd = context.dp(8) })
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(40)))
        addView(context.divider())
    }

    fun bind(tabs: List<TabModel>, selected: TabId) {
        val ids = tabs.map { it.id.key to it.title }
        if (ids != shown) {
            shown = ids
            chips.removeAllViews()
            views = tabs.map { tab ->
                ChipView(context, tab.title) { onSelect(tab.id) }.also {
                    chips.addView(it, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        .apply { marginEnd = context.dp(8) })
                }
            }
        }
        tabs.forEachIndexed { index, tab ->
            val chip = views[index]
            val wasSelected = chip.isSelected
            chip.update(tab.badge, tab.id == selected)
            if (!wasSelected && chip.isSelected) scroll.post { scroll.smoothScrollTo((chip.left - context.dp(16)).coerceAtLeast(0), 0) }
        }
    }

    private fun saveButton(onSave: () -> Unit) = TextView(context).apply {
        setText(R.string.save)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        setTextColor(context.color(R.color.on_accent))
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = context.dp(4)
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_save, 0, 0, 0)
        contentDescription = context.getString(R.string.save_snapshot)
        tappable(R.drawable.pill_primary, onSave)
        // After tappable(): setting a background resets the padding to the drawable's own.
        setPadding(context.dp(12), 0, context.dp(14), 0)
    }

    /** A 32dp pill inside the 40dp row; the badge sits after the title. */
    private class ChipView(context: Context, title: String, onClick: () -> Unit) : LinearLayout(context) {
        private val title = context.label(13f, R.color.text_secondary, bold = true).apply { text = title }
        private val badge = context.label(11f, R.color.status_warning, bold = true).apply { visibility = View.GONE }

        init {
            gravity = Gravity.CENTER_VERTICAL
            addView(this.title)
            addView(badge, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(6) })
            tappable(R.drawable.chip, onClick)
            setPadding(context.dp(14), 0, context.dp(14), 0)
        }

        fun update(count: Int, selected: Boolean) {
            isSelected = selected
            title.setTextColor(context.color(if (selected) R.color.accent else R.color.text_secondary))
            badge.visibility = if (count > 0) View.VISIBLE else View.GONE
            badge.setTextIfChanged(count.toString())
        }
    }
}
