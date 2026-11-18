package com.jantursky.autoscope.car

import androidx.car.app.hardware.common.CarValue
import com.jantursky.autoscope.core.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualDataTest {

    private val clock = FakeClock(now = 0)

    private fun <T> success(value: T) = CarValue(value, 0, CarValue.STATUS_SUCCESS)

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
    fun `samples keep only successful finite values and report every update`() {
        val samples = SensorSamples(clock.millis)
        var changes = 0
        samples.onChange = { changes++ }

        samples.compass(success(listOf(42f, 0f, 0f)))
        samples.accelerometer(success(listOf(1f, -2f, 9.8f)))
        samples.speed(success(10f))
        assertEquals(42f, samples.heading)
        assertEquals(Force(1f, -2f), samples.force)
        assertEquals(10f, samples.speedMetersPerSecond)
        assertEquals(listOf(Sample(0, 10f)), samples.speedHistory.window(0))

        samples.compass(success(listOf(Float.NaN, 0f, 0f)))
        samples.accelerometer(success(listOf(1f)))
        samples.speed(CarValue(null, 0, CarValue.STATUS_UNAVAILABLE))
        assertNull(samples.heading)
        assertNull(samples.force)
        assertNull(samples.speedMetersPerSecond)
        assertEquals(6, changes)
    }

    @Test
    fun `a tile shows data only while its field is OK and has a value`() {
        assertEquals(Tile.Shown(5f), VisualModel.tile(Field.ok("Compass", "5", "t"), 5f))
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.tile(Field.ok("Compass", "5", "t"), null))
        assertEquals(
            Tile.NoData(VisualModel.PERMISSION_MISSING),
            VisualModel.tile(Field.permissionMissing("Raw speed", "t", CarPermissions.CAR_SPEED), 5f),
        )
        assertEquals(Tile.NoData(VisualModel.UNSUPPORTED), VisualModel.tile(Field.notSupported("Compass", "t", "old host"), 5f))
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.tile(Field.unavailable("Compass", "t", "none yet"), 5f))
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.tile(null, 5f))
    }

    @Test
    fun `the model converts speed to km h and follows the speed field for the history`() {
        val fields = listOf(
            Field.ok("Compass", "x", "t"),
            Field.notSupported("Accelerometer", "t", "unimplemented"),
            Field.ok("Raw speed", "x", "t"),
        )
        val model = VisualModel.build(fields, 90f, Force(0f, 0f), 10f, listOf(Sample(0, 10f)))

        assertEquals(Tile.Shown(90f), model.heading)
        assertEquals(Tile.NoData(VisualModel.UNSUPPORTED), model.force)
        assertEquals(Tile.Shown(36f), model.speedKmh)
        assertEquals(Tile.Shown(listOf(Sample(0, 36f))), model.speedHistory)
        assertEquals(Tile.NoData(VisualModel.NO_VALUE), VisualModel.build(fields, null, null, null, emptyList()).speedHistory)
    }
}
