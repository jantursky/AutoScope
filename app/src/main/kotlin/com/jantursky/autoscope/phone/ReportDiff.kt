package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group

/** One line of a saved text report. [occurrence] tells apart lines that repeat a label inside a group. */
internal data class ParsedField(val group: String, val label: String, val occurrence: Int, val value: String)

/** A field that differs between two reports. [old] is null when added, [new] is null when removed. */
internal data class FieldChange(val group: String, val label: String, val old: String?, val new: String?)

private val HEADING = Regex("""== (.+) ==""")
private const val SOURCE_MARK = "  ["

/** Reads the text written by ReportFormat.text: "== Group ==" headings, then "label: value  [source]" lines. */
internal fun parseReportText(text: String): List<ParsedField> {
    val result = mutableListOf<ParsedField>()
    val seen = mutableMapOf<Pair<String, String>, Int>()
    var group: String? = null
    for (line in text.lineSequence()) {
        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            group = heading.groupValues[1]
            continue
        }
        val current = group ?: continue
        val body = line.substringBeforeLast(SOURCE_MARK, line)
        val split = body.indexOf(": ")
        if (split < 0) continue
        val label = body.substring(0, split)
        val occurrence = seen.merge(current to label, 1, Int::plus)!! - 1
        result += ParsedField(current, label, occurrence, body.substring(split + 2))
    }
    return result
}

/** Added, removed and changed fields, groups in enum order, fields in report order (removed ones last). */
internal fun diffReports(older: String, newer: String): List<FieldChange> {
    val before = parseReportText(older)
    val after = parseReportText(newer)
    fun ParsedField.key() = Triple(group, label, occurrence)
    val oldByKey = before.associateBy { it.key() }
    val newKeys = after.map { it.key() }.toSet()
    val changes = after.mapNotNull { f ->
        val old = oldByKey[f.key()]
        if (old?.value == f.value) null else FieldChange(f.group, f.label, old?.value, f.value)
    } + before.filter { it.key() !in newKeys }.map { FieldChange(it.group, it.label, it.value, null) }
    val order = Group.entries.map { it.title }
    return changes.sortedBy { order.indexOf(it.group).let { i -> if (i < 0) order.size else i } }
}
