package cx.aswin.boxlore.core.designsystem.components

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local hints only: neither failures nor transport preferences survive forever. */
class ArtworkLoadPolicy(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val capacity: Int = 256,
) {
    private data class Failure(val until: Long, val transient: Boolean)
    private val failures = LinkedHashMap<String, Failure>()
    private val successes = LinkedHashMap<String, Long>()
    private data class Route(val url: String, val at: Long, val width: Int)
    private val preferredRoutes = LinkedHashMap<String, Route>()
    private val revisions = MutableStateFlow(0L)
    val revision = revisions.asStateFlow()

    @Synchronized
    fun available(url: String): Boolean {
        val failure = failures[url] ?: return true
        if (clock() < failure.until) return false
        failures.remove(url)
        return true
    }

    @Synchronized
    fun succeeded(url: String): Boolean = successes[url]?.let { clock() - it < SUCCESS_LIFETIME } == true

    @Synchronized
    fun recordFailure(url: String, transient: Boolean) {
        successes.remove(url)
        preferredRoutes.entries.removeAll { it.value.url == url }
        failures.remove(url)
        failures[url] = Failure(clock() + if (transient) TRANSIENT_COOLDOWN else PERMANENT_COOLDOWN, transient)
        trim(failures)
    }

    /** [width] is the pixel size the route was requested at; the original image counts as full size. */
    @Synchronized
    fun recordSuccess(url: String, source: String = url, width: Int = Int.MAX_VALUE) {
        failures.remove(url)
        val wasSuccessful = succeeded(url) && preferredRoute(source) == url
        successes.remove(url)
        successes[url] = clock()
        trim(successes)
        preferredRoutes.remove(source)
        preferredRoutes[source] = Route(url, clock(), if (url == source) Int.MAX_VALUE else width)
        trim(preferredRoutes)
        if (!wasSuccessful) revisions.value += 1
    }

    /**
     * The route that last loaded [source], when it is at least [minWidth] pixels. A smaller route
     * would be stretched, so a hero never takes a cover chip's thumbnail as its first choice.
     */
    @Synchronized
    fun preferredRoute(source: String, minWidth: Int = 0): String? = preferredRoutes[source]
        ?.takeIf { clock() - it.at < SUCCESS_LIFETIME && it.width >= minWidth }
        ?.url

    @Synchronized
    fun networkRestored() {
        failures.entries.removeAll { it.value.transient }
    }

    private fun <T> trim(map: MutableMap<String, T>) {
        while (map.size > capacity.coerceAtLeast(1)) map.remove(map.keys.first())
    }

    companion object {
        const val TRANSIENT_COOLDOWN = 30_000L
        private const val PERMANENT_COOLDOWN = 300_000L
        private const val SUCCESS_LIFETIME = 300_000L
        val Shared = ArtworkLoadPolicy()
    }
}

data class ArtworkAttempt(val source: String, val url: String, val recovery: Boolean = false)

/** Optimized then original for each image; at most one extra attempt for a transient failure. */
class ArtworkAttempts(
    sources: List<String>,
    width: Int,
    private val policy: ArtworkLoadPolicy = ArtworkLoadPolicy.Shared,
    optimize: (String, Int) -> String = { url, size -> url.optimizedImageUrl(size) },
) {
    private val pending = ArrayDeque<ArtworkAttempt>()
    private var transientAttempt: ArtworkAttempt? = null
    private var recovered = false

    init {
        val seen = mutableSetOf<String>()
        sources.distinct().take(4).forEach { source ->
            val routes = listOf(policy.preferredRoute(source, minWidth = width) ?: optimize(source, width), source).distinct()
            val preferred = routes.firstOrNull(policy::succeeded)
            // A smaller copy that loaded elsewhere is only a last resort before the fallback mark.
            val smaller = policy.preferredRoute(source)
            (listOfNotNull(preferred) + routes + listOfNotNull(smaller)).distinct().forEach { route ->
                if (seen.add(route) && policy.available(route)) pending.add(ArtworkAttempt(source, route))
            }
        }
    }

    fun next(): ArtworkAttempt? {
        while (pending.isNotEmpty()) {
            val attempt = pending.removeFirst()
            if (policy.available(attempt.url)) return attempt
        }
        if (recovered) return null
        recovered = true
        return transientAttempt?.copy(recovery = true)
    }

    fun failed(attempt: ArtworkAttempt, error: Throwable) {
        if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) return
        val transient = isTransientArtworkFailure(error)
        policy.recordFailure(attempt.url, transient)
        if (transient && transientAttempt == null && !attempt.recovery) transientAttempt = attempt
    }
}

fun isTransientArtworkFailure(error: Throwable): Boolean = when (error) {
    is coil.network.HttpException -> error.response.code == 408 || error.response.code >= 500
    is kotlinx.coroutines.TimeoutCancellationException -> true
    is CancellationException -> false
    is android.graphics.ImageDecoder.DecodeException -> false
    is IOException -> true
    else -> false
}
