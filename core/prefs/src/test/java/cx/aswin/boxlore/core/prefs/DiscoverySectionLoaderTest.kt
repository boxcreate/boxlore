package cx.aswin.boxlore.core.prefs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverySectionLoaderTest {
    private val events = mutableListOf<String>()

    @Test
    fun `an off section clears without a request, even when its key changes`() = runTest {
        val keys = MutableStateFlow("us")
        val on = MutableStateFlow(false)
        val job = launch { collectDiscoverySection(keys, on, clear = { events += "clear" }, load = { events += "load:$it" }) }
        runCurrent()
        keys.value = "gb"
        runCurrent()

        assertEquals(listOf("clear", "clear"), events)
        job.cancel()
    }

    @Test
    fun `turning a section on loads the current key, and a new key reloads it`() = runTest {
        val keys = MutableStateFlow("us")
        val on = MutableStateFlow(false)
        val job = launch { collectDiscoverySection(keys, on, clear = { events += "clear" }, load = { events += "load:$it" }) }
        runCurrent()
        keys.value = "gb"
        on.value = true
        runCurrent()
        keys.value = "in"
        runCurrent()

        assertEquals(listOf("clear", "load:gb", "load:in"), events)
        job.cancel()
    }

    @Test
    fun `turning a section off cancels its request in flight and clears it`() = runTest {
        val on = MutableStateFlow(true)
        val reply = CompletableDeferred<Unit>()
        val job = launch {
            collectDiscoverySection(MutableStateFlow("us"), on, clear = { events += "clear" }) {
                events += "start:$it"
                reply.await()
                events += "shown:$it"
            }
        }
        runCurrent()
        on.value = false
        runCurrent()
        reply.complete(Unit)
        runCurrent()

        assertEquals(listOf("start:us", "clear"), events)
        job.cancel()
    }

    @Test
    fun `repeating the same key and switch makes no extra request`() = runTest {
        val keys = MutableSharedFlow<String>()
        val on = MutableStateFlow(true)
        val job = launch { collectDiscoverySection(keys, on, clear = { events += "clear" }, load = { events += "load:$it" }) }
        runCurrent()
        keys.emit("us")
        runCurrent()
        keys.emit("us")
        on.value = true
        runCurrent()

        assertEquals(listOf("load:us"), events)
        job.cancel()
    }
}
