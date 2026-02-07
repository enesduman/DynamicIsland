package com.dynamicisland.util

import android.content.Context

object PrefsManager {
    private const val P = "di_prefs"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    // Pozisyon: Y offset (px)
    fun getYOffset(c: Context) = p(c).getInt("y_offset", 0)
    fun setYOffset(c: Context, v: Int) = p(c).edit().putInt("y_offset", v).apply()

    // Idle boyut
    fun getIdleWidth(c: Context) = p(c).getInt("idle_w", 105)
    fun setIdleWidth(c: Context, v: Int) = p(c).edit().putInt("idle_w", v).apply()

    fun getIdleHeight(c: Context) = p(c).getInt("idle_h", 32)
    fun setIdleHeight(c: Context, v: Int) = p(c).edit().putInt("idle_h", v).apply()
}
