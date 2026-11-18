package com.jantursky.autoscope.core

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A saved report: the JSON and text twins share one [id]. */
data class SavedReport(val id: String, val json: File, val text: File)

/** Writes reports into [dir] (the app's files directory). Local only, nothing leaves the device. */
class ReportStore(private val dir: File, private val clock: Clock = Clock.systemUTC()) {

    fun save(snapshot: Snapshot, reason: String): SavedReport {
        dir.mkdirs()
        val now = clock.millis()
        val id = "autoscope-${STAMP.format(Instant.ofEpochMilli(now))}-${slug(reason)}"
        val json = File(dir, "$id.json").apply { writeText(ReportFormat.json(snapshot, now, reason)) }
        val text = File(dir, "$id.txt").apply { writeText(ReportFormat.text(snapshot, now, reason)) }
        return SavedReport(id, json, text)
    }

    /** Newest first. */
    fun list(): List<SavedReport> =
        dir.listFiles { f -> f.name.startsWith("autoscope-") && f.extension == "json" }.orEmpty()
            .map { SavedReport(it.nameWithoutExtension, it, File(dir, it.nameWithoutExtension + ".txt")) }
            .filter { it.text.exists() }
            .sortedByDescending { it.id }

    private fun slug(reason: String) =
        reason.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "snapshot" }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
