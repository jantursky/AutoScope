package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.rowText
import com.jantursky.autoscope.core.rowTitle

/** The live report as plain text: one "label: value (STATUS)" line per field, grouped. */
fun liveReportText(snapshot: Snapshot): String = buildString {
    for (group in Group.entries) {
        val fields = snapshot.fields(group)
        if (fields.isEmpty()) continue
        if (isNotEmpty()) appendLine()
        appendLine(group.title)
        fields.forEach { appendLine("${it.rowTitle()}: ${it.rowText()}") }
    }
}.trimEnd()
