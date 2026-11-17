package cx.aswin.boxlore.core.catalog

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Marks a [SubscriptionForegroundSync] pass started by pull to refresh. The pass checks every
 * followed feed, up to [DirectFeedRefreshBatch.MANUAL_MAX], even one read within the six-hour wait:
 * its reads are [cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason.PULL],
 * which skip that wait but keep the conditional (ETag/Last-Modified) check, so they are not forced
 * full downloads: a feed whose saved copy has those validators and answers unchanged is not
 * downloaded again.
 */
internal class ManualRefreshPass : AbstractCoroutineContextElement(ManualRefreshPass) {
    companion object Key : CoroutineContext.Key<ManualRefreshPass>
}

internal suspend fun isManualRefreshPass(): Boolean = currentCoroutineContext()[ManualRefreshPass] != null

/**
 * Whether a followed show's feed is read this pass: it needs an address, and either the pass is
 * pull to refresh or the feed is past its wait ([pastWait]).
 */
internal suspend fun isFeedDueThisPass(feedUrl: String?, pastWait: suspend (String) -> Boolean): Boolean =
    feedUrl != null && (isManualRefreshPass() || pastWait(feedUrl))
