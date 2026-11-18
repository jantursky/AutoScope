package com.jantursky.autoscope.spike.media

import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Spike B: a browsable tree whose rows are plain text, used as a read-only report.
 * Rows are FLAG_BROWSABLE without children, so tapping one does nothing harmful.
 */
class ReportBrowserService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "AutoScopeSpike").apply { isActive = true }
        sessionToken = session.sessionToken
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val items = when (parentId) {
            ROOT_ID -> listOf(
                row("overview", "Overview", "Spike B report"),
                row("device", "Device", "Open to see phone data"),
            )
            "overview" -> listOf(
                row("o1", "Client check", "If you read this, media browse works"),
                row("o2", "Time", System.currentTimeMillis().toString()),
            )
            "device" -> listOf(
                row("d1", "Model", "${Build.MANUFACTURER} ${Build.MODEL}"),
                row("d2", "Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"),
            )
            else -> emptyList()
        }
        result.sendResult(items.toMutableList())
    }

    private fun row(id: String, title: String, subtitle: String): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        // Leaf rows are marked browsable too: playable would start playback in the car.
        return MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
