package com.jantursky.autoscope.car

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Draws [text] cut with an ellipsis to [maxWidth], so it never leaves its tile. */
internal fun Canvas.drawFitted(text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) =
    drawText(TextFit.fit(text, maxWidth) { paint.measureText(it) }, x, y, paint)

/** Replaces the contents of [path] with the closed polygon [points]. */
internal fun Path.setPolygon(points: List<Point>): Path {
    reset()
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
    return this
}
