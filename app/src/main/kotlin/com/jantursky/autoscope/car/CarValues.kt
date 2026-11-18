package com.jantursky.autoscope.car

import androidx.car.app.hardware.common.CarValue
import com.jantursky.autoscope.core.Status

/** CarValue status handling shared by the hardware fields and the telemetry. */
object CarValues {

    /** The value of a STATUS_SUCCESS CarValue, null for any other status or a missing value. */
    fun <T> success(value: CarValue<T>): T? = value.value.takeIf { value.status == CarValue.STATUS_SUCCESS }

    /** Why a CarValue with [status] has no usable value. Short, because car rows and tiles are narrow. */
    fun noValue(status: Int): NoValue = when (status) {
        CarValue.STATUS_SUCCESS -> NoValue(Status.UNAVAILABLE, "Empty value from the car")
        CarValue.STATUS_UNAVAILABLE -> NoValue(Status.UNAVAILABLE, "No value yet")
        CarValue.STATUS_UNIMPLEMENTED -> NoValue(Status.NOT_SUPPORTED_BY_HOST, "Not implemented by this car/host")
        else -> NoValue(Status.UNAVAILABLE, "CarValue STATUS_UNKNOWN ($status)")
    }
}
