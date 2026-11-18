package com.jantursky.autoscope.car

/**
 * One CarHardwareManager data item. [permission] is the Android Auto permission from the official
 * "Car Hardware APIs" table; Model and toll need none. EvStatus has no documented Android Auto permission.
 * [labels] are the exact field labels the item always produces.
 */
enum class HardwareFeature(val source: String, val permission: String?, val labels: List<String>) {
    MODEL("CarInfo.fetchModel", null, listOf("Make", "Model", "Year")),
    ENERGY_PROFILE("CarInfo.fetchEnergyProfile", CarPermissions.CAR_FUEL, listOf("Fuel types", "EV connector types")),
    SPEED("CarInfo.addSpeedListener", CarPermissions.CAR_SPEED, listOf("Raw speed", "Display speed", "Speed display unit")),
    MILEAGE("CarInfo.addMileageListener", CarPermissions.CAR_MILEAGE, listOf("Odometer", "Odometer display unit")),
    ENERGY_LEVEL(
        "CarInfo.addEnergyLevelListener",
        CarPermissions.CAR_FUEL,
        listOf("Battery level", "Fuel level", "Energy low", "Range remaining", "Range display unit"),
    ),
    TOLL("CarInfo.addTollListener", null, listOf("Toll card")),
    EV_STATUS("CarInfo.addEvStatusListener (experimental)", null, listOf("Charge port open", "Charge port connected")),
    ACCELEROMETER("CarSensors.addAccelerometerListener", null, listOf("Accelerometer")),
    GYROSCOPE("CarSensors.addGyroscopeListener", null, listOf("Gyroscope")),
    COMPASS("CarSensors.addCompassListener", null, listOf("Compass")),
    LOCATION("CarSensors.addCarHardwareLocationListener", CarPermissions.FINE_LOCATION, listOf("Car location")),
}

object CarPermissions {
    const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    const val CAR_FUEL = "com.google.android.gms.permission.CAR_FUEL"
    const val CAR_MILEAGE = "com.google.android.gms.permission.CAR_MILEAGE"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"

    /** Runtime permissions the Hardware tab asks for, all declared in the manifest. */
    val HARDWARE = listOf(CAR_SPEED, CAR_FUEL, CAR_MILEAGE, FINE_LOCATION)

    fun shortName(permission: String) = permission.substringAfterLast('.')
}
