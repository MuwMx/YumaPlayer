package moe.rukamori.archivetune.addons

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SharedCallsTest {

    @Test
    fun get_deduplicatesConcurrentCalls() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        var currentTime = 1000L
        val shared = SharedCalls<String>(
            ttlMs = 10_000L,
            scope = testScope,
            now = { currentTime },
        )

        val invocationCount = AtomicInteger(0)
        val call1 = async {
            shared.get("track_1", { "call1" }) {
                delay(100)
                invocationCount.incrementAndGet()
                Result.success("result")
            }
        }
        val call2 = async {
            shared.get("track_1", { "call2" }) {
                delay(100)
                invocationCount.incrementAndGet()
                Result.success("result")
            }
        }

        testScheduler.advanceTimeBy(150)
        val r1 = call1.await().getOrThrow()
        val r2 = call2.await().getOrThrow()

        assertEquals("result", r1)
        assertEquals("result", r2)
        assertEquals(1, invocationCount.get())
    }

    @Test
    fun get_expiresAfterTtl() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        var currentTime = 1000L
        val shared = SharedCalls<String>(
            ttlMs = 5000L,
            scope = testScope,
            now = { currentTime },
        )

        var count = 0
        val r1 = shared.get("key", { "c1" }) {
            count++
            Result.success("v1")
        }.getOrThrow()
        assertEquals("v1", r1)
        assertEquals(1, count)

        currentTime += 2000L
        val r2 = shared.get("key", { "c2" }) {
            count++
            Result.success("v2")
        }.getOrThrow()
        assertEquals("v1", r2)
        assertEquals(1, count)

        currentTime += 6000L
        val r3 = shared.get("key", { "c3" }) {
            count++
            Result.success("v3")
        }.getOrThrow()
        assertEquals("v3", r3)
        assertEquals(2, count)
    }

    @Test
    fun get_doesNotCacheFailures() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val shared = SharedCalls<String>(
            ttlMs = 5000L,
            scope = testScope,
        )

        var attempt = 0
        val fail = shared.get("failKey", { "fail" }) {
            attempt++
            Result.failure(IllegalStateException("failed"))
        }
        assertTrue(fail.isFailure)
        assertEquals(1, attempt)

        val success = shared.get("failKey", { "retry" }) {
            attempt++
            Result.success("recovered")
        }
        assertTrue(success.isSuccess)
        assertEquals("recovered", success.getOrThrow())
        assertEquals(2, attempt)
    }
}
