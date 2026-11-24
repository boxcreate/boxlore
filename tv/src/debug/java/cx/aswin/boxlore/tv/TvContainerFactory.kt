package cx.aswin.boxlore.tv

import android.content.Context
import cx.aswin.boxlore.tv.preview.PreviewTvAccount
import cx.aswin.boxlore.tv.preview.PreviewTvLibrary
import cx.aswin.boxlore.tv.preview.PreviewTvPlayback

/**
 * Debug and benchmark builds run on sample shows so every screen can be looked at on a TV without
 * an account or a network. Building with `-Ptv.sample=false` runs the real library and audio.
 */
internal object TvContainerFactory {
    fun create(context: Context): TvContainer {
        if (!BuildConfig.TV_SAMPLE_DATA) return LiveTvContainer.create(context)
        val library = PreviewTvLibrary()
        return TvContainer(library, PreviewTvPlayback(library), PreviewTvAccount())
    }
}
