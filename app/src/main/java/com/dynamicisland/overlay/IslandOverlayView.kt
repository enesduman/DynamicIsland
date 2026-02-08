package com.dynamicisland.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.dynamicisland.model.*
import com.dynamicisland.model.IslandStateManager
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
    private var autoCollapseR: Runnable? = null
    private val glowView: View
    private val container: LinearLayout
    private val compactV: LinearLayout
    private val expandedV: FrameLayout
    // Secondary (split) island
    private val secondaryContainer: LinearLayout
    private val secondaryContent: LinearLayout
    private var glowEnabled = true
    private var currentGlow = 0

    private val EXPANDED_RADIUS = 24f
    private val IDLE_RADIUS = 16f
    private val AUTO_COLLAPSE_MS = 5000L
    private val NOTIF_DISMISS_MS = 3000L
    private val SPLIT_GAP = 8f

    init {
        glowView = View(context).apply {
            layoutParams = LayoutParams(dp(idleW + 8f), dp(idleH + 8f)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            alpha = 0f
        }
        addView(glowView)

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(idleW.toFloat()), dp(idleH.toFloat())).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4f)
            }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(IDLE_RADIUS).toFloat() }
            elevation = dp(12f).toFloat(); clipChildren = true; clipToPadding = true
        }

        compactV = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(10f), dp(3f), dp(10f), dp(3f))
        }

        expandedV = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }

        container.addView(compactV)
        container.addView(expandedV)
        addView(container)

        // Secondary island (sol tarafta kucuk balon)
        secondaryContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(40f), dp(idleH.toFloat())).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(4f)
            }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(IDLE_RADIUS).toFloat() }
            elevation = dp(12f).toFloat()
            visibility = View.GONE
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        secondaryContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        secondaryContainer.addView(secondaryContent)
        addView(secondaryContainer)

        setupTouch()
        idle()
    }

    private fun setupTouch() {
        val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                vibrateLight()
                IslandStateManager.toggleExpanded()
                if (!cur.expanded) scheduleAutoCollapse()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                vibratePattern()
                when (cur.mode) {
                    IslandMode.MUSIC -> { mt?.togglePlayPause(); pulseAnim() }
                    IslandMode.CALL -> { try { (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall() } catch (_: Exception) {}; pulseAnim() }
                    IslandMode.TIMER -> { TimerManager.stop(); pulseAnim() }
                    IslandMode.NOTIFICATION -> { IslandStateManager.dismissNotification(); pulseAnim() }
                    else -> { if (!cur.expanded) { IslandStateManager.toggleExpanded(); scheduleAutoCollapse() } }
                }
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (cur.mode == IslandMode.MUSIC) { vibrateLight(); mt?.skipNext(); pulseAnim() }
                return true
            }
        })

        this.setOnTouchListener { _, event ->
            val loc = IntArray(2); container.getLocationOnScreen(loc)
            val inMain = event.rawX >= loc[0] && event.rawX <= loc[0] + container.width && event.rawY >= loc[1] && event.rawY <= loc[1] + container.height
            // Secondary container
            val sLoc = IntArray(2); secondaryContainer.getLocationOnScreen(sLoc)
            val inSec = secondaryContainer.visibility == View.VISIBLE && event.rawX >= sLoc[0] && event.rawX <= sLoc[0] + secondaryContainer.width && event.rawY >= sLoc[1] && event.rawY <= sLoc[1] + secondaryContainer.height

            if (inSec && event.action == MotionEvent.ACTION_UP) {
                // Secondary'ye tikla -> secondary mod'u primary yap
                vibrateLight()
                val sec = cur.secondaryMode
                if (sec != IslandMode.IDLE) IslandStateManager.setMode(sec)
                true
            } else if (inMain) {
                gd.onTouchEvent(event)
                true
            } else if (cur.expanded && event.action == MotionEvent.ACTION_UP) {
                IslandStateManager.toggleExpanded()
                true
            } else { false }
        }
    }

    private fun vibrateLight() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun vibratePattern() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 40, 25, 40, 40), -1))
        } catch (_: Exception) {}
    }

    private fun pulseAnim() {
        container.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            container.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun scheduleAutoCollapse() {
        autoCollapseR?.let { handler.removeCallbacks(it) }
        autoCollapseR = Runnable { if (cur.expanded) IslandStateManager.toggleExpanded() }
        handler.postDelayed(autoCollapseR!!, AUTO_COLLAPSE_MS)
    }

    fun setGlowEnabled(e: Boolean) { glowEnabled = e }

    fun updateSizes(w: Int, h: Int) {
        idleW = w; idleH = h
        if (!cur.expanded && cur.mode == IslandMode.IDLE) {
            val lp = container.layoutParams; lp.width = dp(w.toFloat()); lp.height = dp(h.toFloat()); container.layoutParams = lp
        }
    }

    fun update(s: IslandState) {
        val prev = cur; cur = s
        if (s.expanded && !prev.expanded) animTo(true, s)
        else if (!s.expanded && prev.expanded) animTo(false, s)
        if (s.expanded) expanded(s) else compact(s)
        if (s.mode == IslandMode.NOTIFICATION) autoDismiss()
        // Expanded iken auto collapse
        if (s.expanded && s.mode != IslandMode.NOTIFICATION) scheduleAutoCollapse()
        updateGlow(s.glowColor)
        updateSecondary(s)
    }

    // ═══ SECONDARY (SPLIT) ISLAND ═══

    private fun updateSecondary(s: IslandState) {
        if (s.secondaryMode == IslandMode.IDLE || s.expanded) {
            secondaryContainer.visibility = View.GONE
            return
        }
        secondaryContainer.visibility = View.VISIBLE
        secondaryContent.removeAllViews()

        // Konumlandir: ana container'in solunda
        val sw = context.resources.displayMetrics.widthPixels
        val mainW = container.layoutParams.width
        val secW = dp(40f)
        val mainLeft = (sw - mainW) / 2
        val secLeft = mainLeft - secW - dp(SPLIT_GAP)

        (secondaryContainer.layoutParams as LayoutParams).apply {
            gravity = Gravity.TOP
            marginStart = maxOf(dp(8f), secLeft)
            topMargin = (container.layoutParams as LayoutParams).topMargin
            width = secW; height = dp(idleH.toFloat())
        }
        secondaryContainer.requestLayout()

        when (s.secondaryMode) {
            IslandMode.MUSIC -> {
                if (s.music.albumArt != null) {
                    secondaryContent.addView(ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f))
                        setImageBitmap(s.music.albumArt); scaleType = ImageView.ScaleType.CENTER_CROP
                    })
                } else secondaryContent.addView(tv("\uD83C\uDFB5", 10f))
            }
            IslandMode.TIMER -> {
                secondaryContent.addView(tv("\u23F1", 10f, 0xFFFF9500.toInt()))
            }
            IslandMode.NAVIGATION -> {
                secondaryContent.addView(tv("\uD83E\uDDED", 10f, 0xFF007AFF.toInt()))
            }
            IslandMode.CHARGING -> {
                secondaryContent.addView(tv("\u26A1", 10f, 0xFF4CD964.toInt()))
            }
            else -> secondaryContainer.visibility = View.GONE
        }
    }

    private fun updateGlow(color: Int) {
        if (!glowEnabled || color == 0) { glowView.alpha = 0f; return }
        if (color != currentGlow) {
            currentGlow = color
            glowView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20f).toFloat()
                setColor(Color.TRANSPARENT); setStroke(dp(2f), color)
            }
            glowView.alpha = 0f; glowView.animate().alpha(0.6f).setDuration(300).start()
        }
        val lp = glowView.layoutParams as LayoutParams; val clp = container.layoutParams
        lp.width = clp.width + dp(6f); lp.height = clp.height + dp(6f)
        lp.topMargin = (container.layoutParams as LayoutParams).topMargin - dp(3f); glowView.layoutParams = lp
    }

    private fun compact(s: IslandState) {
        compactV.visibility = View.VISIBLE; expandedV.visibility = View.GONE; compactV.removeAllViews()
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
        compactV.removeAllViews(); compactV.gravity = Gravity.CENTER
        repeat(2) { i ->
            compactV.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(9f), dp(9f)).apply { if (i > 0) marginStart = dp(4f) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1A1A1A.toInt()); setStroke(1, 0xFF333333.toInt()) }
            })
        }
    }

    private fun cMusic(m: MusicState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        if (m.albumArt != null) compactV.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)); setImageBitmap(m.albumArt); scaleType = ImageView.ScaleType.CENTER_CROP
        }) else compactV.addView(tv("\uD83C\uDFB5", 10f))
        compactV.addView(spacer())
        val bc = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(16f)) }
        repeat(5) { i ->
            bc.addView(View(context).apply {
                val h = if (m.isPlaying) (3 + (Math.random() * 12)).toInt() else 3
                layoutParams = LinearLayout.LayoutParams(dp(2.5f), dp(h.toFloat())).apply { if (i > 0) marginStart = dp(1.5f) }
                background = GradientDrawable().apply { setColor(0xFFFF6B35.toInt()); cornerRadius = dp(1.5f).toFloat() }
            })
        }
        compactV.addView(bc)
    }

    private fun cCall(c: CallState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(dot(0xFF4CD964.toInt(), 7)); compactV.addView(spacer())
        compactV.addView(tv(fmtD(c.durationSeconds), 12f, 0xFF4CD964.toInt(), Typeface.MONOSPACE))
    }
    private fun cCharge(ch: ChargingState) {
        compactV.gravity = Gravity.CENTER_VERTICAL; compactV.addView(tv("\u26A1", 10f)); compactV.addView(spacer())
        compactV.addView(tv("${ch.level}%", 12f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
    }
    private fun cTimer(t: TimerState) {
        compactV.gravity = Gravity.CENTER_VERTICAL; compactV.addView(tv("\u23F1", 10f)); compactV.addView(spacer())
        val display = if (t.isStopwatch) fmtMs(t.elapsedMs) else fmtMs(maxOf(0, t.targetMs - t.elapsedMs))
        compactV.addView(tv(display, 12f, 0xFFFF9500.toInt(), Typeface.MONOSPACE))
    }
    private fun cNet(n: NetSpeedState) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("\u2193", 10f, 0xFF4CD964.toInt())); compactV.addView(tv(n.downloadSpeed, 9f, 0xFF4CD964.toInt()))
        compactV.addView(spacer()); compactV.addView(tv("\u2191", 10f, 0xFFFF6B35.toInt())); compactV.addView(tv(n.uploadSpeed, 9f, 0xFFFF6B35.toInt()))
    }
    private fun cNav(nav: NavigationState) {
        compactV.gravity = Gravity.CENTER_VERTICAL; compactV.addView(tv("\uD83E\uDDED", 10f)); compactV.addView(spacer())
        compactV.addView(tv(nav.distance, 11f, 0xFF007AFF.toInt()))
    }
    private fun cCamMic(ind: SystemIndicators) {
        compactV.gravity = Gravity.CENTER_VERTICAL
        if (ind.cameraInUse) compactV.addView(dot(0xFF4CD964.toInt(), 7))
        if (ind.micInUse) compactV.addView(dot(0xFFFF9500.toInt(), 7))
    }
    private fun cRecord() {
        compactV.gravity = Gravity.CENTER_VERTICAL; compactV.addView(dot(0xFFFF3B30.toInt(), 8)); compactV.addView(spacer())
        compactV.addView(tv("REC", 10f, 0xFFFF3B30.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
    }

    // ═══ EXPANDED ═══
    private fun expanded(s: IslandState) {
        compactV.visibility = View.GONE; expandedV.visibility = View.VISIBLE; expandedV.removeAllViews()
        when (s.mode) {
            IslandMode.MUSIC -> eMusic(s.music); IslandMode.CALL -> eCall(s.call)
            IslandMode.CHARGING -> eCharge(s.charging); IslandMode.NOTIFICATION -> eNotif(s.notification)
            IslandMode.TIMER -> eTimer(s.timer); IslandMode.NET_SPEED -> eNet(s.netSpeed)
            IslandMode.NAVIGATION -> eNav(s.navigation); IslandMode.CAMERA_MIC -> eCamMic(s.indicators)
            IslandMode.SCREEN_RECORD -> eRecord(); IslandMode.BLUETOOTH -> eBt(s.indicators)
            else -> {}
        }
    }

    private fun eMusic(m: MusicState) {
        val lay = vLay()
        val top = hLay(Gravity.CENTER_VERTICAL)
        top.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f)).apply { marginEnd = dp(8f) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (m.albumArt != null) setImageBitmap(m.albumArt) else setBackgroundColor(0xFF333333.toInt())
            setOnClickListener { try { context.packageManager.getLaunchIntentForPackage(m.packageName)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (_: Exception) {} }
        })
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tc.addView(tv(m.title.ifEmpty { "Bilinmeyen" }, 12f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(m.artist.ifEmpty { "Bilinmeyen" }, 10f, 0xFF999999.toInt(), null, 1))
        top.addView(tc); lay.addView(top)
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2f)).apply { topMargin = dp(6f) }
            max = if (m.duration > 0) (m.duration / 1000).toInt() else 100; progress = (m.position / 1000).toInt()
        })
        val ctrls = hLay(Gravity.CENTER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4f) }
        ctrls.addView(ctrlBtn("\u23EE", false) { mt?.skipPrev() })
        ctrls.addView(ctrlBtn(if (m.isPlaying) "\u23F8" else "\u25B6\uFE0F", true) { mt?.togglePlayPause() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(14f); (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(14f)
        })
        ctrls.addView(ctrlBtn("\u23ED", false) { mt?.skipNext() })
        lay.addView(ctrls); expandedV.addView(lay)
    }

    private fun eCall(c: CallState) {
        val lay = vLay(Gravity.CENTER_HORIZONTAL)
        lay.addView(TextView(context).apply { text = c.callerName.take(2).uppercase(); textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF667EEA.toInt()) } })
        lay.addView(tv(c.callerName.ifEmpty { c.callerNumber.ifEmpty { "Bilinmeyen" } }, 13f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply { gravity = Gravity.CENTER; (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(3f) })
        lay.addView(tv(if (c.isActive) fmtD(c.durationSeconds) else if (c.isIncoming) "Gelen Arama" else "Ariyor...", 11f, if (c.isActive) 0xFF4CD964.toInt() else 0xFF999999.toInt(), Typeface.MONOSPACE).apply { gravity = Gravity.CENTER })
        val btns = hLay(Gravity.CENTER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6f) }
        if (c.isIncoming) {
            btns.addView(actionBtn("\u2714", 0xFF4CD964.toInt()) { try { context.startActivity(context.packageManager.getLaunchIntentForPackage("com.android.dialer")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} })
            btns.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(20f), 1) })
            btns.addView(actionBtn("\u2716", 0xFFFF3B30.toInt()) { try { (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall() } catch (_: Exception) {} })
        } else if (c.isActive) { btns.addView(actionBtn("\u2716 Kapat", 0xFFFF3B30.toInt()) { try { (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall() } catch (_: Exception) {} }) }
        lay.addView(btns); expandedV.addView(lay)
    }

    private fun eCharge(ch: ChargingState) {
        val lay = vLay(Gravity.CENTER)
        val r = hLay(Gravity.CENTER); r.addView(tv("\u26A1", 20f))
        r.addView(tv("${ch.level}%", 24f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply { setPadding(dp(4f), 0, 0, 0) })
        lay.addView(r)
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4f)).apply { topMargin = dp(6f) }; max = 100; progress = ch.level })
        lay.addView(tv(if (ch.isFast) "Hizli sarj" else "Sarj ediliyor", 10f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        expandedV.addView(lay)
    }

    private fun eNotif(n: NotificationInfo?) {
        if (n == null) return
        val lay = hLay(Gravity.CENTER_VERTICAL).apply { setPadding(dp(4f), dp(2f), dp(4f), dp(2f)); layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { try { context.packageManager.getLaunchIntentForPackage(n.packageName)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; IslandStateManager.dismissNotification() } catch (_: Exception) {} }
        }
        if (n.appIcon != null) lay.addView(ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { marginEnd = dp(6f) }; setImageBitmap(n.appIcon) })
        else lay.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { marginEnd = dp(6f) }; background = GradientDrawable().apply { cornerRadius = dp(6f).toFloat(); setColor(n.color) } })
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tc.addView(tv(n.appName.uppercase(), 7f, 0xFF888888.toInt()))
        tc.addView(tv(n.title, 11f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(n.body, 9f, 0xFF999999.toInt(), null, 1))
        lay.addView(tc); lay.addView(tv("\u2715", 12f, 0xFF666666.toInt()).apply { setPadding(dp(6f), 0, 0, 0); setOnClickListener { IslandStateManager.dismissNotification() } })
        expandedV.addView(lay)
    }

    private fun eTimer(t: TimerState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv(if (t.isStopwatch) "Kronometre" else "Zamanlayici", 10f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        val display = if (t.isStopwatch) fmtMsFull(t.elapsedMs) else fmtMsFull(maxOf(0, t.targetMs - t.elapsedMs))
        lay.addView(tv(display, 22f, 0xFFFF9500.toInt(), Typeface.MONOSPACE).apply { gravity = Gravity.CENTER })
        lay.addView(actionBtn("\u23F9 Durdur", 0xFFFF3B30.toInt()) { TimerManager.stop() }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(6f) })
        expandedV.addView(lay)
    }

    private fun eNet(n: NetSpeedState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv("Internet Hizi", 10f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        val dl = hLay(Gravity.CENTER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4f) }
        dl.addView(tv("\u2193 ", 12f, 0xFF4CD964.toInt())); dl.addView(tv(n.downloadSpeed, 15f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        lay.addView(dl)
        val ul = hLay(Gravity.CENTER); ul.addView(tv("\u2191 ", 12f, 0xFFFF6B35.toInt())); ul.addView(tv(n.uploadSpeed, 15f, 0xFFFF6B35.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        lay.addView(ul); expandedV.addView(lay)
    }

    private fun eNav(nav: NavigationState) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv(nav.instruction, 12f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 2).apply { gravity = Gravity.CENTER })
        val row = hLay(Gravity.CENTER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(3f) }
        row.addView(tv(nav.distance, 14f, 0xFF007AFF.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
        row.addView(tv("  \u2022  ", 12f, 0xFF666666.toInt()))
        row.addView(tv(nav.eta, 14f, 0xFF4CD964.toInt()))
        lay.addView(row); expandedV.addView(lay)
    }

    private fun eCamMic(ind: SystemIndicators) {
        val lay = vLay(Gravity.CENTER)
        if (ind.cameraInUse) { val r = hLay(Gravity.CENTER); r.addView(dot(0xFF4CD964.toInt(), 10)); r.addView(tv("  Kamera", 12f, 0xFF4CD964.toInt())); lay.addView(r) }
        if (ind.micInUse) { val r = hLay(Gravity.CENTER); r.addView(dot(0xFFFF9500.toInt(), 10)); r.addView(tv("  Mikrofon", 12f, 0xFFFF9500.toInt())); lay.addView(r) }
        expandedV.addView(lay)
    }

    private fun eRecord() {
        val lay = vLay(Gravity.CENTER); val r = hLay(Gravity.CENTER); r.addView(dot(0xFFFF3B30.toInt(), 10))
        r.addView(tv("  Ekran Kaydediliyor", 12f, 0xFFFF3B30.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD))); lay.addView(r); expandedV.addView(lay)
    }

    private fun eBt(ind: SystemIndicators) {
        val lay = vLay(Gravity.CENTER)
        lay.addView(tv(ind.bluetoothDevice, 14f, 0xFF007AFF.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply { gravity = Gravity.CENTER })
        lay.addView(tv(if (ind.bluetoothConnected) "Bagli" else "Bagli degil", 10f, if (ind.bluetoothConnected) 0xFF4CD964.toInt() else 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
        expandedV.addView(lay)
    }

    // ═══ ANIMATION ═══
    private fun animTo(expand: Boolean, s: IslandState) {
        val sw = context.resources.displayMetrics.widthPixels
        val tw = if (expand) (sw * 0.6).toInt() else if (s.mode != IslandMode.IDLE) dp(idleW.toFloat() + 50f) else dp(idleW.toFloat())
        val th = if (expand) when (s.mode) {
            IslandMode.MUSIC -> dp(110f); IslandMode.CALL -> dp(130f); IslandMode.CHARGING -> dp(85f)
            IslandMode.NOTIFICATION -> dp(55f); IslandMode.TIMER -> dp(90f); IslandMode.NET_SPEED -> dp(85f)
            IslandMode.NAVIGATION -> dp(75f); IslandMode.CAMERA_MIC -> dp(55f); IslandMode.SCREEN_RECORD -> dp(45f)
            IslandMode.BLUETOOTH -> dp(60f); else -> dp(70f)
        } else dp(idleH.toFloat())
        val lp = container.layoutParams
        ValueAnimator.ofInt(lp.width, tw).apply { duration = 350; interpolator = OvershootInterpolator(0.8f); addUpdateListener { lp.width = it.animatedValue as Int; container.layoutParams = lp }; start() }
        ValueAnimator.ofInt(lp.height, th).apply { duration = 350; interpolator = OvershootInterpolator(0.8f); addUpdateListener { lp.height = it.animatedValue as Int; container.layoutParams = lp }; start() }
        (container.background as? GradientDrawable)?.cornerRadius = if (expand) dp(EXPANDED_RADIUS).toFloat() else dp(IDLE_RADIUS).toFloat()
        if (expand) { val olp = this.layoutParams; if (olp != null) { olp.height = th + dp(20f); this.layoutParams = olp } }
        else { val olp = this.layoutParams; if (olp != null) { olp.height = ViewGroup.LayoutParams.WRAP_CONTENT; this.layoutParams = olp } }
    }

    private fun autoDismiss() {
        dismissR?.let { handler.removeCallbacks(it) }
        dismissR = Runnable {
            IslandStateManager.dismissNotification()
            if (cur.expanded) IslandStateManager.toggleExpanded()
        }
        handler.postDelayed(dismissR!!, NOTIF_DISMISS_MS)
    }

    // ═══ HELPERS ═══
    private fun vLay(g: Int = Gravity.NO_GRAVITY) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = g; setPadding(dp(6f), dp(3f), dp(6f), dp(3f)); layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
    private fun hLay(g: Int) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = g; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun tv(t: String, size: Float, color: Int = Color.WHITE, tf: Typeface? = null, maxL: Int = 0) = TextView(context).apply { text = t; textSize = size; setTextColor(color); tf?.let { typeface = it }; if (maxL > 0) { maxLines = maxL; ellipsize = TextUtils.TruncateAt.END }; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun spacer() = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
    private fun dot(color: Int, size: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(size.toFloat()), dp(size.toFloat())); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }
    private fun ctrlBtn(t: String, big: Boolean, click: () -> Unit) = TextView(context).apply { text = t; textSize = if (big) 18f else 13f; gravity = Gravity.CENTER; val s = if (big) dp(36f) else dp(28f); layoutParams = LinearLayout.LayoutParams(s, s); if (big) { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }; setTextColor(Color.BLACK) }; setOnClickListener { vibrateLight(); click(); animate().scaleX(0.85f).scaleY(0.85f).setDuration(60).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(100).start() }.start(); scheduleAutoCollapse() } }
    private fun actionBtn(t: String, bgColor: Int, click: () -> Unit) = TextView(context).apply { text = t; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(14f), dp(6f), dp(14f), dp(6f)); background = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setColor(bgColor) }; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); setOnClickListener { vibrateLight(); click() } }
    private fun fmtD(s: Int) = "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    private fun fmtMs(ms: Long) = fmtD((ms / 1000).toInt())
    private fun fmtMsFull(ms: Long): String { val t = (ms / 1000).toInt(); val m = t / 60; val s = t % 60; val cs = ((ms % 1000) / 10).toInt(); return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}" }
}
