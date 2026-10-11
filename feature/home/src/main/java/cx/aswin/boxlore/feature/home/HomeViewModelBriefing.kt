package cx.aswin.boxlore.feature.home

import android.net.Uri
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.BuildConfig
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
            regions = briefingLoadRegions(userPrefs.briefingDefaultEdition, userPrefs.regionStream, clockContextFlow),
            briefingState = _briefingState,
            chaptersState = _briefingChaptersState,
            fetchMetadata = podcastRepository::getBriefingMetadata,
            fetchChapters = { briefing ->
                val version = Uri.parse(briefing.audioUrl).getQueryParameter("v")
                val url = briefing.chaptersUrl
                    ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}${version?.let { "&v=$it" }.orEmpty()}"
                ChapterRepository.getChapters(url)
            },
        )
    }
}

/** The production collector preserves a usable same-edition Brief during a failed refresh. */
internal suspend fun collectHomeBriefings(
    regions: Flow<String>,
    briefingState: MutableStateFlow<Briefing?>,
    chaptersState: MutableStateFlow<List<Chapter>>,
    fetchMetadata: suspend (String) -> Briefing?,
    fetchChapters: suspend (Briefing) -> List<Chapter>,
) {
    regions.collectLatest { region ->
        if (briefingState.value?.region != region) {
            briefingState.value = null
            chaptersState.value = emptyList()
        }
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
