package com.example.userlistapp.data.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface RealtimeConnectionState {
    data object Disconnected : RealtimeConnectionState
    data object Connecting : RealtimeConnectionState
    data object Connected : RealtimeConnectionState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : RealtimeConnectionState
    data object Failed : RealtimeConnectionState
}

interface UserRealtimeClient {
    val connectionState: StateFlow<RealtimeConnectionState>
    val messages: Flow<String>

    fun connect()
    fun disconnect()
    fun send(message: String): Boolean
}

data class ReconnectPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 4_000,
) {
    init {
        require(maxAttempts >= 0)
        require(initialDelayMillis >= 0)
        require(maxDelayMillis >= initialDelayMillis)
    }

    fun delayMillis(attempt: Int): Long {
        require(attempt > 0)
        val multiplier = 1L shl (attempt - 1).coerceAtMost(MAX_SHIFT)
        return (initialDelayMillis * multiplier).coerceAtMost(maxDelayMillis)
    }

    private companion object {
        const val MAX_SHIFT = 30
    }
}
