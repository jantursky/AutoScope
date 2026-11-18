package com.jantursky.autoscope.phone

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import com.jantursky.autoscope.R

private const val TURN_MILLIS = 450L
private const val DIM_ALPHA = 110
private const val LOW_ENERGY = 0.15f
private const val BOLT_SIZE = 5f

/**
 * Top-down car drawn with the canvas. Only data that exists is drawn: heading, link, energy, charge
 * port, speed and g-force. Missing data is dimmed, never invented.
 */
internal class CarPictureView(context: Context, initial: CarPicture) : View(context) {
    private var picture = initial
    private var shownHeading = 0f
    private var targetHeading = initial.headingDegrees ?: 0f
    private var turn: ValueAnimator? = null
    private val d = resources.displayMetrics.density

    private fun dp(value: Float) = value * d
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    private fun c(@ColorRes id: Int) = context.color(id)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private val dashes = DashPathEffect(floatArrayOf(dp(4f), dp(6f)), 0f)
    private val glow = LinkKind.entries.associateWith { link ->
        val color = c(linkColor(link))
        RadialGradient(0f, 0f, dp(GLOW_RADIUS), ColorUtils.setAlphaComponent(color, 130), ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP)
    }

    init {
        updateDescription()
    }

    fun bind(next: CarPicture) {
        if (next == picture) return
        picture = next
        val target = next.headingDegrees ?: 0f
        if (target != targetHeading) {
            targetHeading = target
            turn?.cancel()
            val start = shownHeading
            val end = start + shortestTurn(start, target)
            turn = ValueAnimator.ofFloat(start, end).apply {
                duration = TURN_MILLIS
                addUpdateListener { shownHeading = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        updateDescription()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        turn?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(getDefaultSize(0, widthSpec), dp(HEIGHT).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cx = (width - dp(RIGHT_COLUMN)) / 2f
        drawTitle(canvas, width / 2f)
        drawStage(canvas, cx, dp(STAGE_Y))
        drawEnergy(canvas, width - dp(RIGHT_COLUMN) / 2f)
        drawSpeed(canvas, cx)
    }

    private fun drawTitle(canvas: Canvas, x: Float) {
        text.apply { typeface = Typeface.DEFAULT_BOLD; textSize = sp(15f); color = c(R.color.text_primary) }
        canvas.drawText(picture.title, x, dp(32f), text)
        picture.subtitle?.let {
            text.apply { typeface = Typeface.DEFAULT; textSize = sp(12f); color = c(R.color.text_tertiary) }
            canvas.drawText(it, x, dp(52f), text)
        }
    }

    private fun drawStage(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        paint.reset()
        paint.isAntiAlias = true
        canvas.save()
        canvas.scale(0.8f, 1f)
        paint.shader = glow.getValue(picture.link)
        canvas.drawCircle(0f, 0f, dp(GLOW_RADIUS), paint)
        canvas.restore()
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = c(R.color.divider)
        paint.pathEffect = dashes
        canvas.drawCircle(0f, 0f, dp(RING_RADIUS), paint)
        text.apply { typeface = Typeface.DEFAULT_BOLD; textSize = sp(11f); color = c(if (picture.headingDegrees == null) R.color.text_tertiary else R.color.text_secondary) }
        canvas.drawText("N", 0f, -dp(RING_RADIUS + 6f), text)

        canvas.save()
        canvas.rotate(shownHeading)
        val bodyAlpha = if (picture.headingDegrees == null) DIM_ALPHA else 255
        canvas.saveLayerAlpha(-dp(60f), -dp(70f), dp(60f), dp(70f), bodyAlpha)
        drawBody(canvas)
        canvas.restore()
        drawChargePort(canvas)
        canvas.restore()

        drawGForce(canvas)
        canvas.restore()
    }

    private fun drawBody(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        fun block(l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Int, stroke: Boolean = false) {
            rect.set(dp(l), dp(t), dp(r), dp(b))
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawRoundRect(rect, dp(radius), dp(radius), paint)
            if (stroke) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f)
                paint.color = c(R.color.text_tertiary)
                canvas.drawRoundRect(rect, dp(radius), dp(radius), paint)
            }
        }
        block(-34f, -19f, -26f, -8f, 3f, c(R.color.car_body), stroke = true)
        block(26f, -19f, 34f, -8f, 3f, c(R.color.car_body), stroke = true)
        block(-28f, -60f, 28f, 60f, 22f, c(R.color.car_body), stroke = true)
        block(-22f, -58f, -11f, -53f, 2f, c(R.color.car_light))
        block(11f, -58f, 22f, -53f, 2f, c(R.color.car_light))
        path.rewind()
        path.moveTo(dp(-19f), dp(-26f)); path.lineTo(dp(19f), dp(-26f)); path.lineTo(dp(23f), dp(-9f)); path.lineTo(dp(-23f), dp(-9f)); path.close()
        path.moveTo(dp(-23f), dp(14f)); path.lineTo(dp(23f), dp(14f)); path.lineTo(dp(19f), dp(30f)); path.lineTo(dp(-19f), dp(30f)); path.close()
        paint.style = Paint.Style.FILL
        paint.color = c(R.color.car_glass)
        canvas.drawPath(path, paint)
    }

    private fun drawChargePort(canvas: Canvas) {
        val lit = when (picture.chargePort) {
            ChargePort.OPEN -> c(R.color.car_charge_open)
            ChargePort.CONNECTED -> c(R.color.car_charge_connected)
            else -> null
        }
        val x = dp(-16f)
        val y = dp(44f)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = lit ?: c(R.color.divider)
        paint.alpha = if (picture.chargePort == ChargePort.UNKNOWN) DIM_ALPHA else 255
        canvas.drawCircle(x, y, dp(7.5f), paint)
        path.rewind()
        path.moveTo(x + dp(1f), y - dp(BOLT_SIZE)); path.lineTo(x - dp(3f), y + dp(1f)); path.lineTo(x, y + dp(1f))
        path.lineTo(x - dp(1f), y + dp(BOLT_SIZE)); path.lineTo(x + dp(3f), y - dp(1f)); path.lineTo(x, y - dp(1f)); path.close()
        paint.color = c(if (lit != null) R.color.surface else R.color.text_tertiary)
        canvas.drawPath(path, paint)
    }

    private fun drawGForce(canvas: Canvas) {
        val g = picture.gForce ?: return
        paint.reset()
        paint.isAntiAlias = true
        paint.color = c(R.color.accent)
        canvas.drawCircle(g.x * dp(RING_RADIUS), -g.y * dp(RING_RADIUS), dp(6f), paint)
    }

    private fun drawEnergy(canvas: Canvas, x: Float) {
        val energy = picture.energy
        val top = dp(TRACK_TOP)
        val bottom = dp(TRACK_BOTTOM)
        val half = dp(7f)
        text.apply { typeface = Typeface.DEFAULT; textSize = sp(11f); color = c(R.color.text_tertiary) }
        canvas.drawText(energy?.kind?.label ?: "Energy", x, top - dp(36f), text)
        text.apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = sp(16f)
            color = c(if (energy == null) R.color.text_tertiary else R.color.text_primary)
        }
        canvas.drawText(energy?.let { "${it.percent}%" } ?: "--", x, top - dp(14f), text)

        paint.reset()
        paint.isAntiAlias = true
        paint.color = c(R.color.divider)
        rect.set(x - half, top, x + half, bottom)
        canvas.drawRoundRect(rect, half, half, paint)
        if (energy != null && energy.fraction > 0f) {
            paint.color = c(if (energy.fraction < LOW_ENERGY) R.color.status_warning else R.color.accent)
            val filled = ((bottom - top) * energy.fraction).coerceAtLeast(half * 2)
            rect.set(x - half, bottom - filled, x + half, bottom)
            canvas.drawRoundRect(rect, half, half, paint)
        }
        text.apply { typeface = Typeface.DEFAULT; textSize = sp(13f); color = c(if (energy == null) R.color.text_tertiary else R.color.text_secondary) }
        val below = when {
            energy == null -> "no data"
            picture.rangeKm == null -> "no range"
            else -> "${picture.rangeKm} km"
        }
        canvas.drawText(below, x, bottom + dp(22f), text)
    }

    private fun drawSpeed(canvas: Canvas, x: Float) {
        val speed = picture.speedKmh
        text.apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = sp(28f); fontFeatureSettings = "tnum"
            color = c(if (speed == null) R.color.text_tertiary else R.color.text_primary)
        }
        canvas.drawText(speed?.toString() ?: "--", x, dp(SPEED_Y), text)
        text.apply { typeface = Typeface.DEFAULT; textSize = sp(12f); fontFeatureSettings = null; color = c(R.color.text_tertiary) }
        canvas.drawText("km/h", x, dp(SPEED_Y + 20f), text)
    }

    private fun updateDescription() {
        val parts = listOfNotNull(
            picture.title,
            picture.speedKmh?.let { "$it km/h" },
            picture.energy?.let { "${it.kind.label} ${it.percent}%" },
            picture.rangeKm?.let { "range $it km" },
        )
        contentDescription = parts.joinToString(", ")
    }

    private fun linkColor(link: LinkKind) = when (link) {
        LinkKind.PROJECTION -> R.color.car_link_projection
        LinkKind.NATIVE -> R.color.car_link_native
        LinkKind.NONE -> R.color.status_muted
    }

    private companion object {
        const val HEIGHT = 360f
        const val STAGE_Y = 190f
        const val RING_RADIUS = 80f
        const val GLOW_RADIUS = 100f
        const val RIGHT_COLUMN = 88f
        const val TRACK_TOP = 130f
        const val TRACK_BOTTOM = 270f
        const val SPEED_Y = 316f
    }
}
