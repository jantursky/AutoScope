package com.jantursky.autoscope.phone

import android.os.Build
import android.view.WindowInsets
import androidx.annotation.RequiresApi

/** Padding to keep content clear of system UI, in pixels. Plain ints so the math runs in unit tests. */
internal data class BarInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** Per side maximum. */
    fun max(other: BarInsets) = BarInsets(
        maxOf(left, other.left), maxOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom),
    )

    companion object {
        val NONE = BarInsets(0, 0, 0, 0)
    }
}

/** System bars plus display cutout as one inset, for API 28 and up. */
internal fun systemBarInsets(windowInsets: WindowInsets): BarInsets =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        barsAndCutoutApi30(windowInsets)
    } else {
        @Suppress("DEPRECATION")
        val bars = BarInsets(
            windowInsets.systemWindowInsetLeft, windowInsets.systemWindowInsetTop,
            windowInsets.systemWindowInsetRight, windowInsets.systemWindowInsetBottom,
        )
        val cutout = windowInsets.displayCutout
            ?.let { BarInsets(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom) }
            ?: BarInsets.NONE
        bars.max(cutout)
    }

@RequiresApi(Build.VERSION_CODES.R)
private fun barsAndCutoutApi30(windowInsets: WindowInsets): BarInsets {
    val i = windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    return BarInsets(i.left, i.top, i.right, i.bottom)
}

/** Insets to return from a listener that applied them itself, so children do not apply them again. */
internal fun consumedInsets(windowInsets: WindowInsets): WindowInsets =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowInsets.CONSUMED
    } else {
        @Suppress("DEPRECATION")
        windowInsets.consumeSystemWindowInsets()
    }
