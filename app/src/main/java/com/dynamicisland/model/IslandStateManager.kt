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

    // Hangi modlarin ayni anda gorunebilecegini belirle
    private fun calcSecondary(primary: IslandMode, s: IslandState): IslandMode {
        if (primary == IslandMode.IDLE) return IslandMode.IDLE
        // Muzik calarken timer varsa veya timer varken muzik caliyorsa
        if (primary == IslandMode.MUSIC && s.timer.isRunning && timerEnabled) return IslandMode.TIMER
        if (primary == IslandMode.TIMER && s.music.isPlaying && musicEnabled) return IslandMode.MUSIC
        // Muzik calarken navigasyon varsa
        if (primary == IslandMode.MUSIC && s.navigation.isActive) return IslandMode.NAVIGATION
        if (primary == IslandMode.NAVIGATION && s.music.isPlaying && musicEnabled) return IslandMode.MUSIC
        // Arama sirasinda muzik
        if (primary == IslandMode.CALL && s.music.isPlaying && musicEnabled) return IslandMode.MUSIC
        // Timer varken navigasyon
        if (primary == IslandMode.TIMER && s.navigation.isActive) return IslandMode.NAVIGATION
        if (primary == IslandMode.NAVIGATION && s.timer.isRunning && timerEnabled) return IslandMode.TIMER
        // Sarj sirasinda muzik
        if (primary == IslandMode.CHARGING && s.music.isPlaying && musicEnabled) return IslandMode.MUSIC
        if (primary == IslandMode.MUSIC && s.charging.isCharging && chargingEnabled) return IslandMode.CHARGING
        return IslandMode.IDLE
    }

    fun updateMusic(music: MusicState) {
        if (!musicEnabled) return
        _state.update { c ->
            val m = when {
                c.mode == IslandMode.CALL -> IslandMode.CALL
                c.mode == IslandMode.NOTIFICATION -> IslandMode.NOTIFICATION
                c.mode == IslandMode.NAVIGATION -> IslandMode.NAVIGATION
                c.mode == IslandMode.TIMER && c.timer.isRunning -> IslandMode.TIMER
                music.isPlaying -> IslandMode.MUSIC
                c.mode == IslandMode.MUSIC -> IslandMode.IDLE
                else -> c.mode
            }
            val ns = c.copy(music = music, mode = m, glowColor = if (music.isPlaying && m == IslandMode.MUSIC) 0xFFFF6B35.toInt() else c.glowColor)
            ns.copy(secondaryMode = calcSecondary(m, ns))
        }
    }

    fun updateCall(call: CallState) {
        if (!callEnabled) return
        _state.update { c ->
            val m = if (call.isActive || call.isIncoming) IslandMode.CALL
                    else if (c.mode == IslandMode.CALL) {
                        if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                    } else c.mode
            val ns = c.copy(call = call, mode = m, glowColor = if (call.isActive || call.isIncoming) 0xFF4CD964.toInt() else c.glowColor)
            ns.copy(secondaryMode = calcSecondary(m, ns))
        }
    }

    fun showNotification(n: NotificationInfo) {
        if (!notifEnabled) return
        _state.update { c ->
            if (c.mode == IslandMode.CALL) c.copy(notification = n)
            else {
                val ns = c.copy(notification = n, mode = IslandMode.NOTIFICATION, expanded = true, glowColor = n.color)
                ns.copy(secondaryMode = calcSecondary(IslandMode.NOTIFICATION, ns))
            }
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
            val ns = c.copy(notification = null, mode = m, expanded = false)
            ns.copy(secondaryMode = calcSecondary(m, ns))
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
            val ns = c.copy(charging = ch, mode = m, expanded = exp, glowColor = if (ch.isCharging && m == IslandMode.CHARGING) 0xFF4CD964.toInt() else c.glowColor)
            ns.copy(secondaryMode = calcSecondary(m, ns))
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
            val ns = c.copy(timer = t, mode = m, glowColor = if (t.isRunning && m == IslandMode.TIMER) 0xFFFF9500.toInt() else c.glowColor)
            ns.copy(secondaryMode = calcSecondary(m, ns))
        }
    }

    fun updateWeather(w: WeatherState) { _state.update { it.copy(weather = w) } }

    fun updateNetSpeed(n: NetSpeedState) {
        if (!netSpeedEnabled) return
        _state.update { c ->
            val m = if (n.isActive && c.mode == IslandMode.IDLE) IslandMode.NET_SPEED
                    else if (!n.isActive && c.mode == IslandMode.NET_SPEED) IslandMode.IDLE else c.mode
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
            val ns = c.copy(navigation = nav, mode = m, expanded = exp, glowColor = if (nav.isActive) 0xFF007AFF.toInt() else c.glowColor)
            ns.copy(secondaryMode = calcSecondary(m, ns))
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
