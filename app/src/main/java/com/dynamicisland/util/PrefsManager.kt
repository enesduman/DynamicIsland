package com.dynamicisland.util

import android.content.Context

object PrefsManager {
    private const val P = "di_prefs"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun getYOffset(c: Context) = p(c).getInt("y_offset", 0)
    fun setYOffset(c: Context, v: Int) = p(c).edit().putInt("y_offset", v).apply()

    fun getIdleWidth(c: Context) = p(c).getInt("idle_w", 105)
    fun setIdleWidth(c: Context, v: Int) = p(c).edit().putInt("idle_w", v).apply()

    fun getIdleHeight(c: Context) = p(c).getInt("idle_h", 32)
    fun setIdleHeight(c: Context, v: Int) = p(c).edit().putInt("idle_h", v).apply()

    fun getGlowEnabled(c: Context) = p(c).getBoolean("glow", true)
    fun setGlowEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("glow", v).apply()

    // Feature toggles
    fun getMusicEnabled(c: Context) = p(c).getBoolean("ft_music", true)
    fun setMusicEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_music", v).apply()

    fun getCallEnabled(c: Context) = p(c).getBoolean("ft_call", true)
    fun setCallEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_call", v).apply()

    fun getNotifEnabled(c: Context) = p(c).getBoolean("ft_notif", true)
    fun setNotifEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_notif", v).apply()

    fun getChargingEnabled(c: Context) = p(c).getBoolean("ft_charge", true)
    fun setChargingEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_charge", v).apply()

    fun getTimerEnabled(c: Context) = p(c).getBoolean("ft_timer", true)
    fun setTimerEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_timer", v).apply()

    fun getNetSpeedEnabled(c: Context) = p(c).getBoolean("ft_net", false)
    fun setNetSpeedEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_net", v).apply()

    fun getBtEnabled(c: Context) = p(c).getBoolean("ft_bt", true)
    fun setBtEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("ft_bt", v).apply()

    fun getHideStatusNotif(c: Context) = p(c).getBoolean("hide_notif", true)
    fun setHideStatusNotif(c: Context, v: Boolean) = p(c).edit().putBoolean("hide_notif", v).apply()
}
