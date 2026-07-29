package com.example.userlistapp

import com.example.userlistapp.settings.InstallSession
import com.example.userlistapp.settings.InstallStatus
import com.example.userlistapp.settings.SettingsInstallCoordinator
import com.example.userlistapp.settings.SettingsInstallGateway
import com.example.userlistapp.settings.SettingsInstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInstallCoordinatorTest {
    @Test
    fun `installed module opens without requesting download`() {
        val gateway = FakeInstallGateway(installed = true)
        var launches = 0
        val coordinator = SettingsInstallCoordinator(gateway) { launches++ }

        coordinator.start()
        coordinator.open()

        assertEquals(1, launches)
        assertEquals(0, gateway.startCalls)
        assertEquals(SettingsInstallState.Installed, coordinator.state.value)
    }

    @Test
    fun `successful request exposes progress and opens installed feature`() {
        val gateway = FakeInstallGateway()
        var launches = 0
        val coordinator = SettingsInstallCoordinator(gateway) { launches++ }
        coordinator.start()

        coordinator.open()
        gateway.emit(session(InstallStatus.DOWNLOADING, downloaded = 25, total = 100))
        assertEquals(SettingsInstallState.Downloading(25, 100), coordinator.state.value)

        gateway.installed = true
        gateway.emit(session(InstallStatus.INSTALLED))

        assertEquals(SettingsInstallState.Installed, coordinator.state.value)
        assertEquals(1, launches)
    }

    @Test
    fun `duplicate opens produce one install request`() {
        val gateway = FakeInstallGateway(deferStartResult = true)
        val coordinator = SettingsInstallCoordinator(gateway) {}
        coordinator.start()

        coordinator.open()
        coordinator.open()
        gateway.completeStart()
        coordinator.open()

        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun `confirmation is requested once and rejection becomes cancellation`() {
        val gateway = FakeInstallGateway()
        val coordinator = SettingsInstallCoordinator(gateway) {}
        coordinator.start()
        coordinator.open()

        gateway.emit(session(InstallStatus.REQUIRES_USER_CONFIRMATION))
        gateway.emit(session(InstallStatus.REQUIRES_USER_CONFIRMATION))
        assertEquals(1, gateway.confirmationRequests)
        assertEquals(SettingsInstallState.AwaitingConfirmation, coordinator.state.value)

        coordinator.confirmationResult(accepted = false)
        assertEquals(SettingsInstallState.Canceled, coordinator.state.value)
    }

    @Test
    fun `failure and cancellation can retry`() {
        val gateway = FakeInstallGateway()
        val coordinator = SettingsInstallCoordinator(gateway) {}
        coordinator.start()

        coordinator.open()
        gateway.emit(session(InstallStatus.FAILED, error = -6))
        assertEquals(SettingsInstallState.Failed(-6), coordinator.state.value)
        coordinator.retry()

        gateway.emit(session(InstallStatus.CANCELED))
        assertEquals(SettingsInstallState.Canceled, coordinator.state.value)
        coordinator.retry()

        assertEquals(3, gateway.startCalls)
    }

    @Test
    fun `active settings session is recovered and unrelated sessions are ignored`() {
        val gateway = FakeInstallGateway(
            recovered = listOf(
                session(InstallStatus.DOWNLOADING, id = 99, modules = setOf("other")),
                session(
                    InstallStatus.DOWNLOADING,
                    id = 42,
                    downloaded = 60,
                    total = 120,
                ),
            ),
        )
        var launches = 0
        val coordinator = SettingsInstallCoordinator(gateway) { launches++ }

        coordinator.start()
        assertEquals(SettingsInstallState.Downloading(60, 120), coordinator.state.value)

        gateway.emit(session(InstallStatus.FAILED, id = 99, error = -1))
        assertEquals(SettingsInstallState.Downloading(60, 120), coordinator.state.value)
        gateway.emit(session(InstallStatus.INSTALLED, id = 42))

        assertEquals(1, launches)
    }

    @Test
    fun `active download can be canceled`() {
        val gateway = FakeInstallGateway()
        val coordinator = SettingsInstallCoordinator(gateway) {}
        coordinator.start()
        coordinator.open()
        gateway.emit(session(InstallStatus.DOWNLOADING))

        coordinator.cancel()

        assertEquals(SESSION_ID, gateway.canceledSession)
        assertEquals(SettingsInstallState.Canceling, coordinator.state.value)
        gateway.emit(session(InstallStatus.CANCELED))
        assertEquals(SettingsInstallState.Canceled, coordinator.state.value)
    }

    @Test
    fun `cancel before session id is returned cancels when id arrives`() {
        val gateway = FakeInstallGateway(deferStartResult = true)
        val coordinator = SettingsInstallCoordinator(gateway) {}
        coordinator.start()
        coordinator.open()

        coordinator.cancel()
        gateway.completeStart()

        assertEquals(SESSION_ID, gateway.canceledSession)
        assertEquals(SettingsInstallState.Canceling, coordinator.state.value)
    }

    @Test
    fun `listener is attached and detached with lifecycle`() {
        val gateway = FakeInstallGateway()
        val coordinator = SettingsInstallCoordinator(gateway) {}

        coordinator.start()
        coordinator.start()
        assertEquals(1, gateway.registerCalls)
        coordinator.stop()

        assertEquals(1, gateway.unregisterCalls)
        assertTrue(gateway.listener == null)
    }

    private fun session(
        status: InstallStatus,
        id: Int = SESSION_ID,
        modules: Set<String> = setOf(SettingsInstallCoordinator.MODULE_NAME),
        downloaded: Long = 0,
        total: Long = 0,
        error: Int? = null,
    ) = InstallSession(id, status, modules, downloaded, total, error)
}

private class FakeInstallGateway(
    var installed: Boolean = false,
    private val recovered: List<InstallSession> = emptyList(),
    private val deferStartResult: Boolean = false,
) : SettingsInstallGateway {
    var listener: ((InstallSession) -> Unit)? = null
    var registerCalls = 0
    var unregisterCalls = 0
    var startCalls = 0
    var confirmationRequests = 0
    var canceledSession: Int? = null
    private var pendingStartSuccess: ((Int) -> Unit)? = null

    override fun isInstalled() = installed

    override fun registerListener(listener: (InstallSession) -> Unit) {
        registerCalls++
        this.listener = listener
    }

    override fun unregisterListener(listener: (InstallSession) -> Unit) {
        unregisterCalls++
        if (this.listener === listener) this.listener = null
    }

    override fun startInstall(onSuccess: (Int) -> Unit, onFailure: (Int?) -> Unit) {
        startCalls++
        if (deferStartResult) pendingStartSuccess = onSuccess else onSuccess(SESSION_ID)
    }

    override fun activeSessions(
        onSuccess: (List<InstallSession>) -> Unit,
        onFailure: (Int?) -> Unit,
    ) = onSuccess(recovered)

    override fun requestConfirmation(sessionId: Int): Boolean {
        confirmationRequests++
        return true
    }

    override fun cancel(sessionId: Int, onFailure: (Int?) -> Unit) {
        canceledSession = sessionId
    }

    fun emit(session: InstallSession) {
        listener?.invoke(session)
    }

    fun completeStart() {
        pendingStartSuccess?.invoke(SESSION_ID)
        pendingStartSuccess = null
    }
}

private const val SESSION_ID = 7
