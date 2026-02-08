package com.dynamicisland.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.dynamicisland.model.CallState
import com.dynamicisland.model.IslandStateManager

class CallStateTracker(private val ctx: Context) {
    private var tm: TelephonyManager? = null
    private var listener: PhoneStateListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var callStart = 0L
    private var timerRunnable: Runnable? = null

    fun start() {
        tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        listener = object : PhoneStateListener() {
            @Deprecated("Deprecated")
            override fun onCallStateChanged(state: Int, number: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        IslandStateManager.updateCall(CallState(
                            isActive = false, isIncoming = true,
                            callerNumber = number ?: "", callerName = number ?: "Bilinmeyen"
                        ))
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        callStart = System.currentTimeMillis()
                        startTimer(number ?: "")
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        stopTimer()
                        IslandStateManager.updateCall(CallState())
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        tm?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun stop() {
        @Suppress("DEPRECATION")
        tm?.listen(listener, PhoneStateListener.LISTEN_NONE)
        stopTimer()
    }

    private fun startTimer(number: String) {
        timerRunnable = object : Runnable {
            override fun run() {
                val dur = ((System.currentTimeMillis() - callStart) / 1000).toInt()
                IslandStateManager.updateCall(CallState(
                    isActive = true, callerNumber = number, callerName = number.ifEmpty { "Arama" },
                    durationSeconds = dur
                ))
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }
}
