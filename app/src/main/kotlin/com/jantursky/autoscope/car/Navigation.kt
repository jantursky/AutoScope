package com.jantursky.autoscope.car

/**
 * Screen depth rules. A host allows five templates per task and the fifth must not be a list, so a list
 * is pushed at most to depth [MAX_LIST_DEPTH] and a pane or map screen at most to [MAX_DEPTH]. The root
 * screen (tabs or the older menu) is depth 1.
 */
object Navigation {
    const val MAX_LIST_DEPTH = 4
    const val MAX_DEPTH = 5

    /** Whether a screen of [kind] may be pushed onto a stack of [stackSize] screens. */
    fun mayPush(kind: ViewKind, stackSize: Int): Boolean =
        stackSize + 1 <= if (kind == ViewKind.LIST) MAX_LIST_DEPTH else MAX_DEPTH
}
