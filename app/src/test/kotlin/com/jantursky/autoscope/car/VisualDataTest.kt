package com.jantursky.autoscope.car

import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDataTest {

    private val clock = FakeClock(now = 0)

    @Test
    fun `history keeps one sample per resolution step, holding the slot time`() {
        val history = SampleHistory(spanMillis = 1_000, resolutionMillis = 250)
        history.add(0, 1f)
        history.add(100, 2f)
        history.add(200, 3f)
        history.add(260, 4f)

        assertEquals(listOf(Sample(0, 3f), Sample(260, 4f)), history.window(300))
    }

    @Test
    fun `history overwrites the oldest slot when full and windows by time`() {
        val history = SampleHistory(spanMillis = 1_000, resolutionMillis = 250)
        (0..9).forEach { history.add(it * 250L, it.toFloat()) }

        val all = history.window(2_250)
        assertEquals(5, all.size)
        assertEquals(listOf(5f, 6f, 7f, 8f, 9f), all.map { it.value })
        assertEquals(listOf(8f, 9f), history.window(3_000).map { it.value })

        history.clear()
        assertEquals(emptyList<Sample>(), history.window(3_000))
    }

    @Test
    fun `samples record only OK speed readings`() {
        val samples = SensorSamples(clock.millis)
        samples.speed(Reading.of(10f))
        samples.speed(Reading.missing(Status.UNAVAILABLE, "No value yet"))
        assertEquals(listOf(Sample(0, 10f)), samples.speedHistory.window(0))
    }

    @Test
    fun `a tile shows a value only for an OK reading and otherwise its reason`() {
        assertEquals(Tile.Shown(5f), VisualModel.tile(Reading.of(5f)))
        assertEquals(Tile.NoData("Missing permission: CAR_SPEED"), VisualModel.tile(Reading.missing<Float>(Status.PERMISSION_MISSING, "Missing permission: CAR_SPEED")))
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.tile(Reading.missing<Float>(Status.UNAVAILABLE, null)))
        assertEquals(Tile.NoData(NO_SESSION), VisualModel.tile(Reading.noSession<Float>()))
    }

    @Test
    fun `the model converts speed to km h and follows the speed reading for the history`() {
        val telemetry = TELEMETRY.copy(gForceY = Reading.missing(Status.NOT_SUPPORTED_BY_HOST, "Not implemented by this car/host"))
        val model = VisualModel.build(telemetry, listOf(Sample(0, 10f)))

        assertEquals(Tile.Shown(90f), model.heading)
        assertEquals(Tile.NoData("Not implemented by this car/host"), model.force)
        assertEquals(Tile.Shown(36f), model.speedKmh)
        assertEquals(Tile.Shown(listOf(Sample(0, 36f))), model.speedHistory)
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.build(TELEMETRY, emptyList()).speedHistory)
        assertEquals(Tile.NoData(NO_SESSION), VisualModel.build(CarTelemetry.EMPTY, listOf(Sample(0, 10f))).speedHistory)
    }

    @Test
    fun `energy prefers fuel, falls back to the battery, else gives the fuel reason`() {
        val missing = Reading.missing<Float>(Status.NOT_SUPPORTED_BY_HOST, "Not implemented by this car/host")
        assertEquals(Tile.Shown(Energy("Fuel", 0.5f)), VisualModel.energy(Reading.of(0.5f), Reading.of(0.9f)))
        assertEquals(Tile.Shown(Energy("Battery", 0.9f)), VisualModel.energy(missing, Reading.of(0.9f)))
        assertEquals(Tile.NoData("Not implemented by this car/host"), VisualModel.energy(missing, Reading.noSession()))
    }

    @Test
    fun `charge port is lit when open or connected and dim without either reading`() {
        assertTrue((VisualModel.chargePort(Reading.of(false), Reading.of(true)) as Tile.Shown).value.lit)
        assertFalse((VisualModel.chargePort(Reading.of(false), Reading.noSession()) as Tile.Shown).value.lit)
        assertEquals(Tile.NoData(NO_SESSION), VisualModel.chargePort(Reading.noSession(), Reading.noSession()))
    }

    @Test
    fun `car lines give values, or reasons dimmed, with missing heading and force last`() {
        val car = VisualModel.build(TELEMETRY, emptyList()).car
        assertEquals(Tile.Shown("Mazda MX-5 2024"), car.name)
        assertEquals(
            listOf(
                CarLine("36 km/h", dim = false),
                CarLine("Fuel 50 %, 412 km", dim = false),
                CarLine("Port open, unplugged", dim = false),
            ),
            car.lines(),
        )

        val empty = VisualModel.build(CarTelemetry.EMPTY, emptyList()).car
        assertEquals(Tile.NoData(NO_SESSION), empty.name)
        assertEquals(
            listOf("Speed", "Energy", "Charge port", "Heading", "G-force").map { CarLine("$it: $NO_SESSION", dim = true) },
            empty.lines(),
        )

        val rangeOnly = VisualModel.build(TELEMETRY.copy(fuelFraction = Reading.noSession(), batteryFraction = Reading.noSession()), emptyList())
        assertEquals(CarLine("Range 412 km", dim = false), rangeOnly.car.lines()[1])
    }

    private companion object {
        val TELEMETRY = CarTelemetry(
            make = Reading.of("Mazda"),
            model = Reading.of("MX-5"),
            year = Reading.of(2024),
            headingDegrees = Reading.of(90f),
            speedMetersPerSecond = Reading.of(10f),
            fuelFraction = Reading.of(0.5f),
            batteryFraction = Reading.noSession(),
            rangeMeters = Reading.of(412_000f),
            chargePortOpen = Reading.of(true),
            chargePortConnected = Reading.of(false),
            gForceX = Reading.of(0.1f),
            gForceY = Reading.of(-0.2f),
        )
    }
}
