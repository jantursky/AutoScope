package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/**
 * Collapsible card for one diagnostics group. [bind] rewrites row texts in place and only rebuilds the
 * rows when the set of labels changes.
 */
internal class GroupCardView(
    context: Context,
    private var model: GroupModel,
    expanded: Boolean,
    private val onExpandedChange: (Boolean) -> Unit,
) : LinearLayout(context) {
    val group = model.group
    private var expanded = expanded
    private val title = context.label(16f, R.color.text_primary, bold = true).apply { text = model.group.title }
    private val badge = context.label(12f, R.color.text_secondary).apply {
        setBackgroundResource(R.drawable.badge_background)
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
    }
    private val chevron = ImageView(context).apply { setImageResource(R.drawable.ic_chevron) }
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val footer = context.label(12f, R.color.text_tertiary).apply {
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
    }
    private var rows = emptyList<RowView>()

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.card_background)
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = context.dp(16) }
        addView(header())
        addView(body)
        rebuildRows()
        bind(model)
        applyExpanded()
    }

    private fun header() = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(56)
        setPadding(context.dp(16), 0, context.dp(8), 0)
        isClickable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(badge, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(chevron, LayoutParams(context.dp(40), context.dp(40)))
        setOnClickListener {
            expanded = !expanded
            onExpandedChange(expanded)
            applyExpanded()
        }
    }

    fun bind(next: GroupModel) {
        val reshaped = !model.sameShape(next) || (model.truncatedFrom == null) != (next.truncatedFrom == null)
        model = next
        if (reshaped) rebuildRows()
        rows.zip(next.rows).forEach { (view, row) -> view.bind(row) }
        val summary = issueSummary(next.issues)
        badge.visibility = if (summary == null) View.GONE else View.VISIBLE
        badge.setTextIfChanged(summary.orEmpty())
        next.truncatedFrom?.let { footer.setTextIfChanged(context.getString(R.string.showing_latest, next.rows.size, it)) }
    }

    private fun rebuildRows() {
        body.removeAllViews()
        rows = model.rows.map { _ ->
            RowView(context).also {
                body.addView(context.divider())
                body.addView(it)
            }
        }
        if (model.truncatedFrom != null) body.addView(footer)
    }

    private fun applyExpanded() {
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.rotation = if (expanded) 180f else 0f
        chevron.contentDescription = context.getString(if (expanded) R.string.collapse else R.string.expand)
    }
}
