package cx.aswin.boxlore.feature.home

import android.net.Uri
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.catalog.ChapterRepository
import cx.aswin.boxlore.core.prefs.BriefingEdition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Edition changes update only the Brief; discovery and ranking keep the app region. */
internal fun HomeViewModel.loadDailyBriefing() {
    viewModelScope.launch {
        homeCacheRestoreGate.awaitRestore()
        briefingLoadRegions(userPrefs.briefingDefaultEdition, userPrefs.regionStream, clockContextFlow).collectLatest { region ->
            if (_briefingState.value?.region != region) {
                _briefingState.value = null
                _briefingChaptersState.value = emptyList()
            }
            val briefing = podcastRepository.getBriefingMetadata(region)
            currentCoroutineContext().ensureActive()
            val previous = _briefingState.value
            if (briefing?.region != previous?.region || briefing?.date != previous?.date) {
                _briefingChaptersState.value = emptyList()
            }
            _briefingState.value = briefing
            if (briefing != null) {
                val version = Uri.parse(briefing.audioUrl).getQueryParameter("v")
                val url = briefing.chaptersUrl
                    ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}${version?.let { "&v=$it" }.orEmpty()}"
                try {
                    val chapters = ChapterRepository.getChapters(url)
                    currentCoroutineContext().ensureActive()
                    _briefingChaptersState.value = chapters
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The edition remains listenable when its chapter metadata is unavailable.
                }
            }
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
