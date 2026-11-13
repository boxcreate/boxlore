package cx.aswin.boxlore.core.wearlink

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

/** One stored value from the other device. Null [bytes] means it was deleted. */
class LinkItem(val nodeId: String, val path: String, val bytes: ByteArray?)

/**
 * The connection between the phone app and the watch app, reduced to what the link needs.
 *
 * Published items are kept by the system and reach the other device whenever the two next
 * connect; messages and requests only work while the other device is reachable.
 */
interface WearLinkTransport {
    /** Whether the other app can be reached right now. */
    val peerReachable: StateFlow<Boolean>

    /** Looks for the other app again; call when a stale answer would matter. */
    suspend fun refreshPeer()

    /** Stores [bytes] at [path] for the other device. False when the link is not available here. */
    suspend fun publish(path: String, bytes: ByteArray): Boolean

    /** The other device's items under [prefix] as they are now. */
    suspend fun current(prefix: String): List<LinkItem>

    /** Every later change to the other device's items under [prefix], while collected. */
    fun changes(prefix: String): Flow<LinkItem>

    /** Sends a one-off message. False when the other device is out of reach. */
    suspend fun send(path: String, bytes: ByteArray): Boolean

    /** Asks the other device a question; null when it is out of reach or slow to answer. */
    suspend fun request(path: String, bytes: ByteArray, timeoutMs: Long = REQUEST_TIMEOUT_MS): ByteArray?

    companion object {
        const val REQUEST_TIMEOUT_MS = 2_500L
    }
}

/** A link that is never there: a device without the system service, and the base for tests. */
open class NoWearLinkTransport : WearLinkTransport {
    override val peerReachable: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun refreshPeer() = Unit

    override suspend fun publish(path: String, bytes: ByteArray): Boolean = false

    override suspend fun current(prefix: String): List<LinkItem> = emptyList()

    override fun changes(prefix: String): Flow<LinkItem> = MutableSharedFlow()

    override suspend fun send(path: String, bytes: ByteArray): Boolean = false

    override suspend fun request(path: String, bytes: ByteArray, timeoutMs: Long): ByteArray? = null
}

/**
 * A link held in memory, for tests on either side: it records what was published and sent, and a
 * test plays the other device through [deliver], [reachable] and [answers].
 */
class RecordingWearLinkTransport : WearLinkTransport {
    val reachable = MutableStateFlow(true)
    val published = LinkedHashMap<String, ByteArray>()
    val sent = ArrayList<Pair<String, ByteArray>>()

    /** What the other device holds; [current] reads it. */
    val peerItems = LinkedHashMap<String, ByteArray>()

    /** The other device's answer to a request path; none means out of reach. */
    val answers = HashMap<String, (ByteArray) -> ByteArray?>()

    private val incoming = MutableSharedFlow<LinkItem>(extraBufferCapacity = 64)

    override val peerReachable: StateFlow<Boolean> = reachable

    override suspend fun refreshPeer() = Unit

    override suspend fun publish(path: String, bytes: ByteArray): Boolean {
        published[path] = bytes
        return true
    }

    override suspend fun current(prefix: String): List<LinkItem> =
        peerItems.filterKeys { it.startsWith(prefix) }.map { (path, bytes) -> LinkItem(PEER, path, bytes) }

    override fun changes(prefix: String): Flow<LinkItem> = incoming.filter { it.path.startsWith(prefix) }

    override suspend fun send(path: String, bytes: ByteArray): Boolean {
        if (!reachable.value) return false
        sent += path to bytes
        return true
    }

    override suspend fun request(path: String, bytes: ByteArray, timeoutMs: Long): ByteArray? =
        if (reachable.value) answers[path]?.invoke(bytes) else null

    /** The other device stores [bytes] at [path]. */
    suspend fun deliver(path: String, bytes: ByteArray) {
        peerItems[path] = bytes
        incoming.emit(LinkItem(PEER, path, bytes))
    }

    companion object {
        const val PEER = "peer"
    }
}
