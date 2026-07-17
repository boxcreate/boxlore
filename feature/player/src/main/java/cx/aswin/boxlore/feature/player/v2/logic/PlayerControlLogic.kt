package cx.aswin.boxlore.feature.player.v2.logic

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

internal fun downloadLabel(isDownloaded: Boolean, isDownloading: Boolean): String = when {
    isDownloading -> "Saving"
    isDownloaded -> "Saved"
    else -> "Download"
}

internal fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"
