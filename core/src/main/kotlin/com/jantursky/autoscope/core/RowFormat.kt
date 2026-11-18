package com.jantursky.autoscope.core

/** Title line of a UI row: the field label. */
fun Field.rowTitle(): String = label

/** Second line of a UI row: "value (STATUS)", with the reason for non-OK states. Kept short on purpose. */
fun Field.rowText(): String = when {
    status == Status.OK -> "${value ?: "-"} (OK)"
    reason != null -> "$status: $reason"
    else -> status.toString()
}

/** "label: value (STATUS)" on one line, used in the text report. */
fun Field.line(): String = "${rowTitle()}: ${rowText()}  [$source]"

/**
 * Splits [fields] into pages of at most [pageSize] rows. A host content limit of zero or less is treated
 * as one row per page so a bad value cannot hide data.
 */
fun <T> paginate(fields: List<T>, pageSize: Int): List<List<T>> =
    fields.chunked(pageSize.coerceAtLeast(1)).ifEmpty { listOf(emptyList()) }
