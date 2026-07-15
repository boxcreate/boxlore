package cx.aswin.boxcast.core.data.content

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class SharedExposureBudget(
    private val maximumRememberedItems: Int = 200,
    private val maximumItemsPerShow: Int = 2,
) {
    private val exposedItems = linkedMapOf<String, String>()
    private val showCounts = mutableMapOf<String, Int>()

    init {
        require(maximumRememberedItems > 0)
        require(maximumItemsPerShow > 0)
    }

    @Synchronized
    fun allows(candidate: ContentCandidate): Boolean {
        return candidate.id !in exposedItems &&
            (showCounts[candidate.podcast.id] ?: 0) < maximumItemsPerShow
    }

    @Synchronized
    fun record(candidates: Collection<ContentCandidate>) {
        candidates.forEach { candidate ->
            if (candidate.id !in exposedItems) {
                exposedItems[candidate.id] = candidate.podcast.id
                showCounts[candidate.podcast.id] =
                    (showCounts[candidate.podcast.id] ?: 0) + 1
            }
        }
        while (exposedItems.size > maximumRememberedItems) {
            val oldest = exposedItems.entries.first()
            exposedItems.remove(oldest.key)
            val remainingCount = (showCounts[oldest.value] ?: 1) - 1
            if (remainingCount <= 0) showCounts.remove(oldest.value)
            else showCounts[oldest.value] = remainingCount
        }
    }

    @Synchronized
    fun reset() {
        exposedItems.clear()
        showCounts.clear()
    }

    @Synchronized
    fun <T> atomically(block: () -> T): T = block()
}

class SlateComposer {
    fun compose(
        context: ContentContext,
        catalogVersion: String,
        rankedByIntent: List<Pair<ContentIntent, List<ContentCandidate>>>,
        exposureBudget: SharedExposureBudget,
        now: Long = System.currentTimeMillis(),
    ): ContentSlate = exposureBudget.atomically {
        val proposed = rankedByIntent.mapNotNull { (intent, candidates) ->
            val eligible = candidates
                .asSequence()
                .distinctBy(ContentCandidate::id)
                .filter(exposureBudget::allows)
                .take(intent.maximumItems)
                .toList()
            eligible.takeIf { it.isNotEmpty() && it.size >= intent.minimumItems }?.let {
                ContentSection(
                    stableId = "${context.surface.name.lowercase()}:${intent.id}",
                    intent = intent,
                    items = it,
                    utility = sectionUtility(it, context),
                    explanation = it.firstOrNull()?.explanationTokens?.firstOrNull(),
                )
            }
        }
        val protected = proposed.filter { it.intent.protected }
        val optional = proposed.filterNot { it.intent.protected }
            .sortedWith(
                compareByDescending<ContentSection>(ContentSection::utility)
                    .thenBy(ContentSection::stableId),
            )
        val sections = enforceCrossSectionConstraints(protected + optional)
        sections.forEach { exposureBudget.record(it.items) }
        ContentSlate(
            surface = context.surface,
            sessionId = context.sessionId,
            sections = sections,
            generatedAt = now,
            catalogVersion = catalogVersion,
        )
    }

    private fun enforceCrossSectionConstraints(
        sections: List<ContentSection>,
    ): List<ContentSection> {
        val seenItems = mutableSetOf<String>()
        val showCounts = mutableMapOf<String, Int>()
        return sections.mapNotNull { section ->
            val constrained = section.items.filter { candidate ->
                candidate.id !in seenItems &&
                    (showCounts[candidate.podcast.id] ?: 0) < MAX_ITEMS_PER_SHOW_IN_SLATE
            }.onEach { candidate ->
                seenItems += candidate.id
                showCounts[candidate.podcast.id] =
                    (showCounts[candidate.podcast.id] ?: 0) + 1
            }
            constrained.takeIf { it.size >= section.intent.minimumItems }?.let {
                section.copy(items = it)
            }
        }
    }

    private fun sectionUtility(
        items: List<ContentCandidate>,
        context: ContentContext,
    ): Double {
        val topQuality = items.take(3).map(ContentCandidate::rankingScore).averageOrZero()
        val novelty = items.count(ContentCandidate::isNovel).toDouble() / items.size
        val onlineFit = if (context.isOnline) 1.0 else {
            items.count { it.source.name == "DOWNLOADED" }.toDouble() / items.size
        }
        return (topQuality * 0.7 + novelty * 0.2 + onlineFit * 0.1).coerceIn(-1.0, 1.0)
    }

    companion object {
        private const val MAX_ITEMS_PER_SHOW_IN_SLATE = 2
    }
}

class ContentOrchestrator(
    private val providers: List<CandidateProvider>,
    private val ranker: ContentCandidateRanker,
    private val intentResolver: ContentIntentResolver = ContentIntentResolver(),
    private val slateComposer: SlateComposer = SlateComposer(),
    private val exposureBudget: SharedExposureBudget = SharedExposureBudget(),
) {
    private val sessionCache = ConcurrentHashMap<String, ContentSlate>()

    suspend fun compose(
        context: ContentContext,
        catalog: ContentCatalogSnapshot?,
        forceRefresh: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): ContentSlate {
        val (catalogVersion, intents) = intentResolver.resolve(catalog, context)
        val cacheKey = cacheKey(context, catalogVersion, intents, now)
        if (!forceRefresh) sessionCache[cacheKey]?.let { return it }
        val rankedByIntent = supervisorScope {
            intents.map { intent ->
                async { intent to rankIntent(intent, context) }
            }.map { it.await() }
        }
        return slateComposer.compose(
            context = context,
            catalogVersion = catalogVersion,
            rankedByIntent = rankedByIntent,
            exposureBudget = exposureBudget,
            now = now,
        ).also { sessionCache[cacheKey] = it }
    }

    private suspend fun rankIntent(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        val candidates = providers.flatMap { provider ->
            providerCandidates(provider, intent, context)
        }.distinctBy(ContentCandidate::id)
        return try {
            ranker.rank(candidates, intent, context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            candidates.sortedByDescending(ContentCandidate::retrievalScore)
        }
    }

    private suspend fun providerCandidates(
        provider: CandidateProvider,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return try {
            provider.candidates(intent, context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSession(sessionId: String) {
        sessionCache.keys.removeIf { it.startsWith("$sessionId:") }
    }

    fun clearAll() {
        sessionCache.clear()
        exposureBudget.reset()
    }

    private fun cacheKey(
        context: ContentContext,
        catalogVersion: String,
        intents: List<ContentIntent>,
        now: Long,
    ): String {
        val refreshFingerprint = intents.joinToString(",") { intent ->
            val token = when (intent.refreshPolicy) {
                ContentRefreshPolicy.SESSION -> context.sessionId
                ContentRefreshPolicy.MANUAL -> "manual"
                ContentRefreshPolicy.DAYPART -> context.daypart.name
                ContentRefreshPolicy.DAILY -> (now / DAY_MILLIS).toString()
            }
            "${intent.id}:${intent.refreshPolicy.name}:$token"
        }
        return listOf(
            context.sessionId,
            context.surface.name,
            catalogVersion,
            context.region,
            context.subscriptionCount,
            context.historyMaturity,
            context.currentEpisodeId.orEmpty(),
            refreshFingerprint,
        ).joinToString(":")
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
