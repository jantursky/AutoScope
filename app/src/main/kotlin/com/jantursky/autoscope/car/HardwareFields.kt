package com.jantursky.autoscope.car

import androidx.annotation.OptIn
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import com.jantursky.autoscope.core.Field
import java.util.Locale

/** Turns CarHardwareManager results into fields. Pure; the labels match [HardwareFeature.labels]. */
object HardwareFields {

    /** Maps a CarValue status to a field status; only STATUS_SUCCESS with a value is OK. */
    fun <T> carValue(label: String, source: String, value: CarValue<T>, format: (T) -> String): Field =
        when (value.status) {
            CarValue.STATUS_SUCCESS -> value.value?.let { Field.ok(label, format(it), source) }
                ?: Field.unavailable(label, source, "CarValue STATUS_SUCCESS without a value")
            CarValue.STATUS_UNAVAILABLE -> Field.unavailable(label, source, "CarValue STATUS_UNAVAILABLE")
            CarValue.STATUS_UNIMPLEMENTED ->
                Field.notSupported(label, source, "CarValue STATUS_UNIMPLEMENTED: not implemented by the car or host")
            else -> Field.unavailable(label, source, "CarValue STATUS_UNKNOWN (${value.status})")
        }

    fun model(model: Model): List<Field> {
        val s = HardwareFeature.MODEL.source
        return listOf(
            carValue("Make", s, model.manufacturer) { it },
            carValue("Model", s, model.name) { it },
            carValue("Year", s, model.year) { it.toString() },
        )
    }

    fun energyProfile(profile: EnergyProfile): List<Field> {
        val s = HardwareFeature.ENERGY_PROFILE.source
        return listOf(
            carValue("Fuel types", s, profile.fuelTypes) { list -> list.joinToString().ifEmpty { "none" } },
            carValue("EV connector types", s, profile.evConnectorTypes) { list -> list.joinToString().ifEmpty { "none" } },
        )
    }

    fun speed(speed: Speed): List<Field> {
        val s = HardwareFeature.SPEED.source
        return listOf(
            carValue("Raw speed", s, speed.rawSpeedMetersPerSecond, ::metersPerSecond),
            carValue("Display speed", s, speed.displaySpeedMetersPerSecond, ::metersPerSecond),
            carValue("Speed display unit", s, speed.speedDisplayUnit, ::unit),
        )
    }

    fun mileage(mileage: Mileage): List<Field> {
        val s = HardwareFeature.MILEAGE.source
        return listOf(
            // getOdometerMeters returns kilometers despite its name (Mileage reference documentation).
            carValue("Odometer", "$s, getOdometerMeters (kilometers)", mileage.odometerMeters) { "${number(it)} km" },
            carValue("Odometer display unit", s, mileage.distanceDisplayUnit, ::unit),
        )
    }

    fun energyLevel(level: EnergyLevel): List<Field> {
        val s = HardwareFeature.ENERGY_LEVEL.source
        return listOf(
            carValue("Battery level", s, level.batteryPercent) { "${number(it)} %" },
            carValue("Fuel level", s, level.fuelPercent) { "${number(it)} %" },
            carValue("Energy low", s, level.energyIsLow) { it.toString() },
            carValue("Range remaining", s, level.rangeRemainingMeters) { "${number(it / 1000f)} km" },
            carValue("Range display unit", s, level.distanceDisplayUnit, ::unit),
        )
    }

    fun toll(card: TollCard): List<Field> =
        listOf(carValue("Toll card", HardwareFeature.TOLL.source, card.cardState, ::tollState))

    // EvStatus is @ExperimentalCarApi; the opt-in is limited to this function and its registration.
    @OptIn(markerClass = [ExperimentalCarApi::class])
    fun evStatus(status: EvStatus): List<Field> {
        val s = HardwareFeature.EV_STATUS.source
        return listOf(
            carValue("Charge port open", s, status.evChargePortOpen) { it.toString() },
            carValue("Charge port connected", s, status.evChargePortConnected) { it.toString() },
        )
    }

    fun accelerometer(data: Accelerometer): List<Field> =
        listOf(carValue("Accelerometer", HardwareFeature.ACCELEROMETER.source, data.forces) { "${vector(it)} m/s²" })

    fun gyroscope(data: Gyroscope): List<Field> =
        listOf(carValue("Gyroscope", HardwareFeature.GYROSCOPE.source, data.rotations) { "${vector(it)} rad/s" })

    fun compass(data: Compass): List<Field> =
        listOf(carValue("Compass", HardwareFeature.COMPASS.source, data.orientations) { "${vector(it)} °" })

    fun location(data: CarHardwareLocation): List<Field> =
        listOf(
            carValue("Car location", HardwareFeature.LOCATION.source, data.location) {
                String.format(Locale.US, "%.5f, %.5f ±%.0f m", it.latitude, it.longitude, it.accuracy)
            },
        )

    private fun metersPerSecond(value: Float) = "${number(value)} m/s (${number(value * 3.6f)} km/h)"

    private fun unit(value: Int) = CarUnit.toString(value)

    private fun tollState(value: Int) = when (value) {
        TollCard.TOLLCARD_STATE_VALID -> "valid"
        TollCard.TOLLCARD_STATE_INVALID -> "invalid"
        TollCard.TOLLCARD_STATE_NOT_INSERTED -> "not inserted"
        else -> "unknown ($value)"
    }

    private fun vector(values: List<Float>) = values.joinToString(", ", "[", "]") { number(it) }

    private fun number(value: Float) = String.format(Locale.US, "%.1f", value)
}
