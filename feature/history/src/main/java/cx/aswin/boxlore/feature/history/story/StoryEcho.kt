package cx.aswin.boxlore.feature.history.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.EchoAccents
import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle
import cx.aswin.boxlore.core.designsystem.echo.EchoReach
import cx.aswin.boxlore.core.designsystem.echo.EchoSaccadeMillis

/**
 * Where Echo stands on a card, in the 400 × 890 story frame: the box the boards put its picture in
 * (left [x], top [y], [height] tall). [centerX], when set, centres Echo on it instead (the time card's
 * Echo stands on the biggest day's bar).
 */
@Immutable
internal data class StoryEchoBox(val x: Float, val y: Float, val height: Float, val centerX: Float? = null)

/** Echo's square frame on the story frame: left, top and side, in frame dp. */
@Immutable
internal data class StoryEchoSquare(val left: Float, val top: Float, val side: Float)

/** How Echo came to this card: hopping from [from] (null: it starts here), its [playKey] (new each time a card opens), and whether the story is held. */
@Immutable
internal data class StoryEchoMoment(val from: StoryEchoBox?, val playKey: Any, val paused: Boolean)

/** Echo's outline across the frame: its [left] and [right] edges, in frame dp. */
@Immutable
internal data class StoryEchoBounds(val left: Float, val right: Float) {
    val center: Float get() = (left + right) / 2f
}

/** Echo on one card: what it plays (an act, then its rest pose with [idle] eyes), where, and in which colours. */
@Immutable
internal data class StoryEchoCast(val sequence: String, val box: StoryEchoBox, val accents: EchoAccents, val idle: EchoIdle)

internal object StoryEchoStage {
    const val HOP = "story-hop"
    private const val HELLO = "story-hello"
    private const val WAVE_CLIP = "wave"
    private const val HOP_CLIP = "hop"

    /** The boards draw a pose's picture cropped to Echo plus 3% of its height around it. */
    private const val POSE_PAD = 0.03f

    /**
     * Echo's square frame so it fills [box] as the boards place it. Rest poses (and their acts, which
     * share the frame) fill the box with their outline plus the boards' 3% margin; the hello wave and
     * the hop fill it with everything their clip reaches, as the boards' clip pictures do.
     */
    fun square(spec: EchoCharacterSpec, sequence: String, box: StoryEchoBox): StoryEchoSquare? {
        val clipReach = when (sequence) {
            HELLO -> spec.clips[WAVE_CLIP]?.reach
            HOP -> spec.clips[HOP_CLIP]?.reach
            else -> null
        }
        if (clipReach != null) return fill(clipReach, box, pad = 0f)
        return pose(spec.reaches["$sequence-rest"] ?: return null, box)
    }

    /** Echo's square frame so a still whose outline is [reach] fills [box] with the boards' 3% margin. */
    fun pose(reach: EchoReach, box: StoryEchoBox): StoryEchoSquare = fill(reach, box, POSE_PAD * (reach.bottom - reach.top))

    private fun fill(reach: EchoReach, box: StoryEchoBox, pad: Float): StoryEchoSquare {
        val side = box.height / (reach.bottom - reach.top + 2 * pad)
        val top = box.y - (reach.top - pad) * side
        val left = box.centerX?.let { it - (reach.left + reach.right) / 2f * side } ?: (box.x - (reach.left - pad) * side)
        return StoryEchoSquare(left, top, side)
    }

    /** Echo's outline at rest on its card (the act's rest pose), left to right, in frame dp; null until the manifest loads. */
    fun bounds(spec: EchoCharacterSpec, cast: StoryEchoCast): StoryEchoBounds? {
        val square = square(spec, cast.sequence, cast.box) ?: return null
        val reach = spec.reaches["${cast.sequence}-rest"] ?: spec.clips[WAVE_CLIP]?.reach ?: return null
        return StoryEchoBounds(square.left + reach.left * square.side, square.left + reach.right * square.side)
    }

    fun hello(palette: StoryPalette) = StoryEchoCast(HELLO, StoryEchoBox(6f, 560f, 300f), palette.echoOwn, EchoIdles.Hello)

    fun time(palette: StoryPalette, barCenterX: Float) =
        StoryEchoCast(EchoOutfit.HOURGLASS.asset, StoryEchoBox(244f, 394f, 210f, centerX = barCenterX), palette.echoOnVivid, EchoIdles.Hourglass)

    fun topShow(palette: StoryPalette, outfit: EchoOutfit) = StoryEchoCast(
        outfit.asset,
        StoryEchoBox(192f, 590f, 280f),
        palette.echoOnGreen,
        when (outfit) {
            EchoOutfit.TRUE_CRIME -> EchoIdles.Detective
            EchoOutfit.HELLO -> EchoIdles.Hello
            else -> EchoIdles.Genre
        },
    )

