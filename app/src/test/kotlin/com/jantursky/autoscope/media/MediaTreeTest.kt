package com.jantursky.autoscope.media

import com.jantursky.autoscope.car.CarView
import com.jantursky.autoscope.core.Field
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import com.jantursky.autoscope.core.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTreeTest {

    private fun field(label: String, value: String?, status: Status = Status.OK, reason: String? = null) =
        Field(label, value, "test", status, reason)

    private fun snapshot(vararg fields: Field) = Snapshot(mapOf(Group.CONNECTION to fields.toList()))

    @Test
    fun `root lists the offered views and the mark action`() {
        val root = MediaTree.children(MediaTree.ROOT, Snapshot.EMPTY)
        assertEquals(MediaTree.VIEWS.map { it.title } + "Mark this moment", root.map { it.title })
        assertEquals(BrowseNode.Kind.ACTION, root.last().kind)
        assertTrue(root.dropLast(1).all { it.kind == BrowseNode.Kind.FOLDER })
    }

    @Test
    fun `a view shows its fields as readings with the status in the subtitle`() {
        val rows = MediaTree.children(CarView.OVERVIEW.name, snapshot(field("Connection type", "Projection")))
        assertEquals("Connection type", rows.first().title)
        assertEquals("Projection (OK)", rows.first().subtitle)
        assertEquals(BrowseNode.Kind.READING, rows.first().kind)
    }

    @Test
    fun `unknown parent and empty view give no crash`() {
        assertTrue(MediaTree.children("nope", Snapshot.EMPTY).isEmpty())
        assertEquals("No data", MediaTree.children(CarView.OVERVIEW.name, Snapshot.EMPTY).single().title)
    }

    @Test
    fun `only views with different rows are reported as changed`() {
        val a = snapshot(field("Connection type", "Not connected"))
        val b = snapshot(field("Connection type", "Projection"))
        assertEquals(listOf(CarView.OVERVIEW), MediaTree.changedViews(a, b).filter { it == CarView.OVERVIEW })
        assertTrue(MediaTree.changedViews(a, a).isEmpty())
    }
}
