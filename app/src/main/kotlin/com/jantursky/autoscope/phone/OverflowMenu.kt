package com.jantursky.autoscope.phone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.jantursky.autoscope.R
import com.jantursky.autoscope.ThemeMode

private val INTERVAL_LABELS = listOf(
    R.string.interval_500, R.string.interval_1000, R.string.interval_2000,
    R.string.interval_5000, R.string.interval_10000,
)
private val THEME_LABELS = mapOf(
    ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark,
)
private const val MENU_WIDTH_DP = 248
private const val RADIO_CLASS = "android.widget.RadioButton"

private fun intervalLabel(millis: Long): Int = INTERVAL_LABELS[REFRESH_CHOICES_MS.indexOf(millis)]

/** Opens the overflow menu under [anchor]; [onAction] runs after the popup closed. */
internal fun showOverflowMenu(anchor: View, sections: List<MenuSection>, onAction: (MenuAction) -> Unit) {
    val context = anchor.context
    val popup = PopupWindow(anchor.context).apply {
        isFocusable = true
        isOutsideTouchable = true
        // Without a background drawable, outside taps would not dismiss on older releases.
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        width = context.dp(MENU_WIDTH_DP)
        height = ViewGroup.LayoutParams.WRAP_CONTENT
    }
    val card = context.card().apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sections.forEachIndexed { index, section ->
            if (index > 0) addView(context.divider())
            addView(context.sectionTitle(section))
            section.entries.forEach { entry ->
                addView(context.menuRow(entry, section.kind != MenuSectionKind.SNAPSHOT) {
                    popup.dismiss()
                    onAction(entry.action)
                })
            }
        }
    }
    popup.contentView = card
    popup.showAsDropDown(anchor, 0, 0, Gravity.END)
}

private fun Context.sectionTitle(section: MenuSection) = when (section.kind) {
    MenuSectionKind.SNAPSHOT -> getString(R.string.snapshot)
    MenuSectionKind.INTERVAL -> {
        val current = section.entries.first { it.checked }.action as MenuAction.Interval
        "${getString(R.string.refresh_interval)} · ${getString(intervalLabel(current.millis))}"
    }
    MenuSectionKind.THEME -> getString(R.string.theme)
}.let { text ->
    label(12f, R.color.text_secondary, bold = true).apply {
        this.text = text
        setPadding(dp(16), dp(12), dp(16), dp(4))
    }
}

private fun Context.menuRow(entry: MenuEntry, radio: Boolean, onClick: () -> Unit): View {
    val text = getString(
        when (val action = entry.action) {
            MenuAction.Save -> R.string.save_snapshot
            MenuAction.Copy -> R.string.copy_report
            is MenuAction.Interval -> intervalLabel(action.millis)
            is MenuAction.Theme -> THEME_LABELS.getValue(action.mode)
        },
    )
    return LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        val slot = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            when {
                !radio -> setImageResource(if (entry.action == MenuAction.Copy) R.drawable.ic_copy else R.drawable.ic_save)
                entry.checked -> setImageResource(R.drawable.ic_check)
            }
        }
        addView(slot, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        addView(label(14f, R.color.text_primary).apply { this.text = text })
        tappable(R.drawable.tab_ripple, onClick)
        // After tappable(): setting a background resets the padding to the drawable's own.
        setPadding(dp(16), 0, dp(16), 0)
        if (radio) {
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RADIO_CLASS
                    info.isCheckable = true
                    info.isChecked = entry.checked
                }
            }
        }
    }
}
