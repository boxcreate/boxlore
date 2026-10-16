package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.artworkUrls
import java.io.IOException
import kotlinx.coroutines.delay

/** Ordered artwork fallback with bounded recovery; existing URL/size cache keys are retained. */
@Composable
@Suppress("LongParameterList") // Retain source compatibility while adding an optional fallback chain.
fun OptimizedImage(
    url: String?,
    proxyWidth: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    errorContent: (@Composable BoxScope.() -> Unit)? = null,
    fallbackUrls: List<String?> = emptyList(),
    adaptToLayout: Boolean = true,
    showLoadingPlaceholder: Boolean = true,
) {
    val sources = remember(url, fallbackUrls) { artworkUrls(url, *fallbackUrls.toTypedArray()) }
    val sizing = artworkLayoutSizing(proxyWidth, modifier, adaptToLayout)
    val width = sizing.width
    val recoveryGeneration = LocalArtworkRecoveryGeneration.current
    var cachedRecovery by remember(sources, width) { mutableStateOf(0) }
    val attempts = remember(sources, width, cachedRecovery) { ArtworkAttempts(sources, width) }
    var current by remember(attempts) { mutableStateOf(attempts.next()) }
    var seenGeneration by remember(sources, width) { mutableStateOf(recoveryGeneration) }
    var cacheRecovered by remember(sources, width) { mutableStateOf(false) }
    LaunchedEffect(recoveryGeneration) {
        if (seenGeneration != recoveryGeneration && current == null) cachedRecovery += 1
        seenGeneration = recoveryGeneration
    }
    val revision by ArtworkLoadPolicy.Shared.revision.collectAsState()
    LaunchedEffect(revision, current) {
        // Another page may have just populated Coil's cache. Recover this failed tile once.
        if (current == null &&
            !cacheRecovered &&
            sources.any {
            ArtworkLoadPolicy.Shared.preferredRoute(it) != null
        }
        ) {
            cacheRecovered = true
            cachedRecovery += 1
        }
    }
    Box(sizing.modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        OptimizedImageLoadingPlaceholder(showLoadingPlaceholder)
        val attempt = current
        if (attempt == null) {
            OptimizedImageFailureContent(errorContent)
        } else if (sizing.ready) {
            key(attempt) {
            ArtworkAttemptImage(attempt, width, contentScale, colorFilter) { error ->
                if (error == null) {
                    ArtworkLoadPolicy.Shared.recordSuccess(attempt.url, attempt.source, width)
                } else {
                    if (error is java.util.concurrent.CancellationException) return@ArtworkAttemptImage
                    if (attempt.url != attempt.source && !attempt.recovery) trackProxyFallback(attempt.source, width)
                    attempts.failed(attempt, error)
                    val next = attempts.next()
                    if (next?.recovery == true) delay(250)
                    current = next
                }
            }
            }
        }
    }
}

@Composable
private fun ArtworkAttemptImage(
    attempt: ArtworkAttempt,
    width: Int,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    onResult: suspend (Throwable?) -> Unit,
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = remember(attempt, width) {
            ImageRequest.Builder(context).data(attempt.url).size(width, width)
                .memoryCacheKey(ArtworkPrefetch.memoryKey(attempt.url, width)).allowHardware(true).crossfade(150).build()
        },
    )
    val state = painter.state
    LaunchedEffect(attempt, state) {
        when (state) {
            is AsyncImagePainter.State.Success -> {
                OnScreenArtwork.record(attempt.source, width, state.result.memoryCacheKey)
                onResult(null)
            }
            is AsyncImagePainter.State.Error -> onResult(state.result.throwable)
            else -> {
                delay(8_000)
                onResult(IOException("Artwork request timed out"))
            }
        }
    }
    Image(painter, null, Modifier.fillMaxSize(), contentScale = contentScale, colorFilter = colorFilter)
}

@Composable
private fun BoxScope.OptimizedImageFailureContent(errorContent: (@Composable BoxScope.() -> Unit)?) {
    if (errorContent != null) errorContent() else AnimatedShapesFallback()
}

@Composable
private fun OptimizedImageLoadingPlaceholder(show: Boolean) {
    // Decorative layers (a blurred backdrop) skip the logo placeholder, so it never shows through.
    if (!show) return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        ArtworkFallbackMark()
    }
}

private fun trackProxyFallback(url: String, proxyWidth: Int) {
    if (kotlin.random.Random.nextInt(10) == 0) {
        val imageHost = try {
            android.net.Uri.parse(url).host ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackProxyFallbackTriggered(
            imageHost = imageHost,
            proxyWidth = proxyWidth,
            sampleMultiplier = 10,
        )
    }
}

private data class ArtworkLayoutSizing(val width: Int, val modifier: Modifier, val ready: Boolean)

@Composable
private fun artworkLayoutSizing(proxyWidth: Int, modifier: Modifier, adaptToLayout: Boolean): ArtworkLayoutSizing {
    val tablet = adaptToLayout && LocalBoxloreWindowLayout.current.usesTabletLayout
    // Kept across phone/tablet changes so returning to a tablet layout never waits for another resize.
    var measuredPixels by remember { mutableIntStateOf(0) }
    var hasRoom by remember { mutableStateOf(false) }
    val width = if (tablet) layoutImageTargetPixels(proxyWidth, measuredPixels) else imageTargetPixels(proxyWidth)
    val sizedModifier = if (tablet) {
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            measuredPixels = maxOf(measuredPixels, placeable.width, placeable.height)
            // A hidden 1px pane waits; artwork sized by its own content loads at the requested size.
            if (constraints.maxWidth > 1 && constraints.maxHeight > 1) hasRoom = true
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    } else {
        modifier
    }
    return ArtworkLayoutSizing(width, sizedModifier, !tablet || measuredPixels > 1 || hasRoom)
}
