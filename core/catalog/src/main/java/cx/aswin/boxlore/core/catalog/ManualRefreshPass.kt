package cx.aswin.boxlore.core.catalog

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Marks a [SubscriptionForegroundSync] pass started by pull to refresh. The pass reads every
 * followed feed it selects, even one read within the local catalog's six-hour wait.
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
