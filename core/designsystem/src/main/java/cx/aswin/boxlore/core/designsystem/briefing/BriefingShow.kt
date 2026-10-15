package cx.aswin.boxlore.core.designsystem.briefing

import android.content.Context
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.Podcast

/**
 * The Brief as a show in the player, notifications, history and Android Auto. Home and the
 * Brief screen share it so the show name always matches the masthead.
 */
fun briefingShowPodcast(context: Context, region: String): Podcast = Podcast(
    id = "briefing_$region",
    title = context.getString(R.string.briefing_brand_name),
    artist = context.getString(R.string.briefing_show_author),
    imageUrl = briefingPlayerArtworkUri(context.packageName, region),
)