    fun type(palette: StoryPalette, outfit: EchoOutfit) =
        StoryEchoCast(outfit.asset, StoryEchoBox(14f, 600f, 270f), palette.echoOwn, if (outfit == EchoOutfit.NIGHT) EchoIdles.Night else EchoIdles.Genre)

    fun records(palette: StoryPalette) = StoryEchoCast(EchoOutfit.TROPHY.asset, StoryEchoBox(224f, 562f, 230f), palette.echoOwn, EchoIdles.Trophy)

    fun share(palette: StoryPalette) = StoryEchoCast(EchoOutfit.CELEBRATE.asset, StoryEchoBox(120f, 300f, 196f), palette.echoOwn, EchoIdles.Celebrate)
}

/**
 * Echo on the story frame. Arriving from another card ([from] is that card's box) it hops there over
 * 0.7s with a little overshoot, then after a second plays the card's act and rests with live eyes;
 * on the first card it acts straight away. [elapsed] is the card's clock.
 */
@Composable
internal fun StoryEcho(
    spec: EchoCharacterSpec,
    cast: StoryEchoCast,
    moment: StoryEchoMoment,
    clock: StoryClock,
    prefetch: List<String>,
    modifier: Modifier = Modifier,
) {
    val from = moment.from
    // Read through derivedStateOf: Echo changes once, when the hop ends, not on every frame of the card's clock.
    val hopping by remember(from, clock) { derivedStateOf { from != null && !clock.motionOff && clock.elapsed < StoryMotion.HOP_PHASE_MS } }
    val sequence = if (hopping) StoryEchoStage.HOP else cast.sequence
    val target = StoryEchoStage.square(spec, sequence, cast.box) ?: return
    val start = from?.let { StoryEchoStage.square(spec, sequence, it) }
    Box(
        modifier
            .offset(target.left.dp, target.top.dp)
            // Echo's frame is often bigger than the story frame's width (its clips reach far): lay it out whole.
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(target.side.dp)
            .graphicsLayer {
                if (start == null || !hopping) return@graphicsLayer
                val p = clock.at(0, StoryMotion.HOP_MS, StoryMotion.Hop)
                val side = start.side + (target.side - start.side) * p
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = (start.left + (target.left - start.left) * p - target.left).dp.toPx()
                translationY = (start.top + (target.top - start.top) * p - target.top).dp.toPx()
                scaleX = side / target.side
                scaleY = side / target.side
            },
    ) {
        EchoCharacter(
            sequence = sequence,
            modifier = Modifier.fillMaxSize(),
            playbackKey = moment.playKey to hopping,
            paused = moment.paused,
            idle = cast.idle,
            prefetch = prefetch,
            accents = cast.accents,
        )
    }
}

/**
 * Echo's resting eyes on each card, as the boards' idle pictures play them: a beat per picture frame
 * (its face, where it looks, how long), a quick blink where a frame shows one.
 */
internal object EchoIdles {
    private val BLINK = 120 to blink()

    val Hello = idle(1400 to happy(0, 0), BLINK, 1500 to look(1, 0), 1200 to look(0, 0), BLINK, 1600 to look(-1, 0))
    val Hourglass = idle(1200 to look(0, 0), BLINK, 1600 to look(0, 1), 1000 to look(0, 0), 1600 to happy(0, 0), BLINK)
    val Detective = idle(1400 to look(-1, 0), BLINK, 1600 to look(-1, 0), 1000 to look(0, 0), 1400 to happy(0, 0), BLINK)
    val Night = idle(2200 to blink(), 900 to look(0, 0), 150 to blink(), 700 to look(0, 0), 2400 to blink(), 1200 to happy(0, 0))
    val Trophy = idle(1600 to happy(0, 0), 1200 to look(-1, 0), BLINK, 1600 to happy(0, 0), 1200 to look(1, 0), BLINK)
    val Celebrate = idle(2000 to happy(0, 0), BLINK, 1400 to look(1, 0), 1800 to happy(0, 0), 1200 to look(-1, 0))
    val Genre = idle(1400 to look(0, 0), BLINK, 1500 to look(-1, 0), 1000 to look(0, 0), 1600 to happy(0, 0), BLINK)

    private fun look(x: Int, y: Int) = EchoFace.Idle to EchoGaze(x.toFloat(), y.toFloat())

    private fun happy(x: Int, y: Int) = EchoFace.Happy to EchoGaze(x.toFloat(), y.toFloat())

    private fun blink() = EchoFace.Blink to null

    /** Picture frames as beats; a blink keeps the gaze before it. The eyes' 180ms dart counts toward each hold. */
    private fun idle(vararg frames: Pair<Int, Pair<EchoFace, EchoGaze?>>): EchoIdle {
        var gaze = EchoGaze(0f, 0f)
        val beats = frames.map { (millis, look) ->
            gaze = look.second ?: gaze
            EchoBeat(gaze, (millis - EchoSaccadeMillis).coerceAtLeast(0L), look.first)
        }
        return EchoIdle(intro = emptyList(), loop = beats)
    }
}
