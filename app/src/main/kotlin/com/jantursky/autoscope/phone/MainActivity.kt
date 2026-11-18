package com.jantursky.autoscope.phone

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.jantursky.autoscope.R
import com.jantursky.autoscope.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phone screen: live report, save and share, refresh interval. Views are built in code because the
 * screen is a single vertical list. The ticker runs only while the screen is visible.
 */
class MainActivity : Activity() {

    private lateinit var liveReport: TextView
    private lateinit var savedList: LinearLayout
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
        collector.launch {
            container.repository.snapshot.collect { liveReport.text = liveReportText(it).ifEmpty { getString(R.string.waiting_for_data) } }
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
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        column.addView(Button(this).apply {
            setText(R.string.save_snapshot)
            setOnClickListener { saveSnapshot() }
        })
        column.addView(sectionTitle(R.string.refresh_interval))
        column.addView(intervalSpinner())
        column.addView(sectionTitle(R.string.live_report))
        liveReport = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        column.addView(liveReport)
        column.addView(sectionTitle(R.string.saved_reports))
        savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(savedList)
        return ScrollView(this).apply { addView(column) }
    }

    private fun saveSnapshot() {
        val saved = container.mark("Phone button")
        Toast.makeText(this, getString(R.string.saved_toast, saved.id), Toast.LENGTH_SHORT).show()
        showSavedReports()
    }

    private fun intervalSpinner(): Spinner {
        val labels = listOf(
            R.string.interval_500, R.string.interval_1000, R.string.interval_2000,
            R.string.interval_5000, R.string.interval_10000,
        ).map(::getString)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(closestRefreshChoice(container.settings.refreshMillis))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    container.settings.refreshMillis = REFRESH_CHOICES_MS[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun showSavedReports() {
        savedList.removeAllViews()
        val reports = container.reports.list()
        if (reports.isEmpty()) {
            savedList.addView(TextView(this).apply { setText(R.string.no_saved_reports) })
            return
        }
        for (report in reports) {
            savedList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(context).apply { text = report.id }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(context).apply {
                    setText(R.string.share)
                    setOnClickListener { startActivity(shareIntent(context, report, getString(R.string.share_chooser))) }
                })
            })
        }
    }

    private fun sectionTitle(text: Int) = TextView(this).apply {
        setText(text)
        textSize = 18f
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
