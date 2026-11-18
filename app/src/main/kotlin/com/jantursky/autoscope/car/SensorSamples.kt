package com.jantursky.autoscope.car

import com.jantursky.autoscope.Reading

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

/** Lateral (x, to the right) and longitudinal (y, forwards) acceleration in g. */
data class Force(val x: Float, val y: Float)

/**
 * The speed of the last minute for the Visual screen. The latest values of every sensor live in
 * [com.jantursky.autoscope.CarTelemetry]; this keeps only the history a single value cannot hold.
 */
class SensorSamples(private val clock: () -> Long) {
    val speedHistory = SampleHistory(HISTORY_SPAN_MS, HISTORY_RESOLUTION_MS)

    fun now(): Long = clock()

    /** Records [speed] when it is an OK reading. */
    fun speed(speed: Reading<Float>) {
        val value = speed.value
        if (speed.ok && value != null) speedHistory.add(clock(), value)
    }

    fun clear() = speedHistory.clear()

    companion object {
        const val HISTORY_SPAN_MS = 60_000L
        const val HISTORY_RESOLUTION_MS = 250L
    }
}
