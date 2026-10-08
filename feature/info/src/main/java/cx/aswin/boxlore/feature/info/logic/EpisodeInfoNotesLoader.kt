package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ResolvedCrossPromotion
import cx.aswin.boxlore.core.model.ShowNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Latest request owns every publication, including metadata refreshes for the same episode. */
internal class EpisodeInfoNotesLoader(
    private val scope: CoroutineScope,
    private val chapters: suspend (String) -> List<Chapter>,
    private val promotion: suspend (Episode, String, String, ShowNotes) -> ResolvedCrossPromotion?,
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var generation = 0
    private var job: Job? = null

    fun load(episode: Episode, hostId: String, hostTitle: String, onNotes: (ShowNotes) -> Unit, onChapters: (List<Chapter>) -> Unit, onPromotion: (ResolvedCrossPromotion?) -> Unit) {
        cancel()
        val request = generation
        job = scope.launch {
            val notes = withContext(parserDispatcher) { ShowNotesParser.parse(episode.description, durationSeconds = episode.duration) }
            if (request != generation) return@launch
            onNotes(notes)
            onChapters(notes.chapters)
            launch {
                val remote = episode.chaptersUrl?.takeIf(String::isNotBlank)?.let { url -> recover { chapters(url) } }.orEmpty()
                val valid = remote.filter { it.startTime.isFinite() && it.startTime >= 0 && (episode.duration <= 0 || it.startTime < episode.duration) }
                    .distinctBy { it.startTime }.sortedBy { it.startTime }
                if (request == generation && valid.isNotEmpty()) onChapters(valid)
            }
            launch {
                val result = recover { promotion(episode, hostId, hostTitle, notes) }
                if (request == generation) onPromotion(result)
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
    }

    private suspend fun <T> recover(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
