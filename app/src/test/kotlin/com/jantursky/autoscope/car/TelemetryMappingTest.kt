package com.jantursky.autoscope.car

import androidx.annotation.OptIn
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.Reading
import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryMappingTest {

    private val ready = TelemetryMapping.initial(8, FakePermissions())

    private fun <T> success(value: T) = CarValue(value, 0, CarValue.STATUS_SUCCESS)

    private fun <T> status(status: Int) = CarValue<T>(null, 0, status)

    private fun speed(value: CarValue<Float>) =
        TelemetryMapping.withSpeed(ready, Speed.Builder().setRawSpeedMetersPerSecond(value).build()).speedMetersPerSecond

    @Test
    fun `car value statuses become reading statuses with short reasons`() {
        assertEquals(Reading.of(12.5f), speed(success(12.5f)))
        assertEquals(Reading.missing<Float>(Status.UNAVAILABLE, "No value yet"), speed(status(CarValue.STATUS_UNAVAILABLE)))
        assertEquals(
            Reading.missing<Float>(Status.NOT_SUPPORTED_BY_HOST, "Not implemented by this car/host"),
            speed(status(CarValue.STATUS_UNIMPLEMENTED)),
        )
        assertEquals(Status.UNAVAILABLE, speed(status(CarValue.STATUS_UNKNOWN)).status)
        assertEquals(Status.UNAVAILABLE, speed(status(CarValue.STATUS_SUCCESS)).status)
    }

    @Test
    fun `NaN is a missing value, not a number to draw`() {
        assertEquals(Reading.missing<Float>(Status.UNAVAILABLE, TelemetryMapping.NOT_REPORTED), speed(success(Float.NaN)))
        val compass = TelemetryMapping.withCompass(ready, Compass(success(listOf(Float.NaN, 0f, 0f))))
        assertEquals(TelemetryMapping.NOT_REPORTED, compass.headingDegrees.reason)
    }

    @Test
    fun `a fresh registration explains blocked items and waits for the others`() {
        val noPermissions = TelemetryMapping.initial(8, FakePermissions(mutableSetOf()))
        assertEquals(Status.PERMISSION_MISSING, noPermissions.speedMetersPerSecond.status)
        assertEquals("Missing permission: CAR_SPEED", noPermissions.speedMetersPerSecond.reason)
        assertEquals(Status.PERMISSION_MISSING, noPermissions.fuelFraction.status)
        assertEquals(Reading.missing<Float>(Status.UNAVAILABLE, NO_VALUE_YET), noPermissions.headingDegrees)

        val oldHost = TelemetryMapping.initial(2, FakePermissions())
        listOf(oldHost.make, oldHost.headingDegrees, oldHost.chargePortOpen, oldHost.gForceY).forEach {
            assertEquals(Status.NOT_SUPPORTED_BY_HOST, it.status)
            assertTrue(it.reason!!.contains("Car App API 3"))
        }
    }

    @Test
    fun `a failed registration marks only that item`() {
        val failed = TelemetryMapping.withMissing(ready, HardwareFeature.ACCELEROMETER, NoValue(Status.UNAVAILABLE, "boom"))
        assertEquals("boom", failed.gForceX.reason)
        assertEquals("boom", failed.gForceY.reason)
        assertEquals(ready.headingDegrees, failed.headingDegrees)
        assertEquals(ready, TelemetryMapping.withMissing(ready, HardwareFeature.TOLL, NoValue(Status.UNAVAILABLE, "x")))
    }

    @Test
    fun `compass azimuth is degrees clockwise from north in 0 until 360`() {
        fun heading(azimuth: Float) =
            TelemetryMapping.withCompass(ready, Compass(success(listOf(azimuth, 5f, 0f)))).headingDegrees.value

        assertEquals(90f, heading(90f))
        assertEquals(0f, heading(360f))
        assertEquals(350f, heading(-10f))
        assertEquals(Status.UNAVAILABLE, TelemetryMapping.withCompass(ready, Compass(success(emptyList()))).headingDegrees.status)
    }

    @Test
    fun `accelerometer forces become g`() {
        val t = TelemetryMapping.withAccelerometer(ready, Accelerometer(success(listOf(TelemetryMapping.STANDARD_GRAVITY, -4.903325f, 9.8f))))
        assertEquals(1f, t.gForceX.value!!, 1e-4f)
        assertEquals(-0.5f, t.gForceY.value!!, 1e-4f)

        val oneAxis = TelemetryMapping.withAccelerometer(ready, Accelerometer(success(listOf(1f))))
        assertTrue(oneAxis.gForceX.ok)
        assertEquals(Status.UNAVAILABLE, oneAxis.gForceY.status)
    }

    @Test
    fun `energy percentages become fractions and range stays in metres`() {
        val level = EnergyLevel.Builder()
            .setFuelPercent(success(42f))
            .setBatteryPercent(success(120f))
            .setRangeRemainingMeters(success(350_000f))
            .build()
        val t = TelemetryMapping.withEnergyLevel(ready, level)

        assertEquals(0.42f, t.fuelFraction.value!!, 1e-6f)
        assertEquals(Status.UNAVAILABLE, t.batteryFraction.status)
        assertTrue(t.batteryFraction.reason!!.startsWith("Out of range"))
        assertEquals(Reading.of(350_000f), t.rangeMeters)

        val empty = TelemetryMapping.withEnergyLevel(ready, EnergyLevel.Builder().build())
        assertEquals(Status.UNAVAILABLE, empty.fuelFraction.status)
        assertEquals(Status.UNAVAILABLE, empty.rangeMeters.status)
    }

    @OptIn(markerClass = [ExperimentalCarApi::class])
    @Test
    fun `charge port flags and model values pass through`() {
        val ev = EvStatus.Builder().setEvChargePortOpen(success(true)).build()
        val t = TelemetryMapping.withEvStatus(ready, ev)
        assertEquals(Reading.of(true), t.chargePortOpen)
        assertEquals(Status.UNAVAILABLE, t.chargePortConnected.status)

        val model = Model.Builder()
            .setManufacturer(success("Mazda"))
            .setName(status(CarValue.STATUS_UNIMPLEMENTED))
            .setYear(success(2024))
            .build()
        val m = TelemetryMapping.withModel(ready, model)
        assertEquals(Reading.of("Mazda"), m.make)
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, m.model.status)
        assertEquals(Reading.of(2024), m.year)
    }

    @Test
    fun `sources start empty and return to empty on unbind`() {
        val sources = CarSources(EventLog(), memory = { MemoryReading(0, 0, 0, null) }, clock = { 0L })
        assertEquals(CarTelemetry.EMPTY, sources.telemetry.value)
        sources.telemetryState.value = ready
        sources.unbind()
        assertEquals(CarTelemetry.EMPTY, sources.telemetry.value)
    }
}
