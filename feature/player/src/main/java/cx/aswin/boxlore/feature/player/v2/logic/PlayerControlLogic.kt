package cx.aswin.boxlore.feature.player.v2.logic

import androidx.annotation.StringRes
import cx.aswin.boxlore.feature.player.R

internal fun targetControlWeight(
    index: Int,
    activeIndex: Int?,
    baseWeights: List<Float>
): Float {
    if (activeIndex == null) return baseWeights[index]
    val extra = baseWeights[activeIndex] * 0.14f
    if (index == activeIndex) return baseWeights[index] + extra
    val otherTotal = baseWeights.sum() - baseWeights[activeIndex]
    return baseWeights[index] - extra * (baseWeights[index] / otherTotal)
}

@StringRes
internal fun downloadLabel(isDownloaded: Boolean, isDownloading: Boolean): Int = when {
    isDownloading -> R.string.player_downloading
    isDownloaded -> R.string.player_downloaded
    else -> R.string.player_download
}

internal fun formatSpeedLabel(speed: Float): String = cx.aswin.boxlore.core.playback.ShowPlaybackPolicy.speedLabel(speed)
