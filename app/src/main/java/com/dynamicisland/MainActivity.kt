package com.dynamicisland

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dynamicisland.service.OverlayService
import com.dynamicisland.service.TimerManager
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
    private val btPL = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateUI() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this).apply { setBackgroundColor(0xFFF5F5F5.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(24))
        }

        // Header
        root.addView(headerCard())
        root.addView(spc(16))

        // ═══ IZINLER ═══
        root.addView(sectionTitle("Izinler"))
        val permCard = cardLayout()
        s1 = permRow(permCard, "Overlay")
        permCard.addView(permBtn("Overlay Izni Ver") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        permCard.addView(divider())
        s2 = permRow(permCard, "Bildirim Erisimi")
        permCard.addView(permBtn("Bildirim Erisimi Ver") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        permCard.addView(divider())
        s3 = permRow(permCard, "Telefon")
        permCard.addView(permBtn("Telefon Izni Ver") {
            phonePL.launch(Manifest.permission.READ_PHONE_STATE)
        })
        permCard.addView(divider())
        s4 = permRow(permCard, "Bildirim")
        if (Build.VERSION.SDK_INT >= 33) {
            permCard.addView(permBtn("Bildirim Izni Ver") {
                notifPL.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permCard.addView(divider())
            permCard.addView(permBtn("Bluetooth Izni") {
                btPL.launch(Manifest.permission.BLUETOOTH_CONNECT)
            })
        }
        root.addView(permCard)
        root.addView(spc(16))

        // ═══ SERVIS ═══
        val swCard = cardLayout()
        sw = Switch(this).apply {
            text = "Dynamic Island"
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setOnCheckedChangeListener { _, on ->
                if (on) {
                    if (allPerms()) {
                        PrefsManager.setEnabled(this@MainActivity, true)
                        OverlayService.start(this@MainActivity)
                        Toast.makeText(this@MainActivity, "Aktif!", Toast.LENGTH_SHORT).show()
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
        swCard.addView(sw)
        root.addView(swCard)
        root.addView(spc(16))

        // ═══ KONUM VE BOYUT ═══
        root.addView(sectionTitle("Konum ve Boyut"))
        val posCard = cardLayout()
        yLabel = sliderLabel(posCard, "Dikey Konum", PrefsManager.getYOffset(this), "px")
        posCard.addView(makeSeek(300, PrefsManager.getYOffset(this)) { v ->
            yLabel.text = "Dikey Konum: $v px"
            PrefsManager.setYOffset(this, v)
            OverlayService.refresh(this)
        })
        posCard.addView(divider())
        wLabel = sliderLabel(posCard, "Genislik", PrefsManager.getIdleWidth(this), "dp")
        posCard.addView(makeSeek(200, PrefsManager.getIdleWidth(this)) { v ->
            val w = maxOf(v, 60)
            wLabel.text = "Genislik: $w dp"
            PrefsManager.setIdleWidth(this, w)
            OverlayService.refresh(this)
        })
        posCard.addView(divider())
        hLabel = sliderLabel(posCard, "Yukseklik", PrefsManager.getIdleHeight(this), "dp")
        posCard.addView(makeSeek(80, PrefsManager.getIdleHeight(this)) { v ->
            val h = maxOf(v, 20)
            hLabel.text = "Yukseklik: $h dp"
            PrefsManager.setIdleHeight(this, h)
            OverlayService.refresh(this)
        })
        root.addView(posCard)
        root.addView(spc(16))

        // ═══ OZELLIKLER ═══
        root.addView(sectionTitle("Ozellikler"))
        val ftCard = cardLayout()
        ftCard.addView(featureSwitch("Muzik Kontrolu", PrefsManager.getMusicEnabled(this)) {
            PrefsManager.setMusicEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Arama Gostergesi", PrefsManager.getCallEnabled(this)) {
            PrefsManager.setCallEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Bildirimler", PrefsManager.getNotifEnabled(this)) {
            PrefsManager.setNotifEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Sarj Gostergesi", PrefsManager.getChargingEnabled(this)) {
            PrefsManager.setChargingEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Zamanlayici", PrefsManager.getTimerEnabled(this)) {
            PrefsManager.setTimerEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Internet Hizi", PrefsManager.getNetSpeedEnabled(this)) {
            PrefsManager.setNetSpeedEnabled(this, it); OverlayService.refresh(this)
        })
        ftCard.addView(divider())
        ftCard.addView(featureSwitch("Bluetooth", PrefsManager.getBtEnabled(this)) {
            PrefsManager.setBtEnabled(this, it); OverlayService.refresh(this)
        })
        root.addView(ftCard)
        root.addView(spc(16))

        // ═══ GORUNUM ═══
        root.addView(sectionTitle("Gorunum"))
        val visCard = cardLayout()
        visCard.addView(featureSwitch("Glow Efekti", PrefsManager.getGlowEnabled(this)) {
            PrefsManager.setGlowEnabled(this, it); OverlayService.refresh(this)
        })
        visCard.addView(divider())
        visCard.addView(featureSwitch("Bildirimleri Status Bar'dan Gizle", PrefsManager.getHideStatusNotif(this)) {
            PrefsManager.setHideStatusNotif(this, it)
        })
        root.addView(visCard)
        root.addView(spc(16))

        // ═══ ARACLAR ═══
        root.addView(sectionTitle("Araclar"))
        val toolCard = cardLayout()
        toolCard.addView(toolBtn("Kronometre Baslat") { TimerManager.startStopwatch() })
        toolCard.addView(spc(8))
        toolCard.addView(toolBtn("5dk Zamanlayici") { TimerManager.startTimer(5 * 60 * 1000L) })
        toolCard.addView(spc(8))
        toolCard.addView(toolBtn("1dk Zamanlayici") { TimerManager.startTimer(60 * 1000L) })
        toolCard.addView(spc(8))
        toolCard.addView(toolBtn("Durdur") { TimerManager.stop() })
        root.addView(toolCard)
        root.addView(spc(24))

        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        sw.isChecked = PrefsManager.isEnabled(this)
    }

    private fun headerCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                colors = intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
                orientation = GradientDrawable.Orientation.TL_BR
            }
            addView(TextView(this@MainActivity).apply {
                text = "Dynamic Island"
                textSize = 24f; setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "Muzik \u2022 Arama \u2022 Bildirim \u2022 Sarj \u2022 Zamanlayici"
                textSize = 12f; setTextColor(0xFF888888.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun featureSwitch(label: String, default: Boolean, onChange: (Boolean) -> Unit): Switch {
        return Switch(this).apply {
            text = label; textSize = 14f; isChecked = default
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
    }

    private fun cardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(Color.WHITE)
            }
            elevation = dp(2).toFloat()
        }
    }

    private fun sectionTitle(t: String): TextView {
        return TextView(this).apply {
            text = t; textSize = 14f; setTextColor(0xFF666666.toInt())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(8), 0, 0, dp(8))
        }
    }

    private fun permRow(parent: LinearLayout, name: String): TextView {
        val tv = TextView(this).apply {
            textSize = 14f; setPadding(dp(4), dp(6), dp(4), dp(2))
        }
        parent.addView(tv)
        return tv
    }

    private fun permBtn(t: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = t; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFF5856D6.toInt())
            }
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(8) }
            setOnClickListener { click() }
        }
    }

    private fun toolBtn(t: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = t; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(0xFFFF9500.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            setOnClickListener { click() }
        }
    }

    private fun sliderLabel(parent: LinearLayout, name: String, value: Int, unit: String): TextView {
        val tv = TextView(this).apply {
            text = "$name: $value $unit"; textSize = 13f
            setTextColor(0xFF333333.toInt()); setPadding(dp(4), dp(4), 0, 0)
        }
        parent.addView(tv)
        return tv
    }

    private fun makeSeek(max: Int, cur: Int, onChange: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            this.max = max; progress = cur
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2); bottomMargin = dp(4) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, user: Boolean) { if (user) onChange(v) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun updateUI() {
        s1.text = st("Overlay", hasOverlay()); s1.setTextColor(if (hasOverlay()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s2.text = st("Bildirim Erisimi", hasNotifAccess()); s2.setTextColor(if (hasNotifAccess()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s3.text = st("Telefon", hasPhone()); s3.setTextColor(if (hasPhone()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        s4.text = st("Bildirim", hasPostNotif()); s4.setTextColor(if (hasPostNotif()) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
    }

    private fun allPerms() = hasOverlay() && hasNotifAccess() && hasPhone() && hasPostNotif()
    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasNotifAccess(): Boolean {
        val cn = ComponentName(this, "com.dynamicisland.service.IslandNotificationListener")
        val f = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return f?.contains(cn.flattenToString()) == true
    }
    private fun hasPhone() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    private fun hasPostNotif() = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(this,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    private fun st(n: String, ok: Boolean) = "$n: ${if (ok) "Verildi" else "Gerekli"}"
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun spc(h: Int): View { val v = View(this); v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h)); return v }
    private fun divider(): View { val v = View(this); v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) }; v.setBackgroundColor(0xFFEEEEEE.toInt()); return v }
}
