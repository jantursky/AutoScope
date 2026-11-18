package com.jantursky.autoscope.phone

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.core.Snapshot

/** The Car tab: the verdict card, the drawn car and a legend card whose rows say the same thing in numbers. Updated in place by [bind]. */
internal class CarPageView(context: Context) : LinearLayout(context) {
    private val idle = carPageModel(CarTelemetry.EMPTY, Snapshot.EMPTY)
    private val verdict = VerdictCardView(context)
    private val picture = CarPictureView(context, idle.picture)
    private val rows = idle.picture.rows.map { RowView(context) }

    init {
        orientation = VERTICAL
        addView(verdict)
        addView(context.card().apply {
            addView(picture, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        addView(context.card().apply {
            rows.forEachIndexed { index, row ->
                if (index > 0) addView(context.divider())
                addView(row)
            }
        })
        bind(idle)
    }

    fun bind(next: CarPageModel) {
        verdict.bind(next.verdict, next.wifi)
        picture.bind(next.picture)
        rows.zip(next.picture.rows).forEach { (view, row) -> view.bind(row) }
    }
}
