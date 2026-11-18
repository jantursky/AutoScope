package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import com.jantursky.autoscope.R

/** Small view helpers on the 8dp grid. */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.color(@ColorRes id: Int): Int = getColor(id)

internal fun Context.label(sizeSp: Float, @ColorRes color: Int, bold: Boolean = false) = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color(color))
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

internal fun Context.divider() = View(this).apply {
    setBackgroundColor(color(R.color.divider))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
}

internal fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundResource(R.drawable.card_background)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(16) }
}

internal fun Context.sectionTitle(text: Int) = label(14f, R.color.text_secondary, bold = true).apply {
    setText(text)
    setPadding(dp(8), dp(8), dp(8), dp(8))
}

internal fun TextView.setTextIfChanged(value: CharSequence) {
    if (text.toString() != value.toString()) text = value
}
