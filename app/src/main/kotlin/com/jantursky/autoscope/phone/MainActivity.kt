package com.jantursky.autoscope.phone

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.jantursky.autoscope.R
import com.jantursky.autoscope.Settings
import com.jantursky.autoscope.container
import com.jantursky.autoscope.core.Group
import com.jantursky.autoscope.core.SavedReport
import com.jantursky.autoscope.core.ReportFormat
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
private const val REQUEST_CAR_PERMISSIONS = 1
private val CAR_RUNTIME_PERMISSIONS = listOf(
    "com.google.android.gms.permission.CAR_SPEED",
    "com.google.android.gms.permission.CAR_FUEL",
    "com.google.android.gms.permission.CAR_MILEAGE",
    "android.permission.ACCESS_FINE_LOCATION",
)
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
    private lateinit var connectionExtras: ConnectionExtrasView
    private lateinit var screenView: ScreenPreviewView
    private lateinit var groupHost: FrameLayout
    private lateinit var grantButton: Button
    private lateinit var changesPage: LinearLayout
    private lateinit var changesHolder: LinearLayout
    private lateinit var reportsPage: ReportsPageView
    private lateinit var helpPage: LinearLayout
    private var groupCard: GroupCardView? = null
    private var models = emptyList<GroupModel>()
    private var tabs = tabModels(emptyList())
    private var selectedKey: String? = null
    private var shownKey: String? = null
    private val scrollPositions = mutableMapOf<String, Int>()
    private var scope: CoroutineScope? = null

    /** `--ez demo true`: shows a fixed fake report for screenshots; nothing live is read, saved or listed. */
    private val demo by lazy { intent.getBooleanExtra("demo", false) }

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
        if (demo) {
            show(DemoData.snapshot)
            carPage.bind(carPageModel(DemoData.telemetry, DemoData.snapshot))
            showSavedReports()
            return
        }
        container.acquireTicker()
        val collector = CoroutineScope(Job() + Dispatchers.Main.immediate)
        scope = collector
        collector.launch { container.repository.snapshot.collect(::show) }
        collector.launch {
            combine(container.carSources.telemetry, container.repository.snapshot, ::carPageModel)
                .distinctUntilChanged()
                .collect(carPage::bind)
        }
        showSavedReports()
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
        if (!demo) container.releaseTicker()
        super.onStop()
    }

    private fun buildContent(): View {
        header = HeaderView(this, container.settings) { action ->
            when (action) {
                MenuAction.Save -> if (!demo) saveSnapshot()
                MenuAction.Copy -> copyReport()
                is MenuAction.Interval -> container.settings.refreshMillis = action.millis
                is MenuAction.Theme -> {
                    container.settings.themeMode = action.mode
                    recreate()
                }
            }
        }
        tabBar = TabBarView(this) { selectedKey = it.key; render() }
        carPage = CarPageView(this)
        connectionExtras = ConnectionExtrasView(this)
        screenView = ScreenPreviewView(this)
        groupHost = FrameLayout(this)
        grantButton = Button(this).apply {
            setText(R.string.grant_car_permissions)
            isAllCaps = false
            setOnClickListener { requestCarPermissions() }
        }
        changesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        changesPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading(R.string.changes_title))
            addView(changesHolder)
        }
        reportsPage = ReportsPageView(this, ::shareReport, ::confirmDeleteReport, ::confirmDeleteAll)
        helpPage = helpPage()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(carPage, connectionExtras, screenView, grantButton, groupHost, changesPage, reportsPage, helpPage).forEach(::addView)
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

    private fun show(snapshot: Snapshot) {
        header.bind(connectionPill(snapshot), CLOCK.format(LocalTime.now()))
        val timeline = if (demo) {
            DemoData.timeline()
        } else {
            connectionTimeline(
                CarConnectionMonitor.shared(this).tracker.history(),
                SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault(),
            )
        }
        connectionExtras.bind(timeline, wifiQuality(snapshot))
        screenView.bind(screenPreview(snapshot))
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
        updateGrantButton()
        connectionExtras.visibility = if (id == TabId.OfGroup(Group.CONNECTION)) View.VISIBLE else View.GONE
        screenView.visibility = if (id == TabId.OfGroup(Group.SCREEN)) View.VISIBLE else View.GONE
        changesPage.visibility = if (id == TabId.Changes) View.VISIBLE else View.GONE
        reportsPage.visibility = if (id == TabId.Reports) View.VISIBLE else View.GONE
        helpPage.visibility = if (id == TabId.Help) View.VISIBLE else View.GONE
        scroll.post { scroll.scrollTo(0, scrollPositions[id.key] ?: 0) }
    }

    /** Runtime permissions the car data needs and the app does not hold yet. */
    private fun missingCarPermissions(): List<String> =
        CAR_RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /** Shown only on the Permissions tab while a runtime permission is missing. */
    private fun updateGrantButton() {
        val show = !demo && shownKey == TabId.OfGroup(Group.PERMISSIONS).key && missingCarPermissions().isNotEmpty()
        grantButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Asks on the phone, where the dialog works even when Android Auto blocks it in the car. */
    private fun requestCarPermissions() {
        val missing = missingCarPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_CAR_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAR_PERMISSIONS) {
            container.repository.refresh()
            updateGrantButton()
        }
    }

    private fun saveSnapshot() {
        val saved = container.mark("Phone button")
        Toast.makeText(this, getString(R.string.saved_toast, saved.id), Toast.LENGTH_SHORT).show()
        showSavedReports()
    }

    private fun copyReport() {
        val text = ReportFormat.text(if (demo) DemoData.snapshot else container.repository.snapshot.value, System.currentTimeMillis(), "Copied from the phone")
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.live_report), text))
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareReport(report: SavedReport) =
        startActivity(shareIntent(this, report, getString(R.string.share_chooser)))

    private fun confirmDeleteReport(report: SavedReport) {
        val label = savedReportLabel(report.id, Instant.now(), ZoneId.systemDefault(), Locale.getDefault())
        val name = listOf(label.title, label.whenText.substringBefore("  ·")).filter { it.isNotEmpty() }.joinToString(", ")
        confirmDelete(getString(R.string.delete_report_title), getString(R.string.delete_report_message, name)) {
            deleteReport(report)
            showSavedReports()
        }
    }

    private fun confirmDeleteAll() {
        val reports = container.reports.list()
        confirmDelete(getString(R.string.delete_all_title, reports.size), getString(R.string.delete_all_message)) {
            deleteReports(reports)
            showSavedReports()
        }
    }

    private fun showSavedReports() {
        val reports = if (demo) emptyList() else container.reports.list()
        showChanges(reports)
        reportsPage.bind(reports)
    }

    private fun showChanges(reports: List<SavedReport>) {
        val texts = reports.take(2).mapNotNull { runCatching { it.text.readText() }.getOrNull() }
        changesHolder.removeAllViews()
        changesHolder.addView(changesCard(if (texts.size == 2) texts[1] to texts[0] else null))
    }
}
