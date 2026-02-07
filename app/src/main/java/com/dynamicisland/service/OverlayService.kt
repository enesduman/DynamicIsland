package com.dynamicisland.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dynamicisland.MainActivity
import com.dynamicisland.R
import com.dynamicisland.MainActivity
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.overlay.IslandOverlayView
import com.dynamicisland.util.PrefsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : LifecycleService() {
    companion object {
        private const val NID = 1001; private const val CID = "di_service"
        const val ACT_START = "com.dynamicisland.START"
        const val ACT_STOP = "com.dynamicisland.STOP"
        const val ACT_REFRESH = "com.dynamicisland.REFRESH"

        fun start(c: Context) { val i = Intent(c, OverlayService::class.java).apply { action = ACT_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i) }
        fun stop(c: Context) { c.startService(Intent(c, OverlayService::class.java).apply { action = ACT_STOP }) }
        fun refresh(c: Context) { c.startService(Intent(c, OverlayService::class.java).apply { action = ACT_REFRESH }) }
    }

    private var wm: WindowManager? = null
    private var ov: IslandOverlayView? = null
    private var mt: MediaSessionTracker? = null
    private var ct: CallStateTracker? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        super.onStartCommand(i, f, id)
        when (i?.action) {
            ACT_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACT_REFRESH -> { refreshOverlay(); return START_STICKY }
            else -> {
                startForeground(NID, notif())
                setup(); trackers(); observe()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { super.onDestroy(); remove(); mt?.stop(); ct?.stop() }
    override fun onBind(i: Intent): IBinder? { super.onBind(i); return null }

    private fun setup() {
        if (ov != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mt = MediaSessionTracker(this)
        ov = IslandOverlayView(this, mt,
            PrefsManager.getIdleWidth(this),
            PrefsManager.getIdleHeight(this))

        val yOffset = PrefsManager.getYOffset(this)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = yOffset
        }
        try { wm?.addView(ov, layoutParams) } catch (_: Exception) {}
    }

    private fun refreshOverlay() {
        // Pozisyon guncelle
        layoutParams?.let { lp ->
            lp.y = PrefsManager.getYOffset(this)
            try { ov?.let { wm?.updateViewLayout(it, lp) } } catch (_: Exception) {}
        }
        // Boyut guncelle
        ov?.updateSizes(
            PrefsManager.getIdleWidth(this),
            PrefsManager.getIdleHeight(this)
        )
    }

    private fun remove() { try { ov?.let { wm?.removeView(it) } } catch (_: Exception) {}; ov = null }

    private fun trackers() {
        try { mt?.start() } catch (_: Exception) {}
        try { ct = CallStateTracker(this); ct?.start() } catch (_: Exception) {}
    }

    private fun observe() {
        lifecycleScope.launch { IslandStateManager.state.collectLatest { ov?.update(it) } }
    }

    private fun notif(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(
                NotificationChannel(CID, "Dynamic Island", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val si = Intent(this, OverlayService::class.java).apply { action = ACT_STOP }
        val sp = PendingIntent.getService(this, 0, si, PendingIntent.FLAG_IMMUTABLE)
        val op = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CID).setContentTitle("Dynamic Island aktif")
            .setContentText("Ayarlar icin dokunun").setSmallIcon(R.drawable.ic_island)
            .setOngoing(true).setSilent(true).setContentIntent(op)
            .addAction(R.drawable.ic_stop, "Durdur", sp).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
