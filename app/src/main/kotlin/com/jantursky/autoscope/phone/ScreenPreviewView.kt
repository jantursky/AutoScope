package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.R

private const val MAX_HEIGHT_DP = 168
private const val PLACEHOLDER_RATIO = 16f / 9f
private const val DIM_ALPHA = 110

/** To-scale drawing of the car display with its usable areas; dimmed with a reason when there is no data. */
private class ScreenDrawingView(context: Context) : View(context) {
    private var preview: ScreenPreview = ScreenPreview.Empty(NO_CAR_SESSION)
    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 14 * density }
    private val rect = RectF()

    fun bind(next: ScreenPreview) {
        if (next == preview) return
        preview = next
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (MAX_HEIGHT_DP * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val ready = preview as? ScreenPreview.Ready
        val ratio = ready?.let { it.widthPx.toFloat() / it.heightPx } ?: PLACEHOLDER_RATIO
        // Fit the display into the view, keeping its aspect ratio, with room for the stroke.
        val pad = 2 * density
        var w = width - 2 * pad
        var h = w / ratio
        if (h > height - 2 * pad) {
            h = height - 2 * pad
            w = h * ratio
        }
        rect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        val alpha = if (ready == null) DIM_ALPHA else 255
        val radius = 12 * density
        fill.color = context.color(R.color.car_glass)
        fill.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (ready == null) {
            text.color = context.color(R.color.text_secondary)
            canvas.drawText((preview as ScreenPreview.Empty).text, rect.centerX(), rect.centerY() + text.textSize / 3, text)
            return
        }
        ready.stable?.let { area(canvas, it, null, R.color.status_info, dashed = true) }
        ready.visible?.let { area(canvas, it, R.color.accent_soft, R.color.accent, dashed = false) }
    }

    private fun area(canvas: Canvas, f: Fractions, fillColor: Int?, strokeColor: Int, dashed: Boolean) {
        val r = RectF(
            rect.left + f.left * rect.width(), rect.top + f.top * rect.height(),
            rect.left + f.right * rect.width(), rect.top + f.bottom * rect.height(),
        )
        if (fillColor != null) {
            fill.color = context.color(fillColor)
            fill.alpha = 90
            canvas.drawRect(r, fill)
        }
        stroke.color = context.color(strokeColor)
        stroke.pathEffect = if (dashed) DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f) else null
        canvas.drawRect(r, stroke)
    }
}

/** Above the Screen group card: the display drawing and its size labels. */
internal class ScreenPreviewView(context: Context) : LinearLayout(context) {
    private val drawing = ScreenDrawingView(context)
    private val px = context.label(14f, R.color.text_primary, bold = true).apply { gravity = Gravity.CENTER }
    private val units = context.label(13f, R.color.text_secondary).apply { gravity = Gravity.CENTER }
    private val visible = context.label(12f, R.color.accent, bold = true).apply { setText(R.string.visible_area) }
    private val stable = context.label(12f, R.color.status_info, bold = true).apply { setText(R.string.stable_area) }
    private val legend = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        addView(visible)
        addView(stable, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(16) })
    }

    init {
        orientation = VERTICAL
        addView(context.heading(R.string.screen_preview_title))
        addView(context.card().apply {
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
            addView(drawing, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(px, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
            addView(units, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(2) })
            addView(legend, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        })
    }

    fun bind(preview: ScreenPreview) {
        drawing.bind(preview)
        val ready = preview as? ScreenPreview.Ready
        px.visibility = if (ready == null) GONE else VISIBLE
        units.visibility = if (ready == null) GONE else VISIBLE
        legend.visibility = if (ready?.visible == null && ready?.stable == null) GONE else VISIBLE
        visible.visibility = if (ready?.visible == null) GONE else VISIBLE
        stable.visibility = if (ready?.stable == null) GONE else VISIBLE
        if (ready == null) return
        px.setTextIfChanged(ready.pxText)
        units.setTextIfChanged(listOfNotNull(ready.dpText, ready.dpiText).joinToString("  ·  "))
    }
}
