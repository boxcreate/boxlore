package cx.aswin.boxlore.wear

import android.app.Application
import cx.aswin.boxlore.wear.data.WatchLibrary
import cx.aswin.boxlore.wear.data.WatchPlayback
import cx.aswin.boxlore.wear.data.WatchSettings

/** The watch app's whole object graph. Built once by [WatchApplication]; nothing else creates one. */
class WatchContainer(
    val library: WatchLibrary,
    val playback: WatchPlayback,
    val settings: WatchSettings,
    /** A player order the phone sent, as it arrived. A watch without a phone link ignores it. */
    val onPhoneCommand: (ByteArray) -> Unit = {},
)

class WatchApplication : Application() {
    lateinit var container: WatchContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = WatchContainerFactory.create(this)
    }
}
