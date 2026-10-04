package cx.aswin.boxlore.feature.explore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal fun loreArtworkSources(imageUrl: String?, feedImage: String?): List<String> =
    listOfNotNull(imageUrl, feedImage).map(String::trim).filter(String::isNotEmpty).distinct()

internal val LearnCuriosityCard.artworkSources: List<String>
    get() = loreArtworkSources(imageUrl, feedImage)

/** ViewModel/main-thread owned: one bounded palette cache for the page and its whole deck. */
internal class LoreArtworkAccentStore(
    private val scope: CoroutineScope,
    private val capacity: Int = 64,
    private val loadAccent: suspend (List<String>) -> Int?,
) {
    private val cache = LinkedHashMap<List<String>, Int>()
    private val pending = mutableMapOf<List<String>, Job>()
    private val permits = Semaphore(2)
    private val _colors = MutableStateFlow<Map<List<String>, Int>>(emptyMap())
    val colors = _colors.asStateFlow()

    init {
        require(capacity > 0)
    }

    fun prefetch(artworks: List<List<String>>) {
        val wanted = artworks.filter { it.isNotEmpty() }.distinct().take(4)
        pending.keys.filterNot { it in wanted }.forEach { pending.remove(it)?.cancel() }
        for (key in wanted) {
            val cached = cache.remove(key)
            if (cached != null) {
                cache[key] = cached
            } else if (key !in pending) {
                request(key)
            }
        }
    }

    private fun request(key: List<String>) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val color = permits.withPermit { loadAccent(key) }
                currentCoroutineContext().ensureActive()
                if (color != null) {
                    cache[key] = color
                    while (cache.size > capacity) cache.remove(cache.keys.first())
                    _colors.value = cache.toMap()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed artwork remains retryable; its card uses the shared theme fallback.
            } finally {
                if (pending[key] === currentCoroutineContext()[Job]) pending.remove(key)
            }
        }
        pending[key] = job
        job.start()
    }
}

/** Mirror visible artwork's optimized/original fallback, then try the show's artwork. */
internal suspend fun resolveLoreArtworkAccent(
    sources: List<String>,
    optimize: (String) -> String,
    readAccent: suspend (String) -> Int?,
): Int? {
    for (source in sources) {
        for (url in listOf(optimize(source), source).distinct()) {
            val color = try {
                readAccent(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            if (color != null) return color
        }
    }
    return null
}
