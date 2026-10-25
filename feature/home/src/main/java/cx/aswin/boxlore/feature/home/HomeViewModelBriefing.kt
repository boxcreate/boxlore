package cx.aswin.boxlore.feature.home

import android.net.Uri
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.BriefingLinks
import cx.aswin.boxlore.core.catalog.ChapterRepository
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.prefs.BriefingEdition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Edition changes update only the Brief; discovery and ranking keep the app region. */
internal fun HomeViewModel.loadDailyBriefing() {
    viewModelScope.launch {
        homeCacheRestoreGate.awaitRestore()
        collectHomeBriefings(
            regions = briefingRegionsWhileOn(
                briefingLoadRegions(userPrefs.briefingDefaultEdition, userPrefs.regionStream, clockContextFlow),
                sectionOn { it.dailyBrief },
            ),
            briefingState = _briefingState,
            chaptersState = _briefingChaptersState,
            fetchMetadata = podcastRepository::getBriefingMetadata,
            fetchChapters = { briefing ->
                val version = Uri.parse(briefing.audioUrl).getQueryParameter("v")
                val url = briefing.chaptersUrl
                    ?: BriefingLinks.chaptersUrl(briefing.region, briefing.date, version)
                ChapterRepository.getChapters(url)
            },
        )
    }
}

/**
 * The Brief's edition region while the listener keeps it on, and null while it is off. Repeats
 * of the same region pass through, since they are the daypart and midnight refreshes.
 */
internal fun briefingRegionsWhileOn(regions: Flow<String>, on: Flow<Boolean>): Flow<String?> =
    combine(regions, on) { region, enabled -> region.takeIf { enabled } }

/**
 * The production collector preserves a usable same-edition Brief during a failed refresh. A null
 * region means the Brief is off: it clears without a request.
 */
internal suspend fun collectHomeBriefings(
    regions: Flow<String?>,
    briefingState: MutableStateFlow<Briefing?>,
    chaptersState: MutableStateFlow<List<Chapter>>,
    fetchMetadata: suspend (String) -> Briefing?,
    fetchChapters: suspend (Briefing) -> List<Chapter>,
) {
    regions.collectLatest { region ->
        if (region == null || briefingState.value?.region != region) {
            briefingState.value = null
            chaptersState.value = emptyList()
        }
        if (region == null) return@collectLatest
        val briefing = fetchMetadata(region)
        currentCoroutineContext().ensureActive()
        if (briefing == null) return@collectLatest
        val previous = briefingState.value
        if (briefing.region != previous?.region || briefing.date != previous.date) {
            chaptersState.value = emptyList()
        }
        briefingState.value = briefing
        try {
            val chapters = fetchChapters(briefing)
            currentCoroutineContext().ensureActive()
            chaptersState.value = chapters
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The edition remains listenable when its chapter metadata is unavailable.
        }
    }
}

/** Preserve the Home daypart refresh and update at midnight without minute-by-minute requests. */
internal fun briefingLoadRegions(
    defaultEditions: Flow<String>,
    appRegions: Flow<String>,
    clockContexts: Flow<HomeClockContext>,
): Flow<String> = combine(defaultEditions, appRegions, clockContexts) { edition, region, clock ->
    Triple(BriefingEdition.resolve(edition, region), clock.date, clock.daypart)
}.distinctUntilChanged().map { it.first }
