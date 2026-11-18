package com.jantursky.autoscope.car

import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Accelerometer
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Gyroscope
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import com.jantursky.autoscope.core.EventLog

/**
 * Registers the CarHardwareManager listeners of one Session and removes them again. The
 * CarHardwareManager on Android Auto comes from the app-projected artifact (CarAppMetadataHolderService
 * meta-data). Must be used on the main thread, as CarInfo and CarSensors are @MainThread.
 */
class HardwareListeners(
    private val carContext: CarContext,
    private val apiLevel: Int,
    private val permissions: PermissionPort,
    private val state: HardwareState,
    private val samples: SensorSamples,
    private val eventLog: EventLog,
) {
    private val removals = mutableListOf<() -> Unit>()

    /** Registers every item the API level and the granted permissions allow. Safe to call again. */
    fun start() {
        stop()
        val allowed = HardwareFeature.entries.filter { HardwareSource.mayRegister(it, apiLevel, permissions) }
        if (allowed.isEmpty()) return
        val manager = try {
            carContext.getCarService(CarHardwareManager::class.java)
        } catch (e: Exception) {
            allowed.forEach { state.fail(it, "CarHardwareManager: ${e::class.simpleName}: ${e.message}") }
            return
        }
        allowed.forEach { feature ->
            try {
                register(feature, manager.carInfo, manager.carSensors)
            } catch (e: Exception) {
                state.fail(feature, "${e::class.simpleName}: ${e.message}")
            }
        }
        eventLog.add("Hardware listeners registered: ${allowed.joinToString { it.name }}")
    }

    fun stop() {
        removals.forEach { remove -> runCatching(remove) }
        if (removals.isNotEmpty()) eventLog.add("Hardware listeners removed")
        removals.clear()
    }

    private fun register(feature: HardwareFeature, info: CarInfo, sensors: CarSensors) {
        val executor = carContext.mainExecutor
        val rate = CarSensors.UPDATE_RATE_NORMAL
        when (feature) {
            HardwareFeature.MODEL ->
                info.fetchModel(executor) { state.store(feature, HardwareFields.model(it)) }
            HardwareFeature.ENERGY_PROFILE ->
                info.fetchEnergyProfile(executor) { state.store(feature, HardwareFields.energyProfile(it)) }
            HardwareFeature.SPEED -> {
                val l = OnCarDataAvailableListener<Speed> {
                    state.store(feature, HardwareFields.speed(it))
                    samples.speed(it.rawSpeedMetersPerSecond)
                }
                info.addSpeedListener(executor, l)
                removals += { info.removeSpeedListener(l) }
            }
            HardwareFeature.MILEAGE -> {
                val l = OnCarDataAvailableListener<Mileage> { state.store(feature, HardwareFields.mileage(it)) }
                info.addMileageListener(executor, l)
                removals += { info.removeMileageListener(l) }
            }
            HardwareFeature.ENERGY_LEVEL -> {
                val l = OnCarDataAvailableListener<EnergyLevel> { state.store(feature, HardwareFields.energyLevel(it)) }
                info.addEnergyLevelListener(executor, l)
                removals += { info.removeEnergyLevelListener(l) }
            }
            HardwareFeature.TOLL -> {
                val l = OnCarDataAvailableListener<TollCard> { state.store(feature, HardwareFields.toll(it)) }
                info.addTollListener(executor, l)
                removals += { info.removeTollListener(l) }
            }
            HardwareFeature.EV_STATUS -> registerEvStatus(info, feature)
            HardwareFeature.ACCELEROMETER -> {
                val l = OnCarDataAvailableListener<Accelerometer> {
                    state.store(feature, HardwareFields.accelerometer(it))
                    samples.accelerometer(it.forces)
                }
                sensors.addAccelerometerListener(rate, executor, l)
                removals += { sensors.removeAccelerometerListener(l) }
            }
            HardwareFeature.GYROSCOPE -> {
                val l = OnCarDataAvailableListener<Gyroscope> { state.store(feature, HardwareFields.gyroscope(it)) }
                sensors.addGyroscopeListener(rate, executor, l)
                removals += { sensors.removeGyroscopeListener(l) }
            }
            HardwareFeature.COMPASS -> {
                val l = OnCarDataAvailableListener<Compass> {
                    state.store(feature, HardwareFields.compass(it))
                    samples.compass(it.orientations)
                }
                sensors.addCompassListener(rate, executor, l)
                removals += { sensors.removeCompassListener(l) }
            }
            HardwareFeature.LOCATION -> {
                val l = OnCarDataAvailableListener<CarHardwareLocation> { state.store(feature, HardwareFields.location(it)) }
                sensors.addCarHardwareLocationListener(rate, executor, l)
                removals += { sensors.removeCarHardwareLocationListener(l) }
            }
        }
    }

    @OptIn(markerClass = [ExperimentalCarApi::class])
    private fun registerEvStatus(info: CarInfo, feature: HardwareFeature) {
        val l = OnCarDataAvailableListener<EvStatus> { state.store(feature, HardwareFields.evStatus(it)) }
        info.addEvStatusListener(carContext.mainExecutor, l)
        removals += { info.removeEvStatusListener(l) }
    }
}
