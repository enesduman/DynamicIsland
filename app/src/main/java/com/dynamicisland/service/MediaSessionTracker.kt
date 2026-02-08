package com.dynamicisland.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.MusicState

class MediaSessionTracker(private val ctx: Context) {
    private var msm: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (controllers != null && controllers.isNotEmpty()) {
            setController(controllers[0])
        } else {
            activeController = null
            IslandStateManager.updateMusic(MusicState())
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController()
        }
    }

    fun start() {
        try {
            msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(ctx, IslandNotificationListener::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionListener, cn)
            val sessions = msm?.getActiveSessions(cn)
            if (sessions != null && sessions.isNotEmpty()) {
                setController(sessions[0])
            }
            startPolling()
        } catch (_: Exception) {}
    }

    fun stop() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) {}
        try { activeController?.unregisterCallback(callback) } catch (_: Exception) {}
        pollRunnable?.let { handler.removeCallbacks(it) }
        activeController = null
    }

    private fun setController(controller: MediaController) {
        try { activeController?.unregisterCallback(callback) } catch (_: Exception) {}
        activeController = controller
        try { controller.registerCallback(callback) } catch (_: Exception) {}
        updateFromController()
    }

    private fun startPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                updateFromController()
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(pollRunnable!!, 1000)
    }

    private fun updateFromController() {
        val c = activeController ?: return
        try {
            val state = c.playbackState
            val meta = c.metadata
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING

            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = state?.position ?: 0L
            val pkg = c.packageName ?: ""

            handler.post {
                IslandStateManager.updateMusic(MusicState(
                    title = title, artist = artist, albumArt = art,
                    isPlaying = isPlaying, position = position,
                    duration = duration, packageName = pkg
                ))
            }
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        try {
            val state = activeController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                activeController?.transportControls?.pause()
            } else {
                activeController?.transportControls?.play()
            }
        } catch (_: Exception) {}
    }

    fun skipNext() { try { activeController?.transportControls?.skipToNext() } catch (_: Exception) {} }
    fun skipPrev() { try { activeController?.transportControls?.skipToPrevious() } catch (_: Exception) {} }
}
