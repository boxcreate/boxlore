package cx.aswin.boxlore.core.playback

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** One-shot positions chosen by a transport request, including an intentional zero start. */
internal object PlaybackActivationRequest {
    data class Position(val positionMs: Long, val applyIntroTrim: Boolean)

    private data class Request(val episodeId: String, val position: Position)

    private val pending = AtomicReference<Request?>()
    private val sessionGeneration = AtomicLong()
    val generation: Long get() = sessionGeneration.get()

    fun set(episodeId: String, positionMs: Long, applyIntroTrim: Boolean = false) {
        pending.set(Request(episodeId, Position(positionMs.coerceAtLeast(0L), applyIntroTrim)))
        sessionGeneration.incrementAndGet()
    }

    fun consume(episodeId: String?): Position? {
        while (true) {
            val request = pending.get() ?: return null
            if (request.episodeId != episodeId) return null
            if (pending.compareAndSet(request, null)) return request.position
        }
    }

    fun clear() {
        pending.set(null)
        sessionGeneration.incrementAndGet()
    }
}
