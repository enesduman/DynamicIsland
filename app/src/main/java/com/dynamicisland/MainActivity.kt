package com.dynamicisland

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.service.OverlayService
import com.dynamicisland.util.PrefsManager

class MainActivity : AppCompatActivity() {
    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var s3: TextView
    private lateinit var s4: TextView
    private lateinit var sw: Switch
    private lateinit var yLabel: TextView
    private lateinit var wLabel: TextView
    private lateinit var hLabel: TextView

    private val phonePL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUI() }
    private val notifPL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUI() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        root.addView(tv("Dynamic Island", 26f, true))
        root.addView(tv("Gercek bildirimler, muzik, aramalar, sarj", 13f, false, 0xFF888888.toInt()).apply {
            setPadding(0, dp(4), 0, dp(28))
        })

        root.addView(tv("Gerekli Izinler", 18f, true).apply { setPadding(0, 0, 0, dp(12)) })

        s1 = tv("", 14f); root.addView(s1)
        root.addView(btn("Overlay Izni") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })

        s2 = tv("", 14f); root.addView(s2)
        root.addView(btn("Bildirim Erisimi") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })

        s3 = tv("", 14f); root.addView(s3)
        root.addView(btn("Telefon Izni") { phonePL.launch(Manifest.permission.READ_PHONE_STATE) })

        s4 = tv("", 14f); root.addView(s4)
        if (Build.VERSION.SDK_INT >= 33) root.addView(btn("Bildirim Izni") {
            notifPL.launch(Manifest.permission.POST_NOTIFICATIONS)
        })

        root.addView(divider())

        sw = Switch(this).apply {
            text = "Dynamic Island Etkinlestir"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                if (on) {
                    if (allPerms()) {
                        PrefsManager.setEnabled(this@MainActivity, true)
                        OverlayService.start(this@MainActivity)
                    } else {
                        isChecked = false
                        Toast.makeText(this@MainActivity, "Once izinleri verin", Toast.LENGTH_LONG).show()
                    }
                } else {
                    PrefsManager.setEnabled(this@MainActivity, false)
                    OverlayService.stop(this@MainActivity)
                }
            }
        }
        root.addView(sw)
        root.addView(divider())

        root.addView(tv("Konum ve Boyut", 18f, true).apply { setPadding(0, dp(8), 0, dp(16)) })

        yLabel = tv("Dikey: ${PrefsManager.getYOffset(this)} px", 14f)
        root.addView(yLabel)
        root.addView(makeSeek(200, PrefsManager.getYOffset(this)) { v ->
            yLabel.text = "Dikey: $v px"
            PrefsManager.setYOffset(this, v)
            OverlayService.refresh(this)
        })

        wLabel = tv("Genislik: ${PrefsManager.getIdleWidth(this)} dp", 14f)
        root.addView(wLabel)
        root.addView(makeSeek(200, PrefsManager.getIdleWidth(this)) { v ->
            val w = maxOf(v, 60)
            wLabel.text = "Genislik: $w dp"
            PrefsManager.setIdleWidth(this, w)
            OverlayService.refresh(this)
        })

        hLabel = tv("Yukseklik: ${PrefsManager.getIdleHeight(this)} dp", 14f)
        root.addView(hLabel)
        root.addView(makeSeek(80, PrefsManager.getIdleHeight(this)) { v ->
            val h = maxOf(v, 20)
            hLabel.text = "Yukseklik: $h dp"
            PrefsManager.setIdleHeight(this, h)
            OverlayService.refresh(this)
        })

        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        sw.isChecked = PrefsManager.isEnabled(this)
    }

    private fun makeSeek(max: Int, cur: Int, onChange: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            this.max = max
            progress = cur
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(16) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, user: Boolean) { if (user) onChange(v) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun updateUI() {
        s1.text = st("Overlay", hasOverlay()); s1.setTextColor(if (hasOverlay()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s2.text = st("Bildirim", hasNotifAccess()); s2.setTextColor(if (hasNotifAccess()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s3.text = st("Telefon", hasPhone()); s3.setTextColor(if (hasPhone()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s4.text = st("Bildirim Post", hasPostNotif()); s4.setTextColor(if (hasPostNotif()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
    }

    private fun allPerms() = hasOverlay() && hasNotifAccess() && hasPhone() && hasPostNotif()
    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasNotifAccess(): Boolean {
        val cn = ComponentName(this, "com.dynamicisland.service.IslandNotificationListener")
        val f = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return f?.contains(cn.flattenToString()) == true
    }
    private fun hasPhone() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    private fun hasPostNotif() = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    private fun st(n: String, ok: Boolean) = "$n: ${if (ok) "Verildi" else "Gerekli"}"
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, s: Float, bold: Boolean = false, c: Int = 0xFF000000.toInt()) = TextView(this).apply {
        text = t; textSize = s; setTextColor(c)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    private fun btn(t: String, click: () -> Unit) = Button(this).apply {
        text = t; textSize = 13f; isAllCaps = false
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(12) }
    }
    private fun divider(): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(16) }
        v.setBackgroundColor(0xFFE0E0E0.toInt())
        return v
    }
}
