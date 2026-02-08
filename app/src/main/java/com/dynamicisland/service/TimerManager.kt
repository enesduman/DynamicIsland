package com.dynamicisland.service

import android.os.Handler
import android.os.Looper
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.TimerState

object TimerManager {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var startTime = 0L
    private var targetMs = 0L
    private var isSW = true

    fun startStopwatch() { isSW = true; startTime = System.currentTimeMillis(); targetMs = 0; go() }

    fun startTimer(ms: Long) { isSW = false; startTime = System.currentTimeMillis(); targetMs = ms; go() }

    fun stop() { runnable?.let { handler.removeCallbacks(it) }; runnable = null
        IslandStateManager.updateTimer(TimerState(isRunning = false)) }

    private fun go() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = object : Runnable {
            override fun run() {
                val e = System.currentTimeMillis() - startTime
                if (!isSW && e >= targetMs) {
                    IslandStateManager.updateTimer(TimerState(false, false, targetMs, targetMs)); return }
                IslandStateManager.updateTimer(TimerState(true, isSW, e, targetMs))
                handler.postDelayed(this, 100)
            }
        }
        handler.postDelayed(runnable!!, 100)
    }
}
