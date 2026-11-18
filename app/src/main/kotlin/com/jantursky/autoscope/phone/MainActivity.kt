package com.jantursky.autoscope.phone

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jantursky.autoscope.R
import com.jantursky.autoscope.Settings
import com.jantursky.autoscope.ThemeMode
import com.jantursky.autoscope.container
import com.jantursky.autoscope.core.Group
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
import kotlinx.coroutines.launch

private const val STATE_EXPANDED = "expanded_groups"
private val INTERVAL_LABELS = listOf(
    R.string.interval_500, R.string.interval_1000, R.string.interval_2000,
    R.string.interval_5000, R.string.interval_10000,
)
private val THEME_LABELS = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Phone screen: sticky connection bar, save, refresh and theme controls, live report cards and saved
 * reports. Views are built in code. Live rows are updated in place so scrolling never jumps. The ticker
 * runs only while the screen is visible.
 */
class MainActivity : Activity() {

    private lateinit var header: HeaderView
    private lateinit var liveCards: LinearLayout
    private lateinit var waiting: TextView
    private lateinit var updated: TextView
    private lateinit var toggleAll: TextView
    private lateinit var savedList: LinearLayout
    private var cards = emptyList<GroupCardView>()
    private val expanded = mutableMapOf<Group, Boolean>()
    private var scope: CoroutineScope? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(themedContext(newBase, Settings(newBase).themeMode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let { open ->
            Group.entries.forEach { expanded[it] = it.name in open }
        }
        setContentView(buildContent())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded.filterValues { it }.keys.map { it.name }))
    }

    override fun onStart() {
        super.onStart()
        container.acquireTicker()
        val collector = CoroutineScope(Job() + Dispatchers.Main.immediate)
        scope = collector
        collector.launch { container.repository.snapshot.collect(::show) }
        showSavedReports()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        container.releaseTicker()
        super.onStop()
    }

    private fun buildContent(): View {
        header = HeaderView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(controlsCard())
        column.addView(liveReportHeader())
        waiting = label(14f, R.color.text_tertiary).apply {
            setText(R.string.waiting_for_data)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        liveCards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(waiting)
        column.addView(liveCards)
        column.addView(sectionTitle(R.string.saved_reports))
        savedList = card()
        column.addView(savedList)
        val scroll = ScrollView(this).apply {
            id = R.id.report_scroll
            isVerticalScrollBarEnabled = false
            setPadding(dp(16), dp(8), dp(16), dp(16))
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            var scrolled = false
            setOnScrollChangeListener { _, _, y, _, _ ->
                if ((y > 0) != scrolled) {
                    scrolled = y > 0
                    header.setContentScrolled(scrolled)
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.background))
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            applySystemBarPadding(this)
        }
    }

    /** Keeps the bar and content clear of the status bar, navigation bar and display cutout (edge-to-edge on API 35+). */
    private fun applySystemBarPadding(view: View) {
        view.setOnApplyWindowInsetsListener { v, windowInsets ->
            val bars = systemBarInsets(windowInsets)
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }

    private fun controlsCard() = card().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(
            Button(this@MainActivity).apply {
                setText(R.string.save_snapshot)
                isAllCaps = false
                textSize = 15f
                setTextColor(color(R.color.on_accent))
                stateListAnimator = null
                minHeight = dp(48)
                tappable(R.drawable.button_primary) { saveSnapshot() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(controlLabel(R.string.refresh_interval))
        addView(
            SegmentedControl(this@MainActivity, INTERVAL_LABELS, closestRefreshChoice(container.settings.refreshMillis)) {
                container.settings.refreshMillis = REFRESH_CHOICES_MS[it]
            },
        )
        addView(controlLabel(R.string.theme))
        addView(
            SegmentedControl(this@MainActivity, THEME_LABELS, container.settings.themeMode.ordinal) {
                container.settings.themeMode = ThemeMode.entries[it]
                recreate()
            },
        )
    }

    private fun controlLabel(text: Int) = label(12f, R.color.text_secondary, bold = true).apply {
        setText(text)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun liveReportHeader() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, 0, 0)
        addView(label(13f, R.color.text_secondary, bold = true).apply { setText(R.string.live_report) })
        updated = label(12f, R.color.text_tertiary).apply { setPadding(dp(8), 0, 0, 0) }
        addView(updated, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toggleAll = label(13f, R.color.accent, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = dp(48)
            setPadding(dp(12), 0, dp(12), 0)
            tappable(R.drawable.ripple_rect) {
                val open = cards.none { it.isExpanded }
                cards.forEach { it.setExpanded(open) }
            }
        }
        addView(toggleAll)
    }

    private fun sectionTitle(text: Int) = label(13f, R.color.text_secondary, bold = true).apply {
        setText(text)
        setPadding(dp(8), dp(16), dp(8), dp(8))
    }

    private fun show(snapshot: Snapshot) {
        header.bind(connectionPill(snapshot))
        updated.setTextIfChanged(getString(R.string.updated_at, CLOCK.format(LocalTime.now())))
        val models = groupModels(snapshot)
        waiting.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
        if (cards.map { it.group } != models.map { it.group }) {
            liveCards.removeAllViews()
            cards = models.map { model ->
                GroupCardView(this, model, expanded[model.group] ?: !model.collapsedByDefault) {
                    expanded[model.group] = it
                    updateToggleLabel()
                }.also(liveCards::addView)
            }
            updateToggleLabel()
        } else {
            cards.zip(models).forEach { (view, model) -> view.bind(model) }
        }
    }

    private fun updateToggleLabel() {
        toggleAll.setText(if (cards.any { it.isExpanded }) R.string.collapse_all else R.string.expand_all)
    }

    private fun saveSnapshot() {
        val saved = container.mark("Phone button")
        Toast.makeText(this, getString(R.string.saved_toast, saved.id), Toast.LENGTH_SHORT).show()
        showSavedReports()
    }

    private fun showSavedReports() {
        savedList.removeAllViews()
        val reports = container.reports.list()
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
            savedList.addView(savedRow(text) { startActivity(shareIntent(this, report, getString(R.string.share_chooser))) })
        }
    }

    private fun savedRow(text: SavedReportLabel, onShare: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPadding(dp(16), dp(4), dp(8), dp(4))
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(14f, R.color.text_primary).apply { this.text = text.title })
            if (text.whenText.isNotEmpty()) addView(label(12f, R.color.text_secondary).apply { this.text = text.whenText })
        }
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            Button(context).apply {
                setText(R.string.share)
                isAllCaps = false
                textSize = 14f
                setTextColor(color(R.color.accent))
                stateListAnimator = null
                minHeight = dp(48)
                minWidth = dp(72)
                tappable(R.drawable.button_text) { onShare() }
            },
        )
    }
}
