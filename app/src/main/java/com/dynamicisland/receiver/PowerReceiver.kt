package com.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.dynamicisland.model.ChargingState
import com.dynamicisland.model.IslandStateManager

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = intent.action == Intent.ACTION_POWER_CONNECTED

        IslandStateManager.updateCharging(ChargingState(
            isCharging = isCharging,
            level = level,
            isUSB = false,
            isFast = false
        ))
    }
}
