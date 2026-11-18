package com.jantursky.autoscope.phone

import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status

/** A rectangle as fractions (0..1) of the display, so it can be drawn at any size. */
data class Fractions(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** What the Screen tab draws: the display with its sizes and, when the host reported them, the usable areas. */
sealed interface ScreenPreview {
    data class Ready(
        val widthPx: Int,
        val heightPx: Int,
        val dpi: Int?,
        val widthDp: Int?,
        val heightDp: Int?,
        val visible: Fractions?,
        val stable: Fractions?,
    ) : ScreenPreview {
        val pxText get() = "$widthPx x $heightPx px"
        val dpText get() = if (widthDp != null && heightDp != null) "$widthDp x $heightDp dp" else null
        val dpiText get() = dpi?.let { "$it dpi" }
    }

    /** Nothing to draw; [text] says why. */
    data class Empty(val text: String) : ScreenPreview
}

private val PIXELS = Regex("""^(\d+) px$""")
// Area.toString() of the car side: "(l, t)-(r, b) WxH".
private val AREA = Regex("""^\((-?\d+), (-?\d+)\)-\((-?\d+), (-?\d+)\)""")

/** Parses the SCREEN group's value strings; anything unparsable gives [ScreenPreview.Empty], never an exception. */
fun screenPreview(snapshot: Snapshot): ScreenPreview {
    val fields = snapshot.fields(Group.SCREEN).associateBy { it.label }
    val width = fields["Width"]?.okValue()?.let { PIXELS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    val height = fields["Height"]?.okValue()?.let { PIXELS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    if (width == null || height == null || width <= 0 || height <= 0) return ScreenPreview.Empty(emptyText(fields["Width"]))
    return ScreenPreview.Ready(
        widthPx = width,
        heightPx = height,
        dpi = fields["Density dpi"]?.okValue()?.toIntOrNull(),
        widthDp = fields["Width dp"]?.okValue()?.toIntOrNull(),
        heightDp = fields["Height dp"]?.okValue()?.toIntOrNull(),
        visible = fields["Visible area"]?.okValue()?.let { fractions(it, width, height) },
        stable = fields["Stable area"]?.okValue()?.let { fractions(it, width, height) },
    )
}

private fun Field.okValue(): String? = value.takeIf { status == Status.OK }

private fun emptyText(width: Field?) =
    if (width == null || width.reason == NO_CAR_SESSION) NO_CAR_SESSION else "No preview available"

private fun fractions(text: String, width: Int, height: Int): Fractions? {
    val (l, t, r, b) = AREA.find(text)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null } ?: return null
    // Areas are surface pixels; keep what lies inside the display so odd values cannot draw outside it.
    val area = Fractions(
        (l.coerceIn(0, width)).toFloat() / width, (t.coerceIn(0, height)).toFloat() / height,
        (r.coerceIn(0, width)).toFloat() / width, (b.coerceIn(0, height)).toFloat() / height,
    )
    return area.takeIf { it.right > it.left && it.bottom > it.top }
}
