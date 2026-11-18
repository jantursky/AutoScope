package com.jantursky.autoscope.car

import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSourceTest {

    private val state = HardwareState()

    private fun read(ports: CarPorts?) = HardwareSource({ ports }, state).read()

    @Test
    fun `every item keeps its labels in every state`() {
        val expected = HardwareFeature.entries.flatMap { it.labels }

        assertEquals(expected, read(null).map { it.label })
        assertEquals(expected, read(fakePorts(apiLevel = 2)).map { it.label })
        assertEquals(expected, read(fakePorts(permissions = FakePermissions(mutableSetOf()))).map { it.label })
        assertEquals(expected, read(fakePorts()).map { it.label })
    }

    @Test
    fun `no session, low API level and missing permission become statuses`() {
        assertAll(read(null), Status.UNAVAILABLE, NO_SESSION)
        assertAll(read(fakePorts(apiLevel = 2)), Status.NOT_SUPPORTED_BY_HOST, "Car App API 3")

        val fields = read(fakePorts(permissions = FakePermissions(mutableSetOf())))
        assertEquals(Status.PERMISSION_MISSING, fields.byLabel("Raw speed").status)
        assertTrue(fields.byLabel("Raw speed").reason!!.contains("CAR_SPEED"))
        assertEquals(Status.PERMISSION_MISSING, fields.byLabel("Car location").status)
        // Model and toll need no permission on Android Auto.
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Make").status)
        assertEquals(Status.UNAVAILABLE, fields.byLabel("Toll card").status)
    }

    @Test
    fun `stored readings and registration failures are reported`() {
        state.store(HardwareFeature.MODEL, HardwareFields.model(model()))
        state.fail(HardwareFeature.SPEED, "SecurityException: denied")

        val fields = read(fakePorts())
        assertEquals("Mazda", fields.byLabel("Make").value)
        assertEquals("SecurityException: denied", fields.byLabel("Raw speed").reason)
        assertEquals("No value from the car yet", fields.byLabel("Odometer").reason)
    }

    @Test
    fun `car value statuses map to field statuses`() {
        val fields = listOf(CarValue.STATUS_SUCCESS, CarValue.STATUS_UNAVAILABLE, CarValue.STATUS_UNIMPLEMENTED, CarValue.STATUS_UNKNOWN)
            .map { status -> HardwareFields.carValue("Speed", "test", CarValue(if (status == CarValue.STATUS_SUCCESS) 1f else null, 0, status)) { "$it" } }

        assertEquals(listOf(Status.OK, Status.UNAVAILABLE, Status.NOT_SUPPORTED_BY_HOST, Status.UNAVAILABLE), fields.map { it.status })
        assertNull(fields[1].value)
        assertTrue(fields[3].reason!!.contains("STATUS_UNKNOWN"))
    }

    @Test
    fun `success without a value is unavailable`() {
        val field = HardwareFields.carValue("Year", "test", CarValue<Int>(null, 0, CarValue.STATUS_SUCCESS)) { "$it" }
        assertEquals(Status.UNAVAILABLE, field.status)
    }

    @Test
    fun `converters produce the declared labels and units`() {
        val speed = Speed.Builder()
            .setRawSpeedMetersPerSecond(CarValue(10f, 0, CarValue.STATUS_SUCCESS))
            .setDisplaySpeedMetersPerSecond(CarValue(null, 0, CarValue.STATUS_UNAVAILABLE))
            .setSpeedDisplayUnit(CarValue(CarUnit.KILOMETERS_PER_HOUR, 0, CarValue.STATUS_SUCCESS))
            .build()
        val mileage = Mileage.Builder()
            .setOdometerMeters(CarValue(12_345f, 0, CarValue.STATUS_SUCCESS))
            .setDistanceDisplayUnit(CarValue(null, 0, CarValue.STATUS_UNIMPLEMENTED))
            .build()

        val speedFields = HardwareFields.speed(speed)
        val mileageFields = HardwareFields.mileage(mileage)

        assertEquals(HardwareFeature.SPEED.labels, speedFields.map { it.label })
        assertEquals(HardwareFeature.MILEAGE.labels, mileageFields.map { it.label })
        assertEquals(HardwareFeature.MODEL.labels, HardwareFields.model(model()).map { it.label })
        assertEquals("10.0 m/s (36.0 km/h)", speedFields[0].value)
        assertEquals("KILOMETERS_PER_HOUR", speedFields[2].value)
        // getOdometerMeters returns kilometers.
        assertEquals("12345.0 km", mileageFields[0].value)
        assertEquals(Status.NOT_SUPPORTED_BY_HOST, mileageFields[1].status)
    }

    private fun model() = Model.Builder()
        .setManufacturer(CarValue("Mazda", 0, CarValue.STATUS_SUCCESS))
        .setName(CarValue("MX-5", 0, CarValue.STATUS_SUCCESS))
        .setYear(CarValue(2024, 0, CarValue.STATUS_SUCCESS))
        .build()
}
