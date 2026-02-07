package com.dynamicisland.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.dynamicisland.model.CallState
import com.dynamicisland.model.IslandStateManager

class CallStateTracker(private val ctx: Context) {
    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var durRunnable: Runnable? = null
    private var tcb: Any? = null
    @Suppress("DEPRECATION") private var psl: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(s: Int) { handle(s) }
            }
            try { tm.registerTelephonyCallback(ctx.mainExecutor, cb); tcb = cb } catch (_: SecurityException) {}
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                override fun onCallStateChanged(s: Int, n: String?) { handle(s, n) }
            }
            @Suppress("DEPRECATION") tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE); psl = l
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tcb?.let { tm.unregisterTelephonyCallback(it as TelephonyCallback) }
        else @Suppress("DEPRECATION") psl?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        stopDur()
    }

    private fun handle(state: Int, number: String? = null) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val name = number?.let { resolve(it) } ?: ""
                IslandStateManager.updateCall(CallState(false, true, number ?: "",
                    name.ifEmpty { number ?: "Bilinmeyen" }, 0))
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                startTime = System.currentTimeMillis()
                val c = IslandStateManager.state.value.call
                IslandStateManager.updateCall(c.copy(isActive = true, isIncoming = false))
                startDur()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                stopDur(); IslandStateManager.updateCall(CallState()); startTime = 0
            }
        }
    }

    private fun startDur() { stopDur()
        durRunnable = object : Runnable { override fun run() {
            if (startTime > 0) { val s = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                IslandStateManager.updateCall(IslandStateManager.state.value.call.copy(durationSeconds = s)) }
            handler.postDelayed(this, 1000) } }
        handler.postDelayed(durRunnable!!, 1000)
    }
    private fun stopDur() { durRunnable?.let { handler.removeCallbacks(it) }; durRunnable = null }

    private fun resolve(num: String): String = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) ?: "" else "" } ?: ""
    } catch (_: Exception) { "" }
}
