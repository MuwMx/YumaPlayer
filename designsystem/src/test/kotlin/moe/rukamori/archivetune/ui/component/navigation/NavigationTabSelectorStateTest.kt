package moe.rukamori.archivetune.ui.component

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.ui.haptics.NoOpYumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTabSelectorStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val items = listOf(Screens.Home, Screens.Search, Screens.Library)

    @Test
    fun hoveringDoesNotNavigate_navigatesOnlyOnRelease() = testScope.runTest {
        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        val state = NavigationTabSelectorState(
            items = items,
            isSelectedProvider = { { it == currentSelected } },
            onItemClickProvider = { { screen, isSelected -> invocations.add(screen to isSelected) } },
            hapticsProvider = { NoOpYumaHaptics },
            animationsDisabledProvider = { true },
            coroutineScope = this,
        )

        state.rowWidthPx = 300f // 100px per tab
        assertEquals(100f, state.tabWidthPx, 0.01f)

        // Drag starts on Home (index 0)
        state.onDragStart(50f)
        assertTrue(state.isDragging)
        assertEquals(0, state.candidateIndex)

        // Move to Search (index 1)
        state.onDrag(100f)
        assertEquals(1, state.candidateIndex)
        assertEquals(0, invocations.size)

        // Wait a long time — hovering must NEVER trigger navigation
        advanceTimeBy(1000)
        assertEquals(0, invocations.size)

        // Release on Search triggers navigation exactly once
        state.onRelease()
        assertEquals(1, invocations.size)
        assertEquals(Screens.Search to false, invocations[0])
    }

    @Test
    fun dragAcrossMultipleTabs_onlyNavigatesToFinalTabOnRelease() = testScope.runTest {
        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        val state = NavigationTabSelectorState(
            items = items,
            isSelectedProvider = { { it == currentSelected } },
            onItemClickProvider = { { screen, isSelected -> invocations.add(screen to isSelected) } },
            hapticsProvider = { NoOpYumaHaptics },
            animationsDisabledProvider = { true },
            coroutineScope = this,
        )

        state.rowWidthPx = 300f
        state.onDragStart(50f)

        // Move to Search, wait
        state.onDrag(100f)
        advanceTimeBy(300)
        assertEquals(0, invocations.size)

        // Move to Library, wait
        state.onDrag(100f)
        advanceTimeBy(300)
        assertEquals(0, invocations.size)

        // Release on Library
        state.onRelease()
        assertEquals(1, invocations.size)
        assertEquals(Screens.Library to false, invocations[0])
    }

    @Test
    fun releaseOnAlreadySelectedTab_doesNotNavigate() = testScope.runTest {
        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        val state = NavigationTabSelectorState(
            items = items,
            isSelectedProvider = { { it == currentSelected } },
            onItemClickProvider = { { screen, isSelected -> invocations.add(screen to isSelected) } },
            hapticsProvider = { NoOpYumaHaptics },
            animationsDisabledProvider = { true },
            coroutineScope = this,
        )

        state.rowWidthPx = 300f
        state.onDragStart(50f) // Home

        state.onRelease()
        assertEquals(0, invocations.size)
    }

    @Test
    fun onCancel_triggersZeroNavigation() = testScope.runTest {
        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        val state = NavigationTabSelectorState(
            items = items,
            isSelectedProvider = { { it == currentSelected } },
            onItemClickProvider = { { screen, isSelected -> invocations.add(screen to isSelected) } },
            hapticsProvider = { NoOpYumaHaptics },
            animationsDisabledProvider = { true },
            coroutineScope = this,
        )

        state.rowWidthPx = 300f
        state.onDragStart(50f)
        state.onDrag(100f) // Over Search

        state.onCancel()
        advanceTimeBy(500)

        assertEquals(0, invocations.size)
        assertEquals(-1, state.candidateIndex)
    }

    @Test
    fun onTabTap_triggersImmediateSpringAndCallback() = testScope.runTest {
        val clock = object : androidx.compose.runtime.MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
                return onFrame(System.nanoTime())
            }
        }

        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        kotlinx.coroutines.withContext(clock) {
            val state = NavigationTabSelectorState(
                items = items,
                isSelectedProvider = { { it == currentSelected } },
                onItemClickProvider = { { screen, isSelected -> invocations.add(screen to isSelected) } },
                hapticsProvider = { NoOpYumaHaptics },
                animationsDisabledProvider = { true },
                coroutineScope = this,
            )

            state.rowWidthPx = 300f
            state.syncToTab(0, animate = false)
            testScheduler.advanceUntilIdle()
            assertEquals(50f, state.selectorXAnimatable.value, 0.01f)

            state.onTabTap(Screens.Library, isSelected = false)
            testScheduler.advanceUntilIdle()

            assertEquals(1, invocations.size)
            assertEquals(Screens.Library to false, invocations[0])
            assertEquals(250f, state.selectorXAnimatable.value, 0.01f)
        }
    }
}