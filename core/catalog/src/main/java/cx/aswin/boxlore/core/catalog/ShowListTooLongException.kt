package cx.aswin.boxlore.core.catalog

import java.io.IOException

/**
 * The whole-show read ([PodcastRepository.getWholeShowPage]) reached the last offset `/v2/episodes`
 * pages through while the show still lists more episodes. Unlike a failed page, reading again
 * can't help, so callers say the show is too long rather than asking to check the connection.
 */
class ShowListTooLongException : IOException("The show lists more episodes than /v2/episodes pages through")
