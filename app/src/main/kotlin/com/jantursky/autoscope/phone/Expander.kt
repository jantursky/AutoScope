package com.jantursky.autoscope.phone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

internal const val EXPAND_MS = 180L

/** Shows or hides [body] by animating its height; [cardWidth] is the width it will be laid out with. */
internal fun animateBody(body: View, expand: Boolean, cardWidth: Int) {
    (body.tag as? Animator)?.cancel()
    val params = body.layoutParams
    if (cardWidth == 0 || !body.isAttachedToWindow) {
        body.visibility = if (expand) View.VISIBLE else View.GONE
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        return
    }
    val from: Int
    val to: Int
    if (expand) {
        body.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        from = 0
        to = body.measuredHeight
        params.height = 0
        body.visibility = View.VISIBLE
    } else {
        from = body.height
        to = 0
    }
    body.tag = ValueAnimator.ofInt(from, to).apply {
        duration = EXPAND_MS
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            params.height = it.animatedValue as Int
            body.requestLayout()
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                body.visibility = if (expand) View.VISIBLE else View.GONE
                body.requestLayout()
            }
        })
        start()
    }
}
