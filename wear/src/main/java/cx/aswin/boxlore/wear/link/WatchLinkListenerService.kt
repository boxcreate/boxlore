package cx.aswin.boxlore.wear.link

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import cx.aswin.boxlore.wear.WatchApplication

/**
 * Receives the phone's player orders for the watch: pause, or play this episode here. The system
 * starts it for a message, so an order arrives even when the watch app is not open.
 */
class WatchLinkListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearLinkProtocol.PATH_WATCH_COMMAND) return
        (application as? WatchApplication)?.container?.onPhoneCommand?.invoke(event.data)
    }
}
