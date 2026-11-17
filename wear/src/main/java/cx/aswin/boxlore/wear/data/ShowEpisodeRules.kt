package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.ShowEpisodes
import cx.aswin.boxlore.wear.model.WatchEpisode

/** A show's episodes as the phone has them saved, and when the phone last read the show's feed (null when it does not say). */
class PhoneEpisodeList(val episodes: List<WatchEpisode>, val readAtMillis: Long?)

/** A show's episodes as the watch read them itself, and whether they came from the feed rather than the API's list. */
internal class OwnEpisodes(val episodes: List<WatchEpisode>, val fromFeed: Boolean)

/** Where loading a show's episodes stands. */
internal enum class EpisodeLoad { Loading, Loaded, Failed }

/** How a show page's episode list is put together on the watch: pure rules for [LiveWatchLibrary]. */
internal object ShowEpisodeRules {
    /**
     * The phone reads a followed show's feed at most this often on its own, so a saved list older
     * than this may be missing the newest episodes. The watch also tries for a fresher list at
     * most this often per show.
     */
    const val STALE_AFTER_MS = 6L * 60L * 60L * 1_000L

    /** Whether a list read at [readAtMillis] is recent enough to show without looking further. */
    fun isFresh(readAtMillis: Long?, nowMillis: Long): Boolean =
        readAtMillis != null && readAtMillis > 0L && nowMillis - readAtMillis < STALE_AFTER_MS

    /**
     * Whether to look past the phone's saved list, read at [savedReadAtMillis]: only when it is
     * old and the watch has not already tried for a fresher one recently ([lastTryMillis]).
     */
    fun wantsFresher(savedReadAtMillis: Long?, lastTryMillis: Long?, nowMillis: Long): Boolean =
        !isFresh(savedReadAtMillis, nowMillis) && !isFresh(lastTryMillis, nowMillis)

    /**
     * The feed as just read ([incoming]) in place of what the page [shown] from the oldest dated
     * incoming episode on, so episodes taken off the feed drop off. Older shown episodes stay, as a
     * feed read may cover only the newest ones.
     */
    fun replaced(shown: List<WatchEpisode>, incoming: List<WatchEpisode>): List<WatchEpisode> {
        val oldest = incoming.mapNotNull(WatchEpisode::releasedAtMillis).minOrNull() ?: return newestFirst(incoming)
        val kept = shown.filter { old -> old.releasedAtMillis.let { it != null && it < oldest } && incoming.none { it.sameEpisode(old) } }
        return newestFirst(incoming + kept)
    }

    /**
     * What an older list ([incoming]) can add to what the page [shown]: only episodes newer than
     * the newest shown, so it never brings back episodes a newer read dropped, or lists one twice.
     * An empty page takes the whole list.
     */
    fun added(shown: List<WatchEpisode>, incoming: List<WatchEpisode>): List<WatchEpisode> {
        if (shown.isEmpty()) return newestFirst(incoming)
        val newestShown = shown.mapNotNull(WatchEpisode::releasedAtMillis).maxOrNull() ?: return shown
        val newer = incoming.filter { new -> new.releasedAtMillis.let { it != null && it > newestShown } && shown.none { it.sameEpisode(new) } }
        return newestFirst(newer + shown)
    }

    // The sort is stable: episodes without a date keep their order, after the dated ones.
    private fun newestFirst(episodes: List<WatchEpisode>): List<WatchEpisode> = episodes.sortedByDescending { it.releasedAtMillis ?: Long.MIN_VALUE }

    /** What the page shows. A list on screen stays there even when a later read fails. */
    fun state(load: EpisodeLoad?, episodes: List<WatchEpisode>, showUnknown: Boolean): ShowEpisodes =
        when {
            showUnknown -> ShowEpisodes.Failed
            episodes.isNotEmpty() || load == EpisodeLoad.Loaded -> ShowEpisodes.Loaded(episodes)
            load == EpisodeLoad.Failed -> ShowEpisodes.Failed
            else -> ShowEpisodes.Loading
        }

    private fun WatchEpisode.sameEpisode(other: WatchEpisode): Boolean =
        id == other.id ||
            (catalogKey != null && catalogKey == other.catalogKey) ||
            (audioUrl != null && audioUrl == other.audioUrl)
}
