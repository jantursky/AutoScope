package com.jantursky.autoscope.car

import androidx.car.app.hardware.common.CarValue

data class Sample(val atMillis: Long, val value: Float)

/**
 * Ring buffer of the samples of the last [spanMillis], at most one per [resolutionMillis]: a sample less
 * than that after the newest slot was opened overwrites its value (the slot keeps its time). So the buffer
 * never needs more than span / resolution + 1 slots, however often the car reports. Thread safe.
 */
class SampleHistory(private val spanMillis: Long, private val resolutionMillis: Long) {
    private val capacity = (spanMillis / resolutionMillis).toInt() + 1
    private val times = LongArray(capacity)
    private val values = FloatArray(capacity)
    private var start = 0
    private var size = 0

    @Synchronized
    fun add(atMillis: Long, value: Float) {
        if (size > 0 && atMillis - times[index(size - 1)] < resolutionMillis) {
            values[index(size - 1)] = value
            return
        }
        if (size == capacity) {
            start = (start + 1) % capacity
            size--
        }
        times[index(size)] = atMillis
        values[index(size)] = value
        size++
    }

    /** The samples of the last [spanMillis] before [nowMillis], oldest first. */
    @Synchronized
    fun window(nowMillis: Long): List<Sample> =
        (0 until size).map { Sample(times[index(it)], values[index(it)]) }.filter { nowMillis - it.atMillis <= spanMillis }

    @Synchronized
    fun clear() {
        start = 0
        size = 0
    }

    private fun index(i: Int) = (start + i) % capacity
}

data class Force(val x: Float, val y: Float)

/**
 * The latest raw sensor numbers for the Visual screen, next to the formatted fields in [HardwareState].
 * A value is null unless the last CarValue was STATUS_SUCCESS with finite numbers. [onChange] is called
 * on the listener thread (the main executor) after every update.
 */
class SensorSamples(private val clock: () -> Long) {
    @Volatile var heading: Float? = null
        private set
    @Volatile var force: Force? = null
        private set
    @Volatile var speedMetersPerSecond: Float? = null
        private set
    val speedHistory = SampleHistory(HISTORY_SPAN_MS, HISTORY_RESOLUTION_MS)

    @Volatile var onChange: (() -> Unit)? = null

    fun now(): Long = clock()

    fun compass(orientations: CarValue<List<Float>>) {
        heading = success(orientations)?.getOrNull(0)?.takeIf { it.isFinite() }
        onChange?.invoke()
    }

    fun accelerometer(forces: CarValue<List<Float>>) {
        val values = success(forces)
        val x = values?.getOrNull(0)
        val y = values?.getOrNull(1)
        force = if (x != null && y != null && x.isFinite() && y.isFinite()) Force(x, y) else null
        onChange?.invoke()
    }

    fun speed(rawMetersPerSecond: CarValue<Float>) {
        val value = success(rawMetersPerSecond)?.takeIf { it.isFinite() }
        speedMetersPerSecond = value
        if (value != null) speedHistory.add(clock(), value)
        onChange?.invoke()
    }

    fun clear() {
        heading = null
        force = null
        speedMetersPerSecond = null
        speedHistory.clear()
    }

    private fun <T> success(value: CarValue<T>): T? = value.value.takeIf { value.status == CarValue.STATUS_SUCCESS }

    companion object {
        const val HISTORY_SPAN_MS = 60_000L
        const val HISTORY_RESOLUTION_MS = 250L
    }
}
