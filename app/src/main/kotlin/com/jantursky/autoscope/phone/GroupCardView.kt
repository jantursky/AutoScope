package com.jantursky.autoscope.phone

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

/**
 * Card with the rows of one diagnostics group. [bind] rewrites row texts in place and only rebuilds the
 * rows when the set of labels changes.
 */
internal class GroupCardView(context: Context, private var model: GroupModel) : LinearLayout(context) {
    val group = model.group
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
        rebuildRows()
        bind(model)
    }

    fun bind(next: GroupModel) {
        val reshaped = !model.sameShape(next) || (model.truncatedFrom == null) != (next.truncatedFrom == null)
        model = next
        if (reshaped) rebuildRows()
        rows.zip(next.rows).forEach { (view, row) -> view.bind(row) }
        next.truncatedFrom?.let { footer.setTextIfChanged(context.getString(R.string.showing_latest, next.rows.size, it)) }
    }

    private fun rebuildRows() {
        removeAllViews()
        rows = model.rows.mapIndexed { index, _ ->
            RowView(context).also { row ->
                if (index > 0) addView(context.divider())
                addView(row)
            }
        }
        if (model.truncatedFrom != null) {
            addView(context.divider())
            addView(footer)
        }
    }
}
