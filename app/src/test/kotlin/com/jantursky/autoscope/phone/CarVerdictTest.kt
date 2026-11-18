package com.jantursky.autoscope.phone

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarVerdictTest {
    private val projection = Field.ok("Connection type", "Projection (Android Auto)", "s")
    private val touchReady = Field.ok("Touch tester", "ready, not opened yet", "s")

    private fun snapshot(
        connection: Field = projection,
        touch: Field? = touchReady,
        hardware: List<Field> = listOf(Field.ok("Make", "X", "s"), Field.unavailable("Odometer", "s", "No value from the car yet")),
    ) = Snapshot(
        mapOf(Group.CONNECTION to listOf(connection), Group.HARDWARE to hardware, Group.TOUCH to listOfNotNull(touch)),
    )

    private fun healthy(): CarTelemetry {
        val none = CarTelemetry.EMPTY
        return none.copy(
            make = Reading.of("Volvo"), headingDegrees = Reading.of(90f), speedMetersPerSecond = Reading.of(10f),
            batteryFraction = Reading.of(0.5f), rangeMeters = Reading.of(100_000f), chargePortOpen = Reading.of(false),
            gForceX = Reading.of(0f),
        )
    }

    @Test fun connectedWithoutACarSessionIsMediaOnly() {
        val hardware = listOf(Field.unavailable("Make", "s", NO_CAR_SESSION), Field.unavailable("Speed", "s", NO_CAR_SESSION))
        val verdict = carVerdict(snapshot(hardware = hardware), CarTelemetry.EMPTY)
        assertEquals("Media only", verdict.word)
        assertEquals(Tone.IDLE, verdict.tone)
    }

    @Test fun noSessionIsCalmGreyWithoutRows() {
        val verdict = carVerdict(Snapshot.EMPTY, CarTelemetry.EMPTY)
        assertEquals("No car session", verdict.word)
        assertEquals(Tone.IDLE, verdict.tone)
        assertTrue(verdict.missing.isEmpty())
        assertTrue(verdictRows(verdict).isEmpty())
    }

    @Test fun allValuesPresentIsReady() {
        val verdict = carVerdict(snapshot(), healthy())
        assertEquals("Ready", verdict.word)
        assertEquals(Tone.GOOD, verdict.tone)
        assertEquals(LinkKind.PROJECTION, verdict.link)
        assertTrue(verdict.missing.isEmpty())
        assertEquals("ready, not opened yet", verdict.touch)
    }

    @Test fun countsOkHardwareFieldsOutOfTotal() {
        val verdict = carVerdict(snapshot(), healthy())
        assertEquals(1, verdict.hardwareOk)
        assertEquals(2, verdict.hardwareTotal)
        assertEquals("1 of 2 OK", verdictRows(verdict).first { it.key == "values" }.value)
    }

    @Test fun missingPermissionIsPartlyWorkingAndNamesTheValue() {
        val t = healthy().copy(speedMetersPerSecond = Reading.missing(Status.PERMISSION_MISSING, "Missing permission: CAR_SPEED"))
        val verdict = carVerdict(snapshot(), t)
        assertEquals("Partly working", verdict.word)
        assertEquals(Tone.WARNING, verdict.tone)
        assertEquals(listOf(MissingValue("Speed", "permission missing", StatusStyle.WARNING)), verdict.missing)
    }

    @Test fun valuesTheCarDoesNotReportStayReadyButAreListed() {
        val unreported = Reading.missing<Boolean>(Status.UNAVAILABLE, "no listener data")
        val t = healthy().copy(chargePortOpen = unreported, chargePortConnected = unreported)
        val verdict = carVerdict(snapshot(), t)
        assertEquals("Ready", verdict.word)
        assertEquals(listOf(MissingValue("EV charge port", "not reported", StatusStyle.MUTED)), verdict.missing)
    }

    @Test fun unsupportedByHostIsInformationNotAProblem() {
        val t = healthy().copy(rangeMeters = Reading.missing(Status.NOT_SUPPORTED_BY_HOST, "API level 3"))
        val verdict = carVerdict(snapshot(), t)
        assertEquals(Tone.GOOD, verdict.tone)
        assertEquals("not supported by host", verdict.missing.single().reason)
    }

    @Test fun touchTesterWithoutSurfaceIsAProblem() {
        val touch = Field.unavailable("Touch tester", "s", "Map screen opened, but the host provided no surface within 5 s")
        val verdict = carVerdict(snapshot(touch = touch), healthy())
        assertEquals(Tone.WARNING, verdict.tone)
        assertEquals("not available", verdict.touch)
    }

    @Test fun touchTesterNotSupportedIsNotAProblem() {
        val touch = Field.notSupported("Touch tester", "s", "needs level 7")
        val verdict = carVerdict(snapshot(touch = touch), healthy())
        assertEquals(Tone.GOOD, verdict.tone)
        assertEquals("not supported by host", verdict.touch)
    }

    @Test fun missingTouchFieldReadsAsNotReported() {
        assertEquals("not reported", carVerdict(snapshot(touch = null), healthy()).touch)
    }

    @Test fun rowsListLinkCountTouchThenMissing() {
        val t = healthy().copy(gForceX = Reading.noSession())
        val rows = verdictRows(carVerdict(snapshot(), t))
        assertEquals(listOf("link", "values", "touch", "missing:G-force"), rows.map { it.key })
        assertEquals("Android Auto (projection)", rows.first().value)
        assertEquals("no car session", rows.last().value)
    }

    @Test fun energyFallsBackToFuelWhenThereIsNoBattery() {
        val t = healthy().copy(batteryFraction = Reading.noSession(), fuelFraction = Reading.of(0.4f))
        assertTrue(carVerdict(snapshot(), t).missing.none { it.label == "Energy level" })
    }
}
