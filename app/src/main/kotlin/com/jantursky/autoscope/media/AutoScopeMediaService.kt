package com.jantursky.autoscope.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.jantursky.autoscope.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Entry point for Android Auto hosts that launch only media apps from unknown sources. Shows the
 * [MediaTree] as browsable text rows; nothing is ever played. A browse list refreshes when the part of the
 * report it shows changes, at most once per timer tick.
 */
class AutoScopeMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = container
        session = MediaSessionCompat(this, "AutoScope").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId == MediaTree.MARK) app.mark("car media")
                    setState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onPlay() = setState(PlaybackStateCompat.STATE_PAUSED)
            })
            setState(PlaybackStateCompat.STATE_PAUSED)
            isActive = true
        }
        sessionToken = session.sessionToken
        app.eventLog.add("Media service created")
        app.acquireTicker()
        scope.launch {
            var shown = app.repository.snapshot.value
            app.repository.snapshot.collect { next ->
                MediaTree.changedViews(shown, next).forEach { notifyChildrenChanged(it.name) }
                shown = next
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        container.releaseTicker()
        session.release()
        super.onDestroy()
    }

    // Personal tool installed outside Play: any client may browse, like the car host validator.
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(MediaTree.ROOT, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val nodes = MediaTree.children(parentId, container.repository.snapshot.value)
        result.sendResult(nodes.map(::item).toMutableList())
    }

    private fun item(node: BrowseNode): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(node.id)
            .setTitle(node.title)
            .setSubtitle(node.subtitle)
            .build()
        val flag = if (node.kind == BrowseNode.Kind.ACTION) MediaItem.FLAG_PLAYABLE else MediaItem.FLAG_BROWSABLE
        return MediaItem(description, flag)
    }

    private fun setState(state: Int) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(state, 0, 0f)
                .build(),
        )
    }
}
