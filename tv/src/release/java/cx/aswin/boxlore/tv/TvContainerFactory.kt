package cx.aswin.boxlore.tv

import android.content.Context

/** Release builds read the listener's real library and play real audio; they never show made-up content. */
internal object TvContainerFactory {
    fun create(context: Context): TvContainer = LiveTvContainer.create(context)
}
