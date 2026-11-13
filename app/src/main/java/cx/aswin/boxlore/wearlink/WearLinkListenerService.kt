package cx.aswin.boxlore.wearlink

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.core.wearlink.LinkItem
import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import kotlinx.coroutines.launch

/**
 * Where the system hands the phone what boxlore on a watch published, sent or asked, whether or
 * not the phone app is open. It only passes things on to the app's one [cx.aswin.boxlore.core.wearlink.PhoneWearLink].
 */
class WearLinkListenerService : WearableListenerService() {
    private val container get() = (application as BoxLoreApplication).container

    override fun onDataChanged(events: DataEventBuffer) {
        // The buffer is only valid inside this call, so the bytes are copied out here.
        val items =
            events.mapNotNull { event ->
                val uri = event.dataItem.uri
                val bytes = if (event.type == DataEvent.TYPE_DELETED) null else event.dataItem.data
                uri.path?.let { LinkItem(uri.host.orEmpty(), it, bytes) }
            }
        container.wearLinkScope.launch { items.forEach { container.phoneWearLink.onItem(it) } }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearLinkProtocol.PATH_COMMAND) return
        val bytes = event.data
        container.wearLinkScope.launch { container.phoneWearLink.onCommand(bytes) }
    }

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        val answer = TaskCompletionSource<ByteArray>()
        container.wearLinkScope.launch {
            val bytes = runCatching { container.phoneWearLink.onRequest(path, request) }.getOrNull()
            answer.setResult(bytes ?: ByteArray(0))
        }
        return answer.task
    }
}
