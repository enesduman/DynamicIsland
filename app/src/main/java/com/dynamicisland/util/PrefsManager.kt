package com.dynamicisland.util

import android.content.Context

object PrefsManager {
    private const val P = "di_prefs"
    private const val K = "enabled"
    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)
    fun isEnabled(c: Context) = p(c).getBoolean(K, false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean(K, v).apply()
}
