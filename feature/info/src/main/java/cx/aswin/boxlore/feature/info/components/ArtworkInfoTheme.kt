package cx.aswin.boxlore.feature.info.components

import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxlore.core.designsystem.theme.ArtworkColorCandidate
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationBaseColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationEntryId
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.artworkLoadingColorScheme
import cx.aswin.boxlore.core.designsystem.theme.artworkSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.core.designsystem.theme.rememberAnimatedArtworkColors
import cx.aswin.boxlore.core.designsystem.theme.selectArtworkSeed
import cx.aswin.boxlore.feature.info.logic.loadArtworkSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ArtworkResult(val urls: List<String>, val seed: Int?, val complete: Boolean)

private val artworkSeeds = LruCache<String, Int>(80)

/** The page and navbar share the same animated palette; playback and saved app colors stay independent. */
@Composable
internal fun ArtworkInfoTheme(imageUrls: List<String?>, isLoading: Boolean = false, content: @Composable () -> Unit) {
    val navigationEntryId = LocalArtworkNavigationEntryId.current
    val enabled = LocalArtworkColorsEnabled.current
    val urls = imageUrls.filterNotNull().map(String::trim).filter(String::isNotEmpty).distinct()
    val result = rememberArtworkResult(urls, enabled)
    val seed = result.seed.takeIf { result.urls == urls && enabled }
    val base = LocalArtworkNavigationBaseColors.current ?: MaterialTheme.colorScheme
    val dark = LocalEffectiveDarkTheme.current
    val style = artworkSurfaceStyle(LocalSurfaceStyle.current)
    val navigationColors = LocalArtworkNavigationColors.current
    val inherited = navigationColors?.initialColorsFor(navigationEntryId)
    val handoffKey = navigationEntryId to navigationColors?.handoffKeyFor(navigationEntryId)
    val initial = remember(inherited, base, enabled, dark, style) {
        if (enabled) artworkLoadingColorScheme(base, inherited, dark, style) else base
    }
    val waiting = isLoading || result.urls != urls || !result.complete
    val target = remember(seed, enabled, dark, style, base, initial, waiting) {
        when {
            enabled && seed != null -> generatePersonalColorScheme(CustomThemeSeeds(Color(seed)), dark, style)
            enabled && waiting -> initial
            else -> base
        }
    }
    val colors = rememberAnimatedArtworkColors(target, initialColors = initial, animationKey = handoffKey)
    SideEffect {
        if (navigationEntryId != null) navigationColors?.update(navigationEntryId, colors)
    }
    DisposableEffect(navigationColors, navigationEntryId) {
        onDispose { if (navigationEntryId != null) navigationColors?.update(navigationEntryId, null) }
    }
    CompositionLocalProvider(LocalArtworkLoaderColors provides colors) {
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
                // Surface propagates its minimum size. Keep that on one page container,
                // rather than forcing sibling header overlays to fill the whole viewport.
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun rememberArtworkResult(urls: List<String>, enabled: Boolean): ArtworkResult {
    val context = LocalContext.current
    val cached = if (enabled) urls.firstOrNull()?.let { artworkSeeds[it] } else null
    val initial = ArtworkResult(urls, cached, cached != null || urls.isEmpty() || !enabled)
    val result by produceState(initialValue = initial, urls, enabled, context) {
        value = initial
        if (enabled) {
            val seed = loadArtworkSeed(urls, cached = { artworkSeeds[it] }, fetch = { fetchArtworkSeed(context, it) })
            value = ArtworkResult(urls, seed, complete = true)
        }
    }
    return result
}

private suspend fun fetchArtworkSeed(context: android.content.Context, url: String): Int? {
    val proxy = url.optimizedImageUrl(128)
    suspend fun request(data: String) = context.imageLoader.execute(ImageRequest.Builder(context).data(data).size(128).allowHardware(false).build())
    val optimized = request(proxy)
    val response = if (optimized !is SuccessResult && proxy != url) request(url) else optimized
    if (response !is SuccessResult) return null
    return withContext(Dispatchers.Default) {
        val bitmap = response.drawable.toBitmap(128, 128)
        val swatches = Palette.from(bitmap).maximumColorCount(16).generate().swatches
        selectArtworkSeed(swatches.map { ArtworkColorCandidate(it.rgb, it.population) })?.also { artworkSeeds.put(url, it) }
    }
}
