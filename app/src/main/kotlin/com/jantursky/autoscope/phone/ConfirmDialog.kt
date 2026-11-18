package com.jantursky.autoscope.phone

import android.app.AlertDialog
import android.content.Context
import com.jantursky.autoscope.R

/** Delete confirmation. The dialog takes the activity theme, so it follows light and dark. */
internal fun Context.confirmDelete(title: String, message: String, onConfirm: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.delete) { _, _ -> onConfirm() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
