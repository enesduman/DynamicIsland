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
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.NavigationState
import com.dynamicisland.model.NotificationInfo
import com.dynamicisland.model.TimerState
import com.dynamicisland.util.PrefsManager

class IslandNotificationListener : NotificationListenerService() {
    private val ignored = setOf("com.dynamicisland", "android", "com.android.systemui",
        "com.android.server.telecom", "com.google.android.dialer", "com.samsung.android.incallui")
    private val handler = Handler(Looper.getMainLooper())

    // Navigasyon paketleri
    private val navPkgs = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.yandex.navi",
        "com.sygic.aura",
        "com.here.app.maps"
    )

    // Saat/timer paketleri
    private val timerPkgs = setOf(
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.huawei.deskclock",
        "com.oppo.alarmclock"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // Google Maps navigasyon
            if (pkg in navPkgs && sbn.isOngoing) {
                handleNavigation(title, text)
                return
            }

            // Sistem saat uygulamasi - timer/kronometre/alarm
            if (pkg in timerPkgs) {
                handleSystemTimer(title, text, sbn.isOngoing)
                return
            }

            // Normal bildirimler
            if (pkg in ignored || sbn.isOngoing) return
            if (sbn.notification?.flags?.and(Notification.FLAG_FOREGROUND_SERVICE) != 0) return
            if (sbn.notification?.flags?.and(Notification.FLAG_GROUP_SUMMARY) != 0) return
            if (title.isBlank() && text.isBlank()) return

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg.substringAfterLast('.') }

            val icon = getAppIcon(pkg)

            val color = when {
                pkg.contains("whatsapp") -> 0xFF25D366.toInt()
                pkg.contains("instagram") -> 0xFFE1306C.toInt()
                pkg.contains("telegram") -> 0xFF0088CC.toInt()
                pkg.contains("twitter") || pkg.contains("x.android") -> 0xFF1DA1F2.toInt()
                pkg.contains("youtube") -> 0xFFFF0000.toInt()
                pkg.contains("spotify") -> 0xFF1DB954.toInt()
                pkg.contains("discord") -> 0xFF5865F2.toInt()
                pkg.contains("snapchat") -> 0xFFFFFC00.toInt()
                pkg.contains("tiktok") -> 0xFF000000.toInt()
                pkg.contains("facebook") || pkg.contains("messenger") -> 0xFF1877F2.toInt()
                else -> 0xFF5856D6.toInt()
            }

            IslandStateManager.showNotification(
                NotificationInfo(sbn.id, pkg, appName, title, text, icon, color, sbn.postTime)
            )

            // Status bar'dan kaldir
            if (PrefsManager.getHideStatusNotif(this)) {
                handler.postDelayed({
                    try { cancelNotification(sbn.key) } catch (_: Exception) {}
                }, 200)
            }
        } catch (_: Exception) {}
    }

    private fun handleNavigation(title: String, text: String) {
        // Google Maps navigasyon bildirimi parse et
        // Genellikle title: "5 dk (2.3 km)" veya yol tarifi
        // text: "Sola don" veya cadde adi
        val instruction = text.ifEmpty { title }
        var distance = ""
        var eta = ""

        // Mesafe ve sure ayikla
        val kmRegex = Regex("""(\d+[.,]?\d*\s*km)""")
        val mRegex = Regex("""(\d+\s*m\b)""")
        val minRegex = Regex("""(\d+\s*dk|\d+\s*min|\d+\s*sa)""")

        kmRegex.find(title)?.let { distance = it.value }
        if (distance.isEmpty()) mRegex.find(title)?.let { distance = it.value }
        minRegex.find(title)?.let { eta = it.value }

        if (distance.isEmpty() && eta.isEmpty()) {
            // Baslik mesafe/sure icermiyorsa text'ten dene
            kmRegex.find(text)?.let { distance = it.value }
            minRegex.find(text)?.let { eta = it.value }
        }

        IslandStateManager.updateNavigation(NavigationState(
            isActive = true,
            instruction = instruction,
            distance = distance,
            eta = eta
        ))
    }

    private fun handleSystemTimer(title: String, text: String, ongoing: Boolean) {
        val combined = "$title $text".lowercase()
        val isTimer = combined.contains("zamanlay") || combined.contains("timer") ||
                combined.contains("geri say") || combined.contains("countdown")
        val isStopwatch = combined.contains("kronometre") || combined.contains("stopwatch")
        val isAlarm = combined.contains("alarm") || combined.contains("calar")

        if (isTimer || isStopwatch) {
            // Zaman bilgisini parse et
            val timeRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
            val match = timeRegex.find(combined)
            var elapsedMs = 0L
            if (match != null) {
                val parts = match.groupValues
                val mins = parts[1].toLongOrNull() ?: 0
                val secs = parts[2].toLongOrNull() ?: 0
                elapsedMs = (mins * 60 + secs) * 1000
            }

            if (ongoing) {
                IslandStateManager.updateTimer(TimerState(
                    isRunning = true,
                    isStopwatch = isStopwatch,
                    elapsedMs = elapsedMs,
                    targetMs = if (isTimer) elapsedMs else 0L
                ))
            } else {
                // Timer bitti
                IslandStateManager.updateTimer(TimerState(isRunning = false))
            }
        } else if (isAlarm && !ongoing) {
            // Alarm bildirimi normal bildirim olarak goster
            IslandStateManager.showNotification(NotificationInfo(
                0, "alarm", "Alarm", title, text, null, 0xFFFF9500.toInt(), System.currentTimeMillis()
            ))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Navigasyon bildirimi kaldirildi
        if (sbn.packageName in navPkgs && sbn.isOngoing) {
            IslandStateManager.updateNavigation(NavigationState(isActive = false))
        }
        // Timer bildirimi kaldirildi
        if (sbn.packageName in timerPkgs) {
            val cur = IslandStateManager.state.value
            if (cur.mode == IslandMode.TIMER) {
                IslandStateManager.updateTimer(TimerState(isRunning = false))
            }
        }
    }

    private fun getAppIcon(pkg: String): Bitmap? {
        return try {
            val d = packageManager.getApplicationIcon(pkg)
            if (d is BitmapDrawable) d.bitmap
            else {
                val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val cv = Canvas(b); d.setBounds(0, 0, 48, 48); d.draw(cv); b
            }
        } catch (_: Exception) { null }
    }
}
