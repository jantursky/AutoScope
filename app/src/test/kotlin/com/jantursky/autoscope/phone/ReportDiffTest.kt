package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.ReportFormat
import com.jantursky.autoscope.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDiffTest {
    private fun report(vararg groups: Pair<Group, List<Field>>) =
        ReportFormat.text(Snapshot(groups.toMap()), 0L, "test")

    private fun ok(label: String, value: String) = Field.ok(label, value, "src")

    @Test fun parsesGroupsLabelsAndStripsSource() {
        val parsed = parseReportText(report(Group.HOST to listOf(ok("Version", "1.2"))))
        assertEquals(listOf(ParsedField("Host and library", "Version", 0, "1.2 (OK)")), parsed)
    }

    @Test fun keepsColonsInsideValues() {
        val parsed = parseReportText(report(Group.HOST to listOf(Field.unavailable("Speed", "src", "No car: yet"))))
        assertEquals("UNAVAILABLE: No car: yet", parsed.single().value)
    }

    @Test fun repeatedLabelsGetOccurrenceIndex() {
        val parsed = parseReportText(report(Group.LIFECYCLE to listOf(ok("Event", "a"), ok("Event", "b"))))
        assertEquals(listOf(0, 1), parsed.map { it.occurrence })
    }

    @Test fun identicalReportsHaveNoChanges() {
        val text = report(Group.HOST to listOf(ok("Version", "1")))
        assertTrue(diffReports(text, text).isEmpty())
    }

    @Test fun reportsAddedRemovedAndChanged() {
        val older = report(Group.HOST to listOf(ok("A", "1"), ok("Gone", "x")))
        val newer = report(Group.HOST to listOf(ok("A", "2"), ok("New", "y")))
        assertEquals(
            listOf(
                FieldChange("Host and library", "A", "1 (OK)", "2 (OK)"),
                FieldChange("Host and library", "New", null, "y (OK)"),
                FieldChange("Host and library", "Gone", "x (OK)", null),
            ),
            diffReports(older, newer),
        )
    }

    @Test fun groupsFollowEnumOrder() {
        val older = report(Group.PHONE to listOf(ok("P", "1")), Group.HOST to listOf(ok("H", "1")))
        val newer = report(Group.PHONE to listOf(ok("P", "2")), Group.HOST to listOf(ok("H", "2")))
        assertEquals(listOf("Host and library", "Phone"), diffReports(older, newer).map { it.group })
    }

    @Test fun headerLinesAreIgnored() {
        assertTrue(parseReportText("AutoScope report\nCaptured: x\nReason: y\n").isEmpty())
    }
}
