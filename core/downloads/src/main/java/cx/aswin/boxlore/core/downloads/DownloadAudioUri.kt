package cx.aswin.boxlore.core.downloads

import android.net.Uri
import cx.aswin.boxlore.core.model.Episode

/** Gson can leave [Episode.audioUrl] null despite its type; such episodes cannot be downloaded. */
internal object DownloadAudioUri {
    fun parseOrNull(episode: Episode): Uri? {
        val url: String? = episode.audioUrl
        return url?.trim()?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
    }
}
