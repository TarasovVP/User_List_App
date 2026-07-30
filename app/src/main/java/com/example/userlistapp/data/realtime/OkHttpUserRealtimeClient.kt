package com.example.userlistapp.data.realtime

import com.example.userlistapp.BuildConfig
import com.example.userlistapp.core.common.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpUserRealtimeClient @Inject constructor(
    client: OkHttpClient,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : UserRealtimeClient {
    private val delegate = WebSocketSession(
        webSocketFactory = client,
        url = BuildConfig.WEBSOCKET_URL,
        dispatcher = dispatcher,
    )

    override val connectionState = delegate.connectionState
    override val messages = delegate.messages

    override fun connect() = delegate.connect()
    override fun disconnect() = delegate.disconnect()
    override fun send(message: String): Boolean = delegate.send(message)
}

class WebSocketSession(
    private val webSocketFactory: WebSocket.Factory,
    private val url: String,
    dispatcher: CoroutineDispatcher,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) : UserRealtimeClient {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _connectionState =
        MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = MESSAGE_BUFFER_CAPACITY)

    override val connectionState = _connectionState.asStateFlow()
    override val messages = _messages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldBeConnected = false
    private var reconnectAttempt = 0
    private var generation = 0L

    override fun connect() {
        synchronized(lock) {
            if (shouldBeConnected) return
            shouldBeConnected = true
            reconnectAttempt = 0
            openSocketLocked()
        }
    }

    override fun disconnect() {
        val socket = synchronized(lock) {
            shouldBeConnected = false
            generation++
            reconnectAttempt = 0
            reconnectJob?.cancel()
            reconnectJob = null
            webSocket.also { webSocket = null }
        }
        socket?.close(NORMAL_CLOSURE_CODE, ROUTE_INACTIVE_REASON)
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    override fun send(message: String): Boolean = synchronized(lock) {
        if (_connectionState.value != RealtimeConnectionState.Connected) return false
        webSocket?.send(message) == true
    }

    private fun openSocketLocked() {
        if (!shouldBeConnected || webSocket != null) return
        val socketGeneration = ++generation
        if (reconnectAttempt == 0) {
            _connectionState.value = RealtimeConnectionState.Connecting
        }
        val request = Request.Builder().url(url).build()
        webSocket = webSocketFactory.newWebSocket(request, listener(socketGeneration))
    }

    private fun listener(socketGeneration: Long) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (socketGeneration != generation || !shouldBeConnected) {
                    webSocket.close(NORMAL_CLOSURE_CODE, STALE_CONNECTION_REASON)
                    return
                }
                reconnectJob = null
                reconnectAttempt = 0
                _connectionState.value = RealtimeConnectionState.Connected
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isCurrent(socketGeneration)) _messages.tryEmit(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleUnexpectedClosure(socketGeneration)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleUnexpectedClosure(socketGeneration)
        }
    }

    private fun isCurrent(socketGeneration: Long): Boolean = synchronized(lock) {
        socketGeneration == generation && shouldBeConnected
    }

    private fun handleUnexpectedClosure(socketGeneration: Long) {
        synchronized(lock) {
            if (socketGeneration != generation) return
            generation++
            webSocket = null
            if (!shouldBeConnected) {
                _connectionState.value = RealtimeConnectionState.Disconnected
                return
            }
            scheduleReconnectLocked()
        }
    }

    private fun scheduleReconnectLocked() {
        if (reconnectAttempt >= reconnectPolicy.maxAttempts) {
            reconnectJob = null
            _connectionState.value = RealtimeConnectionState.Failed
            return
        }
        reconnectAttempt++
        val attempt = reconnectAttempt
        _connectionState.value = RealtimeConnectionState.Reconnecting(
            attempt = attempt,
            maxAttempts = reconnectPolicy.maxAttempts,
        )
        reconnectJob = scope.launch {
            delay(reconnectPolicy.delayMillis(attempt))
            synchronized(lock) {
                reconnectJob = null
                if (shouldBeConnected && webSocket == null) openSocketLocked()
            }
        }
    }

    private companion object {
        const val MESSAGE_BUFFER_CAPACITY = 16
        const val NORMAL_CLOSURE_CODE = 1_000
        const val ROUTE_INACTIVE_REASON = "User List route inactive"
        const val STALE_CONNECTION_REASON = "Stale connection"
    }
}
