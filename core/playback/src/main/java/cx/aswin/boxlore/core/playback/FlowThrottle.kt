package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * The first value at once, then at most one value per [periodMs]: the latest one, after values
 * that came in the meantime are dropped. Keeps whole-history reads from following every progress
 * save during playback.
 */
internal fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = flow {
    conflate().collect { value ->
        emit(value)
        delay(periodMs)
    }
}
