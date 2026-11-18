package com.jantursky.autoscope.phone

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPictureTest {
    private fun picture(t: CarTelemetry = CarTelemetry.EMPTY, s: Snapshot = Snapshot.EMPTY) = carPicture(t, s)
    private fun connection(value: String) =
        Snapshot(mapOf(Group.CONNECTION to listOf(Field.ok("Connection type", value, "s"))))

    private fun CarPicture.row(label: String) = rows.first { it.label == label }

    @Test fun noSessionIsCalmAndDim() {
        val p = picture()
        assertEquals("No car session", p.title)
        assertEquals("Connect to Android Auto to see live car data", p.subtitle)
        assertNull(p.headingDegrees)
        assertNull(p.energy)
        assertEquals(ChargePort.UNKNOWN, p.chargePort)
        assertEquals(LinkKind.NONE, p.link)
        assertEquals(9, p.rows.size)
        assertEquals(setOf(StatusStyle.CALM), p.rows.map { it.style }.toSet())
        assertEquals("No car session", p.row("Speed").reason)
    }

    @Test fun captionJoinsReportedParts() {
        val t = CarTelemetry.EMPTY.copy(make = Reading.of("Polestar"), model = Reading.of("2"), year = Reading.of(2024))
        assertEquals("Polestar 2 2024", picture(t).title)
        assertNull(picture(t).subtitle)
        assertEquals("Polestar", picture(t.copy(model = Reading.missing(Status.NOT_SUPPORTED_BY_HOST, "x"), year = Reading.missing(Status.UNAVAILABLE, "y"))).title)
    }

    @Test fun reportedSessionWithoutNameSaysSo() {
        val t = CarTelemetry.EMPTY.copy(make = Reading.missing(Status.PERMISSION_MISSING, "Missing permission: p"))
        assertEquals("Car model not reported", picture(t).title)
        assertEquals(StatusStyle.WARNING, picture(t).row("Make").style)
    }

    @Test fun speedConvertsMetersPerSecondToKmh() {
        val t = CarTelemetry.EMPTY.copy(speedMetersPerSecond = Reading.of(25f))
        assertEquals(90, picture(t).speedKmh)
        assertEquals("90 km/h", picture(t).row("Speed").value)
    }

    @Test fun batteryWinsOverFuelAndFractionIsClamped() {
        val both = CarTelemetry.EMPTY.copy(batteryFraction = Reading.of(0.64f), fuelFraction = Reading.of(0.2f))
        assertEquals(Energy(EnergyKind.BATTERY, 0.64f), picture(both).energy)
        assertEquals("Battery 64%", picture(both).row("Energy").value)
        val fuel = CarTelemetry.EMPTY.copy(fuelFraction = Reading.of(1.4f))
        assertEquals(Energy(EnergyKind.FUEL, 1f), picture(fuel).energy)
    }

    @Test fun rangeIsShownInKilometres() {
        val t = CarTelemetry.EMPTY.copy(rangeMeters = Reading.of(320_400f))
        assertEquals(320, picture(t).rangeKm)
        assertEquals("320 km", picture(t).row("Range").value)
    }

    @Test fun chargePortStates() {
        val no = Reading.of(false)
        val yes = Reading.of(true)
        val base = CarTelemetry.EMPTY
        assertEquals(ChargePort.CLOSED, picture(base.copy(chargePortOpen = no, chargePortConnected = no)).chargePort)
        assertEquals(ChargePort.OPEN, picture(base.copy(chargePortOpen = yes, chargePortConnected = no)).chargePort)
        assertEquals(ChargePort.CONNECTED, picture(base.copy(chargePortOpen = yes, chargePortConnected = yes)).chargePort)
        assertEquals(ChargePort.OPEN, picture(base.copy(chargePortOpen = yes)).chargePort)
        assertEquals("Connected", picture(base.copy(chargePortConnected = yes)).row("Charge port").value)
        assertEquals("Not connected", picture(base.copy(chargePortConnected = no)).row("Charge port").value)
    }

    @Test fun gForceIsClampedToOneG() {
        val strong = CarTelemetry.EMPTY.copy(gForceX = Reading.of(3f), gForceY = Reading.of(4f))
        assertEquals(GForce(0.6f, 0.8f), picture(strong).gForce)
        val mild = CarTelemetry.EMPTY.copy(gForceX = Reading.of(0.3f), gForceY = Reading.of(-0.2f))
        assertEquals(GForce(0.3f, -0.2f), picture(mild).gForce)
        assertEquals("x 0.30, y -0.20 g", picture(mild).row("G-force").value)
        assertNull(picture(mild.copy(gForceY = Reading.noSession())).gForce)
    }

    @Test fun headingRowNamesTheCompassDirection() {
        val t = CarTelemetry.EMPTY.copy(headingDegrees = Reading.of(359.6f))
        assertEquals("0° N", picture(t).row("Heading").value)
        assertEquals("90° E", picture(t.copy(headingDegrees = Reading.of(90f))).row("Heading").value)
        assertEquals(359.6f, picture(t).headingDegrees!!, 0.001f)
    }

    @Test fun linkColorFollowsConnectionType() {
        assertEquals(LinkKind.PROJECTION, linkKind(connection("Projection (Android Auto)")))
        assertEquals(LinkKind.NATIVE, linkKind(connection("Native (Android Automotive OS)")))
        assertEquals(LinkKind.NONE, linkKind(connection("Not connected")))
        assertEquals(LinkKind.NONE, linkKind(connection("Unknown (7)")))
        assertEquals(LinkKind.NONE, linkKind(Snapshot.EMPTY))
    }

    @Test fun shortestTurnTakesTheNearArc() {
        assertEquals(20f, shortestTurn(350f, 10f), 0.001f)
        assertEquals(-20f, shortestTurn(10f, 350f), 0.001f)
        assertEquals(90f, shortestTurn(0f, 90f), 0.001f)
        assertEquals(-30f, shortestTurn(750f, 360f), 0.001f)
    }
}
