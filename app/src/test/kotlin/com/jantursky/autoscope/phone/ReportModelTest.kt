package com.jantursky.autoscope.phone

import android.content.res.Configuration
import com.jantursky.autoscope.ThemeMode
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportModelTest {
    private fun snap(vararg pairs: Pair<Group, List<Field>>) = Snapshot(mapOf(*pairs))

    @Test fun statusStyles() {
        assertEquals(StatusStyle.NONE, statusStyle(Field.ok("a", 1, "s")))
        assertEquals(StatusStyle.MUTED, statusStyle(Field.unavailable("a", "s", "nope")))
        assertEquals(StatusStyle.CALM, statusStyle(Field.unavailable("a", "s", NO_CAR_SESSION)))
        assertEquals(StatusStyle.WARNING, statusStyle(Field.permissionMissing("a", "s", "P")))
        assertEquals(StatusStyle.INFO, statusStyle(Field.notSupported("a", "s", "old host")))
    }

    @Test fun groupsInEnumOrderWithoutEmptyOnes() {
        val models = groupModels(
            snap(
                Group.PHONE to listOf(Field.ok("a", 1, "s")),
                Group.CONNECTION to listOf(Field.ok("b", 2, "s")),
                Group.HOST to emptyList(),
            ),
        )
        assertEquals(listOf(Group.CONNECTION, Group.PHONE), models.map { it.group })
    }

    @Test fun issuesIgnoreOkAndNoSession() {
        val model = groupModels(
            snap(
                Group.HARDWARE to listOf(
                    Field.ok("a", 1, "s"),
                    Field.unavailable("b", "s", NO_CAR_SESSION),
                    Field.unavailable("c", "s", "broken"),
                    Field.permissionMissing("d", "s", "P"),
                ),
            ),
        ).single()
        assertEquals(2, model.issues)
        assertEquals("2 unavailable", issueSummary(model.issues))
        assertNull(issueSummary(0))
        assertEquals("broken", model.rows[2].reason)
        assertNull(model.rows[0].reason)
        assertEquals("-", model.rows[1].value)
    }

    @Test fun lifecycleIsCollapsedAndBounded() {
        val log = (1..100).map { Field.ok("t$it", "m", "s") }
        val model = groupModels(snap(Group.LIFECYCLE to log, Group.PHONE to listOf(Field.ok("a", 1, "s")))).first()
        assertTrue(model.collapsedByDefault)
        assertEquals(LIFECYCLE_ROW_LIMIT, model.rows.size)
        assertEquals(100, model.truncatedFrom)
        assertFalse(groupModels(snap(Group.PHONE to listOf(Field.ok("a", 1, "s")))).single().collapsedByDefault)
    }

    @Test fun sameShapeIgnoresValuesOnly() {
        val a = groupModels(snap(Group.PHONE to listOf(Field.ok("a", 1, "s")))).single()
        val b = groupModels(snap(Group.PHONE to listOf(Field.ok("a", 2, "s")))).single()
        val c = groupModels(snap(Group.PHONE to listOf(Field.ok("x", 2, "s")))).single()
        assertTrue(a.sameShape(b))
        assertFalse(a.sameShape(c))
    }

    @Test fun pill() {
        fun pill(value: Field?) = connectionPill(snap(Group.CONNECTION to listOfNotNull(value)))
        assertEquals(ConnectionPill("Projection", PillKind.CONNECTED), pill(Field.ok("Connection type", "Projection (Android Auto)", "s")))
        assertEquals(ConnectionPill("Native", PillKind.CONNECTED), pill(Field.ok("Connection type", "Native (Android Automotive OS)", "s")))
        assertEquals(ConnectionPill("Not connected", PillKind.DISCONNECTED), pill(Field.ok("Connection type", "Not connected", "s")))
        assertEquals(ConnectionPill("Not connected", PillKind.DISCONNECTED), pill(null))
        assertEquals(ConnectionPill("Not connected", PillKind.DISCONNECTED), pill(Field.unavailable("Connection type", "s", "r")))
    }

    @Test fun reportLabels() {
        val id = "autoscope-20261118-143205-123-phone-button"
        assertEquals(Instant.parse("2026-11-18T14:32:05.123Z"), reportInstant(id))
        assertNull(reportInstant("whatever"))
        val label = savedReportLabel(id, Instant.parse("2026-11-18T14:37:30Z"), ZoneId.of("UTC"), Locale.ENGLISH)
        assertEquals("Phone button", label.title)
        assertEquals("18 Nov, 14:32:05  ·  5 min ago", label.whenText)
        assertEquals(SavedReportLabel("whatever", ""), savedReportLabel("whatever", Instant.EPOCH, ZoneId.of("UTC"), Locale.ENGLISH))
    }

    @Test fun connectionReasonsBecomeEvents() {
        fun title(slug: String) = reportTitle("autoscope-20261118-143205-123-$slug")
        assertEquals("Disconnected", title("connection-not-connected"))
        assertEquals("Connected via Android Auto", title("connection-projection-android-au"))
        assertEquals("Connected natively", title("connection-native-android-automo"))
        assertEquals("Mark this moment", title("mark-this-moment"))
    }

    @Test fun connectionTypeRowIsOnlyInThePill() {
        val model = groupModels(
            snap(Group.CONNECTION to listOf(Field.ok("Connection type", "Not connected", "s"), Field.ok("Reconnects", 0, "s"))),
        ).single()
        assertEquals(listOf("Reconnects"), model.rows.map { it.label })
    }

    @Test fun themeModeForcesNightBits() {
        val dayWithFlags = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        assertEquals(dayWithFlags, uiModeFor(ThemeMode.SYSTEM, dayWithFlags))
        assertEquals(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL, uiModeFor(ThemeMode.DARK, dayWithFlags))
        val night = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR
        assertEquals(Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_CAR, uiModeFor(ThemeMode.LIGHT, night))
    }

    @Test fun relativeTimes() {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        assertEquals("just now", relativeTime(now.minusSeconds(30), now))
        assertEquals("59 min ago", relativeTime(now.minusSeconds(59 * 60), now))
        assertEquals("3 h ago", relativeTime(now.minusSeconds(3 * 3600 + 5), now))
        assertEquals("2 d ago", relativeTime(now.minusSeconds(2 * 86400), now))
        assertEquals("just now", relativeTime(now.plusSeconds(100), now))
    }
}
