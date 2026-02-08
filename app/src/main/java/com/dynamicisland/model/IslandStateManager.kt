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
            // Muzik sadece baska onemli mod yoksa aktif olsun
            val m = when {
                c.mode == IslandMode.CALL -> IslandMode.CALL
                c.mode == IslandMode.NOTIFICATION -> IslandMode.NOTIFICATION
                c.mode == IslandMode.NAVIGATION -> IslandMode.NAVIGATION
                c.mode == IslandMode.TIMER && c.timer.isRunning -> IslandMode.TIMER
                music.isPlaying -> IslandMode.MUSIC
                c.mode == IslandMode.MUSIC -> IslandMode.IDLE
                else -> c.mode
            }
            c.copy(music = music, mode = m, glowColor = if (music.isPlaying && m == IslandMode.MUSIC) 0xFFFF6B35.toInt() else c.glowColor)
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
            // Bildirim CALL haricinde her seyin ustune gecsin
            if (c.mode == IslandMode.CALL) c.copy(notification = n)
            else c.copy(notification = n, mode = IslandMode.NOTIFICATION, expanded = true, glowColor = n.color)
        }
    }

    fun dismissNotification() {
        _state.update { c ->
            val m = when {
                c.call.isActive -> IslandMode.CALL
                c.navigation.isActive -> IslandMode.NAVIGATION
                c.timer.isRunning && timerEnabled -> IslandMode.TIMER
                c.music.isPlaying -> IslandMode.MUSIC
                c.charging.isCharging && chargingEnabled -> IslandMode.CHARGING
                else -> IslandMode.IDLE
            }
            c.copy(notification = null, mode = m, expanded = false)
        }
    }

    fun updateCharging(ch: ChargingState) {
        if (!chargingEnabled) return
        _state.update { c ->
            val m = when {
                c.mode == IslandMode.CALL || c.mode == IslandMode.MUSIC || c.mode == IslandMode.NOTIFICATION -> c.mode
                ch.isCharging && !c.charging.isCharging -> IslandMode.CHARGING
                ch.isCharging && c.mode == IslandMode.CHARGING -> IslandMode.CHARGING
                ch.isCharging && c.mode == IslandMode.IDLE -> IslandMode.CHARGING
                !ch.isCharging && c.mode == IslandMode.CHARGING -> if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                else -> c.mode
            }
            val exp = if (ch.isCharging && !c.charging.isCharging && m == IslandMode.CHARGING) true else c.expanded
            c.copy(charging = ch, mode = m, expanded = exp, glowColor = if (ch.isCharging && m == IslandMode.CHARGING) 0xFF4CD964.toInt() else c.glowColor)
        }
    }

    fun updateTimer(t: TimerState) {
        if (!timerEnabled) return
        _state.update { c ->
            val m = when {
                c.mode == IslandMode.CALL || c.mode == IslandMode.NOTIFICATION -> c.mode
                t.isRunning && (c.mode == IslandMode.IDLE || c.mode == IslandMode.TIMER || c.mode == IslandMode.CHARGING) -> IslandMode.TIMER
                !t.isRunning && c.mode == IslandMode.TIMER -> if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                else -> c.mode
            }
            c.copy(timer = t, mode = m, glowColor = if (t.isRunning && m == IslandMode.TIMER) 0xFFFF9500.toInt() else c.glowColor)
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
            val m = when {
                c.mode == IslandMode.CALL -> IslandMode.CALL
                nav.isActive -> IslandMode.NAVIGATION
                c.mode == IslandMode.NAVIGATION -> if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                else -> c.mode
            }
            val exp = if (nav.isActive && !c.navigation.isActive) true else c.expanded
            c.copy(navigation = nav, mode = m, expanded = exp, glowColor = if (nav.isActive) 0xFF007AFF.toInt() else c.glowColor)
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
