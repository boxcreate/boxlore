package cx.aswin.boxlore.feature.onboarding

/** One readable phrase at a time, with an icon-led entrance and a clean handoff. */
internal object WelcomeWordLoopMotion {
    const val TermCount = 7
    const val LoreIndex = 6
    const val StartMillis = 900L
    const val EnterMillis = 520L
    const val ExitMillis = 180L

    private const val TermMillis = 3300L
    private const val LoreMillis = 4300L
    private const val CycleMillis = TermMillis * LoreIndex + LoreMillis
    private const val WordDelayMillis = 70L
    private const val WordEnteredMillis = 430L
    private const val IconOpaqueMillis = 240L

    data class Cue(
        val index: Int,
        val localMillis: Long,
        val visible: Boolean,
    )

    data class Frame(
        val wordAlpha: Float,
        val wordTranslationFraction: Float,
        val iconAlpha: Float,
        val iconScale: Float,
        val iconRotationDegrees: Float,
    )

    fun cue(
        elapsedMillis: Long,
        animationsDisabled: Boolean = false,
    ): Cue {
        if (animationsDisabled) return Cue(LoreIndex, EnterMillis, visible = true)
        if (elapsedMillis < StartMillis) return Cue(0, 0L, visible = false)

        // Subtract only after the lower-bound check; no addition to a long-running clock.
        val cycleTime = (elapsedMillis - StartMillis) % CycleMillis
        val index = (cycleTime / TermMillis).toInt().coerceAtMost(LoreIndex)
        return Cue(index, cycleTime - index * TermMillis, visible = true)
    }

    fun frame(cue: Cue): Frame {
        if (!cue.visible) return Frame(0f, 0.45f, 0f, 0.55f, -22f)

        val duration = if (cue.index == LoreIndex) LoreMillis else TermMillis
        val local = cue.localMillis.coerceIn(0L, duration)
        val exitStart = duration - ExitMillis
        if (local > exitStart) {
            val exit = smoothstep((local - exitStart).toFloat() / ExitMillis)
            return Frame(
                wordAlpha = 1f - exit,
                wordTranslationFraction = -0.25f * exit,
                iconAlpha = 1f - exit,
                iconScale = 1f - 0.12f * exit,
                iconRotationDegrees = 8f * exit,
            )
        }

        val wordProgress =
            smoothstep((local - WordDelayMillis).toFloat() / (WordEnteredMillis - WordDelayMillis))
        val iconProgress = easeOutBack(local.toFloat() / EnterMillis)
        return Frame(
            wordAlpha = wordProgress,
            wordTranslationFraction = 0.45f * (1f - wordProgress),
            iconAlpha = smoothstep(local.toFloat() / IconOpaqueMillis),
            iconScale = 0.55f + 0.45f * iconProgress,
            iconRotationDegrees = 22f * (iconProgress - 1f),
        )
    }

    private fun smoothstep(progress: Float): Float {
        val fraction = progress.coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }

    private fun easeOutBack(progress: Float): Float {
        val fraction = progress.coerceIn(0f, 1f) - 1f
        return 1f + 2.5f * fraction * fraction * fraction + 1.5f * fraction * fraction
    }
}
