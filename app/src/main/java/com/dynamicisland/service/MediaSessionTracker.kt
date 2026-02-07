package com.dynamicisland.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.MusicState

class MediaSessionTracker(private val ctx: Context) {
    private val sm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private var ctrl: MediaController? = null
    private var posRunnable: Runnable? = null

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(s: PlaybackState?) { update() }
        override fun onMetadataChanged(m: MediaMetadata?) { update() }
        override fun onSessionDestroyed() {
            ctrl = null; IslandStateManager.updateMusic(MusicState()); stopPos()
        }
    }

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { cs ->
        if (cs.isNullOrEmpty()) { detach(); IslandStateManager.updateMusic(MusicState()); return@OnActiveSessionsChangedListener }
        val p = cs.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: cs[0]
        attach(p)
    }

    fun start() {
        try {
            val cn = ComponentName(ctx, IslandNotificationListener::class.java)
            sm.addOnActiveSessionsChangedListener(listener, cn)
            val ss = sm.getActiveSessions(cn)
            if (ss.isNotEmpty()) attach(ss.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: ss[0])
        } catch (_: SecurityException) {}
    }

    fun stop() { try { sm.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) {}; detach() }

    private fun attach(c: MediaController) {
        if (ctrl?.sessionToken == c.sessionToken) return
        detach(); ctrl = c; c.registerCallback(cb); update()
    }

    private fun detach() { try { ctrl?.unregisterCallback(cb) } catch (_: Exception) {}; ctrl = null; stopPos() }

    private fun update() {
        val c = ctrl ?: return
        val md = c.metadata; val ps = c.playbackState
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        IslandStateManager.updateMusic(MusicState(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            albumArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            isPlaying = playing,
            position = ps?.position ?: 0L,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            packageName = c.packageName
        ))
        if (playing) startPos() else stopPos()
    }

    private fun startPos() { stopPos()
        posRunnable = object : Runnable { override fun run() {
            ctrl?.playbackState?.let { if (it.state == PlaybackState.STATE_PLAYING)
                IslandStateManager.updateMusic(IslandStateManager.state.value.music.copy(position = it.position))
            }; handler.postDelayed(this, 1000) } }
        handler.postDelayed(posRunnable!!, 1000)
    }
    private fun stopPos() { posRunnable?.let { handler.removeCallbacks(it) }; posRunnable = null }

    fun togglePlayPause() { ctrl?.let {
        if (it.playbackState?.state == PlaybackState.STATE_PLAYING) it.transportControls.pause()
        else it.transportControls.play() } }
    fun skipNext() { ctrl?.transportControls?.skipToNext() }
    fun skipPrev() { ctrl?.transportControls?.skipToPrevious() }
}
