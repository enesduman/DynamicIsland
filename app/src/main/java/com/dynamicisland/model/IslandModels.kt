package com.dynamicisland.model

import android.graphics.Bitmap

enum class IslandMode { IDLE, MUSIC, CALL, NOTIFICATION, CHARGING }

data class MusicState(
    val title: String = "", val artist: String = "",
    val albumArt: Bitmap? = null, val isPlaying: Boolean = false,
    val position: Long = 0L, val duration: Long = 0L,
    val packageName: String = ""
)

data class CallState(
    val isActive: Boolean = false, val isIncoming: Boolean = false,
    val callerNumber: String = "", val callerName: String = "",
    val durationSeconds: Int = 0
)

data class NotificationInfo(
    val id: Int = 0, val packageName: String = "",
    val appName: String = "", val title: String = "",
    val body: String = "", val appIcon: Bitmap? = null,
    val color: Int = 0xFF5856D6.toInt(), val timestamp: Long = 0L
)

data class ChargingState(
    val isCharging: Boolean = false, val level: Int = 0,
    val isUSB: Boolean = false, val isFast: Boolean = false
)

data class IslandState(
    val mode: IslandMode = IslandMode.IDLE, val expanded: Boolean = false,
    val music: MusicState = MusicState(), val call: CallState = CallState(),
    val notification: NotificationInfo? = null,
    val charging: ChargingState = ChargingState()
)
