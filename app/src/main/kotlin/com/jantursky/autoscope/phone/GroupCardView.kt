package com.jantursky.autoscope.phone

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.jantursky.autoscope.R
import android.view.animation.DecelerateInterpolator

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
    var isExpanded = expanded
        private set
    private val title = context.label(15f, R.color.text_primary, bold = true).apply { text = model.group.title }
    private val badge = context.label(11f, R.color.text_secondary).apply {
        setBackgroundResource(R.drawable.badge_background)
        setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
    }
    private val chevron = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron)
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val footer = context.label(12f, R.color.text_tertiary).apply {
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
    }
    private var rows = emptyList<RowView>()

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.card_background)
        clipToOutline = true
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = context.dp(8) }
        addView(header())
        addView(body)
        rebuildRows()
        bind(model)
        body.visibility = if (isExpanded) View.VISIBLE else View.GONE
        chevron.rotation = if (isExpanded) 180f else 0f
    }

    private fun header() = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(44)
        setPadding(context.dp(16), 0, context.dp(4), 0)
        addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(badge, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(chevron, LayoutParams(context.dp(40), context.dp(40)))
        tappable(R.drawable.ripple_rect) { setExpanded(!isExpanded) }
    }

    fun setExpanded(value: Boolean) {
        if (value == isExpanded) return
        isExpanded = value
        onExpandedChange(value)
        chevron.animate().rotation(if (value) 180f else 0f).setDuration(EXPAND_MS).setInterpolator(DecelerateInterpolator()).start()
        animateBody(body, value, width)
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
        rows = model.rows.map {
            RowView(context).also { row ->
                body.addView(context.divider())
                body.addView(row)
            }
        }
        if (model.truncatedFrom != null) {
            body.addView(context.divider())
            body.addView(footer)
        }
    }
}
