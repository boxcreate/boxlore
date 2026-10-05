package cx.aswin.boxlore.core.rss

import kotlinx.coroutines.sync.Semaphore

/** One full-feed budget shared by catalog and directly imported subscriptions. */
internal object PublisherFeedRefreshGate {
    val permits = Semaphore(2)
}
