package com.jantursky.autoscope.car

import android.os.SystemClock
import com.jantursky.autoscope.core.EventLog
import com.jantursky.autoscope.core.FieldSource

/**
 * The car side sources. They exist from process start and report UNAVAILABLE ("No car session") until a
 * Session binds its [CarPorts]; [unbind] on Session destroy returns them to that state.
 */
class CarSources(
    private val eventLog: EventLog,
    memory: MemoryPort = AndroidMemoryPort(),
    clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    @Volatile private var ports: CarPorts? = null
    @Volatile var constraints: ConstraintReadings = ConstraintReadings.NONE
        private set

    val surface = SurfaceState(clock)
    val touch = TouchRecorder(clock)
    val performance = PerformanceTracker(clock)
    val hardware = HardwareState()

    val sources: List<FieldSource> = listOf(
        CarHostSource(::currentPorts),
        ScreenSource(::currentPorts, surface),
        ConstraintsSource(::currentPorts) { constraints },
        HardwareSource(::currentPorts, hardware),
        TouchSource(::currentPorts, surface, touch),
        PerformanceSource(::currentPorts, performance, memory),
    )

    fun currentPorts(): CarPorts? = ports

    fun bind(ports: CarPorts) {
        surface.reset()
        touch.reset()
        hardware.clear()
        this.ports = ports
        refreshConstraints()
        eventLog.add("Car sources bound, Car App API ${ports.apiLevel}")
    }

    /** Re-reads the host limits; call on a car configuration change. */
    fun refreshConstraints() {
        val p = ports ?: return
        constraints = ConstraintReadings.read(p.constraints, p.apiLevel)
    }

    fun unbind() {
        ports = null
        constraints = ConstraintReadings.NONE
        hardware.clear()
        surface.reset()
        eventLog.add("Car sources unbound")
    }
}
