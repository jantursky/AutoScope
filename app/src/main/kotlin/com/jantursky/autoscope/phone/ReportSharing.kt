package com.jantursky.autoscope.phone

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jantursky.autoscope.core.SavedReport

/** Builds the share intent for a saved report: JSON and text twins as content URIs from the app's FileProvider. */
internal fun shareIntent(context: Context, report: SavedReport, chooserTitle: String): Intent {
    val authority = "${context.packageName}.reports"
    val uris = arrayListOf(
        FileProvider.getUriForFile(context, authority, report.text),
        FileProvider.getUriForFile(context, authority, report.json),
    )
    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("*/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .putExtra(Intent.EXTRA_SUBJECT, report.id)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData makes the read grant apply to the chooser's targets.
    send.clipData = ClipData.newRawUri(report.id, uris[0]).also { clip ->
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    }
    return Intent.createChooser(send, chooserTitle)
}
