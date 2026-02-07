package com.dynamicisland

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.service.OverlayService
import com.dynamicisland.util.PrefsManager

class MainActivity : AppCompatActivity() {
    private lateinit var s1: TextView; private lateinit var s2: TextView
    private lateinit var s3: TextView; private lateinit var s4: TextView
    private lateinit var sw: Switch

    private val phonePL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUI() }
    private val notifPL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUI() }

    override fun onCreate(b: Bundle?) { super.onCreate(b)
        val sv = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(48), dp(24), dp(24)) }

        root.addView(tv("⬬ Dynamic Island", 26f, true))
        root.addView(tv("Gerçek bildirimler • Müzik • Aramalar • Şarj", 13f, false, 0xFF888888.toInt()).apply {
            setPadding(0, dp(4), 0, dp(28)) })

        root.addView(tv("Gerekli İzinler", 18f, true).apply { setPadding(0, 0, 0, dp(12)) })

        s1 = tv("", 14f); root.addView(s1)
        root.addView(btn("Overlay İzni") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })

        s2 = tv("", 14f); root.addView(s2)
        root.addView(btn("Bildirim Erişimi") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })

        s3 = tv("", 14f); root.addView(s3)
        root.addView(btn("Telefon İzni") { phonePL.launch(Manifest.permission.READ_PHONE_STATE) })

        s4 = tv("", 14f); root.addView(s4)
        if (Build.VERSION.SDK_INT >= 33) root.addView(btn("Bildirim İzni") {
            notifPL.launch(Manifest.permission.POST_NOTIFICATIONS) })

        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) }
            setBackgroundColor(0xFFE0E0E0.toInt()) })

        sw = Switch(this).apply { text = "Dynamic Island'ı Etkinleştir"; textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                if (on) { if (allPerms()) { PrefsManager.setEnabled(this@MainActivity, true)
                    OverlayService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "✅ Dynamic Island aktif!", Toast.LENGTH_SHORT).show()
                } else { isChecked = false; Toast.makeText(this@MainActivity, "❌ Önce tüm izinleri verin", Toast.LENGTH_LONG).show() }
                } else { PrefsManager.setEnabled(this@MainActivity, false); OverlayService.stop(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Dynamic Island durduruldu", Toast.LENGTH_SHORT).show() }
            } }
        root.addView(sw)

        root.addView(tv("""
            |
            |Nasıl çalışır:
            |• Spotify/YouTube Music açın → Şarkı bilgisi görünür
            |• Biri arasın → Arayan kişi ve süre görünür
            |• WhatsApp mesajı gelsin → Bildirim gösterilir
            |• Şarj kablosu takın → Pil yüzdesi görünür
            |• Island'a dokunarak genişletin/daraltın
        """.trimMargin(), 13f, false, 0xFF666666.toInt()))

        sv.addView(root); setContentView(sv) }

    override fun onResume() { super.onResume(); updateUI(); sw.isChecked = PrefsManager.isEnabled(this) }

    private fun updateUI() {
        s1.text = st("Overlay", hasOverlay()); s1.setTextColor(if (hasOverlay()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s2.text = st("Bildirim Erişimi", hasNotifAccess()); s2.setTextColor(if (hasNotifAccess()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s3.text = st("Telefon", hasPhone()); s3.setTextColor(if (hasPhone()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s4.text = st("Bildirim Gönderme", hasPostNotif()); s4.setTextColor(if (hasPostNotif()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
    }

    private fun allPerms() = hasOverlay() && hasNotifAccess() && hasPhone() && hasPostNotif()
    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasNotifAccess(): Boolean { val cn = ComponentName(this, "com.dynamicisland.service.IslandNotificationListener")
        val f = Settings.Secure.getString(contentResolver, "enabled_notification_listeners"); return f?.contains(cn.flattenToString()) == true }
    private fun hasPhone() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    private fun hasPostNotif() = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(this,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    private fun st(n: String, ok: Boolean) = "$n: ${if (ok) "✅ Verildi" else "❌ Gerekli"}"
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, s: Float, bold: Boolean = false, c: Int = 0xFF000000.toInt()) = TextView(this).apply {
        text = t; textSize = s; setTextColor(c)
        if (bold) typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun btn(t: String, click: () -> Unit) = Button(this).apply { text = t; textSize = 13f; isAllCaps = false
        setOnClickListener { click() }; layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(12) } }
}
