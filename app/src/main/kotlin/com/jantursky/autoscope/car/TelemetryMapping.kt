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
import com.jantursky.autoscope.core.Status
import java.util.Locale

/**
 * Turns CarHardwareManager results into [CarTelemetry] readings. Pure; the statuses and reasons come from
 * [HardwareGate] and [CarValues], like the HARDWARE fields.
 *
 * Units as documented in androidx.car.app 1.7.0: Compass orientations[0] is the azimuth in degrees
 * (0 to 359, 0 = north, 90 = east); EnergyLevel battery and fuel are percentages; range is in metres;
 * Speed raw is m/s. The Accelerometer javadoc names no unit ("acceleration including gravity"); it is
 * taken as m/s² like Android's SensorManager, so forces are divided by standard gravity.
 */
object TelemetryMapping {
    const val STANDARD_GRAVITY = 9.80665f
    const val NOT_REPORTED = "Not reported (NaN)"

    /** The readings of a fresh registration: blocked items say why, the others wait for a value. */
    fun initial(apiLevel: Int, permissions: PermissionPort): CarTelemetry =
        HardwareFeature.entries.fold(CarTelemetry.EMPTY) { telemetry, feature ->
            withMissing(
                telemetry,
                feature,
                HardwareGate.block(feature, apiLevel, permissions) ?: NoValue(Status.UNAVAILABLE, NO_VALUE_YET),
            )
        }

    /** Every reading of [feature] set to [missing]; items the telemetry does not carry leave it unchanged. */
    fun withMissing(telemetry: CarTelemetry, feature: HardwareFeature, missing: NoValue): CarTelemetry {
        val r = missing.reading<Nothing>()
        return when (feature) {
            HardwareFeature.MODEL -> telemetry.copy(make = r, model = r, year = r)
            HardwareFeature.SPEED -> telemetry.copy(speedMetersPerSecond = r)
            HardwareFeature.ENERGY_LEVEL -> telemetry.copy(fuelFraction = r, batteryFraction = r, rangeMeters = r)
            HardwareFeature.EV_STATUS -> telemetry.copy(chargePortOpen = r, chargePortConnected = r)
            HardwareFeature.ACCELEROMETER -> telemetry.copy(gForceX = r, gForceY = r)
            HardwareFeature.COMPASS -> telemetry.copy(headingDegrees = r)
            HardwareFeature.ENERGY_PROFILE, HardwareFeature.MILEAGE, HardwareFeature.TOLL,
            HardwareFeature.GYROSCOPE, HardwareFeature.LOCATION -> telemetry
        }
    }

    fun withModel(telemetry: CarTelemetry, model: Model) = telemetry.copy(
        make = reading(model.manufacturer) { Reading.of(it) },
        model = reading(model.name) { Reading.of(it) },
        year = reading(model.year) { Reading.of(it) },
    )

    fun withSpeed(telemetry: CarTelemetry, speed: Speed) =
        telemetry.copy(speedMetersPerSecond = reading(speed.rawSpeedMetersPerSecond, ::finite))

    fun withEnergyLevel(telemetry: CarTelemetry, level: EnergyLevel) = telemetry.copy(
        fuelFraction = fraction(level.fuelPercent),
        batteryFraction = fraction(level.batteryPercent),
        rangeMeters = reading(level.rangeRemainingMeters) { meters ->
            finite(meters).then { if (it >= 0f) Reading.of(it) else outOfRange("$it m") }
        },
    )

    // EvStatus is @ExperimentalCarApi, like its listener in HardwareListeners.
    @OptIn(markerClass = [ExperimentalCarApi::class])
    fun withEvStatus(telemetry: CarTelemetry, status: EvStatus) = telemetry.copy(
        chargePortOpen = reading(status.evChargePortOpen) { Reading.of(it) },
        chargePortConnected = reading(status.evChargePortConnected) { Reading.of(it) },
    )

    fun withAccelerometer(telemetry: CarTelemetry, data: Accelerometer) = telemetry.copy(
        gForceX = reading(data.forces) { forces -> element(forces, 0).then { Reading.of(it / STANDARD_GRAVITY) } },
        gForceY = reading(data.forces) { forces -> element(forces, 1).then { Reading.of(it / STANDARD_GRAVITY) } },
    )

    fun withCompass(telemetry: CarTelemetry, data: Compass) = telemetry.copy(
        headingDegrees = reading(data.orientations) { values -> element(values, 0).then { Reading.of(normalizeDegrees(it)) } },
    )

    /** [degrees] in 0 until 360. */
    fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /** A percentage CarValue as a fraction 0..1; values outside 0..100 % are reported, not clamped. */
    fun fraction(percent: CarValue<Float>): Reading<Float> = reading(percent) { p ->
        finite(p).then { if (it in 0f..100f) Reading.of(it / 100f) else outOfRange(String.format(Locale.US, "%.1f %%", it)) }
    }

    fun <T, R> reading(value: CarValue<T>, convert: (T) -> Reading<R>): Reading<R> =
        CarValues.success(value)?.let(convert) ?: CarValues.noValue(value.status).reading()

    fun finite(value: Float): Reading<Float> =
        if (value.isFinite()) Reading.of(value) else Reading.missing(Status.UNAVAILABLE, NOT_REPORTED)

    private fun element(values: List<Float>, index: Int): Reading<Float> =
        values.getOrNull(index)?.let(::finite) ?: Reading.missing(Status.UNAVAILABLE, "Not in the car's data")

    private fun <T> outOfRange(value: String) = Reading.missing<T>(Status.UNAVAILABLE, "Out of range: $value")

    private inline fun <T, R> Reading<T>.then(next: (T) -> Reading<R>): Reading<R> {
        val v = value
        return if (status == Status.OK && v != null) next(v) else Reading.missing(status, reason)
    }
}
