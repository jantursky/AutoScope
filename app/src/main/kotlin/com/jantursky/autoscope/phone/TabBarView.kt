package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.jantursky.autoscope.R

private const val INDICATOR_BASE_DP = 100
private const val SLIDE_MS = 200L
private const val ROLE_DESCRIPTION_KEY = "AccessibilityNodeInfo.roleDescription"

/**
 * Sticky scrollable tab strip with a sliding underline. [bind] updates tabs in place and only rebuilds
 * them when the set of tabs changes. Tabs expose the tab role, selection and position to accessibility.
 */
internal class TabBarView(context: Context, private val onSelect: (TabId) -> Unit) : LinearLayout(context) {
    private val tabRow = LinearLayout(context).apply {
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                    1, childCount, false, AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE,
                )
            }
        }
    }
    private val indicator = View(context).apply { setBackgroundColor(context.color(R.color.accent)) }
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(
            FrameLayout(context).apply {
                addView(tabRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(
                    indicator,
                    FrameLayout.LayoutParams(context.dp(INDICATOR_BASE_DP), context.dp(3), Gravity.BOTTOM or Gravity.START),
                )
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }
    private var shown = emptyList<Pair<String, String>>()
    private var views = emptyList<TabView>()
    private var selectedView: TabView? = null
    private var indicatorLeft = -1
    private var indicatorWidth = -1

    init {
        orientation = VERTICAL
        setBackgroundColor(context.color(R.color.background))
        indicator.pivotX = 0f
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(40)))
        addView(context.divider())
        // A badge or a weight change resizes tabs without the selection changing; keep the underline on its tab.
        tabRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> selectedView?.let { placeIndicator(it, animate = false) } }
    }

    fun bind(tabs: List<TabModel>, selected: TabId) {
        val ids = tabs.map { it.id.key to it.title }
        if (ids != shown) {
            shown = ids
            tabRow.removeAllViews()
            views = tabs.mapIndexed { index, tab ->
                TabView(context, tab.title, index) { onSelect(tab.id) }.also(tabRow::addView)
            }
            selectedView = null
        }
        tabs.forEachIndexed { index, tab ->
            val view = views[index]
            view.update(tab.badge, tab.id == selected)
            if (view.isSelected && view !== selectedView) {
                val first = selectedView == null
                selectedView = view
                view.post {
                    placeIndicator(view, animate = !first)
                    scroll.smoothScrollTo(view.left - (scroll.width - view.width) / 2, 0)
                }
            }
        }
    }

    private fun placeIndicator(tab: View, animate: Boolean) {
        if (tab.width == 0 || (tab.left == indicatorLeft && tab.width == indicatorWidth)) return
        indicatorLeft = tab.left
        indicatorWidth = tab.width
        val scaleX = tab.width / context.dp(INDICATOR_BASE_DP).toFloat()
        indicator.animate().cancel()
        if (animate) {
            indicator.animate().translationX(tab.left.toFloat()).scaleX(scaleX).setDuration(SLIDE_MS).start()
        } else {
            indicator.translationX = tab.left.toFloat()
            indicator.scaleX = scaleX
        }
    }

    /** Text tab: label in the primary colour and medium weight when selected, then a small warning count. */
    private class TabView(
        context: Context,
        private val name: String,
        private val position: Int,
        onClick: () -> Unit,
    ) : LinearLayout(context) {
        private val title = context.label(14f, R.color.text_secondary).apply { text = name }
        private val badge = context.label(11f, R.color.status_warning, bold = true).apply { visibility = View.GONE }

        init {
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(badge, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(4)
                bottomMargin = context.dp(8)
            })
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            tappable(R.drawable.tab_ripple, onClick)
            // After tappable(): setting a background resets the padding to the drawable's own.
            setPadding(context.dp(16), 0, context.dp(16), 0)
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.extras.putCharSequence(ROLE_DESCRIPTION_KEY, context.getString(R.string.tab_role))
                    info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, position, 1, false, isSelected)
                }
            }
        }

        fun update(problems: Int, selected: Boolean) {
            isSelected = selected
            title.setTextColor(context.color(if (selected) R.color.text_primary else R.color.text_secondary))
            title.typeface = if (selected) Typeface.create(Typeface.DEFAULT, MEDIUM_WEIGHT, false) else Typeface.DEFAULT
            badge.visibility = if (problems > 0) View.VISIBLE else View.GONE
            badge.setTextIfChanged(problems.toString())
            contentDescription = if (problems > 0) context.getString(R.string.tab_problems, name, problems) else name
        }
    }

    private companion object {
        const val MEDIUM_WEIGHT = 500
    }
}
