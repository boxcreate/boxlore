package cx.aswin.boxlore.tv.playback

import cx.aswin.boxlore.tv.model.TvEpisode

/**
 * The TV's Smart Queue: what plays when an episode ends and Up next is empty. A lighter take on
 * the phone's, from what the TV already holds rather than the phone's listening scores.
 *
 * First the same show carries on: the episode released right after the one that ended, so a
 * series plays in order and an older news episode moves to the next day's. When the show has
 * nothing newer to play, the newest unfinished episode of another followed show plays. Never an
 * episode already played, the one that just ended, a trailer, or one with no audio.
 */
object TvSmartQueue {
    /**
     * [sameShow]: the ended episode's show, any order. [followed]: other candidates in the order
     * they should play, such as New episodes then Daily Mix.
     */
    fun next(finished: TvEpisode, sameShow: List<TvEpisode>, followed: List<TvEpisode>, played: Set<String>): TvEpisode? {
        fun playable(episode: TvEpisode) = episode.id != finished.id && episode.id !in played && !episode.played && !episode.trailer && episode.audioUrl != null
        val releasedAt = finished.releasedAtMillis
        val carriesOn =
            releasedAt?.let { ended ->
                sameShow.filter { playable(it) && (it.releasedAtMillis ?: 0L) > ended }.minByOrNull { it.releasedAtMillis ?: 0L }
            }
        return carriesOn ?: followed.firstOrNull(::playable)
    }
}
