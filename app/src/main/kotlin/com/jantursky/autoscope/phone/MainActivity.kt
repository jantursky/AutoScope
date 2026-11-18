package com.jantursky.autoscope.phone

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jantursky.autoscope.R
import com.jantursky.autoscope.Settings
import com.jantursky.autoscope.container
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.SavedReport
import com.jantursky.autoscope.core.Snapshot
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val STATE_TAB = "selected_tab"
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Phone screen: sticky connection bar and tab strip, one page below them (the car, a diagnostics group, changes,
 * saved reports or help). Views are built in code. Live rows are updated in place so scrolling never
 * jumps. The ticker runs only while the screen is visible.
 */
class MainActivity : Activity() {

    private lateinit var header: HeaderView
    private lateinit var tabBar: TabBarView
    private lateinit var scroll: ScrollView
    private lateinit var carPage: CarPageView
    private lateinit var groupHost: FrameLayout
    private lateinit var changesPage: LinearLayout
    private lateinit var changesHolder: LinearLayout
    private lateinit var savedList: LinearLayout
    private lateinit var helpPage: LinearLayout
    private var groupCard: GroupCardView? = null
    private var models = emptyList<GroupModel>()
    private var tabs = tabModels(emptyList())
    private var selectedKey: String? = null
    private var shownKey: String? = null
    private val scrollPositions = mutableMapOf<String, Int>()
    private var scope: CoroutineScope? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(themedContext(newBase, Settings(newBase).themeMode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedKey = savedInstanceState?.getString(STATE_TAB)
        setContentView(buildContent())
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, selectedKey)
    }

    override fun onStart() {
        super.onStart()
        container.acquireTicker()
        val collector = CoroutineScope(Job() + Dispatchers.Main.immediate)
        scope = collector
        collector.launch { container.repository.snapshot.collect(::show) }
        collector.launch {
            combine(container.carSources.telemetry, container.repository.snapshot, ::carPicture)
                .distinctUntilChanged()
                .collect(carPage::bind)
        }
        showSavedReports()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        container.releaseTicker()
        super.onStop()
    }

    private fun buildContent(): View {
        header = HeaderView(this, container.settings) { action ->
            when (action) {
                MenuAction.Save -> saveSnapshot()
                is MenuAction.Interval -> container.settings.refreshMillis = action.millis
                is MenuAction.Theme -> {
                    container.settings.themeMode = action.mode
                    recreate()
                }
            }
        }
        tabBar = TabBarView(this) { selectedKey = it.key; render() }
        carPage = CarPageView(this)
        groupHost = FrameLayout(this)
        changesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        changesPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle(R.string.changes_title))
            addView(changesHolder)
        }
        savedList = card()
        helpPage = helpPage()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(carPage, groupHost, changesPage, savedList, helpPage).forEach(::addView)
        }
        scroll = ScrollView(this).apply {
            id = R.id.report_scroll
            isVerticalScrollBarEnabled = false
            setPadding(dp(16), dp(8), dp(16), dp(16))
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.background))
            addView(header)
            addView(tabBar)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            applySystemBarPadding(this)
        }
    }

    /** Keeps the bar and content clear of the status bar, navigation bar and display cutout (edge-to-edge on API 35+). */
    private fun applySystemBarPadding(view: View) {
        view.setOnApplyWindowInsetsListener { v, windowInsets ->
            val bars = systemBarInsets(windowInsets)
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            consumedInsets(windowInsets)
        }
    }

    private fun sectionTitle(text: Int) = label(13f, R.color.text_secondary, bold = true).apply {
        setText(text)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun show(snapshot: Snapshot) {
        header.bind(connectionPill(snapshot), CLOCK.format(LocalTime.now()))
        models = groupModels(snapshot)
        tabs = tabModels(models)
        render()
    }

    /** Applies tabs and the selection to the views; swaps the page only when the selected tab changed. */
    private fun render() {
        val selected = resolveTab(tabs, selectedKey)
        tabBar.bind(tabs, selected.id)
        val id = selected.id
        if (selected.id.key != shownKey) switchTo(selected.id)
        if (id is TabId.OfGroup) {
            val model = models.first { it.group == id.group }
            val card = groupCard
            if (card == null || card.group != id.group) {
                groupHost.removeAllViews()
                groupCard = GroupCardView(this, model).also(groupHost::addView)
            } else {
                card.bind(model)
            }
        }
    }

    private fun switchTo(id: TabId) {
        shownKey?.let { scrollPositions[it] = scroll.scrollY }
        shownKey = id.key
        carPage.visibility = if (id == TabId.Car) View.VISIBLE else View.GONE
        groupHost.visibility = if (id is TabId.OfGroup) View.VISIBLE else View.GONE
        changesPage.visibility = if (id == TabId.Changes) View.VISIBLE else View.GONE
        savedList.visibility = if (id == TabId.Reports) View.VISIBLE else View.GONE
        helpPage.visibility = if (id == TabId.Help) View.VISIBLE else View.GONE
        scroll.post { scroll.scrollTo(0, scrollPositions[id.key] ?: 0) }
    }

    private fun saveSnapshot() {
        val saved = container.mark("Phone button")
        Toast.makeText(this, getString(R.string.saved_toast, saved.id), Toast.LENGTH_SHORT).show()
        showSavedReports()
    }

    private fun showSavedReports() {
        savedList.removeAllViews()
        val reports = container.reports.list()
        showChanges(reports)
        if (reports.isEmpty()) {
            savedList.addView(
                label(13f, R.color.text_tertiary).apply {
                    setText(R.string.no_saved_reports)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(24), dp(16), dp(24))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            return
        }
        val now = Instant.now()
        reports.forEachIndexed { index, report ->
            if (index > 0) savedList.addView(divider())
            val text = savedReportLabel(report.id, now, ZoneId.systemDefault(), Locale.getDefault())
            savedList.addView(
                savedRow(text) { startActivity(shareIntent(this, report, getString(R.string.share_chooser))) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)),
            )
        }
    }

    private fun showChanges(reports: List<SavedReport>) {
        val texts = reports.take(2).mapNotNull { runCatching { it.text.readText() }.getOrNull() }
        changesHolder.removeAllViews()
        changesHolder.addView(changesCard(if (texts.size == 2) texts[1] to texts[0] else null))
    }

    private fun savedRow(text: SavedReportLabel, onShare: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(8), 0)
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(14f, R.color.text_primary).apply { this.text = text.title })
            if (text.whenText.isNotEmpty()) addView(label(12f, R.color.text_secondary).apply { this.text = text.whenText })
        }
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            TextView(context).apply {
                setText(R.string.share)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.accent))
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = dp(6)
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_share, 0, 0, 0)
                tappable(R.drawable.pill_tonal) { onShare() }
                // After tappable(): setting a background resets the padding to the drawable's own.
                setPadding(dp(12), 0, dp(14), 0)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)),
        )
    }
}
