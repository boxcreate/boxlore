package cx.aswin.boxlore.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastRepositoryMemoryPolicyTest {

    @Test
    fun `clampPageLimit bounds limits within safe range`() {
        assertEquals(1, clampPageLimit(-5))
        assertEquals(1, clampPageLimit(0))
        assertEquals(20, clampPageLimit(20))
        assertEquals(50, clampPageLimit(50))
        assertEquals(100, clampPageLimit(100))
        assertEquals(100, clampPageLimit(500))
        assertEquals(100, clampPageLimit(1000))
    }

    @Test
    fun `TimedLruCache returns cached value before ttl expires`() {
        var currentTime = 1000L
        val cache = TimedLruCache<String, String>(
            maxEntries = 3,
            ttlMillis = 500L,
            timeProvider = { currentTime },
        )

        cache["a"] = "apple"
        assertEquals("apple", cache["a"])

        currentTime += 499L
        assertEquals("apple", cache["a"])

        currentTime += 2L // total 501ms elapsed
        assertNull(cache["a"])
    }

    @Test
    fun `TimedLruCache evicts least recently accessed entry when capacity is exceeded`() {
        var currentTime = 1000L
        val cache = TimedLruCache<String, String>(
            maxEntries = 3,
            ttlMillis = 10_000L,
            timeProvider = { currentTime },
        )

        cache["a"] = "apple"
        currentTime += 10L
        cache["b"] = "banana"
        currentTime += 10L
        cache["c"] = "cherry"

        // Access "a" to make it most recently accessed; "b" becomes the eldest
        assertNotNull(cache["a"])

        currentTime += 10L
        cache["d"] = "date"

        // "b" should have been evicted
        assertNull(cache["b"])
        assertEquals("apple", cache["a"])
        assertEquals("cherry", cache["c"])
        assertEquals("date", cache["d"])
    }

    @Test
    fun `TimedLruCache invalidateIf removes matching keys`() {
        val cache = TimedLruCache<String, String>(
            maxEntries = 10,
            ttlMillis = 10_000L,
        )

        cache["feed1|page1"] = "data1"
        cache["feed1|page2"] = "data2"
        cache["feed2|page1"] = "data3"

        cache.invalidateIf { it.startsWith("feed1|") }

        assertNull(cache["feed1|page1"])
        assertNull(cache["feed1|page2"])
        assertEquals("data3", cache["feed2|page1"])
    }
}
