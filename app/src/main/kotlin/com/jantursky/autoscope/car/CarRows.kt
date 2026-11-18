package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Status
import com.jantursky.autoscope.core.paginate
import com.jantursky.autoscope.core.rowTitle

/** What a row does when tapped. Rows without an action are plain readings. */
sealed interface RowAction {
    data object Refresh : RowAction
    data object NextPage : RowAction
    data object Mark : RowAction
    data object GrantPermissions : RowAction

    /** Pushes the screen of [view]; the rows of the More tab. */
    data class Open(val view: CarView) : RowAction
}

/** A car list row before it becomes a template Row. A Row allows a title and at most two text lines. */
data class UiRow(val title: String, val lines: List<String>, val action: RowAction? = null)

data class Page(val rows: List<UiRow>, val index: Int, val count: Int)

object CarRows {
    const val MAX_LINE = 60
    const val MAX_LINES = 2

    /**
     * Line length for the content pane of a map screen, which is narrower than a list: the DHU cut a pane
     * line after about 25 characters, so wrapping earlier keeps both lines readable.
     */
    const val MAP_PANE_LINE = 28

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

    fun fieldRow(field: Field, maxLine: Int = MAX_LINE) = UiRow(field.rowTitle(), splitText(fieldText(field), maxLine))

    /**
     * Second line of a car row: "value (OK)", or why there is no value with the most telling words first,
     * as the host cuts what does not fit. A short status word leads unless the reason already says the
     * value is missing ("No…", "Not…", "Missing…"); package prefixes of permission names are dropped.
     */
    fun fieldText(field: Field): String {
        if (field.status == Status.OK) return "${field.value ?: "-"} (OK)"
        val word = statusWord(field.status)
        val reason = field.reason?.let(::shortPermissions) ?: return word
        return if (SAYS_MISSING.containsMatchIn(reason)) reason else "$word: $reason"
    }

    fun statusWord(status: Status): String = when (status) {
        Status.OK -> "OK"
        Status.UNAVAILABLE -> "No value"
        Status.PERMISSION_MISSING -> "Not granted"
        Status.NOT_SUPPORTED_BY_HOST -> "Not supported"
    }

    /** "com.google.android.gms.permission.CAR_SPEED" becomes "CAR_SPEED". */
    fun shortPermissions(text: String): String = PERMISSION_NAME.replace(text) { it.groupValues[1] }

    private val SAYS_MISSING = Regex("^(No|Not|Missing)\\b")
    private val PERMISSION_NAME = Regex("\\b(?:[a-z][a-z0-9_]*\\.)+([A-Z][A-Z0-9_]+)\\b")

    /** Log rows get stable titles (#1 is the newest) so new entries refresh the page instead of adding a step. */
    fun logRows(entries: List<Field>): List<UiRow> = entries.mapIndexed { i, f ->
        UiRow("#${i + 1}", splitText("${f.label} ${f.value ?: fieldText(f)}"))
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
            canGoDeeper -> listOf(UiRow("Next page", listOf("Page ${i + 2} of ${pages.size}"), RowAction.NextPage))
            else -> listOf(UiRow("More rows", listOf("${rows.size - shown} more in the saved report (screen depth limit)")))
        }
        return Page(pages[i] + tail, i, pages.size)
    }

    /**
     * [page] with the [REFRESH] row first, within the same [limit]. The refresh row is on every page and
     * never changes, so a refresh keeps the row count and titles.
     */
    fun refreshablePage(rows: List<UiRow>, limit: Int, index: Int, canGoDeeper: Boolean): Page {
        val page = page(rows, limit - 1, index, canGoDeeper)
        return page.copy(rows = listOf(REFRESH) + page.rows)
    }

    /**
     * Title of a pushed list page. The log grows, so its title leaves out the page count: a changed title
     * would turn an explicit refresh into a new template step.
     */
    fun pageTitle(view: CarView, page: Page): String = when {
        view == CarView.LOG -> if (page.index == 0) view.title else "${view.title} ${page.index + 1}"
        page.count == 1 -> view.title
        else -> "${view.title} ${page.index + 1}/${page.count}"
    }

    const val REFRESH_TITLE = "Refresh values"

    val REFRESH = UiRow(REFRESH_TITLE, listOf("Updates timers, counters, sensors and the log"), RowAction.Refresh)

    private val EMPTY = UiRow("No data", listOf("-"))
}
