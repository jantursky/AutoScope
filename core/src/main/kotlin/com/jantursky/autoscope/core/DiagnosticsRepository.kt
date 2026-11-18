package com.jantursky.autoscope.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collects all [FieldSource]s into one immutable [Snapshot].
 *
 * A source that throws never takes the app down: its group shows a single UNAVAILABLE field instead.
 * [StateFlow] only emits when the snapshot differs from the last one, which is what lets the car UI
 * refresh its template only when data really changed.
 */
class DiagnosticsRepository(sources: List<FieldSource>) {

    private val sources = sources.toList()
    private val state = MutableStateFlow(Snapshot.EMPTY)

    val snapshot: StateFlow<Snapshot> = state.asStateFlow()

    /** Reads every source now and publishes the result. Call from a timer. */
    fun refresh(): Snapshot {
        val next = Snapshot(
            sources.groupBy { it.group }
                .toSortedMap()
                .mapValues { (_, groupSources) -> groupSources.flatMap(::readSafely) },
        )
        state.value = next
        return next
    }

    private fun readSafely(source: FieldSource): List<Field> =
        try {
            source.read()
        } catch (e: Exception) {
            listOf(
                Field.unavailable(
                    label = source::class.simpleName ?: "source",
                    source = "FieldSource.read",
                    reason = "${e::class.simpleName}: ${e.message}",
                ),
            )
        }
}
