package com.dynamicisland.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.dynamicisland.model.*
import com.dynamicisland.service.MediaSessionTracker

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
    private val container: LinearLayout
    private val compactV: LinearLayout
    private val expandedV: FrameLayout

    private val EXPANDED_MARGIN = 16f
    private val IDLE_RADIUS = 16f
    private val EXPANDED_RADIUS = 28f

    init {
        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(idleW.toFloat()), dp(idleH.toFloat())).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(IDLE_RADIUS).toFloat() }
            elevation = dp(12f).toFloat(); clipChildren = true
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
        container.addView(compactV); container.addView(expandedV)
        addView(container)
        container.setOnClickListener { IslandStateManager.toggleExpanded() }
        idle()
    }

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
    }

    private fun compact(s: IslandState) {
        compactV.visibility = View.VISIBLE; expandedV.visibility = View.GONE; compactV.removeAllViews()
        when (s.mode) {
            IslandMode.MUSIC -> cMusic(s.music)
            IslandMode.CALL -> cCall(s.call)
            IslandMode.CHARGING -> cCharge(s.charging)
            else -> idle()
        }
    }

    private fun idle() { compactV.removeAllViews(); compactV.gravity = Gravity.CENTER
        repeat(2) { i -> compactV.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(9f), dp(9f)).apply { if (i>0) marginStart = dp(4f) }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1A1A1A.toInt()); setStroke(1, 0xFF333333.toInt()) }
        })}
    }

    private fun cMusic(m: MusicState) { compactV.gravity = Gravity.CENTER_VERTICAL
        if (m.albumArt != null) compactV.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)); setImageBitmap(m.albumArt)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }) else compactV.addView(tv("🎵", 10f))
        compactV.addView(spacer())
        val bc = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(16f)) }
        repeat(5) { i -> bc.addView(View(context).apply {
            val h = if (m.isPlaying) (3 + (Math.random()*12)).toInt() else 3
            layoutParams = LinearLayout.LayoutParams(dp(2.5f), dp(h.toFloat())).apply { if (i>0) marginStart = dp(1.5f) }
            background = GradientDrawable().apply { setColor(0xFFFF6B35.toInt()); cornerRadius = dp(1.5f).toFloat() }
        })}
        compactV.addView(bc)
    }

    private fun cCall(c: CallState) { compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF4CD964.toInt()) } })
        compactV.addView(spacer())
        compactV.addView(tv(fmtD(c.durationSeconds), 12f, 0xFF4CD964.toInt(), Typeface.MONOSPACE))
    }

    private fun cCharge(ch: ChargingState) { compactV.gravity = Gravity.CENTER_VERTICAL
        compactV.addView(tv("⚡", 10f)); compactV.addView(spacer())
        compactV.addView(tv("${ch.level}%", 12f, 0xFF4CD964.toInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD)))
    }

    private fun expanded(s: IslandState) {
        compactV.visibility = View.GONE; expandedV.visibility = View.VISIBLE; expandedV.removeAllViews()
        when (s.mode) {
            IslandMode.MUSIC -> eMusic(s.music)
            IslandMode.CALL -> eCall(s.call)
            IslandMode.CHARGING -> eCharge(s.charging)
            IslandMode.NOTIFICATION -> eNotif(s.notification)
            else -> {}
        }
    }

    private fun eMusic(m: MusicState) {
        val lay = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL
            setPadding(dp(4f), dp(2f), dp(4f), dp(2f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val art = ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(10f) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (m.albumArt != null) setImageBitmap(m.albumArt) else setBackgroundColor(0xFF333333.toInt()) }
        top.addView(art)
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tc.addView(tv(m.title.ifEmpty { "Bilinmeyen" }, 13f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(m.artist.ifEmpty { "Bilinmeyen" }, 11f, 0xFF999999.toInt(), null, 1))
        top.addView(tc); lay.addView(top)
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3f)).apply { topMargin = dp(10f) }
            max = if (m.duration > 0) (m.duration / 1000).toInt() else 100; progress = (m.position / 1000).toInt() })
        val tr = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) } }
        tr.addView(tv(fmtMs(m.position), 9f, 0xFF666666.toInt(), Typeface.MONOSPACE).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        tr.addView(tv(fmtMs(m.duration), 9f, 0xFF666666.toInt(), Typeface.MONOSPACE)); lay.addView(tr)
        val ctrls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) } }
        ctrls.addView(btn("⏮") { mt?.skipPrev() })
        ctrls.addView(btn(if (m.isPlaying) "⏸" else "▶️", true) { mt?.togglePlayPause() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(18f); (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(18f) })
        ctrls.addView(btn("⏭") { mt?.skipNext() }); lay.addView(ctrls); expandedV.addView(lay)
    }

    private fun eCall(c: CallState) {
        val lay = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
        lay.addView(TextView(context).apply { text = c.callerName.take(2).uppercase(); textSize = 16f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF667EEA.toInt()) } })
        lay.addView(tv(c.callerName.ifEmpty { c.callerNumber.ifEmpty { "Bilinmeyen" } }, 14f, Color.WHITE,
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply { gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f) })
        lay.addView(tv(if (c.isActive) fmtD(c.durationSeconds) else if (c.isIncoming) "Gelen Arama..." else "Ariyor...",
            12f, if (c.isActive) 0xFF4CD964.toInt() else 0xFF999999.toInt(), Typeface.MONOSPACE).apply { gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(2f) })
        expandedV.addView(lay)
    }

    private fun eCharge(ch: ChargingState) {
        val lay = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        r.addView(tv("⚡", 22f)); r.addView(tv("${ch.level}%", 28f, 0xFF4CD964.toInt(),
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)).apply { setPadding(dp(6f), 0, 0, 0) })
        lay.addView(r)
        lay.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6f)).apply { topMargin = dp(8f) }
            max = 100; progress = ch.level })
        lay.addView(tv(if (ch.isFast) "Hizli sarj" else "Sarj ediliyor", 11f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4f) })
        expandedV.addView(lay)
    }

    private fun eNotif(n: NotificationInfo?) { if (n == null) return
        val lay = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(2f), dp(4f), dp(2f))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT) }
        if (n.appIcon != null) lay.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(8f) }; setImageBitmap(n.appIcon) })
        else lay.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { marginEnd = dp(8f) }
            background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(n.color) } })
        val tc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tc.addView(tv(n.appName.uppercase(), 8f, 0xFF888888.toInt()))
        tc.addView(tv(n.title, 12f, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 1))
        tc.addView(tv(n.body, 10f, 0xFF999999.toInt(), null, 1))
        lay.addView(tc); expandedV.addView(lay)
    }

    private fun animTo(expand: Boolean, s: IslandState) {
        val sw = context.resources.displayMetrics.widthPixels
        val tw = if (expand) sw - dp(EXPANDED_MARGIN * 2)
                 else if (s.mode != IslandMode.IDLE) dp(idleW.toFloat() + 50f)
                 else dp(idleW.toFloat())
        val th = if (expand) when (s.mode) { IslandMode.MUSIC -> dp(160f); IslandMode.CALL -> dp(145f)
            IslandMode.CHARGING -> dp(115f); IslandMode.NOTIFICATION -> dp(65f); else -> dp(90f) }
            else dp(idleH.toFloat())
        val tr = if (expand) dp(EXPANDED_RADIUS).toFloat() else dp(IDLE_RADIUS).toFloat()
        val lp = container.layoutParams
        ValueAnimator.ofInt(lp.width, tw).apply { duration = 400; interpolator = OvershootInterpolator(0.5f)
            addUpdateListener { lp.width = it.animatedValue as Int; container.layoutParams = lp }; start() }
        ValueAnimator.ofInt(lp.height, th).apply { duration = 400; interpolator = OvershootInterpolator(0.5f)
            addUpdateListener { lp.height = it.animatedValue as Int; container.layoutParams = lp }; start() }
        (container.background as? GradientDrawable)?.cornerRadius = tr
    }

    private fun autoDismiss() { dismissR?.let { handler.removeCallbacks(it) }
        dismissR = Runnable { IslandStateManager.dismissNotification() }; handler.postDelayed(dismissR!!, 4000) }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, tf: Typeface? = null, maxL: Int = 0) = TextView(context).apply {
        text = t; textSize = size; setTextColor(color); tf?.let { typeface = it }
        if (maxL > 0) { maxLines = maxL; ellipsize = TextUtils.TruncateAt.END }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun spacer() = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
    private fun btn(t: String, big: Boolean = false, click: () -> Unit) = TextView(context).apply {
        text = t; textSize = if (big) 20f else 14f; gravity = Gravity.CENTER
        val s = if (big) dp(40f) else dp(30f); layoutParams = LinearLayout.LayoutParams(s, s)
        if (big) { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }; setTextColor(Color.BLACK) }
        setOnClickListener { click() } }
    private fun fmtD(s: Int) = "${(s/60).toString().padStart(2,'0')}:${(s%60).toString().padStart(2,'0')}"
    private fun fmtMs(ms: Long) = fmtD((ms / 1000).toInt())
}
