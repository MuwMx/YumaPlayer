package moe.rukamori.archivetune.network

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNetworkBannerStateUseCaseTest {
    @Test
    fun backOnlineBannerHidesAfterBriefConnectivityFlap() =
        runTest {
            val connectivity = MutableSharedFlow<Boolean>()

            connectivity.asNetworkBannerUiState(
                offlineDebounceMillis = 750,
                offlineBannerDurationMillis = 3000,
                backOnlineBannerDurationMillis = 2500,
            ).test {
                connectivity.emit(true)
                assertEquals(NetworkBannerUiState.Hidden, awaitItem())

                connectivity.emit(false)
                runCurrent()
                advanceTimeBy(749)
                expectNoEvents()
                advanceTimeBy(1)
                runCurrent()
                assertEquals(NetworkBannerUiState.Offline, awaitItem())
                advanceTimeBy(3000)
                runCurrent()
                assertEquals(NetworkBannerUiState.Hidden, awaitItem())

                connectivity.emit(true)
                assertEquals(NetworkBannerUiState.BackOnline, awaitItem())
                connectivity.emit(false)
                runCurrent()
                advanceTimeBy(200)
                connectivity.emit(true)
                assertEquals(NetworkBannerUiState.BackOnline, awaitItem())

                advanceTimeBy(2499)
                expectNoEvents()
                advanceTimeBy(1)
                runCurrent()
                assertEquals(NetworkBannerUiState.Hidden, awaitItem())
            }
        }
}
