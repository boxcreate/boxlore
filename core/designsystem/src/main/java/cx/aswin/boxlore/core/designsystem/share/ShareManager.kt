package cx.aswin.boxlore.core.designsystem.share

import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.ShareLinkBuilder
import cx.aswin.boxlore.core.model.ShareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareManager {

    private val shareScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sharePodcast(context: Context, podcast: Podcast, target: ShareTarget = ShareTarget.MESSAGE) {
        trackShare(contentType = "podcast", podcastId = podcast.id, target = target)
        val shareUrl = ShareLinkBuilder.podcast(podcast.id)
        val creator = podcast.artist.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
        val shareText = "I think you'll like this podcast:\n\n" +
            "${podcast.title}$creator\n\nListen on boxlore:\n$shareUrl"

        shareWithCompositeImage(
            context = context,
            text = shareText,
            shareUrl = shareUrl,
            title = "Share Podcast",
            cardTitle = podcast.title,
            cardSubtitle = podcast.artist,
            imageUrl = podcast.imageUrl,
            fallbackImageUrl = podcast.fallbackImageUrl,
            target = target
        )
    }

    fun shareEpisode(
        context: Context,
        episode: Episode,
        podcastTitle: String,
        timestampMs: Long? = null,
        startMs: Long? = null,
        endMs: Long? = null,
        target: ShareTarget = ShareTarget.MESSAGE
    ) {
        trackShare(
            contentType = "episode",
            podcastId = episode.podcastId,
            episodeId = episode.id,
            target = target,
        )
        shareScope.launch {
            val link = episodeShareLink(episode, timestampMs, startMs, endMs)
            val shareUrl = link.url
            val timeContext = when {
                // A link to the show can't start at a time or play a clip.
                link.opensShow -> null
                startMs != null && endMs != null -> {
                    "Clip ${formatTime(startMs)}–${formatTime(endMs)}"
                }
                timestampMs != null && timestampMs > 0 -> {
                    "Start at ${formatTime(timestampMs)}"
                }
                else -> null
            }

            val contextLine = listOfNotNull(
                podcastTitle.takeIf { it.isNotBlank() },
                timeContext
            ).joinToString(" • ")
            val shareText = "This episode is worth a listen:\n\n" +
                "“${episode.title}”\n$contextLine\n\nListen on boxlore:\n$shareUrl"

            shareWithCompositeImage(
                context = context,
                text = shareText,
                shareUrl = shareUrl,
                title = "Share Episode",
                cardTitle = episode.title,
                cardSubtitle = podcastTitle,
                imageUrl = episode.imageUrl,
                fallbackImageUrl = episode.podcastImageUrl,
                target = target
            )
        }
    }

    /**
     * Copies [episode]'s share link ([episodeShareUrl]) and confirms with the sheet's "Link copied"
     * toast. Runs outside the share sheet, so closing the sheet doesn't cancel it.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    fun copyEpisodeLink(context: Context, episode: Episode, timestampMs: Long? = null) {
        val appContext = context.applicationContext
        shareScope.launch {
            val link = episodeShareUrl(episode, timestampMs)
            withContext(Dispatchers.Main) {
                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("boxlore link", link))
                android.widget.Toast.makeText(appContext, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Finds which ID an episode share link names. The app installs a source that reads the
     * episode's aliases and registers it with the API; without one, the episode's own fields decide.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    @Volatile
    var episodeShareTargets: EpisodeShareTargetSource = EpisodeShareTargetSource.FROM_EPISODE

    /**
     * An episode share link: [url], and whether it opens the episode's show ([opensShow]) because
     * the episode has no ID another phone could open.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    data class EpisodeShareLink(val url: String, val opensShow: Boolean)

    /**
     * The share link for [episode]: its shared ID with `&show=` for a Podcast Index show, or the
     * show's link when the episode has only a phone-only ID ([EpisodeShareTargetSource]). When the
     * installed source fails, the episode's own fields decide ([EpisodeShareTargetSource.FROM_EPISODE]).
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    suspend fun episodeShareLink(episode: Episode, timestampMs: Long? = null, startMs: Long? = null, endMs: Long? = null,): EpisodeShareLink {
        val target = try {
            episodeShareTargets.targetFor(episode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ShareManager", "Share link lookup failed", e)
            EpisodeShareTargetSource.FROM_EPISODE.targetFor(episode)
        }
        return EpisodeShareLink(ShareLinkBuilder.forTarget(target, timestampMs, startMs, endMs), opensShow = target is EpisodeShareTarget.Show)
    }

    /** [episodeShareLink]'s address alone. */
    suspend fun episodeShareUrl(episode: Episode, timestampMs: Long? = null, startMs: Long? = null, endMs: Long? = null,): String =
        episodeShareLink(episode, timestampMs, startMs, endMs).url

    private fun trackShare(contentType: String, podcastId: String?, episodeId: String? = null, target: ShareTarget,) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShareContent(
            contentType = contentType,
            podcastId = podcastId,
            episodeId = episodeId,
            channel =
            when (target) {
                ShareTarget.MESSAGE -> "message"
                ShareTarget.INSTAGRAM_STORY -> "instagram_story"
            },
        )
    }

    private fun shareWithCompositeImage(
        context: Context,
        text: String,
        shareUrl: String,
        title: String,
        cardTitle: String,
        cardSubtitle: String,
        imageUrl: String?,
        fallbackImageUrl: String? = null,
        target: ShareTarget
    ) {
        shareScope.launch {
            var sharedUri: android.net.Uri? = null
            try {
                    val originalBitmap = cx.aswin.boxlore.core.designsystem.components.loadArtworkBitmap(
                        context,
                        listOfNotNull(imageUrl, fallbackImageUrl),
                        width = 800,
                    ) ?: androidx.core.content.ContextCompat.getDrawable(
                        context,
                        cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_app_mark,
                    )?.toBitmap(800, 624)

                    if (originalBitmap != null) {
                        val shareCard = ShareCardRenderer.createShareCard(
                            context = context,
                            artwork = originalBitmap,
                            title = cardTitle,
                            subtitle = cardSubtitle,
                            target = target
                        )
                        clearExpiredShareCards(context)
                        val formatName = if (target == ShareTarget.INSTAGRAM_STORY) {
                            "story"
                        } else {
                            "message"
                        }
                        val cacheFile = java.io.File(
                            context.cacheDir,
                            "boxlore_share_${formatName}_${System.currentTimeMillis()}.png"
                        )
                        java.io.FileOutputStream(cacheFile).use { out ->
                            shareCard.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }

                        sharedUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cacheFile
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShareManager", "Error generating composite image", e)
                }

            withContext(Dispatchers.Main) {
                val openedInstagram = if (
                    target == ShareTarget.INSTAGRAM_STORY &&
                    sharedUri != null
                ) {
                    openInstagramStory(
                        context = context,
                        imageUri = sharedUri,
                        shareUrl = shareUrl
                    )
                } else {
                    false
                }

                if (!openedInstagram) {
                    if (target == ShareTarget.INSTAGRAM_STORY) {
                        android.widget.Toast.makeText(
                            context,
                            "Instagram isn't available — choose another app",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    openShareSheet(
                        context = context,
                        imageUri = sharedUri,
                        text = text,
                        chooserTitle = title
                    )
                }
            }
        }
    }

    /**
     * Shares a rendered card ([image], a story's share card, say) with [text] through the share sheet.
     * The PNG is written off the main thread to the cache, beside the other share cards, and cleared
     * with them a day later; if it can't be written, the text goes alone.
     */
    fun shareImage(context: Context, image: android.graphics.Bitmap, text: String, chooserTitle: String) {
        shareScope.launch {
            val uri = try {
                clearExpiredShareCards(context)
                val file = java.io.File(context.cacheDir, "boxlore_share_card_${System.currentTimeMillis()}.png")
                java.io.FileOutputStream(file).use { out -> image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                android.util.Log.e("ShareManager", "Error writing the share card", e)
                null
            }
            withContext(Dispatchers.Main) { openShareSheet(context, uri, text, chooserTitle) }
        }
    }

    private fun openShareSheet(context: Context, imageUri: android.net.Uri?, text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (imageUri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                clipData = android.content.ClipData.newRawUri("boxlore share card", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, chooserTitle)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun openInstagramStory(context: Context, imageUri: android.net.Uri, shareUrl: String): Boolean {
        val instagramPackage = "com.instagram.android"
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(imageUri, "image/png")
            setPackage(instagramPackage)
            clipData = android.content.ClipData.newRawUri("boxlore Story", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false

        context.grantUriPermission(
            instagramPackage,
            imageUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("boxlore link", shareUrl)
        )
        android.widget.Toast.makeText(
            context,
            "Link copied — add it with Instagram's Link sticker",
            android.widget.Toast.LENGTH_LONG
        ).show()
        context.startActivity(intent)
        return true
    }

    private fun clearExpiredShareCards(context: Context) {
        val expiry = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("boxlore_share_") && it.lastModified() < expiry }
            ?.forEach { it.delete() }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
