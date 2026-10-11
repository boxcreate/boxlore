package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.prefs.BriefingEdition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class BriefingLoadState(
    val region: String = "global",
    val briefing: Briefing? = null,
    val chapters: List<Chapter> = emptyList(),
    val session: PlaybackSession? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/** Owns the ViewModel's cancellable load sequence without constructing repositories or playback. */
internal class BriefingLoader(
    private val scope: CoroutineScope,
    private val fetchMetadata: suspend (String) -> Briefing?,
    private val fetchChapters: suspend (Briefing) -> List<Chapter>,
    private val fetchSession: suspend (Briefing) -> PlaybackSession?,
    private val publish: (BriefingLoadState) -> Unit,
) {
    private var request: Job? = null

    fun initialize(defaultEdition: Flow<String>, appRegion: Flow<String>, explicitRegion: String?) {
        scope.launch {
            val region = BriefingEdition.resolve(defaultEdition.first(), appRegion.first(), explicitRegion)
            // A user choice made during preference restoration must win over initialization.
            if (request == null) load(region)
        }
    }

    /** Retries even the same edition; cancelled responses cannot publish after a newer selection. */
    fun load(region: String) {
        request?.cancel()
        request = scope.launch {
            val selected = BriefingEdition.resolve(region, "global")
            publish(BriefingLoadState(region = selected))
            val briefing = try {
                fetchMetadata(selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            if (briefing == null) {
                publish(BriefingLoadState(region = selected, isLoading = false, failed = true))
                return@launch
            }
            val session = fetchSession(briefing)
            currentCoroutineContext().ensureActive()
            val chapters = try {
                fetchChapters(briefing)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            currentCoroutineContext().ensureActive()
            publish(BriefingLoadState(selected, briefing, chapters, session, isLoading = false))
        }
    }
}
