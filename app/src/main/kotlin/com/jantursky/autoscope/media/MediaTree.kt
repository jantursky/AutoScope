package com.jantursky.autoscope.media

import com.jantursky.autoscope.car.CarRows
import com.jantursky.autoscope.car.CarView
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.rowTitle

/** One row of the media browse tree. Pure data, so the tree is testable without Android. */
data class BrowseNode(val id: String, val title: String, val subtitle: String?, val kind: Kind) {
    enum class Kind {
        /** Opens a list of rows. */
        FOLDER,

        /** A plain reading: browsable without children, so tapping it does nothing. */
        READING,

        /** Tapping it runs the action behind [id]. */
        ACTION,
    }
}

/**
 * The report as a media browse tree, for cars that only launch media apps from unknown sources (the real
 * Android Auto host refuses sideloaded Car App Library apps). A media service has no CarContext, so only
 * the views that do not need one are offered: the screen, touch and car hardware data stay in the template UI.
 */
object MediaTree {
    const val ROOT = "root"
    const val MARK = "action:mark"

    val VIEWS = listOf(CarView.OVERVIEW, CarView.NETWORK, CarView.PERMISSIONS, CarView.LOG)

    fun children(parentId: String, snapshot: Snapshot): List<BrowseNode> {
        if (parentId == ROOT) return rootRows()
        val view = VIEWS.firstOrNull { it.name == parentId } ?: return emptyList()
        return view.fields(snapshot).mapIndexed { i, field ->
            val title = if (view == CarView.LOG) "#${i + 1}" else field.rowTitle()
            val text = if (view == CarView.LOG) "${field.label} ${field.value ?: CarRows.fieldText(field)}" else CarRows.fieldText(field)
            BrowseNode("$parentId:$i", title, text, BrowseNode.Kind.READING)
        }.ifEmpty { listOf(BrowseNode("$parentId:empty", "No data", null, BrowseNode.Kind.READING)) }
    }

    /** Views whose rows differ between [old] and [new], ignoring live values, as the car template does. */
    fun changedViews(old: Snapshot, new: Snapshot): List<CarView> = VIEWS.filter { it.visible(old) != it.visible(new) }

    private fun rootRows(): List<BrowseNode> =
        VIEWS.map { BrowseNode(it.name, it.title, it.summary, BrowseNode.Kind.FOLDER) } +
            BrowseNode(MARK, "Mark this moment", "Saves a report now", BrowseNode.Kind.ACTION)
}
