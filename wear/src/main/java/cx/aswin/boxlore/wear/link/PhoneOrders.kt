package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.wearlink.WearLinkCodec
import cx.aswin.boxlore.core.wearlink.WearLinkCommand
import cx.aswin.boxlore.wear.data.WatchDownloads
import cx.aswin.boxlore.wear.data.WatchLibraryStore

/**
 * Sorts what the phone orders the watch to do: downloads go to the download store, which applies
 * the listener's storage limit as for any other download; everything else is for the player.
 */
internal class PhoneOrders(
    private val store: WatchLibraryStore,
    private val downloads: WatchDownloads,
    private val player: (ByteArray) -> Unit,
) {
    fun take(bytes: ByteArray) {
        val command = WearLinkCodec.decode<WearLinkCommand>(bytes) ?: return
        // The watch's own copy, when it has one: its ID is the one the download is filed under.
        val episode = command.episode?.let { store.state.value.merged(WatchLinkRules.watchEpisode(it)) }
        when (command.action) {
            WearLinkCommand.DOWNLOAD_EPISODE -> episode?.let(downloads::start)
            WearLinkCommand.REMOVE_DOWNLOAD -> episode?.let { downloads.remove(it.id) }
            else -> player(bytes)
        }
    }
}
