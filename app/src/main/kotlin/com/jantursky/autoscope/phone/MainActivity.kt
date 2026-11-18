package com.jantursky.autoscope.phone

import android.app.Activity
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
import com.jantursky.autoscope.container
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.Snapshot
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phone screen: connection status, save and refresh controls, live report cards and saved reports.
 * Views are built in code. Live rows are updated in place so scrolling never jumps. The ticker runs
 * only while the screen is visible.
 */
class MainActivity : Activity() {

    private lateinit var header: HeaderView
    private lateinit var liveCards: LinearLayout
    private lateinit var waiting: TextView
    private lateinit var savedList: LinearLayout
    private var cards = emptyList<GroupCardView>()
    private val expanded = mutableMapOf<Group, Boolean>()
    private var scope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
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
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header = HeaderView(this)
        column.addView(header)
        column.addView(controlsCard())
        column.addView(sectionTitle(R.string.live_report))
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
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            applySystemBarPadding(this)
        }
    }

    /** Keeps content clear of the status bar, navigation bar and display cutout (edge-to-edge on API 35+). */
    private fun applySystemBarPadding(view: View) {
        val margin = dp(16)
        view.setOnApplyWindowInsetsListener { v, windowInsets ->
            val bars = systemBarInsets(windowInsets)
            v.setPadding(bars.left + margin, bars.top + margin, bars.right + margin, bars.bottom + margin)
            WindowInsets.CONSUMED
        }
    }

    private fun controlsCard() = card().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(
            Button(this@MainActivity).apply {
                setText(R.string.save_snapshot)
                isAllCaps = false
                textSize = 16f
                setTextColor(color(R.color.on_accent))
                setBackgroundResource(R.drawable.button_primary)
                stateListAnimator = null
                minHeight = dp(52)
                setOnClickListener { saveSnapshot() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            label(14f, R.color.text_secondary, bold = true).apply {
                setText(R.string.refresh_interval)
                setPadding(0, dp(16), 0, dp(8))
            },
        )
        addView(
            IntervalSelector(this@MainActivity, closestRefreshChoice(container.settings.refreshMillis)) {
                container.settings.refreshMillis = REFRESH_CHOICES_MS[it]
            },
        )
    }

    private fun show(snapshot: Snapshot) {
        header.bind(connectionPill(snapshot))
        val models = groupModels(snapshot)
        waiting.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
        if (cards.map { it.group } != models.map { it.group }) {
            liveCards.removeAllViews()
            cards = models.map { model ->
                GroupCardView(this, model, expanded[model.group] ?: !model.collapsedByDefault) { expanded[model.group] = it }
                    .also(liveCards::addView)
            }
        } else {
            cards.zip(models).forEach { (view, model) -> view.bind(model) }
        }
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
                label(14f, R.color.text_tertiary).apply {
                    setText(R.string.no_saved_reports)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(32), dp(16), dp(32))
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
        minimumHeight = dp(64)
        setPadding(dp(16), dp(8), dp(8), dp(8))
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(15f, R.color.text_primary).apply { this.text = text.title })
            if (text.whenText.isNotEmpty()) addView(label(12f, R.color.text_secondary).apply { this.text = text.whenText })
        }
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            Button(context).apply {
                setText(R.string.share)
                isAllCaps = false
                setTextColor(color(R.color.accent))
                setBackgroundResource(R.drawable.button_text)
                stateListAnimator = null
                minHeight = dp(48)
                minWidth = dp(72)
                setOnClickListener { onShare() }
            },
        )
    }
}
