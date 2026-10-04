package cx.aswin.boxlore.feature.home

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Serial shows share a small request budget rather than launching the whole library. */
internal class HomeSerialResolutionLimiter {
    private val semaphore = Semaphore(2)

    suspend fun <T> resolve(block: suspend () -> T): T = semaphore.withPermit { block() }
}
