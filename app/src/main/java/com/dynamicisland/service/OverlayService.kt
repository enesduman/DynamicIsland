package com.dynamicisland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dynamicisland.MainActivity
import com.dynamicisland.R
import com.dynamicisland.model.ChargingState
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.overlay.IslandOverlayView
import com.dynamicisland.util.PrefsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : LifecycleService() {
    companion object {
        private const val NID = 1001
        private const val CID = "di_service"
        const val ACT_START = "com.dynamicisland.START"
        const val ACT_STOP = "com.dynamicisland.STOP"
        const val ACT_REFRESH = "com.dynamicisland.REFRESH"

        fun start(c: Context) {
            val i = Intent(c, OverlayService::class.java).apply { action = ACT_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.startService(Intent(c, OverlayService::class.java).apply { action = ACT_STOP }) }
        fun refresh(c: Context) { c.startService(Intent(c, OverlayService::class.java).apply { action = ACT_REFRESH }) }
    }

    private var wm: WindowManager? = null
    private var ov: IslandOverlayView? = null
    private var mt: MediaSessionTracker? = null
    private var ct: CallStateTracker? = null
    private var st: SystemTracker? = null
    private var lp: WindowManager.LayoutParams? = null
    private var batteryReceiver: BroadcastReceiver? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        super.onStartCommand(i, f, id)
        when (i?.action) {
            ACT_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACT_REFRESH -> { refreshOverlay(); return START_STICKY }
            else -> {
                startForeground(NID, notif())
                loadFeatureFlags()
                setup()
                trackers()
                observe()
                registerBattery()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        remove()
        mt?.stop()
        ct?.stop()
        st?.stop()
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
    }

    override fun onBind(i: Intent): IBinder? { super.onBind(i); return null }

    private fun loadFeatureFlags() {
        IslandStateManager.musicEnabled = PrefsManager.getMusicEnabled(this)
        IslandStateManager.callEnabled = PrefsManager.getCallEnabled(this)
        IslandStateManager.notifEnabled = PrefsManager.getNotifEnabled(this)
        IslandStateManager.chargingEnabled = PrefsManager.getChargingEnabled(this)
        IslandStateManager.timerEnabled = PrefsManager.getTimerEnabled(this)
        IslandStateManager.netSpeedEnabled = PrefsManager.getNetSpeedEnabled(this)
        IslandStateManager.btEnabled = PrefsManager.getBtEnabled(this)
    }

    private fun setup() {
        if (ov != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mt = MediaSessionTracker(this)
        ov = IslandOverlayView(this, mt, PrefsManager.getIdleWidth(this), PrefsManager.getIdleHeight(this))
        ov?.setGlowEnabled(PrefsManager.getGlowEnabled(this))

        val wmType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = PrefsManager.getYOffset(this@OverlayService)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try { wm?.addView(ov, lp) } catch (_: Exception) {}
    }

    private fun registerBattery() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = (level * 100) / scale
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val isFast = plugged == BatteryManager.BATTERY_PLUGGED_AC

                IslandStateManager.updateCharging(ChargingState(
                    isCharging = isCharging,
                    level = pct,
                    isUSB = plugged == BatteryManager.BATTERY_PLUGGED_USB,
                    isFast = isFast
                ))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun refreshOverlay() {
        loadFeatureFlags()
        lp?.let { p ->
            p.y = PrefsManager.getYOffset(this)
            try { ov?.let { wm?.updateViewLayout(it, p) } } catch (_: Exception) {}
        }
        ov?.updateSizes(PrefsManager.getIdleWidth(this), PrefsManager.getIdleHeight(this))
        ov?.setGlowEnabled(PrefsManager.getGlowEnabled(this))
    }

    private fun remove() { try { ov?.let { wm?.removeView(it) } } catch (_: Exception) {}; ov = null }

    private fun trackers() {
        try { mt?.start() } catch (_: Exception) {}
        try { ct = CallStateTracker(this); ct?.start() } catch (_: Exception) {}
        try { st = SystemTracker(this); st?.start() } catch (_: Exception) {}
    }

    private fun observe() {
        lifecycleScope.launch { IslandStateManager.state.collectLatest { ov?.update(it) } }
    }

    private fun notif(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(
                NotificationChannel(CID, "Dynamic Island", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                })
        val si = PendingIntent.getService(this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACT_STOP }, PendingIntent.FLAG_IMMUTABLE)
        val op = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CID)
            .setContentTitle("Dynamic Island")
            .setSmallIcon(R.drawable.ic_island)
            .setOngoing(true).setSilent(true).setContentIntent(op)
            .addAction(R.drawable.ic_stop, "Durdur", si)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
