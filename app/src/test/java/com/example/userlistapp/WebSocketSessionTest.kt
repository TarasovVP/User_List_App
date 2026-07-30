package com.example.userlistapp

import app.cash.turbine.test
import com.example.userlistapp.data.realtime.RealtimeConnectionState
import com.example.userlistapp.data.realtime.ReconnectPolicy
import com.example.userlistapp.data.realtime.WebSocketSession
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketSessionTest {
    @Test
    fun `connection state message exchange and duplicate prevention are observable`() = runTest {
        val factory = FakeWebSocketFactory()
        val session = WebSocketSession(factory, TEST_URL, StandardTestDispatcher(testScheduler))

        session.connect()
        session.connect()

        assertEquals(1, factory.connections.size)
        assertEquals(RealtimeConnectionState.Connecting, session.connectionState.value)
        factory.openLatest()
        assertEquals(RealtimeConnectionState.Connected, session.connectionState.value)

        session.messages.test {
            factory.messageLatest(ECHO)
            assertEquals(ECHO, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(session.send(OUTBOUND))
        assertEquals(listOf(OUTBOUND), factory.latestSocket.sentMessages)
    }

    @Test
    fun `disconnect closes the socket and disables sending`() = runTest {
        val factory = FakeWebSocketFactory()
        val session = WebSocketSession(factory, TEST_URL, StandardTestDispatcher(testScheduler))
        session.connect()
        factory.openLatest()

        session.disconnect()

        assertEquals(RealtimeConnectionState.Disconnected, session.connectionState.value)
        assertEquals(1_000, factory.latestSocket.closeCode)
        assertFalse(session.send(OUTBOUND))
    }

    @Test
    fun `unexpected failures reconnect with bounded exponential delays`() = runTest {
        val factory = FakeWebSocketFactory()
        val session = WebSocketSession(
            factory,
            TEST_URL,
            StandardTestDispatcher(testScheduler),
            ReconnectPolicy(maxAttempts = 3, initialDelayMillis = 100, maxDelayMillis = 400),
        )
        session.connect()

        factory.failLatest()
        assertEquals(RealtimeConnectionState.Reconnecting(1, 3), session.connectionState.value)
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, factory.connections.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, factory.connections.size)

        factory.failLatest()
        assertEquals(RealtimeConnectionState.Reconnecting(2, 3), session.connectionState.value)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(3, factory.connections.size)

        factory.failLatest()
        assertEquals(RealtimeConnectionState.Reconnecting(3, 3), session.connectionState.value)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(4, factory.connections.size)

        factory.failLatest()
        assertEquals(RealtimeConnectionState.Failed, session.connectionState.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(4, factory.connections.size)
    }

    @Test
    fun `server closure is acknowledged and starts reconnect policy`() = runTest {
        val factory = FakeWebSocketFactory()
        val session = WebSocketSession(
            factory,
            TEST_URL,
            StandardTestDispatcher(testScheduler),
            ReconnectPolicy(maxAttempts = 1, initialDelayMillis = 100, maxDelayMillis = 100),
        )
        session.connect()
        factory.openLatest()

        factory.closeLatestFromServer()

        assertEquals(1_001, factory.latestSocket.closeCode)
        assertEquals(RealtimeConnectionState.Reconnecting(1, 1), session.connectionState.value)
    }

    @Test
    fun `disconnect cancels a pending reconnect`() = runTest {
        val factory = FakeWebSocketFactory()
        val session = WebSocketSession(
            factory,
            TEST_URL,
            StandardTestDispatcher(testScheduler),
            ReconnectPolicy(maxAttempts = 3, initialDelayMillis = 100, maxDelayMillis = 400),
        )
        session.connect()
        factory.failLatest()

        session.disconnect()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(RealtimeConnectionState.Disconnected, session.connectionState.value)
        assertEquals(1, factory.connections.size)
    }
}

private class FakeWebSocketFactory : WebSocket.Factory {
    data class Connection(
        val socket: FakeWebSocket,
        val listener: WebSocketListener,
    )

    val connections = mutableListOf<Connection>()
    val latestSocket: FakeWebSocket get() = connections.last().socket

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
        FakeWebSocket(request).also { connections += Connection(it, listener) }

    fun openLatest() {
        connections.last().listener.onOpen(latestSocket, mockk<Response>(relaxed = true))
    }

    fun messageLatest(message: String) {
        connections.last().listener.onMessage(latestSocket, message)
    }

    fun failLatest() {
        connections.last().listener.onFailure(latestSocket, IllegalStateException("boom"), null)
    }

    fun closeLatestFromServer() {
        val connection = connections.last()
        connection.listener.onClosing(connection.socket, 1_001, "going away")
        connection.listener.onClosed(connection.socket, 1_001, "going away")
    }
}

private class FakeWebSocket(
    private val requestValue: Request,
) : WebSocket {
    val sentMessages = mutableListOf<String>()
    var closeCode: Int? = null

    override fun request(): Request = requestValue
    override fun queueSize(): Long = 0
    override fun send(text: String): Boolean = sentMessages.add(text)
    override fun send(bytes: ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean {
        closeCode = code
        return true
    }

    override fun cancel() = Unit
}

private const val TEST_URL = "wss://example.test/raw"
private const val OUTBOUND = """{"type":"echo"}"""
private const val ECHO = """{"type":"echo","received":true}"""
