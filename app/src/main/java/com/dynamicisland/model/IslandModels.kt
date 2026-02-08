package com.dynamicisland.model

import android.graphics.Bitmap

enum class IslandMode {
    IDLE, MUSIC, CALL, NOTIFICATION, CHARGING,
    TIMER, WEATHER, NET_SPEED, NAVIGATION,
    CAMERA_MIC, SILENT_MODE, SCREEN_RECORD, BLUETOOTH
}

data class IslandState(
    val mode: IslandMode = IslandMode.IDLE,
    val secondaryMode: IslandMode = IslandMode.IDLE,
    val expanded: Boolean = false,
    val music: MusicState = MusicState(),
    val call: CallState = CallState(),
    val notification: NotificationInfo? = null,
    val charging: ChargingState = ChargingState(),
    val timer: TimerState = TimerState(),
    val weather: WeatherState = WeatherState(),
    val netSpeed: NetSpeedState = NetSpeedState(),
    val navigation: NavigationState = NavigationState(),
    val indicators: SystemIndicators = SystemIndicators(),
    val glowColor: Int = 0
)

data class MusicState(
    val title: String = "", val artist: String = "",
    val albumArt: Bitmap? = null, val isPlaying: Boolean = false,
    val position: Long = 0, val duration: Long = 0,
    val packageName: String = ""
)

data class CallState(
    val isActive: Boolean = false, val isIncoming: Boolean = false,
    val callerName: String = "", val callerNumber: String = "",
    val durationSeconds: Int = 0
)

data class NotificationInfo(
    val id: Int = 0, val packageName: String = "",
    val appName: String = "", val title: String = "",
    val body: String = "", val appIcon: Bitmap? = null,
    val color: Int = 0, val postTime: Long = 0
)

data class ChargingState(
    val isCharging: Boolean = false, val level: Int = 0,
    val isUSB: Boolean = false, val isFast: Boolean = false
)

data class TimerState(
    val isRunning: Boolean = false, val isStopwatch: Boolean = false,
    val elapsedMs: Long = 0, val targetMs: Long = 0
)

data class WeatherState(
    val temp: Int = 0, val condition: String = "",
    val city: String = "", val icon: String = ""
)

data class NetSpeedState(
    val downloadSpeed: String = "0 B/s", val uploadSpeed: String = "0 B/s",
    val isActive: Boolean = false
)

data class NavigationState(
    val isActive: Boolean = false, val instruction: String = "",
    val distance: String = "", val eta: String = ""
)

data class SystemIndicators(
    val cameraInUse: Boolean = false, val micInUse: Boolean = false,
    val isSilent: Boolean = false, val isDnd: Boolean = false,
    val isScreenRecording: Boolean = false,
    val bluetoothDevice: String = "", val bluetoothConnected: Boolean = false
)
