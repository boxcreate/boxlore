package cx.aswin.boxlore.core.designsystem.components

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * Warms the image cache for artwork that is about to appear. It issues the same request
 * [OptimizedImage] makes on phones (first route, size and memory key), so the card finds the
 * image in memory instead of starting its download when it is shown.
 */
object ArtworkPrefetch {
    fun prefetch(context: Context, urls: List<String?>, proxyWidth: Int) {
        val width = imageTargetPixels(proxyWidth)
        val loader = context.imageLoader
        prefetchRoutes(urls, width).forEach { route ->
            runCatching {
                loader.enqueue(
                    ImageRequest.Builder(context).data(route).size(width, width)
                        .memoryCacheKey(memoryKey(route, width)).allowHardware(true).build(),
                )
            }
        }
    }

    /** The first route [OptimizedImage] would try for each distinct artwork. */
    internal fun prefetchRoutes(
        urls: List<String?>,
        width: Int,
        policy: ArtworkLoadPolicy = ArtworkLoadPolicy.Shared,
        optimize: (String, Int) -> String = { url, size -> url.optimizedImageUrl(size) },
    ): List<String> = urls.filterNotNull().filter(String::isNotBlank).distinct()
        .mapNotNull { url -> ArtworkAttempts(artworkUrls(url), width, policy, optimize).next()?.url }
        .distinct()

    /** Must match the key [OptimizedImage] uses for a loaded attempt. */
    internal fun memoryKey(route: String, width: Int): String = "$route-w$width"
}
