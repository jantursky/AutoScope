package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.paginate
import com.jantursky.autoscope.core.rowText
import com.jantursky.autoscope.core.rowTitle

/** What a row does when tapped. Rows without an action are plain readings. */
enum class RowAction { NEXT_PAGE, MARK, OPEN_LOG, GRANT_PERMISSIONS }

/** A car list row before it becomes a template Row. A Row allows a title and at most two text lines. */
data class UiRow(val title: String, val lines: List<String>, val action: RowAction? = null)

/**
 * The car views. TabTemplate allows at most four tabs (TabsConstraints in 1.7.0), so the six areas share
 * four views: Screen also shows the constraints and Overview links to the log.
 */
enum class CarView(val title: String, val groups: List<Group>) {
    OVERVIEW("Overview", listOf(Group.CONNECTION, Group.HOST, Group.PHONE, Group.PERFORMANCE)),
    SCREEN("Screen", listOf(Group.SCREEN, Group.CONSTRAINTS)),
    TOUCH("Touch", listOf(Group.TOUCH)),
    HARDWARE("Hardware", listOf(Group.HARDWARE)),
    LOG("Log", listOf(Group.LIFECYCLE)),
    ;

    /** The part of [snapshot] this view shows; the view refreshes only when this changes. */
    fun visible(snapshot: Snapshot): List<List<Field>> = groups.map(snapshot::fields)

    companion object {
        val TABS = listOf(OVERVIEW, SCREEN, TOUCH, HARDWARE)
    }
}

data class Page(val rows: List<UiRow>, val index: Int, val count: Int)

object CarRows {
    const val MAX_LINE = 60
    const val MAX_LINES = 2

    /**
     * Wraps [text] at spaces into at most [maxLines] lines of [maxLine] characters; the rest is cut with
     * an ellipsis. Values never spill into extra rows, because a changing row count would turn a
     * refresh into a new template step.
     */
    fun splitText(text: String, maxLine: Int = MAX_LINE, maxLines: Int = MAX_LINES): List<String> {
        val lines = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty() && lines.size < maxLines) {
            if (rest.length <= maxLine) {
                lines += rest
                rest = ""
                break
            }
            val cut = rest.lastIndexOf(' ', maxLine).takeIf { it > 0 } ?: maxLine
            lines += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) {
            val last = lines.removeAt(lines.lastIndex)
            lines += last.take(maxLine - 1).trimEnd() + "…"
        }
        return lines.ifEmpty { listOf("-") }
    }

    fun fieldRow(field: Field) = UiRow(field.rowTitle(), splitText(field.rowText()))

    /** Log rows get stable titles (#1 is the newest) so new entries refresh the page instead of adding a step. */
    fun logRows(entries: List<Field>): List<UiRow> = entries.mapIndexed { i, f ->
        UiRow("#${i + 1}", splitText("${f.label} ${f.value ?: f.rowText()}"))
    }

    /**
     * Page [index] of [rows] for a host list limit of [limit]. Every page but the last ends with a
     * "Next page" row. When [canGoDeeper] is false (screen depth limit) that row only says how much is
     * left, because a further page would be a further screen.
     */
    fun page(rows: List<UiRow>, limit: Int, index: Int, canGoDeeper: Boolean): Page {
        val capacity = limit.coerceAtLeast(2)
        if (rows.size <= capacity) return Page(rows.ifEmpty { listOf(EMPTY) }, 0, 1)
        val pages = paginate(rows, capacity - 1)
        val i = index.coerceIn(0, pages.lastIndex)
        val shown = rows.size - pages.drop(i + 1).sumOf { it.size }
        val tail = when {
            i == pages.lastIndex -> emptyList()
            canGoDeeper -> listOf(UiRow("Next page", listOf("Page ${i + 2} of ${pages.size}"), RowAction.NEXT_PAGE))
            else -> listOf(UiRow("More rows", listOf("${rows.size - shown} more in the saved report (screen depth limit)")))
        }
        return Page(pages[i] + tail, i, pages.size)
    }

    private val EMPTY = UiRow("No data", listOf("-"))
}
