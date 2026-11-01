package moe.rukamori.archivetune.home.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.pages.LibraryPage
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class HomeAccountCoordinatorTest {

    private val context: Context = mockk(relaxed = true)
    private val syncUtils: SyncUtils = mockk(relaxed = true)
    private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase = mockk(relaxed = true)
    private val dataStore: DataStore<Preferences> = mockk(relaxed = true)
    private val preferencesFlow = MutableSharedFlow<Preferences>(replay = 1)

    private lateinit var coordinator: HomeAccountCoordinator

    @Before
    fun setUp() {
        mockkObject(YouTube)
        mockkStatic("moe.rukamori.archivetune.utils.DataStoreKt")

        every { context.dataStore } returns dataStore
        every { dataStore.data } returns preferencesFlow
        every { any<DataStore<Preferences>>().get(YtmSyncKey, any()) } returns true

        coEvery { YouTube.accountInfo() } returns Result.success(
            AccountInfo(
                name = "Test User",
                email = "test@example.com",
                channelHandle = "@test",
                thumbnailUrl = "https://example.com/avatar.jpg",
            ),
        )
        coEvery { YouTube.accountChannels() } returns Result.success(emptyList())
        coEvery { YouTube.library(any(), any()) } returns Result.success(LibraryPage(emptyList(), null))

        coordinator = HomeAccountCoordinator(context, syncUtils, switchSavedYouTubeAccount)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockPreferences(cookie: String?): Preferences {
        val prefs = mockk<Preferences>()
        every { prefs[InnerTubeCookieKey] } returns cookie
        return prefs
    }

    @Test
    fun initialEmission_whenLoggedIn_refreshesIdentityWithoutStartupSync() = runBlocking {
        preferencesFlow.emit(mockPreferences("SAPISID=valid_cookie_123"))

        val authSuccess = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope,
                onAuthSuccess = { authSuccess.complete(Unit) },
            )

            withTimeout(5_000) {
                authSuccess.await()
            }

            assertEquals("Test User", coordinator.accountName.value)
            assertEquals("https://example.com/avatar.jpg", coordinator.accountImageUrl.value)

            coVerify(exactly = 0) { syncUtils.performFullSync(any()) }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun validLogin_thenLogout_clearsPriorProfile() = runBlocking {
        preferencesFlow.emit(mockPreferences("SAPISID=valid_cookie_123"))

        var authSuccess: CompletableDeferred<Unit>? = CompletableDeferred()
        val authCleared = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope,
                onAuthSuccess = { authSuccess?.complete(Unit) },
                onAuthCleared = { authCleared.complete(Unit) },
            )

            withTimeout(5_000) {
                authSuccess?.await()
            }
            assertEquals("Test User", coordinator.accountName.value)
            assertEquals("https://example.com/avatar.jpg", coordinator.accountImageUrl.value)

            authSuccess = null
            preferencesFlow.emit(mockPreferences(null))

            withTimeout(5_000) {
                authCleared.await()
            }

            assertEquals("", coordinator.accountName.value)
            assertNull(coordinator.accountImageUrl.value)
            assertNull(coordinator.accountPlaylists.value)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun loginTransition_triggersExactlyOneSyncAndRefreshesIdentity() = runBlocking {
        val initialCleared = CompletableDeferred<Unit>()
        val loginSuccess = CompletableDeferred<Unit>()
        val syncInvoked = CompletableDeferred<Unit>()

        coEvery { syncUtils.performFullSync() } coAnswers {
            syncInvoked.complete(Unit)
        }

        preferencesFlow.emit(mockPreferences(null))

        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope,
                onAuthCleared = { initialCleared.complete(Unit) },
                onAuthSuccess = { loginSuccess.complete(Unit) },
            )

            withTimeout(5_000) {
                initialCleared.await()
            }

            preferencesFlow.emit(mockPreferences("SAPISID=login_cookie_456"))

            withTimeout(5_000) {
                syncInvoked.await()
            }
            withTimeout(5_000) {
                loginSuccess.await()
            }

            assertEquals("Test User", coordinator.accountName.value)
            assertEquals("https://example.com/avatar.jpg", coordinator.accountImageUrl.value)

            coVerify(exactly = 1) { syncUtils.performFullSync() }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun multipleSubscribers_shareSingleObserverJob_withoutDuplicatingRefreshesOrSync() = runBlocking {
        val initialCleared = CompletableDeferred<Unit>()
        val loginSuccess1 = CompletableDeferred<Unit>()
        val loginSuccess2 = CompletableDeferred<Unit>()
        val syncInvoked = CompletableDeferred<Unit>()

        coEvery { syncUtils.performFullSync() } coAnswers {
            syncInvoked.complete(Unit)
        }

        preferencesFlow.emit(mockPreferences(null))

        val scope1 = CoroutineScope(Dispatchers.Default + Job())
        val scope2 = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope1,
                onAuthCleared = { initialCleared.complete(Unit) },
                onAuthSuccess = { loginSuccess1.complete(Unit) },
            )
            coordinator.observeCookies(
                scope = scope2,
                onAuthSuccess = { loginSuccess2.complete(Unit) },
            )

            withTimeout(5_000) {
                initialCleared.await()
            }

            preferencesFlow.emit(mockPreferences("SAPISID=shared_login_123"))

            withTimeout(5_000) {
                syncInvoked.await()
            }
            withTimeout(5_000) {
                loginSuccess1.await()
            }
            withTimeout(5_000) {
                loginSuccess2.await()
            }

            assertEquals("Test User", coordinator.accountName.value)
            assertEquals("https://example.com/avatar.jpg", coordinator.accountImageUrl.value)

            coVerify(exactly = 1) { YouTube.accountInfo() }
            coVerify(exactly = 1) { syncUtils.performFullSync() }
        } finally {
            scope1.coroutineContext[Job]?.cancel()
            scope2.coroutineContext[Job]?.cancel()
            scope1.coroutineContext[Job]?.join()
            scope2.coroutineContext[Job]?.join()
        }
    }

    @Test
    fun subscriberCancellation_transfersObserverToRemainingSubscriber() = runBlocking {
        val initialLoginSuccess = CompletableDeferred<Unit>()
        val handoffLoginSuccess = CompletableDeferred<Unit>()

        preferencesFlow.emit(mockPreferences("SAPISID=valid_cookie_123"))

        val scope1 = CoroutineScope(Dispatchers.Default + Job())
        val scope2 = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope1,
                onAuthSuccess = { initialLoginSuccess.complete(Unit) },
            )
            coordinator.observeCookies(
                scope = scope2,
                onAuthSuccess = {
                    if (coordinator.accountName.value == "Updated User") {
                        handoffLoginSuccess.complete(Unit)
                    }
                },
            )

            withTimeout(5_000) {
                initialLoginSuccess.await()
            }

            scope1.coroutineContext[Job]?.cancelAndJoin()

            coEvery { YouTube.accountInfo() } returns Result.success(
                AccountInfo(
                    name = "Updated User",
                    email = "updated@example.com",
                    channelHandle = "@updated",
                    thumbnailUrl = "https://example.com/new_avatar.jpg",
                ),
            )

            preferencesFlow.emit(mockPreferences("SAPISID=changed_cookie_789"))

            withTimeout(5_000) {
                handoffLoginSuccess.await()
            }

            assertEquals("Updated User", coordinator.accountName.value)
            assertEquals("https://example.com/new_avatar.jpg", coordinator.accountImageUrl.value)
        } finally {
            scope1.coroutineContext[Job]?.cancelAndJoin()
            scope2.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun allSubscribersCancelled_andResubscribe_restartsObserverCorrectly() = runBlocking {
        val loginSuccess = CompletableDeferred<Unit>()
        val logoutCleared = CompletableDeferred<Unit>()

        preferencesFlow.emit(mockPreferences("SAPISID=valid_cookie_123"))

        val scope1 = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope1,
                onAuthSuccess = { loginSuccess.complete(Unit) },
            )

            withTimeout(5_000) {
                loginSuccess.await()
            }
            assertEquals("Test User", coordinator.accountName.value)
        } finally {
            scope1.coroutineContext[Job]?.cancelAndJoin()
        }

        preferencesFlow.emit(mockPreferences(null))

        val scope2 = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeCookies(
                scope = scope2,
                onAuthCleared = { logoutCleared.complete(Unit) },
            )

            withTimeout(5_000) {
                logoutCleared.await()
            }

            assertEquals("", coordinator.accountName.value)
            assertNull(coordinator.accountImageUrl.value)
        } finally {
            scope2.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun cancelledScope_doesNotReceiveCallbacks() = runBlocking {
        val initialCleared = CompletableDeferred<Unit>()
        preferencesFlow.emit(mockPreferences(null))

        val scope1 = CoroutineScope(Dispatchers.Default + Job())
        val cancelledCallbackInvoked = AtomicBoolean(false)

        try {
            coordinator.observeCookies(
                scope = scope1,
                onAuthCleared = { initialCleared.complete(Unit) },
                onAuthSuccess = { cancelledCallbackInvoked.set(true) },
            )

            withTimeout(5_000) {
                initialCleared.await()
            }
        } finally {
            scope1.coroutineContext[Job]?.cancelAndJoin()
        }

        val scope2 = CoroutineScope(Dispatchers.Default + Job())
        val activeCallbackInvoked = CompletableDeferred<Unit>()

        try {
            coordinator.observeCookies(
                scope = scope2,
                onAuthSuccess = { activeCallbackInvoked.complete(Unit) },
            )

            preferencesFlow.emit(mockPreferences("SAPISID=auth_success_cookie"))

            withTimeout(5_000) {
                activeCallbackInvoked.await()
            }

            assertFalse(cancelledCallbackInvoked.get())
        } finally {
            scope2.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun observeAccentColor_lifecycle_nullInitial_and_cleanupOnScopeCancel() = runBlocking {
        assertNull(coordinator.accountAccentColor.value)

        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            coordinator.observeAccentColor(scope)
            assertNull(coordinator.accountAccentColor.value)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }

        assertNull(coordinator.accountAccentColor.value)
    }

    @Test
    fun observeAccentColor_rebindOldScopeCancel_doesNotCancelNewObserver() = runBlocking {
        val scope1 = CoroutineScope(Dispatchers.Default + Job())
        val scope2 = CoroutineScope(Dispatchers.Default + Job())

        try {
            coordinator.observeAccentColor(scope1)
            coordinator.observeAccentColor(scope2)

            val scope2Child = scope2.coroutineContext[Job]?.children?.firstOrNull()
            org.junit.Assert.assertNotNull("Scope2 must have launched observer job", scope2Child)
            org.junit.Assert.assertTrue("Scope2 observer job must be active", scope2Child!!.isActive)

            // Cancel old owner scope1
            scope1.coroutineContext[Job]?.cancelAndJoin()

            // Scope2 observer job must NOT be canceled by scope1 cleanup
            org.junit.Assert.assertTrue("Scope2 observer job must still be active after scope1 cancellation", scope2Child.isActive)
        } finally {
            scope2.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun observeAccentColor_deadScope_doesNotRegisterObserver() = runBlocking {
        val deadScope = CoroutineScope(Dispatchers.Default + Job())
        deadScope.coroutineContext[Job]?.cancel()

        coordinator.observeAccentColor(deadScope)
        assertNull(coordinator.accountAccentColor.value)
    }
}
