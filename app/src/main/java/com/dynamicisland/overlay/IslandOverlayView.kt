package com.dynamicisland.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.dynamicisland.model.*
import com.dynamicisland.service.MediaSessionTracker
import com.dynamicisland.service.TimerManager

class IslandOverlayView(
    context: Context,
    private val mt: MediaSessionTracker?,
    private var idleW: Int = 105,
    private var idleH: Int = 32
) : FrameLayout(context) {

    private val d = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private var cur = IslandState()
    private var dismissR: Runnable? = null
    private val glowView: View
    private val container: LinearLayout
    private val compactV: LinearLayout
    private val expandedV: FrameLayout
    private val indicatorDots: LinearLayout
    private var glowEnabled = true
    private var currentGlow = 0

    private val EXPANDED_MARGIN = 16f
    private val IDLE_RADIUS = 16f
    private val EXPANDED_RADIUS = 28f

    private val gestureDetector: GestureDetector

    init {
        glowView = View(context).apply {
            layoutParams = LayoutParams(dp(idleW + 8f), dp(idleH + 8f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            alpha = 0f
        }
        addView(glowView)

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(idleW.toFloat()), dp(idleH.toFloat())).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4f)
            }
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(IDLE_RADIUS).toFloat()
            }
            elevation = dp(12f).toFloat()
            clipChildren = true
        }

        compactV = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(10f), dp(3f), dp(10f), dp(3f))
        }

        expandedV = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }

        indicatorDots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(12f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(idleH.toFloat() + 8)
            }
        }

        container.addView(compactV)
        container.addView(expandedV)
        addView(container)
        addView(indicatorDots)

        // Gesture detector - tek dokunma ve basili tutma
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (cur.expanded) {
                    // Expanded iken dokunma -> daralt
                    IslandStateManager.toggleExpanded()
                } else {
                    // Compact iken dokunma -> genislet
                    IslandStateManager.toggleExpanded()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Basili tutma -> hizli islem
                when (cur.mode) {
                    IslandMode.MUSIC -> {
                        // Muzik calarken basili tut -> play/pause
                        mt?.togglePlayPause()
                        pulseAnim()
                    }
                    IslandMode.CALL -> {
                        // Arama sirasinda basili tut -> arama sonlandir
                        try {
                            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                            tm.endCall()
                        } catch (_: Exception) {}
                        pulseAnim()
                    }
                    IslandMode.TIMER -> {
                        TimerManager.stop()
                        pulseAnim()
                    }
                    IslandMode.NOTIFICATION -> {
                        IslandStateManager.dismissNotification()
                        pulseAnim()
                    }
                    else -> {
                        // Diger modlarda basili tut -> genislet
                        if (!cur.expanded) IslandStateManager.toggleExpanded()
                    }
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Cift dokunma -> muzik ise sonraki sarki
                if (cur.mode == IslandMode.MUSIC) {
                    mt?.skipNext()
                    pulseAnim()
                }
                return true
            }
        })

        container.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        idle()
    }

    private fun pulseAnim() {
        container.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            container.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    fun setGlowEnabled(e: Boolean) { glowEnabled = e }

    fun updateSizes(w: Int, h: Int) {
        idleW = w; idleH = h
        if (!cur.expanded && cur.mode == IslandMode.IDLE) {
            val lp = container.layoutParams
            lp.width = dp(w.toFloat()); lp.height = dp(h.toFloat())
            container.layoutParams = lp
        }
    }

    fun update(s: IslandState) {
        val prev = cur; cur = s
        if (s.expanded && !prev.expanded) animTo(true, s)
        else if (!s.expanded && prev.expanded) animTo(false, s)
        if (s.expanded) expanded(s) else compact(s)
        if (s.mode == IslandMode.NOTIFICATION && s.expanded) autoDismiss()
        updateGlow(s.glowColor)
        updateDots(s)
    }

    private fun updateGlow(color: Int) {
        if (!glowEnabled || color == 0) {
            glowView.alpha = 0f; return
        }
        if (color != currentGlow) {
            currentGlow = color
            glowView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(2f), color)
            }
            glowView.alpha = 0f
            glowView.animate().alpha(0.7f).setDuration(300).start()
        }
        val lp = glowView.layoutParams as LayoutParams
        val clp = container.layoutParams
        lp.width = clp.width + dp(8f)
        lp.height = clp.height + dp(8f)
        lp.topMargin = (container.layoutParams as LayoutParams).topMargin - dp(4f)
        glowView.layoutParams = lp
    }

    private fun updateDots(s: IslandState) {
        indicatorDots.removeAllViews()
        val ind = s.indicators
        if (ind.cameraInUse) addDot(0xFF4CD964.toInt())
        if (ind.micInUse) addDot(0xFFFF9500.toInt())
        if (ind.isSilent) addDot(0xFFFF3B30.toInt())
        if (ind.isDnd) addDot(0xFF5856D6.toInt())
        if (ind.isScreenRecording) addDot(0xFFFF3B30.toInt())
        if (ind.bluetoothConnected) addDot(0xFF007AFF.toInt())
    }

    private fun addDot(color: Int) {
        indicatorDots.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5f), dp(5f)).apply {
                marginStart = dp(2f); marginEnd = dp(2f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
            }
        })
    }

    private fun compact(s: IslandState) {
        compactV.visibility = View.VISIBLE
        expandedV.visibility = View.GONE
        compactV.removeAllViews()
        when (s.mode) {
            IslandMode.MUSIC -> cMusic(s.music)
            IslandMode.CALL -> cCall(s.call)
            IslandMode.CHARGING -> cCharge(s.charging)
            IslandMode.TIMER -> cTimer(s.timer)
            IslandMode.NET_SPEED -> cNet(s.netSpeed)
            IslandMode.NAVIGATION -> cNav(s.navigation)
            IslandMode.CAMERA_MIC -> cCamMic(s.indicators)
            IslandMode.SCREEN_RECORD -> cRecord()
            else -> idle()
        }
    }

    private fun idle() {
        compactV.removeAllViews()
        compactV.gravity = Gravity.CENTER
        repeat(2) { i ->
            compactV.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(9f), dp(9f)).apply {
                    if (i > 0) marginStart = dp(4f)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF1A1A1A.toInt())
                    setStroke(1, 0xFF333333.toInt())
                }
            })
        }
    }

    private fun cMusic(m: MusicState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        if (m.albumArt != null) compactV.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
            setImageBitmap(m.albumArt); scaleType = ImageView.ScaleType.CENTER_CROP
        }) else compactV.addView(tv("\uD83C\uDFB5", 10f))
        compactV.addView(spacer())
        val bc = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(16f))
        }
        repeat(5) { i ->
            bc.addView(View(context).apply {
                val h = if (m.isPlaying) (3 + (Math.random() * 12)).toInt() else 3
                layoutParams = LinearLayout.LayoutParams(dp(2.5f), dp(h.toFloat())).apply {
                    if (i > 0) marginStart = dp(1.5f)
                }
                background = GradientDrawable().apply {
                    setColor(0xFFFF6B35.toInt()); cornerRadius = dp(1.5f).toFloat()
                }
            })
        }
        compactV.addView(bc)
    }

    private fun cCall(c: CallState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF4CD964.toInt()) }
        })
        compactV.addView(spacer())
        compactV.addView(tv(fmtD(c.durationSeconds), 12f, 0xFF4CD964.toInt(), Typeface.MONOSPACE))
    }

    private fun cCharge(ch: ChargingState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("\u26A1", 10f))
        compactV.addView(spacer())
        compactV.addView(tv("${ch.level}%", 12f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
    }

    private fun cTimer(t: TimerState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("\u23F1", 10f))
        compactV.addView(spacer())
        val display = if (t.isStopwatch) fmtMs(t.elapsedMs) else fmtMs(maxOf(0, t.targetMs - t.elapsedMs))
        compactV.addView(tv(display, 12f, 0xFFFF9500.toInt(), Typeface.MONOSPACE))
    }

    private fun cNet(n: NetSpeedState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("\u2193", 10f, 0xFF4CD964.toInt()))
        compactV.addView(tv(n.downloadSpeed, 9f, 0xFF4CD964.toInt()))
        compactV.addView(spacer())
        compactV.addView(tv("\u2191", 10f, 0xFFFF6B35.toInt()))
        compactV.addView(tv(n.uploadSpeed, 9f, 0xFFFF6B35.toInt()))
    }

    private fun cNav(nav: NavigationState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("\uD83E\uDDED", 10f))
        compactV.addView(spacer())
        compactV.addView(tv(nav.distance, 11f, 0xFF007AFF.toInt()))
    }

    private fun cCamMic(ind: SystemIndicators) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        if (ind.cameraInUse) compactV.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF4CD964.toInt()) }
        })
        if (ind.micInUse) compactV.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f)).apply { marginStart = dp(3f) }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF9500.toInt()) }
        })
    }

    private fun cRecord() {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFF3B30.toInt()) }
        })
        compactV.addView(spacer())
        compactV.addView(tv("REC", 10f, 0xFFFF3B30.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
    }

    // ═══ EXPANDED VIEWS ═══

    private fun expanded(s: IslandState) {
        compactV.visibility = View.GONE
        expandedV.visibility = View.VISIBLE
        expandedV.removeAllViews()
        when (s.mode) {
            IslandMode.MUSIC -> eMusic(s.music)
            IslandMode.CALL -> eCall(s.call)
            IslandMode.CHARGING -> eCharge(s.charging)
            IslandMode.NOTIFICATION -> eNotif(s.notification)
            IslandMode.TIMER -> eTimer(s.timer)
            IslandMode.NET_SPEED -> eNet(s.netSpeed)
            IslandMode.NAVIGATION -> eNav(s.navigation)
            IslandMode.CAMERA_MIC -> eCamMic(s.indicators)
            IslandMode.SCREEN_RECORD -> eRecord()
            IslandMode.BLUETOOTH -> eBt(s.indicators)
            else -> {}
        }
    }

    private fun eMusic(m: MusicState) {
        val lay = vLay()
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { marginEnd = dp(10f) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (m.albumArt != null) setImageBitmap(m.albumArt) else setBackgroundColor(0xFF333333.toInt())
            // Album art'a tikla -> uygulamayi ac
            setOnClickListener {
                try {
                    val li = context.packageManager.getLaunchIntentForPackage(m.packageName)
                    li?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } catch (_: Exception) {}
            }
        })
        val tc = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tc.addView(tv(m.title.ifEmpty { "Bilinmeyen" }, 13f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(m.artist.ifEmpty { "Bilinmeyen" }, 11f, 0xFF999999.toInt(), null, 1))
        top.addView(tc)
        lay.addView(top)

        // Progress bar
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3f)).apply { topMargin = dp(10f) }
            max = if (m.duration > 0) (m.duration / 1000).toInt() else 100
            progress = (m.position / 1000).toInt()
        })

        // Time labels
        val tr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) }
        }
        tr.addView(tv(fmtMs(m.position), 9f, 0xFF666666.toInt(), Typeface.MONOSPACE).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tr.addView(tv(fmtMs(m.duration), 9f, 0xFF666666.toInt(), Typeface.MONOSPACE))
        lay.addView(tr)

        // Controls - INTERACTIVE
        val ctrls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
        }
        ctrls.addView(ctrlBtn("\u23EE", false) { mt?.skipPrev() })
        ctrls.addView(ctrlBtn(if (m.isPlaying) "\u23F8" else "\u25B6\uFE0F", true) { mt?.togglePlayPause() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(16f)
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(16f)
        })
        ctrls.addView(ctrlBtn("\u23ED", false) { mt?.skipNext() })
        lay.addView(ctrls)

        // Hint
        lay.addView(tv("Basili tut: Duraklat | Cift dokun: Sonraki", 8f, 0xFF555555.toInt()).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f)
        })

        expandedV.addView(lay)
    }

    private fun eCall(c: CallState) {
        val lay = vLay(Gravity.CENTER_HORIZONTAL)

        // Avatar
        lay.addView(TextView(context).apply {
            text = c.callerName.take(2).uppercase(); textSize = 16f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF667EEA.toInt()) }
        })

        // Name
        lay.addView(tv(c.callerName.ifEmpty { c.callerNumber.ifEmpty { "Bilinmeyen" } }, 14f, Color.WHITE,
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f)
        })

        // Duration/Status
        lay.addView(tv(if (c.isActive) fmtD(c.durationSeconds) else if (c.isIncoming) "Gelen Arama..." else "Ariyor...",
            12f, if (c.isActive) 0xFF4CD964.toInt() else 0xFF999999.toInt(), Typeface.MONOSPACE).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(2f)
        })

        // Call action buttons
        val btns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) }
        }

        if (c.isIncoming) {
            // Gelen arama: Cevapla + Reddet
            btns.addView(actionBtn("\u2714", 0xFF4CD964.toInt()) {
                // Cevapla - telefon uygulamasini ac
                try {
                    val i = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage("com.android.dialer")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i)
                } catch (_: Exception) {}
            })
            btns.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24f), 1)
            })
            btns.addView(actionBtn("\u2716", 0xFFFF3B30.toInt()) {
                try {
                    val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    tm.endCall()
                } catch (_: Exception) {}
            })
        } else if (c.isActive) {
            // Aktif arama: Kapat
            btns.addView(actionBtn("\u2716 Kapat", 0xFFFF3B30.toInt()) {
                try {
                    val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    tm.endCall()
                } catch (_: Exception) {}
            })
        }

        lay.addView(btns)

        // Hint
        lay.addView(tv("Basili tut: Aramayi sonlandir", 8f, 0xFF555555.toInt()).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f)
        })

        expandedV.addView(lay)
    }

    private fun eCharge(ch: ChargingState) {
        val lay = vLay(Gravity.CENTER)
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        r.addView(tv("\u26A1", 22f))
        r.addView(tv("${ch.level}%", 28f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply {
            setPadding(dp(6f), 0, 0, 0)
        })
        lay.addView(r)
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6f)).apply { topMargin = dp(8f) }
            max = 100; progress = ch.level
        })
        lay.addView(tv(if (ch.isFast) "Hizli sarj" else "Sarj ediliyor", 11f, 0xFF999999.toInt()).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f)
        })
        expandedV.addView(lay)
    }

    private fun eNotif(n: NotificationInfo?) {
        if (n == null) return
        val lay = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(2f), dp(4f), dp(2f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            // Bildirime tikla -> uygulamayi ac
            setOnClickListener {
                try {
                    val li = context.packageManager.getLaunchIntentForPackage(n.packageName)
                    li?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    IslandStateManager.dismissNotification()
                } catch (_: Exception) {}
            }
        }
        if (n.appIcon != null) lay.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(8f) }
            setImageBitmap(n.appIcon)
        }) else lay.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(8f) }
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(n.color) }
        })
        val tc = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tc.addView(tv(n.appName.uppercase(), 8f, 0xFF888888.toInt()))
        tc.addView(tv(n.title, 12f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(n.body, 10f, 0xFF999999.toInt(), null, 2))
        lay.addView(tc)
        // X butonu
        lay.addView(tv("\u2715", 14f, 0xFF666666.toInt()).apply {
            setPadding(dp(8f), 0, 0, 0)
            setOnClickListener { IslandStateManager.dismissNotification() }
        })
        expandedV.addView(lay)
    }

    private fun eTimer(t: TimerState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv(if (t.isStopwatch) "Kronometre" else "Zamanlayici", 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        val display = if (t.isStopwatch) fmtMsFull(t.elapsedMs) else fmtMsFull(maxOf(0, t.targetMs - t.elapsedMs))
        lay.addView(tv(display, 28f, 0xFFFF9500.toInt(), Typeface.MONOSPACE).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f)
        })
        val btns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) }
        }
        btns.addView(actionBtn("\u23F9 Durdur", 0xFFFF3B30.toInt()) { TimerManager.stop() })
        lay.addView(btns)
        expandedV.addView(lay)
    }

    private fun eNet(n: NetSpeedState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv("Internet Hizi", 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        val dl = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
        }
        dl.addView(tv("\u2193 ", 14f, 0xFF4CD964.toInt()))
        dl.addView(tv(n.downloadSpeed, 18f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        lay.addView(dl)
        val ul = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) }
        }
        ul.addView(tv("\u2191 ", 14f, 0xFFFF6B35.toInt()))
        ul.addView(tv(n.uploadSpeed, 18f, 0xFFFF6B35.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        lay.addView(ul)
        expandedV.addView(lay)
    }

    private fun eNav(nav: NavigationState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv("\uD83E\uDDED Navigasyon", 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        lay.addView(tv(nav.instruction, 14f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 2).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(6f)
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) }
        }
        row.addView(tv(nav.distance, 16f, 0xFF007AFF.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        row.addView(tv("  \u2022  ", 14f, 0xFF666666.toInt()))
        row.addView(tv(nav.eta, 16f, 0xFF4CD964.toInt()))
        lay.addView(row)
        expandedV.addView(lay)
    }

    private fun eCamMic(ind: SystemIndicators) {
        val lay = vLay(Gravity.CENTER)
        if (ind.cameraInUse) {
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            r.addView(dot(0xFF4CD964.toInt(), 10))
            r.addView(tv("  Kamera kullaniliyor", 13f, 0xFF4CD964.toInt()))
            lay.addView(r)
        }
        if (ind.micInUse) {
            val r = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) }
            }
            r.addView(dot(0xFFFF9500.toInt(), 10))
            r.addView(tv("  Mikrofon kullaniliyor", 13f, 0xFFFF9500.toInt()))
            lay.addView(r)
        }
        expandedV.addView(lay)
    }

    private fun eRecord() {
        val lay = vLay(Gravity.CENTER)
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        r.addView(dot(0xFFFF3B30.toInt(), 12))
        r.addView(tv("  Ekran Kaydediliyor", 14f, 0xFFFF3B30.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        lay.addView(r)
        expandedV.addView(lay)
    }

    private fun eBt(ind: SystemIndicators) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv("\uD83D\uDD0C Bluetooth", 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        lay.addView(tv(ind.bluetoothDevice, 16f, 0xFF007AFF.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply {
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(6f)
        })
        expandedV.addView(lay)
    }

    // ═══ ANIMATION ═══

    private fun animTo(expand: Boolean, s: IslandState) {
        val sw = context.resources.displayMetrics.widthPixels
        val tw = if (expand) sw - dp(EXPANDED_MARGIN * 2)
                 else if (s.mode != IslandMode.IDLE) dp(idleW.toFloat() + 50f)
                 else dp(idleW.toFloat())
        val th = if (expand) when (s.mode) {
            IslandMode.MUSIC -> dp(175f)
            IslandMode.CALL -> dp(175f)
            IslandMode.CHARGING -> dp(115f)
            IslandMode.NOTIFICATION -> dp(70f)
            IslandMode.TIMER -> dp(120f)
            IslandMode.NET_SPEED -> dp(110f)
            IslandMode.NAVIGATION -> dp(120f)
            IslandMode.CAMERA_MIC -> dp(80f)
            IslandMode.SCREEN_RECORD -> dp(60f)
            IslandMode.BLUETOOTH -> dp(100f)
            else -> dp(90f)
        } else dp(idleH.toFloat())
        val tr = if (expand) dp(EXPANDED_RADIUS).toFloat() else dp(IDLE_RADIUS).toFloat()
        val lp = container.layoutParams
        ValueAnimator.ofInt(lp.width, tw).apply {
            duration = 450; interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { lp.width = it.animatedValue as Int; container.layoutParams = lp }
            start()
        }
        ValueAnimator.ofInt(lp.height, th).apply {
            duration = 450; interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { lp.height = it.animatedValue as Int; container.layoutParams = lp }
            start()
        }
        (container.background as? GradientDrawable)?.cornerRadius = tr
    }

    private fun autoDismiss() {
        dismissR?.let { handler.removeCallbacks(it) }
        dismissR = Runnable { IslandStateManager.dismissNotification() }
        handler.postDelayed(dismissR!!, 4000)
    }

    // ═══ HELPERS ═══

    private fun vLay(g: Int = Gravity.NO_GRAVITY): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = g
        setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, tf: Typeface? = null, maxL: Int = 0) = TextView(context).apply {
        text = t; textSize = size; setTextColor(color); tf?.let { typeface = it }
        if (maxL > 0) { maxLines = maxL; ellipsize = TextUtils.TruncateAt.END }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun spacer() = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    private fun dot(color: Int, size: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size.toFloat()), dp(size.toFloat()))
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    private fun ctrlBtn(t: String, big: Boolean, click: () -> Unit) = TextView(context).apply {
        text = t; textSize = if (big) 20f else 14f; gravity = Gravity.CENTER
        val s = if (big) dp(42f) else dp(32f)
        layoutParams = LinearLayout.LayoutParams(s, s)
        if (big) {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            setTextColor(Color.BLACK)
        }
        setOnClickListener {
            click()
            // Dokunma animasyonu
            animate().scaleX(0.85f).scaleY(0.85f).setDuration(60).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }
    }

    private fun actionBtn(t: String, bgColor: Int, click: () -> Unit) = TextView(context).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        background = GradientDrawable().apply {
            cornerRadius = dp(20f).toFloat()
            setColor(bgColor)
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setOnClickListener {
            click()
            animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }
    }

    private fun fmtD(s: Int) = "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    private fun fmtMs(ms: Long) = fmtD((ms / 1000).toInt())
    private fun fmtMsFull(ms: Long): String {
        val t = (ms / 1000).toInt()
        val m = t / 60; val s = t % 60; val cs = ((ms % 1000) / 10).toInt()
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}"
    }
}
