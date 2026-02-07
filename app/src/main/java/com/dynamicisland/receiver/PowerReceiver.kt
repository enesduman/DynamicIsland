package com.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.dynamicisland.model.ChargingState
import com.dynamicisland.model.IslandStateManager

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lv = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val p = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                IslandStateManager.updateCharging(ChargingState(true, lv, p == BatteryManager.BATTERY_PLUGGED_USB, p == BatteryManager.BATTERY_PLUGGED_AC))
            }
            Intent.ACTION_POWER_DISCONNECTED -> IslandStateManager.updateCharging(ChargingState(false, lv))
        }
    }
}
