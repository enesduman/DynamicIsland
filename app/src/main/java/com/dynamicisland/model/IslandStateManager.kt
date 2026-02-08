package com.dynamicisland.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object IslandStateManager {
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    var musicEnabled = true
    var callEnabled = true
    var notifEnabled = true
    var chargingEnabled = true
    var timerEnabled = true
    var netSpeedEnabled = false
    var btEnabled = true

    fun updateMusic(music: MusicState) {
        if (!musicEnabled) return
        _state.update { c ->
            val m = if (music.isPlaying) IslandMode.MUSIC
                    else if (c.mode == IslandMode.MUSIC) IslandMode.IDLE else c.mode
            c.copy(music = music, mode = m, glowColor = if (music.isPlaying) 0xFFFF6B35.toInt() else c.glowColor)
        }
    }

    fun updateCall(call: CallState) {
        if (!callEnabled) return
        _state.update { c ->
            val m = if (call.isActive || call.isIncoming) IslandMode.CALL
                    else if (c.mode == IslandMode.CALL) {
                        if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                    } else c.mode
            c.copy(call = call, mode = m, glowColor = if (call.isActive || call.isIncoming) 0xFF4CD964.toInt() else c.glowColor)
        }
    }

    fun showNotification(n: NotificationInfo) {
        if (!notifEnabled) return
        _state.update { c ->
            if (c.mode == IslandMode.CALL) c.copy(notification = n)
            else c.copy(notification = n, mode = IslandMode.NOTIFICATION, expanded = true, glowColor = n.color)
        }
    }

    fun dismissNotification() {
        _state.update { c ->
            val m = when {
                c.call.isActive -> IslandMode.CALL
                c.music.isPlaying -> IslandMode.MUSIC
                c.charging.isCharging && chargingEnabled -> IslandMode.CHARGING
                c.timer.isRunning && timerEnabled -> IslandMode.TIMER
                else -> IslandMode.IDLE
            }
            c.copy(notification = null, mode = m, expanded = false)
        }
    }

    fun updateCharging(ch: ChargingState) {
        if (!chargingEnabled) return
        _state.update { c ->
            val m = when {
                ch.isCharging && c.mode == IslandMode.IDLE -> IslandMode.CHARGING
                ch.isCharging && c.mode == IslandMode.CHARGING -> IslandMode.CHARGING
                !ch.isCharging && c.mode == IslandMode.CHARGING -> IslandMode.IDLE
                else -> c.mode
            }
            val exp = if (ch.isCharging && !c.charging.isCharging) true else c.expanded
            c.copy(charging = ch, mode = m, expanded = exp, glowColor = if (ch.isCharging) 0xFF4CD964.toInt() else c.glowColor)
        }
    }

    fun updateTimer(t: TimerState) {
        if (!timerEnabled) return
        _state.update { c ->
            val m = if (t.isRunning && (c.mode == IslandMode.IDLE || c.mode == IslandMode.TIMER)) IslandMode.TIMER
                    else if (!t.isRunning && c.mode == IslandMode.TIMER) IslandMode.IDLE
                    else c.mode
            c.copy(timer = t, mode = m, glowColor = if (t.isRunning) 0xFFFF9500.toInt() else c.glowColor)
        }
    }

    fun updateWeather(w: WeatherState) { _state.update { it.copy(weather = w) } }

    fun updateNetSpeed(n: NetSpeedState) {
        if (!netSpeedEnabled) return
        _state.update { c ->
            val m = if (n.isActive && c.mode == IslandMode.IDLE) IslandMode.NET_SPEED
                    else if (!n.isActive && c.mode == IslandMode.NET_SPEED) IslandMode.IDLE
                    else c.mode
            c.copy(netSpeed = n, mode = m)
        }
    }

    fun updateNavigation(nav: NavigationState) {
        _state.update { c ->
            val m = if (nav.isActive) IslandMode.NAVIGATION
                    else if (c.mode == IslandMode.NAVIGATION) IslandMode.IDLE else c.mode
            c.copy(navigation = nav, mode = m)
        }
    }

    fun updateIndicators(ind: SystemIndicators) {
        _state.update { c ->
            val m = if ((ind.cameraInUse || ind.micInUse) && c.mode == IslandMode.IDLE) IslandMode.CAMERA_MIC
                    else if (ind.isScreenRecording && c.mode == IslandMode.IDLE) IslandMode.SCREEN_RECORD
                    else c.mode
            c.copy(indicators = ind, mode = m,
                glowColor = when {
                    ind.cameraInUse -> 0xFF4CD964.toInt()
                    ind.micInUse -> 0xFFFF9500.toInt()
                    ind.isScreenRecording -> 0xFFFF3B30.toInt()
                    else -> c.glowColor
                })
        }
    }

    fun toggleExpanded() { _state.update { it.copy(expanded = !it.expanded) } }
    fun setMode(mode: IslandMode) { _state.update { it.copy(mode = mode) } }
}
