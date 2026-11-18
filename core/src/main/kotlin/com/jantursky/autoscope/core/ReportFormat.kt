package com.jantursky.autoscope.core

import java.time.Instant

/** Renders a [Snapshot] as plain text or JSON. Pure functions, no Android types. */
object ReportFormat {

    fun text(snapshot: Snapshot, capturedAtMillis: Long, reason: String): String = buildString {
        appendLine("AutoScope report")
        appendLine("Captured: ${Instant.ofEpochMilli(capturedAtMillis)}")
        appendLine("Reason: $reason")
        for (group in Group.entries) {
            val fields = snapshot.fields(group)
            if (fields.isEmpty()) continue
            appendLine()
            appendLine("== ${group.title} ==")
            fields.forEach { appendLine(it.line()) }
        }
    }

    fun json(snapshot: Snapshot, capturedAtMillis: Long, reason: String): String = buildString {
        append("{\"app\":\"AutoScope\",\"capturedAt\":").append(quote(Instant.ofEpochMilli(capturedAtMillis).toString()))
        append(",\"capturedAtMillis\":").append(capturedAtMillis)
        append(",\"reason\":").append(quote(reason))
        append(",\"groups\":{")
        Group.entries.filter { snapshot.fields(it).isNotEmpty() }.forEachIndexed { gi, group ->
            if (gi > 0) append(',')
            append(quote(group.name)).append(":[")
            snapshot.fields(group).forEachIndexed { fi, f ->
                if (fi > 0) append(',')
                append("{\"label\":").append(quote(f.label))
                append(",\"value\":").append(f.value?.let(::quote) ?: "null")
                append(",\"source\":").append(quote(f.source))
                append(",\"status\":").append(quote(f.status.name))
                append(",\"reason\":").append(f.reason?.let(::quote) ?: "null")
                append('}')
            }
            append(']')
        }
        append("}}")
    }

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
