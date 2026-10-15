package cx.aswin.boxlore.feature.info.logic

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Starts [block] at most once, on first await, and shares the result with every caller.
 * Failures resolve to `null` for all callers; cancellation still propagates.
 */
internal fun <T> CoroutineScope.sharedLookup(block: suspend () -> T?): Deferred<T?> =
    async(start = CoroutineStart.LAZY) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
