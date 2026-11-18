package com.jantursky.autoscope.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat

/** Thin glue from [UiRow]s to Car App Library models. */
object Templates {

    fun itemList(rows: List<UiRow>, onAction: (RowAction) -> Unit): ItemList =
        ItemList.Builder().apply { rows.forEach { addItem(row(it, onAction)) } }.build()

    fun row(row: UiRow, onAction: (RowAction) -> Unit): Row = Row.Builder().apply {
        setTitle(row.title)
        row.lines.forEach { addText(it) }
        row.action?.let { action ->
            val click = OnClickListener { onAction(action) }
            // CarContext.requestPermissions should be called from a ParkedOnlyOnClickListener (its javadoc).
            setOnClickListener(if (action == RowAction.GRANT_PERMISSIONS) ParkedOnlyOnClickListener.create(click) else click)
            if (action == RowAction.NEXT_PAGE || action == RowAction.OPEN_LOG) setBrowsable(true)
        }
    }.build()

    /** A plain pane row; pane rows cannot be clickable (ROW_CONSTRAINTS_PANE). */
    fun paneRow(row: UiRow): Row = Row.Builder().setTitle(row.title).apply { row.lines.forEach { addText(it) } }.build()

    /** Tab contents must not carry a title, header or action strip (TabContents javadoc). */
    fun tabList(list: ItemList): ListTemplate = ListTemplate.Builder().setSingleList(list).build()

    fun tabPane(pane: Pane): PaneTemplate = PaneTemplate.Builder(pane).build()

    /** A pushed list screen with a back action. Header is used from Car App API 7, the older title otherwise. */
    fun screenList(title: String, list: ItemList, apiLevel: Int): ListTemplate {
        val builder = ListTemplate.Builder().setSingleList(list)
        if (apiLevel >= CarAppApiLevels.LEVEL_7) {
            builder.setHeader(header(title))
        } else {
            @Suppress("DEPRECATION")
            builder.setTitle(title).setHeaderAction(Action.BACK)
        }
        return builder.build()
    }

    fun screenPane(title: String, pane: Pane, apiLevel: Int): PaneTemplate {
        val builder = PaneTemplate.Builder(pane)
        if (apiLevel >= CarAppApiLevels.LEVEL_7) {
            builder.setHeader(header(title))
        } else {
            @Suppress("DEPRECATION")
            builder.setTitle(title).setHeaderAction(Action.BACK)
        }
        return builder.build()
    }

    fun header(title: String): Header = Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build()

    /**
     * A tab icon: the view's first letter on a bitmap (TabTemplate requires an icon per tab). Build once per
     * Session and reuse, so the tabs stay equal between refreshes.
     */
    fun letterIcon(letter: Char): CarIcon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.8f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(letter.toString(), size / 2f, baseline, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).setTint(CarColor.DEFAULT).build()
    }
}
