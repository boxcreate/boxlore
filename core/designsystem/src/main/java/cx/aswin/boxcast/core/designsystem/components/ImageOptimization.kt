package cx.aswin.boxcast.core.designsystem.components

import java.net.URLEncoder

/**
 * Optimizes an image URL by passing it through a resizing CDN (wsrv.nl).
 * This significantly improves loading times for lists and grids by preventing
 * the app from downloading 5MB uncompressed podcast cover arts.
 * 
 * @param width The desired maximum width in pixels.
 * @return The optimized URL, or the original if it's not an HTTP/HTTPS URL.
 */
fun String.optimizedImageUrl(width: Int = 400): String {
    if (this.isBlank() || (!this.startsWith("http://") && !this.startsWith("https://"))) {
        return this
    }
    
    // Dynamically scale width based on screen density and tablet/viewport configuration
    val scaledWidth = try {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val density = metrics.density
        val screenWidthPx = metrics.widthPixels
        val isLargeScreen = (screenWidthPx / density) >= 600f
        
        val scale = when {
            isLargeScreen -> 2.5f   // Table/large viewports need significantly higher resolution
            density >= 3.5f -> 1.8f // High-density QHD phones
            density >= 2.5f -> 1.4f // Medium-high density FHD phones
            else -> 1.1f
        }
        (width * scale).toInt().coerceIn(10, 2048)
    } catch (e: Exception) {
        width
    }
    
    // Some podcast servers block wsrv.nl, but for most standard URLs it works perfectly.
    return try {
        val encodedUrl = URLEncoder.encode(this, "UTF-8")
        "https://wsrv.nl/?url=$encodedUrl&w=$scaledWidth&q=85&output=webp"
    } catch (e: Exception) {
        this
    }
}

