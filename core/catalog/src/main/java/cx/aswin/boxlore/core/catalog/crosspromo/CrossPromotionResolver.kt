package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException

class CrossPromotionResolver internal constructor(
    private val search: suspend (String) -> List<Podcast>,
    private val lookup: suspend (String) -> Podcast?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(repository: PodcastRepository) : this(repository::searchPodcasts, repository::getPodcastDetails)
    private data class Cached(val podcast: Podcast?, val expiresAt: Long)
    private val cache = linkedMapOf<String, Cached>()

    suspend fun resolve(extractedName: String, hostPodcastId: String? = null, links: List<EpisodeLink> = emptyList()): Podcast? {
        val name = extractedName.trim().trim('"', '“', '”')
        if (name.isBlank()) return null
        val podcastLinks = links.filter { it.kind == EpisodeLinkKind.PODCAST }
        val namedLinks = podcastLinks.filter { it.title?.let { title -> CrossPromotionDetector.sameShow(title, name) } == true }
        val contextualLinks = podcastLinks.filter { it.title == null && CrossPromotionDetector.normalizedName(it.context).contains(CrossPromotionDetector.normalizedName(name)) }
        // A lone unnamed destination is usable only after its resolved title matches the promoted show.
        val unnamedLink = podcastLinks.singleOrNull()?.takeIf { it.title == null }
        val targetLinks = namedLinks.ifEmpty { contextualLinks.takeIf { it.size == 1 } ?: listOfNotNull(unnamedLink) }
        val key = "${CrossPromotionDetector.normalizedName(name)}|$hostPodcastId|${targetLinks.joinToString { it.url }}"
        synchronized(cache) { cache[key]?.takeIf { it.expiresAt > now() }?.let { return it.podcast } }
        val result = try {
            val appleIds = targetLinks.filter { it.platform == "Apple Podcasts" }
                .mapNotNull { Regex("/id(\\d+)").find(it.url)?.groupValues?.get(1) }.distinct()
            val direct = appleIds.singleOrNull()?.let { id -> lookupSafely("itunes:$id") }?.takeIf {
                it.id != hostPodcastId && (namedLinks.isNotEmpty() || bestMatch(listOf(it), name, hostPodcastId) != null)
            }
            // "Closereads: Donald Davidson" and "NeferTV with Ann Foster" are found by their shorter names.
            direct ?: (listOf(name) + CrossPromotionNames.variants(name)).distinct().firstNotNullOfOrNull { query ->
                bestMatch(search(query), name, hostPodcastId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null // Transient failures remain retryable.
        }
        val lifetime = cacheLifetime(result)
        synchronized(cache) {
            cache.entries.removeAll { it.value.expiresAt <= now() }
            cache[key] = Cached(result, now() + lifetime)
            while (cache.size > 50) cache.remove(cache.keys.first())
        }
        return result
    }

    private fun cacheLifetime(podcast: Podcast?): Long = podcast?.let { 3_600_000L } ?: 60_000L

    private suspend fun lookupSafely(id: String): Podcast? = try {
        lookup(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null // Search can still resolve the explicitly named show.
    }

    internal fun bestMatch(results: List<Podcast>, name: String, hostId: String?): Podcast? {
        val scored = results.distinctBy { it.id }.filter { it.id != hostId }.mapNotNull { podcast ->
            score(name, podcast.title)?.let { podcast to it }
        }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        if (scored.getOrNull(1)?.let { best.second - it.second < 15 } == true) return null
        return best.first
    }

    /** How well a show titled [podcastTitle] fits the extracted [name], or null when it does not. */
    private fun score(name: String, podcastTitle: String): Int? {
        val query = CrossPromotionDetector.normalizedName(name)
        val queryTokens = query.split(' ').toSet()
        val title = CrossPromotionDetector.normalizedName(podcastTitle)
        val titleTokens = title.split(' ').toSet()
        val shared = queryTokens.intersect(titleTokens).size.toFloat()
        return when {
            query == title -> 100
            query == CrossPromotionDetector.normalizedName(podcastTitle.substringBefore(" (")) && ' ' in query -> 80
            colonPart(name, title) || queryTokens.size >= 2 && colonPart(podcastTitle, query) -> 80
            queryTokens.size >= 2 && shared / queryTokens.size >= .8f && shared / titleTokens.size >= .8f -> 75
            hostedVariant(query, title) -> 70
            else -> null
        }
    }

    /** "Fiasco: Benghazi" names a season of Fiasco; "Smoke Screen: Betrayal on the Bayou" ends with its show. */
    private fun colonPart(full: String, normalizedPart: String): Boolean {
        if (':' !in full || normalizedPart.length < 4) return false
        val head = CrossPromotionDetector.normalizedName(full.substringBefore(':'))
        val tail = CrossPromotionDetector.normalizedName(full.substringAfterLast(':'))
        return head == normalizedPart || tail == normalizedPart && ' ' in normalizedPart
    }

    /** "Uncharted" and "Uncharted with Hannah Fry" are one show named with and without its host. */
    private fun hostedVariant(query: String, title: String): Boolean {
        val (shorter, longer) = if (query.length <= title.length) query to title else title to query
        return shorter.length >= 4 && longer.startsWith("$shorter with ")
    }
}
