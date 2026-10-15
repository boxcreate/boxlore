package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Echo never scrolls. Sizes come from the height left between the top bar and the footer. Echo
 * is always centred with its bubble underneath, like a messaging profile; shorter screens get a
 * smaller Echo and shorter cards (never under the 48dp touch target). Larger text drops a tier, and
 * an open keyboard folds Echo away. Long questions and labels shrink their type to fit instead.
 */
internal enum class EchoLayoutTier { Spacious, Roomy, Regular, Compact }

internal data class EchoLayoutMetrics(
    val tier: EchoLayoutTier,
    val showEcho: Boolean,
    val avatarWidth: Dp,
    val bubbleLines: Int,
    /** Later turns' rows: badge beside a label of up to two lines. */
    val rowHeight: Dp,
    /** Opening topic tiles; stacked tiles put the badge on its own line above the one-line label. */
    val tileHeight: Dp,
    val tileStacked: Boolean,
    val badgeSize: Dp,
    val questionMax: TextUnit,
    val questionMin: TextUnit,
    val gap: Dp,
    /** Show deck artwork; the deck's buttons use [rowHeight]. */
    val artworkSize: Dp,
)

internal object EchoLayout {
    fun tier(bodyHeight: Dp, fontScale: Float): EchoLayoutTier {
        val byHeight = when {
            bodyHeight >= 680.dp -> EchoLayoutTier.Spacious
            bodyHeight >= 600.dp -> EchoLayoutTier.Roomy
            bodyHeight >= 480.dp -> EchoLayoutTier.Regular
            else -> EchoLayoutTier.Compact
        }
        return if (fontScale >= 1.3f) EchoLayoutTier.entries[(byHeight.ordinal + 1).coerceAtMost(EchoLayoutTier.Compact.ordinal)] else byHeight
    }

    fun metrics(bodyHeight: Dp, fontScale: Float, keyboardOpen: Boolean): EchoLayoutMetrics = when (tier(bodyHeight, fontScale)) {
        EchoLayoutTier.Spacious -> EchoLayoutMetrics(EchoLayoutTier.Spacious, !keyboardOpen, 168.dp, 3, 72.dp, 96.dp, true, 48.dp, 30.sp, 18.sp, 22.dp, 128.dp)
        EchoLayoutTier.Roomy -> EchoLayoutMetrics(EchoLayoutTier.Roomy, !keyboardOpen, 132.dp, 2, 64.dp, 80.dp, true, 42.dp, 28.sp, 18.sp, 18.dp, 116.dp)
        EchoLayoutTier.Regular -> EchoLayoutMetrics(EchoLayoutTier.Regular, !keyboardOpen, 112.dp, 2, 58.dp, 58.dp, false, 36.dp, 26.sp, 17.sp, 14.dp, 100.dp)
        // On the tightest screens (tiny phones, very large text) the question outranks Echo.
        EchoLayoutTier.Compact -> EchoLayoutMetrics(
            EchoLayoutTier.Compact,
            !keyboardOpen && bodyHeight >= TightHeight && fontScale < TightFontScale,
            84.dp, 2, 50.dp, 50.dp, false, 32.dp, 22.sp, 15.sp, 8.dp, 80.dp,
        )
    }

    /** Height the answer slot needs before growing: four rows, their gaps and Something else. */
    fun answersMinHeight(rowHeight: Dp): Dp = rowHeight * AnswerRows + GroupGap * (AnswerRows - 1) + SomethingElseHeight

    const val AnswerRows = 4
    val GroupGap = 3.dp
    val SomethingElseHeight = 52.dp

    /** Cards grow into this share of the spare height, up to [MaxGrowPerRow] each. */
    const val GrowShare = 0.5f
    val MaxGrowPerRow = 16.dp

    /**
     * Spare height, after Echo, the question and the answers, is shared out so the answers sit just
     * below the middle: a little above Echo, most between the question and the answers, the rest
     * under the answers. On a tight screen there is none and everything packs together.
     */
    const val SpareAboveShare = 0.12f
    const val SpareBetweenShare = 0.55f

    private val TightHeight = 400.dp
    private const val TightFontScale = 1.6f
}
