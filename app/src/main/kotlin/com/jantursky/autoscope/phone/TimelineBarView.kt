package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.jantursky.autoscope.R

private const val GAP_DP = 2f
private const val RADIUS_DP = 4f

/** Horizontal bar of connection segments: green while connected, grey in between, widths in proportion to time. */
internal class TimelineBarView(context: Context) : View(context) {
    private var segments = emptyList<BarSegment>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val connected = context.color(R.color.car_link_projection)
    private val idle = context.color(R.color.car_body)

    fun bind(next: List<BarSegment>) {
        if (next == segments) return
        segments = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val gap = context.resources.displayMetrics.density * GAP_DP
        val radius = context.resources.displayMetrics.density * RADIUS_DP
        val usable = width - gap * (segments.size - 1)
        var x = 0f
        for (segment in segments) {
            val w = usable * segment.share
            rect.set(x, 0f, x + w, height.toFloat())
            paint.color = if (segment.connected) connected else idle
            canvas.drawRoundRect(rect, radius, radius, paint)
            x += w + gap
        }
    }
}
