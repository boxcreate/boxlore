package cx.aswin.boxlore.core.wearlink

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The link over the system's wearable service. Every call that fails (no service on this device,
 * no watch paired, the other device away) answers "not available" instead of throwing, so a
 * phone without a watch pays nothing for the link.
 *
 * @param peerCapability the name the other app declares in its `wear.xml`.
 * @param localCapability this app's own name. It is declared in `wear.xml` too, and announced
 *   again here: a release build once lost the declaration to resource shrinking, and the other
 *   device then never saw this one.
 */
class GmsWearLinkTransport(
    context: Context,
    private val peerCapability: String,
    private val scope: CoroutineScope,
    private val localCapability: String? = null,
) : WearLinkTransport {
    private val data = Wearable.getDataClient(context.applicationContext)
    private val messages = Wearable.getMessageClient(context.applicationContext)
    private val capabilities = Wearable.getCapabilityClient(context.applicationContext)
    private val peer = MutableStateFlow<String?>(null)
    private val peerListener = CapabilityClient.OnCapabilityChangedListener { info -> peer.value = nearest(info.nodes) }

    override val peerReachable: StateFlow<Boolean> = peer.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)

    private val following = AtomicBoolean(false)

    /** Looks for the other app now, and from the first call on keeps following whether it is in reach. */
    override suspend fun refreshPeer() {
        if (following.compareAndSet(false, true)) {
            // Fails harmlessly when the declaration in wear.xml already announced it.
            localCapability?.let { own -> attempt { capabilities.addLocalCapability(own).await() } }
            attempt { capabilities.addListener(peerListener, peerCapability).await() }
        }
        attempt {
            peer.value = nearest(capabilities.getCapability(peerCapability, CapabilityClient.FILTER_REACHABLE).await().nodes)
        }
    }

    override suspend fun publish(path: String, bytes: ByteArray): Boolean =
        attempt {
            val request = PutDataRequest.create(path).setData(bytes).setUrgent()
            data.putDataItem(request).await()
            true
        } ?: false

    override suspend fun current(prefix: String): List<LinkItem> =
        attempt {
            val buffer = data.getDataItems(under(prefix), DataClient.FILTER_PREFIX).await()
            try {
                buffer.mapNotNull { item -> item.uri.path?.let { LinkItem(item.uri.host.orEmpty(), it, item.data) } }
            } finally {
                buffer.release()
            }
        }.orEmpty()

    override fun changes(prefix: String): Flow<LinkItem> =
        callbackFlow {
            val listener =
                DataClient.OnDataChangedListener { events ->
                    // The buffer is only valid inside this call, so the bytes are copied out here.
                    for (event in events) {
                        val uri = event.dataItem.uri
                        val path = uri.path ?: continue
                        val bytes = if (event.type == DataEvent.TYPE_DELETED) null else event.dataItem.data
                        trySend(LinkItem(uri.host.orEmpty(), path, bytes))
                    }
                }
            attempt { data.addListener(listener, under(prefix), DataClient.FILTER_PREFIX).await() }
            awaitClose { data.removeListener(listener) }
        }

    override suspend fun send(path: String, bytes: ByteArray): Boolean {
        val node = peer.value ?: return false
        val sent =
            attempt {
                messages.sendMessage(node, path, bytes).await()
                true
            } ?: false
        // A message that could not be handed over is the first sign the other device is gone,
        // often before the system says so; looking again keeps "in reach" honest.
        if (!sent) refreshPeer()
        return sent
    }

    override suspend fun request(path: String, bytes: ByteArray, timeoutMs: Long): ByteArray? {
        val node = peer.value ?: return null
        return attempt { withTimeoutOrNull(timeoutMs) { messages.sendRequest(node, path, bytes).await() } }
    }

    /** A watch in direct reach answers faster than one reached through the cloud. */
    private fun nearest(nodes: Set<Node>): String? = (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id

    private fun under(prefix: String): Uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(prefix).build()

    /** Runs a call to the system service; any failure but cancellation is "not available". */
    private suspend fun <T> attempt(call: suspend () -> T): T? =
        runCatching { call() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
}
