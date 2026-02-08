package com.dynamicisland.service

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.dynamicisland.model.IslandStateManager
import com.dynamicisland.model.NetSpeedState
import com.dynamicisland.model.SystemIndicators

class SystemTracker(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var prevRx = 0L
    private var prevTx = 0L
    private var netR: Runnable? = null
    private var indR: Runnable? = null
    private var recv: BroadcastReceiver? = null

    fun start() { startNet(); startInd(); regBt() }

    fun stop() {
        netR?.let { handler.removeCallbacks(it) }
        indR?.let { handler.removeCallbacks(it) }
        recv?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
    }

    private fun startNet() {
        prevRx = TrafficStats.getTotalRxBytes()
        prevTx = TrafficStats.getTotalTxBytes()
        netR = object : Runnable {
            override fun run() {
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val dl = rx - prevRx; val ul = tx - prevTx
                prevRx = rx; prevTx = tx
                val active = dl > 10000 || ul > 10000
                IslandStateManager.updateNetSpeed(NetSpeedState(fmtSpd(dl), fmtSpd(ul), active))
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(netR!!, 1000)
    }

    private fun startInd() {
        indR = object : Runnable {
            override fun run() {
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val silent = audio.ringerMode == AudioManager.RINGER_MODE_SILENT
                val dnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL else false
                val bt = getBtDev()
                val cur = IslandStateManager.state.value.indicators
                IslandStateManager.updateIndicators(cur.copy(isSilent = silent, isDnd = dnd,
                    bluetoothDevice = bt, bluetoothConnected = bt.isNotEmpty()))
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(indR!!, 1000)
    }

    private fun regBt() {
        recv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val bt = getBtDev()
                val cur = IslandStateManager.state.value.indicators
                IslandStateManager.updateIndicators(cur.copy(bluetoothDevice = bt, bluetoothConnected = bt.isNotEmpty()))
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        try { ctx.registerReceiver(recv, f) } catch (_: Exception) {}
    }

    private fun getBtDev(): String {
        return try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val a = bm.adapter ?: return ""
            a.bondedDevices.firstOrNull()?.name ?: ""
        } catch (_: Exception) { "" }
    }

    private fun fmtSpd(b: Long): String = when {
        b < 1024 -> "${b} B/s"
        b < 1024 * 1024 -> "${b / 1024} KB/s"
        else -> String.format("%.1f MB/s", b / (1024.0 * 1024.0))
    }
}
