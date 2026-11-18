package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status

/** How a row's status is drawn. [NONE] means no status noise at all (OK rows). */
enum class StatusStyle { NONE, CALM, MUTED, WARNING, INFO }

/** Reason the car side reports before a car session exists; it is expected, so it reads calmly. */
internal const val NO_CAR_SESSION = "No car session"

/** Lifecycle log entries shown on the phone; the log itself keeps more. */
internal const val LIFECYCLE_ROW_LIMIT = 30

fun statusStyle(field: Field): StatusStyle = when (field.status) {
    Status.OK -> StatusStyle.NONE
    Status.UNAVAILABLE -> if (field.reason == NO_CAR_SESSION) StatusStyle.CALM else StatusStyle.MUTED
    Status.PERMISSION_MISSING -> StatusStyle.WARNING
    Status.NOT_SUPPORTED_BY_HOST -> StatusStyle.INFO
}

/** One row of a group card. Rows of the same group are identified by [key] (the label), made unique by position. */
data class RowModel(
    val key: String,
    val label: String,
    val value: String,
    val reason: String?,
    val style: StatusStyle,
)

/** One group card. [issues] counts fields that are not OK and not just waiting for a car session. */
data class GroupModel(
    val group: Group,
    val rows: List<RowModel>,
    val issues: Int,
    val truncatedFrom: Int?,
    val collapsedByDefault: Boolean,
) {
    /** Same group with the same row keys in the same order: values can be updated in place. */
    fun sameShape(other: GroupModel) = group == other.group && rows.map { it.key } == other.rows.map { it.key }
}

/** Groups in enum order, only those with fields. */
fun groupModels(snapshot: Snapshot): List<GroupModel> = Group.entries.mapNotNull { group ->
    // The connection type is already shown once, as the pill in the top bar.
    val fields = snapshot.fields(group).filterNot { group == Group.CONNECTION && it.label == CONNECTION_TYPE_LABEL }
    if (fields.isEmpty()) return@mapNotNull null
    val limit = if (group == Group.LIFECYCLE) LIFECYCLE_ROW_LIMIT else Int.MAX_VALUE
    val shown = fields.take(limit)
    GroupModel(
        group = group,
        rows = shown.mapIndexed { index, field -> field.toRow(index) },
        issues = fields.count { statusStyle(it) !in setOf(StatusStyle.NONE, StatusStyle.CALM) },
        truncatedFrom = fields.size.takeIf { it > shown.size },
        collapsedByDefault = group == Group.LIFECYCLE,
    )
}

private fun Field.toRow(index: Int) = RowModel(
    key = "$index:$label",
    label = label,
    value = value ?: "-",
    reason = reason.takeIf { status != Status.OK },
    style = statusStyle(this),
)

/** Header badge text for a group with problems, for example "3 unavailable"; null when there are none. */
fun issueSummary(issues: Int): String? = if (issues > 0) "$issues unavailable" else null
