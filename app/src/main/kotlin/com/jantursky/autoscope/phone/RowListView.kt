package com.jantursky.autoscope.phone

import android.content.Context
import android.widget.LinearLayout

/**
 * Rows separated by dividers, each preceded by one. [bind] rewrites the texts in place and only rebuilds the
 * views when the row keys change, so a live update never resets scrolling or a ripple.
 */
internal class RowListView(context: Context) : LinearLayout(context) {
    private var keys = emptyList<String>()
    private var views = emptyList<RowView>()

    init {
        orientation = VERTICAL
    }

    fun bind(rows: List<RowModel>) {
        val next = rows.map { it.key }
        if (next != keys) {
            keys = next
            removeAllViews()
            views = rows.map { RowView(context).also { row -> addView(context.divider()); addView(row) } }
        }
        views.zip(rows).forEach { (view, row) -> view.bind(row) }
    }
}
