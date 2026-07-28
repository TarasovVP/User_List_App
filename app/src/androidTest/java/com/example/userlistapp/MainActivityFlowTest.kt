package com.example.userlistapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.core.common.TimeProvider
import com.example.userlistapp.core.quality.AppQualityMonitor
import com.example.userlistapp.core.quality.NoOpAppQualityMonitor
import com.example.userlistapp.core.ui.UiTestTags
import com.example.userlistapp.data.realtime.RealtimeConnectionState
import com.example.userlistapp.data.realtime.UserRealtimeClient
import com.example.userlistapp.di.AppModule
import com.example.userlistapp.domain.model.Account
import com.example.userlistapp.domain.model.AppSettings
import com.example.userlistapp.domain.model.RefreshSource
import com.example.userlistapp.domain.model.SessionState
import com.example.userlistapp.domain.model.SyncState
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.feature.account.AccountImplementationFlag
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(AppModule::class)
class MainActivityFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val users = FakeUserRepository()
    private val auth = FakeAuthSessionRepository()
    private val realtime = FakeUserRealtimeClient()

    @BindValue
    @JvmField
    val timeProvider: TimeProvider = TimeProvider { 123456789L }

    @BindValue
    @JvmField
    val userRepository: UserRepository = users

    @BindValue
    @JvmField
    val authSessionRepository: AuthSessionRepository = auth

    @BindValue
    @JvmField
    val appQualityMonitor: AppQualityMonitor = NoOpAppQualityMonitor

    @BindValue
    @JvmField
    val settingsRepository: SettingsRepository = FakeSettingsRepository()

    @BindValue
    @JvmField
    val syncScheduler: SyncScheduler = FakeSyncScheduler()

    @BindValue
    @JvmField
    val realtimeClient: UserRealtimeClient = realtime

    @BindValue
    @DefaultDispatcher
    @JvmField
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    @BindValue
    @JvmField
    val accountImplementationFlag: AccountImplementationFlag =
        object : AccountImplementationFlag {
            override val useModularAccount = true
        }

    @Before
    fun inject() {
        hilt.inject()
    }

    @Test
    fun guestSignsInSearchesUserAndSavesNote() {
        val context = compose.activity
        val note = "Met at the computing conference"

        compose.waitForText(context.getString(R.string.guest_title))
        compose.onNodeWithTag(UiTestTags.SIGN_IN_OPEN).performClick()
        compose.onNodeWithTag(UiTestTags.LOGIN_USERNAME).performTextInput("emilys")
        compose.onNodeWithTag(UiTestTags.LOGIN_PASSWORD).performTextInput("emilyspass")
        compose.onNodeWithTag(UiTestTags.LOGIN_SUBMIT).performClick()

        compose.waitForText(context.getString(R.string.users_title))
        compose.runOnIdle { assertTrue(realtime.connectCalls > 0) }
        compose.onNodeWithContentDescription(context.getString(R.string.search_users))
            .performClick()
        compose.onNodeWithTag(UiTestTags.SEARCH).performTextInput("Grace")
        compose.waitForText("Grace Hopper")
        compose.onNodeWithTag(UiTestTags.user(2)).performClick()

        compose.waitForText("Grace Hopper")
        compose.runOnIdle { assertTrue(realtime.disconnectCalls > 0) }
        compose.onNodeWithTag(UiTestTags.NOTE_FIELD).performScrollTo().performTextInput(note)
        compose.onNodeWithTag(UiTestTags.SAVE_NOTE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(note, users.users.value.single { it.id == 2 }.note)
        }

        compose.onNodeWithTag(UiTestTags.NOTE_FIELD)
            .assertIsDisplayed()
            .assertTextContains(note)
    }

    @Test
    fun cancellingPhotoPickerKeepsAccountAvatarUnchanged() {
        val context = compose.activity
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val appPackage = context.packageName

        compose.waitForText(context.getString(R.string.guest_title))
        compose.onNodeWithTag(UiTestTags.SIGN_IN_OPEN).performClick()
        compose.onNodeWithTag(UiTestTags.LOGIN_USERNAME).performTextInput("emilys")
        compose.onNodeWithTag(UiTestTags.LOGIN_PASSWORD).performTextInput("emilyspass")
        compose.onNodeWithTag(UiTestTags.LOGIN_SUBMIT).performClick()
        compose.waitForText(context.getString(R.string.users_title))
        compose.onNodeWithText(context.getString(R.string.account_title)).performClick()
        compose.waitForText("Emily Johnson")

        compose.onNodeWithText(context.getString(R.string.choose_local_photo)).performClick()
        assertTrue(device.wait(Until.gone(By.pkg(appPackage)), SYSTEM_UI_TIMEOUT_MILLIS))

        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.pkg(appPackage)), SYSTEM_UI_TIMEOUT_MILLIS))
        compose.waitForText("Emily Johnson")
        compose.runOnIdle {
            assertEquals(null, auth.localAvatarUri.value)
            assertEquals(0, auth.importAvatarCalls)
        }
        compose.onNodeWithText(context.getString(R.string.choose_local_photo)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.remove_local_photo))
            .assertCountEquals(0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForText(text: String) {
        waitUntil(5_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val SYSTEM_UI_TIMEOUT_MILLIS = 5_000L
    }
}

private class FakeUserRealtimeClient : UserRealtimeClient {
    override val connectionState =
        MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
    override val messages: Flow<String> = emptyFlow()
    var connectCalls = 0
    var disconnectCalls = 0

    override fun connect() {
        connectCalls++
    }

    override fun disconnect() {
        disconnectCalls++
    }

    override fun send(message: String): Boolean = false
}

private class FakeAuthSessionRepository : AuthSessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val localAvatarUri = MutableStateFlow<String?>(null)
    var importAvatarCalls = 0
        private set
    private val account = Account(
        id = 1,
        username = "emilys",
        firstName = "Emily",
        lastName = "Johnson",
        email = "emily@example.com",
        remoteImageUrl = String.EMPTY,
    )

    override suspend fun signIn(username: String, password: String): AppResult<Account> {
        sessionState.value = SessionState.SignedIn(account.id)
        return AppResult.Success(account)
    }

    override suspend fun loadAccount(userId: Int) = AppResult.Success(account)

    override suspend fun signOut(): AppResult<Unit> {
        sessionState.value = SessionState.SignedOut
        return AppResult.Success(Unit)
    }

    override suspend fun importLocalAvatar(sourceUri: String): AppResult<Unit> {
        importAvatarCalls++
        localAvatarUri.value = sourceUri
        return AppResult.Success(Unit)
    }

    override suspend fun removeLocalAvatar() = AppResult.Success(Unit)
}

