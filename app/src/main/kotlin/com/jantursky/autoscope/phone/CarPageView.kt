package com.jantursky.autoscope.phone

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jantursky.autoscope.CarTelemetry
import com.jantursky.autoscope.core.Snapshot

/** The Car tab: the drawn car and a legend card whose rows say the same thing in numbers. Updated in place by [bind]. */
internal class CarPageView(context: Context) : LinearLayout(context) {
    private val idle = carPicture(CarTelemetry.EMPTY, Snapshot.EMPTY)
    private val picture = CarPictureView(context, idle)
    private val rows = idle.rows.map { RowView(context) }

    init {
        orientation = VERTICAL
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

    fun bind(next: CarPicture) {
        picture.bind(next)
        rows.zip(next.rows).forEach { (view, row) -> view.bind(row) }
    }
}
