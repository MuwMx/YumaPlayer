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
    fun dwell_selectsCandidateAfterDelay() = testScope.runTest {
        var currentSelected: Screens = Screens.Home
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

        // Move to Search (index 1, center at 150f)
        state.onDrag(100f)
        assertEquals(1, state.candidateIndex)
        assertEquals(0, invocations.size)

        // Advance 200ms - below 250ms threshold, no dwell yet
        advanceTimeBy(200)
        assertEquals(0, invocations.size)

        // Advance past threshold
        advanceTimeBy(60)
        assertEquals(1, invocations.size)
        assertEquals(Screens.Search to false, invocations[0])
    }

    @Test
    fun movementOffCandidate_cancelsPriorDwell() = testScope.runTest {
        var currentSelected: Screens = Screens.Home
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
        // Move to Search
        state.onDrag(100f)
        assertEquals(1, state.candidateIndex)

        // Dwell partially (150ms)
        advanceTimeBy(150)
        assertEquals(0, invocations.size)

        // Move to Library (index 2) before dwell completes
        state.onDrag(100f)
        assertEquals(2, state.candidateIndex)

        // Advance 150ms: Search was cancelled, Library only at 150ms
        advanceTimeBy(150)
        assertEquals(0, invocations.size)

        // Advance another 110ms: Library completes dwell
        advanceTimeBy(110)
        assertEquals(1, invocations.size)
        assertEquals(Screens.Library to false, invocations[0])
    }

    @Test
    fun release_doesNotDuplicateIfAlreadyDwellSelected() = testScope.runTest {
        var currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        val state = NavigationTabSelectorState(
            items = items,
            isSelectedProvider = { { it == currentSelected } },
            onItemClickProvider = { { screen, isSelected ->
                invocations.add(screen to isSelected)
                currentSelected = screen
            } },
            hapticsProvider = { NoOpYumaHaptics },
            animationsDisabledProvider = { true },
            coroutineScope = this,
        )

        state.rowWidthPx = 300f
        state.onDragStart(50f)
        state.onDrag(100f) // Move to Search

        // Complete dwell
        advanceTimeBy(260)
        assertEquals(1, invocations.size)

        // Release on Search
        state.onRelease()
        // Must NOT emit duplicate
        assertEquals(1, invocations.size)
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
        val clock =
            object : androidx.compose.runtime.MonotonicFrameClock {
                override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
                    return onFrame(System.nanoTime())
                }
            }

        val currentSelected: Screens = Screens.Home
        val invocations = mutableListOf<Pair<Screens, Boolean>>()

        kotlinx.coroutines.withContext(clock) {
            val state =
                NavigationTabSelectorState(
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
