package com.dynamicisland.service

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.NotificationInfo

class IslandNotificationListener : NotificationListenerService() {
    private val ignored = setOf("com.dynamicisland", "android", "com.android.systemui")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            if (pkg in ignored || sbn.isOngoing) return
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (_: PackageManager.NameNotFoundException) { pkg.substringAfterLast('.') }

            val icon = try {
                val d = packageManager.getApplicationIcon(pkg)
                if (d is BitmapDrawable) d.bitmap
                else {
                    val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(b); d.setBounds(0, 0, 48, 48); d.draw(cv); b
                }
            } catch (_: Exception) { null }

            val color = when {
                pkg.contains("whatsapp") -> 0xFF25D366.toInt()
                pkg.contains("instagram") -> 0xFFE1306C.toInt()
                pkg.contains("telegram") -> 0xFF0088CC.toInt()
                pkg.contains("twitter") -> 0xFF1DA1F2.toInt()
                pkg.contains("youtube") -> 0xFFFF0000.toInt()
                pkg.contains("spotify") -> 0xFF1DB954.toInt()
                else -> 0xFF5856D6.toInt()
            }

            IslandStateManager.showNotification(
                NotificationInfo(sbn.id, pkg, appName, title, text, icon, color, sbn.postTime)
            )
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val cur = IslandStateManager.state.value.notification
        if (cur?.id == sbn.id && cur.packageName == sbn.packageName) {
            IslandStateManager.dismissNotification()
        }
    }
}
