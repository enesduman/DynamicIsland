package com.dynamicisland.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object IslandStateManager {
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()

    fun updateMusic(music: MusicState) {
        _state.update { c ->
            val m = if (music.isPlaying) IslandMode.MUSIC
                    else if (c.mode == IslandMode.MUSIC) IslandMode.IDLE else c.mode
            c.copy(music = music, mode = m)
        }
    }

    fun updateCall(call: CallState) {
        _state.update { c ->
            val m = if (call.isActive || call.isIncoming) IslandMode.CALL
                    else if (c.mode == IslandMode.CALL) {
                        if (c.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                    } else c.mode
            c.copy(call = call, mode = m)
        }
    }

    fun showNotification(n: NotificationInfo) {
        _state.update { c ->
            if (c.mode == IslandMode.CALL) c.copy(notification = n)
            else c.copy(notification = n, mode = IslandMode.NOTIFICATION, expanded = true)
        }
    }

    fun dismissNotification() {
        _state.update { c ->
            val m = when {
                c.call.isActive -> IslandMode.CALL
                c.music.isPlaying -> IslandMode.MUSIC
                c.charging.isCharging -> IslandMode.CHARGING
                else -> IslandMode.IDLE
            }
            c.copy(notification = null, mode = m, expanded = false)
        }
    }

    fun updateCharging(ch: ChargingState) {
        _state.update { c ->
            val m = if (ch.isCharging && c.mode == IslandMode.IDLE) IslandMode.CHARGING
                    else if (!ch.isCharging && c.mode == IslandMode.CHARGING) IslandMode.IDLE
                    else c.mode
            c.copy(charging = ch, mode = m)
        }
    }

    fun toggleExpanded() { _state.update { it.copy(expanded = !it.expanded) } }
}