private class FakeUserRepository : UserRepository {
    val users = MutableStateFlow(
        listOf(
            uiTestUser(1, "Ada", "Lovelace"),
            uiTestUser(2, "Grace", "Hopper"),
        ),
    )

    override fun observeUsers(): Flow<List<User>> = users
    override fun observeUser(userId: Int): Flow<User?> =
        users.map { list -> list.firstOrNull { it.id == userId } }

    override suspend fun refreshUsers(source: RefreshSource) = AppResult.Success(Unit)

    override suspend fun setFavorite(userId: Int, favorite: Boolean): AppResult<Unit> {
        users.value =
            users.value.map { if (it.id == userId) it.copy(isFavorite = favorite) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun saveNote(userId: Int, note: String): AppResult<Unit> {
        users.value = users.value.map {
            if (it.id == userId) it.copy(note = note, noteModifiedAt = 1L) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteNote(userId: Int): AppResult<Unit> {
        users.value = users.value.map {
            if (it.id == userId) it.copy(note = null, noteModifiedAt = null) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun markUserAsViewed(userId: Int, viewedAt: Long): AppResult<Unit> =
        AppResult.Success(Unit)
}

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings(themeMode = ThemeMode.LIGHT))
    override val settings: Flow<AppSettings> = state

    override suspend fun setTheme(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setBackgroundSync(enabled: Boolean) {
        state.value = state.value.copy(backgroundSyncEnabled = enabled)
    }

    override suspend fun setLastSuccessfulSync(timestamp: Long) {
        state.value = state.value.copy(lastSuccessfulSync = timestamp)
    }
}

private class FakeSyncScheduler : SyncScheduler {
    private val state = MutableStateFlow(SyncState.IDLE)
    override fun observeState(): Flow<SyncState> = state
    override fun setEnabled(enabled: Boolean) = Unit
}

private fun uiTestUser(id: Int, firstName: String, lastName: String) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = 30,
    email = "$firstName@example.com",
    phone = "123",
    username = firstName.lowercase(),
    imageUrl = String.EMPTY,
    role = "user",
    companyName = "Computing",
    department = "Research",
    jobTitle = "Engineer",
    street = "1 Main Street",
    city = "London",
    state = "England",
    country = "UK",
)
