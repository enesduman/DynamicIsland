package com.dynamicisland.service

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.model.IslandMode
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.NavigationState
import com.dynamicisland.model.NotificationInfo
import com.dynamicisland.model.TimerState
import com.dynamicisland.util.PrefsManager

class IslandNotificationListener : NotificationListenerService() {
    private val ignored = setOf("com.dynamicisland", "android", "com.android.systemui",
        "com.android.server.telecom", "com.google.android.dialer", "com.samsung.android.incallui")
    private val handler = Handler(Looper.getMainLooper())

    private val navPkgs = setOf(
        "com.google.android.apps.maps", "com.waze",
        "com.yandex.navi", "com.sygic.aura", "com.here.app.maps"
    )

    private val timerPkgs = setOf(
        "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.android.deskclock", "com.oneplus.deskclock",
        "com.huawei.deskclock", "com.oppo.alarmclock"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (pkg in navPkgs && sbn.isOngoing) { handleNavigation(title, text); return }
            if (pkg in timerPkgs) { handleSystemTimer(title, text, sbn.isOngoing); return }

            if (pkg in ignored || sbn.isOngoing) return
            if (sbn.notification?.flags?.and(Notification.FLAG_FOREGROUND_SERVICE) != 0) return
            if (sbn.notification?.flags?.and(Notification.FLAG_GROUP_SUMMARY) != 0) return
            if (title.isBlank() && text.isBlank()) return

            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg.substringAfterLast('.') }

            val icon = getAppIcon(pkg)
            val color = appColor(pkg)

            IslandStateManager.showNotification(
                NotificationInfo(sbn.id, pkg, appName, title, text, icon, color, sbn.postTime)
            )

            if (PrefsManager.getHideStatusNotif(this)) {
                handler.postDelayed({ try { cancelNotification(sbn.key) } catch (_: Exception) {} }, 200)
            }
        } catch (_: Exception) {}
    }

    private fun handleNavigation(title: String, text: String) {
        val instruction = text.ifEmpty { title }
        var distance = ""; var eta = ""
        val kmR = Regex("""(\d+[.,]?\d*\s*km)""")
        val mR = Regex("""(\d+\s*m\b)""")
        val minR = Regex("""(\d+\s*dk|\d+\s*min|\d+\s*sa)""")
        kmR.find(title)?.let { distance = it.value }
        if (distance.isEmpty()) mR.find(title)?.let { distance = it.value }
        minR.find(title)?.let { eta = it.value }
        if (distance.isEmpty()) kmR.find(text)?.let { distance = it.value }
        if (eta.isEmpty()) minR.find(text)?.let { eta = it.value }
        IslandStateManager.updateNavigation(NavigationState(true, instruction, distance, eta))
    }

    private fun handleSystemTimer(title: String, text: String, ongoing: Boolean) {
        val combined = "$title $text".lowercase()
        val isTimer = combined.contains("zamanlay") || combined.contains("timer") || combined.contains("geri say") || combined.contains("countdown")
        val isStopwatch = combined.contains("kronometre") || combined.contains("stopwatch")

        if (isTimer || isStopwatch) {
            val timeR = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
            val match = timeR.find(combined)
            var elapsedMs = 0L
            if (match != null) {
                val mins = match.groupValues[1].toLongOrNull() ?: 0
                val secs = match.groupValues[2].toLongOrNull() ?: 0
                elapsedMs = (mins * 60 + secs) * 1000
            }
            if (ongoing) {
                IslandStateManager.updateTimer(TimerState(true, isStopwatch, elapsedMs, if (isTimer) elapsedMs else 0L))
            } else {
                IslandStateManager.updateTimer(TimerState(false))
            }
        } else if (!ongoing) {
            IslandStateManager.showNotification(NotificationInfo(
                0, "alarm", "Alarm", title, text, null, 0xFFFF9500.toInt(), System.currentTimeMillis()
            ))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in navPkgs && sbn.isOngoing) {
            IslandStateManager.updateNavigation(NavigationState(false))
        }
        if (sbn.packageName in timerPkgs) {
            if (IslandStateManager.state.value.mode == IslandMode.TIMER) {
                IslandStateManager.updateTimer(TimerState(false))
            }
        }
    }

    private fun getAppIcon(pkg: String): Bitmap? {
        return try {
            val d = packageManager.getApplicationIcon(pkg)
            if (d is BitmapDrawable) d.bitmap
            else { val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888); val cv = Canvas(b); d.setBounds(0, 0, 48, 48); d.draw(cv); b }
        } catch (_: Exception) { null }
    }

    private fun appColor(pkg: String) = when {
        pkg.contains("whatsapp") -> 0xFF25D366.toInt()
        pkg.contains("instagram") -> 0xFFE1306C.toInt()
        pkg.contains("telegram") -> 0xFF0088CC.toInt()
        pkg.contains("twitter") || pkg.contains("x.android") -> 0xFF1DA1F2.toInt()
        pkg.contains("youtube") -> 0xFFFF0000.toInt()
        pkg.contains("spotify") -> 0xFF1DB954.toInt()
        pkg.contains("discord") -> 0xFF5865F2.toInt()
        pkg.contains("facebook") || pkg.contains("messenger") -> 0xFF1877F2.toInt()
        else -> 0xFF5856D6.toInt()
    }
}
