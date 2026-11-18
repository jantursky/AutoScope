package com.jantursky.autoscope.phone

import android.graphics.Insets
import android.os.Build
import android.view.WindowInsets

/** System bars plus display cutout as one inset, for API 28 and up. */
internal fun systemBarInsets(windowInsets: WindowInsets): Insets =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    } else {
        @Suppress("DEPRECATION")
        val bars = Insets.of(
            windowInsets.systemWindowInsetLeft, windowInsets.systemWindowInsetTop,
            windowInsets.systemWindowInsetRight, windowInsets.systemWindowInsetBottom,
        )
        val cutout = windowInsets.displayCutout
            ?.let { Insets.of(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom) }
            ?: Insets.NONE
        Insets.max(bars, cutout)
    }
