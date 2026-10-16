package cx.aswin.boxlore.feature.onboarding

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns [fallback] when it throws. Use inside `async` children: an exception
 * escaping a child fails its parent job even if the caller wraps `await()` in try/catch.
 */
internal suspend fun <T> orFallbackOnFailure(
    fallback: T,
    onFailure: (Exception) -> Unit = {},
    block: suspend () -> T,
): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onFailure(e)
    fallback
}
