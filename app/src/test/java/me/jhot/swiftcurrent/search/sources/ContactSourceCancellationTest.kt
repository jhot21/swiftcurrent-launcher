package me.jhot.swiftcurrent.search.sources

import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.search.LaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds the provider query open until its signal is cancelled, the way a slow provider would be. */
private class BlockingReader : ContactReader {
    val entered = CountDownLatch(1)
    val finished = CountDownLatch(1)

    @Volatile var cancelled = false

    @Volatile var calls = 0
    override fun rows(query: String): List<ContactRow> = rows(query, CancellationSignal())
    override fun rows(query: String, signal: CancellationSignal): List<ContactRow> {
        calls++
        entered.countDown()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!signal.isCanceled && System.nanoTime() < deadline) Thread.sleep(5)
        cancelled = signal.isCanceled
        finished.countDown()
        return emptyList()
    }
    override fun rowsForContacts(ids: Set<Long>): List<ContactRow> = emptyList()
}

// Robolectric because CancellationSignal is an Android class: the plain JVM stub throws.
@RunWith(RobolectricTestRunner::class)
class ContactSourceCancellationTest {
    private fun sourceOf(reader: ContactReader) = ContactSource(reader, { true }, EnglishSearchStrings, actionFactory = ContactActionFactory { LaunchOutcome.Launched })

    @Test
    fun cancelling_a_search_cancels_the_provider_query_in_flight() {
        val reader = BlockingReader()
        runBlocking {
            val job = launch(Dispatchers.Default) { sourceOf(reader).search("alex") }
            assertTrue(reader.entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("the query kept running", reader.finished.await(2, TimeUnit.SECONDS))
        }
        assertTrue(reader.cancelled)
    }

    @Test
    fun a_search_replaced_within_the_debounce_never_reaches_the_provider() {
        val reader = BlockingReader()
        runBlocking {
            val job = launch(Dispatchers.Default) { sourceOf(reader).search("a") }
            job.cancelAndJoin()
        }
        assertEquals(0, reader.calls)
    }
}
