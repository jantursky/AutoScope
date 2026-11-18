package com.jantursky.autoscope.car

enum class TouchKind { CLICK, SCROLL, FLING, SCALE }

/**
 * One SurfaceCallback event. [x]/[y] is the cursor after the event in surface pixels: a click or a scale
 * focus sets it, a scroll moves it, a fling (which has no position) keeps it. [dx]/[dy] is the scroll
 * distance or the fling velocity, [scale] the scale factor.
 */
data class TouchEvent(
    val kind: TouchKind,
    val atMillis: Long,
    val x: Float,
    val y: Float,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
)

data class TouchStats(
    val counts: Map<TouchKind, Int>,
    /** Oldest first, at most [TouchRecorder.TRAIL_SIZE]. */
    val trail: List<TouchEvent>,
    val eventsPerSecond: Int,
    /** Last measured tap latency, see [TouchRecorder.tapStarted]. */
    val tapLatencyMillis: Long?,
) {
    val last: TouchEvent? get() = trail.lastOrNull()
}

/** Bounded, thread safe record of touch events and of the row tap latency probe. */
class TouchRecorder(private val clock: () -> Long) {
    private val lock = Any()
    private val counts = mutableMapOf<TouchKind, Int>()
    private val trail = ArrayDeque<TouchEvent>()
    private val recent = ArrayDeque<Long>()
    private var cursorX = 0f
    private var cursorY = 0f
    private var tapStartedAt: Long? = null
    private var tapLatency: Long? = null

    fun click(x: Float, y: Float) = add(TouchKind.CLICK, x, y)

    /** distanceX/Y follow GestureDetector: previous position minus current position. */
    fun scroll(distanceX: Float, distanceY: Float) = synchronized(lock) {
        add(TouchKind.SCROLL, cursorX - distanceX, cursorY - distanceY, distanceX, distanceY)
    }

    fun fling(velocityX: Float, velocityY: Float) = synchronized(lock) {
        add(TouchKind.FLING, cursorX, cursorY, velocityX, velocityY)
    }

    /** The host passes -1 for an unknown focus; the cursor then stays where it is. */
    fun scale(focusX: Float, focusY: Float, factor: Float) = synchronized(lock) {
        val known = focusX >= 0f && focusY >= 0f
        add(TouchKind.SCALE, if (known) focusX else cursorX, if (known) focusY else cursorY, scale = factor)
    }

    /** Starts the tap latency probe: a row was tapped and the screen invalidated. */
    fun tapStarted() = synchronized(lock) { tapStartedAt = clock() }

    /** Ends the probe when the host asks for the next template. */
    fun templateServed() = synchronized(lock) {
        tapStartedAt?.let { tapLatency = clock() - it }
        tapStartedAt = null
    }

    fun stats(): TouchStats = synchronized(lock) {
        val now = clock()
        TouchStats(
            counts = TouchKind.entries.associateWith { counts[it] ?: 0 },
            trail = trail.toList(),
            eventsPerSecond = recent.count { now - it < 1_000 },
            tapLatencyMillis = tapLatency,
        )
    }

    fun reset() = synchronized(lock) {
        counts.clear()
        trail.clear()
        recent.clear()
        cursorX = 0f
        cursorY = 0f
        tapStartedAt = null
        tapLatency = null
    }

    private fun add(kind: TouchKind, x: Float, y: Float, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) =
        synchronized(lock) {
            val now = clock()
            cursorX = x
            cursorY = y
            counts[kind] = (counts[kind] ?: 0) + 1
            trail.addLast(TouchEvent(kind, now, x, y, dx, dy, scale))
            while (trail.size > TRAIL_SIZE) trail.removeFirst()
            recent.addLast(now)
            while (recent.isNotEmpty() && now - recent.first() >= 1_000) recent.removeFirst()
        }

    companion object {
        const val TRAIL_SIZE = 10
    }
}
