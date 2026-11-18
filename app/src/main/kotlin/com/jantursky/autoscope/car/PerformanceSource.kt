package com.jantursky.autoscope.car

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.FieldSource
import com.jantursky.autoscope.core.Group

/** Template timing of the current Session. Thread safe; [clock] is a monotonic millisecond clock. */
class PerformanceTracker(private val clock: () -> Long) {
    private val lock = Any()
    private var sessionCreatedAt: Long? = null
    private var firstTemplateMillis: Long? = null
    private var invalidatedAt: Long? = null
    private var invalidateToTemplateMillis: Long? = null
    private val templateTimes = ArrayDeque<Long>()

    fun sessionCreated() = synchronized(lock) {
        sessionCreatedAt = clock()
        firstTemplateMillis = null
        invalidatedAt = null
        invalidateToTemplateMillis = null
        templateTimes.clear()
    }

    fun invalidateRequested() = synchronized(lock) {
        if (invalidatedAt == null) invalidatedAt = clock()
    }

    /** Call from every onGetTemplate. */
    fun templateServed() = synchronized(lock) {
        val now = clock()
        val created = sessionCreatedAt
        if (firstTemplateMillis == null && created != null) firstTemplateMillis = now - created
        invalidatedAt?.let { invalidateToTemplateMillis = now - it }
        invalidatedAt = null
        templateTimes.addLast(now)
        trim(now)
    }

    fun reading(): PerformanceReading = synchronized(lock) {
        trim(clock())
        PerformanceReading(firstTemplateMillis, invalidateToTemplateMillis, templateTimes.size)
    }

    private fun trim(now: Long) {
        while (templateTimes.isNotEmpty() && now - templateTimes.first() >= 60_000) templateTimes.removeFirst()
    }
}

data class PerformanceReading(
    val sessionToFirstTemplateMillis: Long?,
    val invalidateToTemplateMillis: Long?,
    val templatesLastMinute: Int,
)

/** PERFORMANCE group: template timing (needs a Session) and process memory (always). */
class PerformanceSource(
    private val ports: () -> CarPorts?,
    private val tracker: PerformanceTracker,
    private val memory: MemoryPort,
) : FieldSource {
    override val group = Group.PERFORMANCE

    override fun read(): List<Field> = templateFields() + memoryFields()

    private fun templateFields(): List<Field> {
        if (ports() == null) return TEMPLATE_LABELS.allUnavailable(SOURCE_TEMPLATE, NO_SESSION)
        val r = tracker.reading()
        return listOf(
            r.sessionToFirstTemplateMillis?.let { Field.ok(FIRST_TEMPLATE, "$it ms", SOURCE_TEMPLATE) }
                ?: Field.unavailable(FIRST_TEMPLATE, SOURCE_TEMPLATE, "No template served yet"),
            r.invalidateToTemplateMillis?.let { Field.ok(INVALIDATE, "$it ms", SOURCE_INVALIDATE) }
                ?: Field.unavailable(INVALIDATE, SOURCE_INVALIDATE, "No invalidate() yet"),
            Field.ok(REFRESHES, r.templatesLastMinute, SOURCE_TEMPLATE),
        )
    }

    private fun memoryFields(): List<Field> {
        val m = try {
            memory.read()
        } catch (e: Exception) {
            return MEMORY_LABELS.allUnavailable(SOURCE_RUNTIME, "${e::class.simpleName}: ${e.message}")
        }
        return listOf(
            Field.ok(JAVA_HEAP, "${mib(m.javaHeapUsedBytes)} of ${mib(m.javaHeapMaxBytes)} MiB", SOURCE_RUNTIME),
            Field.ok(NATIVE_HEAP, "${mib(m.nativeHeapAllocatedBytes)} MiB", "Debug.getNativeHeapAllocatedSize"),
            m.pssKb?.let { Field.ok(PSS, "${it / 1024} MiB", SOURCE_PSS) }
                ?: Field.unavailable(PSS, SOURCE_PSS, "Not sampled yet"),
        )
    }

    private fun mib(bytes: Long) = bytes / (1024 * 1024)

    private companion object {
        const val SOURCE_TEMPLATE = "Screen.onGetTemplate"
        const val SOURCE_INVALIDATE = "Screen.invalidate to onGetTemplate"
        const val SOURCE_RUNTIME = "Runtime.totalMemory - freeMemory"
        const val SOURCE_PSS = "Debug.getPss (sampled every 10 s)"
        const val FIRST_TEMPLATE = "Session to first template"
        const val INVALIDATE = "Invalidate to template"
        const val REFRESHES = "Templates in the last minute"
        const val JAVA_HEAP = "Java heap"
        const val NATIVE_HEAP = "Native heap"
        const val PSS = "Process PSS"
        val TEMPLATE_LABELS = listOf(FIRST_TEMPLATE, INVALIDATE, REFRESHES)
        val MEMORY_LABELS = listOf(JAVA_HEAP, NATIVE_HEAP, PSS)
    }
}
