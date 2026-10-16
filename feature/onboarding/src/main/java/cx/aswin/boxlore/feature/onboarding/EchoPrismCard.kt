package cx.aswin.boxlore.feature.onboarding

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.SpaceMonoFamily
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlin.math.PI
import kotlin.math.sin

/**
 * The show card: a Prism collector card. A rainbow edge turns slowly round a deep-space ground,
 * a starburst turns behind the tilted art, glitter twinkles as the card leans, a rainbow sweep and
 * a glare follow the light, and a foil sticker on the art's corner says what the card is for
 * ("Taste test · 01/03", or "Wild card"). The name is in wide display capitals, the pitch in a
 * framed box beneath. [EchoPrismLayout] sizes it from the room it gets, so nothing is ever cut
 * off: a short screen gets the compact arrangement instead. It keeps the deck's physics: it floats,
 * leans under the finger and is dealt or flipped in by [reveal].
 */
@Composable
internal fun EchoPrismCard(
    card: OnboardingProbeCard,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    reveal: EchoCardReveal? = null,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val measure = rememberPrismTextMeasure()
        // The display faces arrive from the font provider; re-plan once they have, so the fit is real.
        val fontsReady by rememberPrismFontsReady()
        val title = remember(card.title) { card.title.uppercase() }
        val plan = remember(title, card.hook, card.author, constraints.maxWidth, constraints.maxHeight, density, measure, fontsReady) {
            EchoPrismLayout.plan(
                title = title,
                hook = card.hook,
                hasPublisher = !card.author.isNullOrBlank(),
                room = PrismRoom(
                    widthPx = constraints.maxWidth,
                    heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE / 2,
                    density = density.density,
                    fontScale = density.fontScale,
                ),
                measure = measure,
            )
        }
        EchoPrismFace(card, title, index, count, plan, reveal)
    }
}

@Composable
private fun EchoPrismFace(card: OnboardingProbeCard, title: String, index: Int, count: Int, plan: PrismPlan, reveal: EchoCardReveal?) {
    val motion = rememberEchoCardMotion()
    val clock = rememberPrismClock()
    val sparkles = remember(card.id) { echoSparkles(card.id, PrismDust) }
    val revealClock = remember { Animatable(0f) }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        if (reveal == null) return@LaunchedEffect
        val land = EchoCardRevealTiming.landMillis(reveal)
        revealClock.animateTo(land, tween(land.toInt(), easing = LinearEasing))
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val rest = EchoCardRevealTiming.totalMillis(reveal)
        revealClock.animateTo(rest, tween((rest - revealClock.value).toInt(), easing = LinearEasing))
    }
    val frame = { EchoCardRevealTiming.frame(reveal, revealClock.value) }
    // While the light runs across after landing, it leads the glare, the sweep and the glitter.
    val light = { frame().sweep?.let { Offset(-0.25f + 1.5f * it, 0.35f) } ?: motion.light }
    val lean = { frame().sweep?.let { motion.lean.copy(y = motion.lean.y + it * 18f) } ?: motion.lean }
    val glare = { PrismGlare * (1f + 1.8f * (frame().sweep?.let { sin(it * PI.toFloat()) } ?: 0f)) }
    val sweep = { prismSweep(clock.value) }
    val density = LocalDensity.current
    val art = with(density) { plan.art.toDp() }
    val look = PrismLook(clock, lean, light, glare, sweep)
    Box(
        Modifier
            .width(with(density) { plan.width.toDp() })
            .testTag("echo_deck_card")
            .echoCardTilt(motion, EchoPrismShape, frame)
            .drawBehind { drawPrismEdge(clock.value * PrismEdgeDegreesPerSecond % 360f) },
    ) {
        Box(Modifier.padding(EchoPrismLayout.Border.dp).clip(EchoPrismInnerShape).prismGround(plan, sparkles, look).padding(EchoPrismLayout.Padding.dp)) {
            if (plan.compact) PrismCompactFace(card, title, index, count, plan, art, sweep) else PrismStackedFace(card, title, index, count, plan, art, sweep)
        }
        if (reveal != null) {
            // Past the half-turn the back faces the listener; it is mirrored back so its mark reads true.
            EchoPrismBack(
                Modifier.matchParentSize().graphicsLayer {
                    alpha = if (frame().turn > 90f) 1f else 0f
                    scaleX = -1f
                },
                mark = true,
            )
        }
    }
}

/** What the ground's drawing reads each frame: the clock, the card's lean and the light. */
private class PrismLook(
    val clock: State<Float>,
    val lean: () -> EchoCardLean,
    val light: () -> Offset,
    val glare: () -> Float,
    val sweep: () -> Float,
)

/** The deep-space ground, the turning starburst centred on the art and the glitter; then the sweep and glare over everything. */
private fun Modifier.prismGround(plan: PrismPlan, sparkles: List<EchoSparkle>, look: PrismLook): Modifier = this
    .drawWithCache {
        val inset = EchoPrismLayout.Padding.dp.toPx()
        val centre = if (plan.compact) {
            Offset(inset + plan.art / 2f, inset + plan.art / 2f)
        } else {
            Offset(size.width / 2f, inset + EchoPrismLayout.TiltClearance.dp.toPx() + plan.art / 2f)
        }
        val reach = PrismRayReach.toPx()
        val rays = prismRays(centre, reach)
        val corner = PrismInnerCorner.toPx()
        onDrawBehind {
            drawPrismGround(corner)
            drawPrismRays(rays, centre, reach, look.clock.value * PrismRayDegreesPerSecond % 360f)
            drawSparkles(sparkles, look.lean(), Color.White)
        }
    }
    .drawWithContent {
        drawContent()
        drawPrismHolo(look.light(), look.sweep())
        drawGlare(look.light(), look.glare())
    }

/** Tilted art with the sticker on its corner, then the name, the publisher beside a short name, and the pitch. */
@Composable
private fun PrismStackedFace(card: OnboardingProbeCard, title: String, index: Int, count: Int, plan: PrismPlan, art: Dp, sweep: () -> Float) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(vertical = EchoPrismLayout.TiltClearance.dp)) {
            PrismArtwork(card, art, tilt = true, sweep = sweep) {
                PrismTag(
                    card,
                    index,
                    count,
                    sticker = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // The sticker may be wider than the art; it overhangs to the left rather than shrink.
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .offset(x = PrismStickerOverhang, y = PrismStickerDrop)
                        .graphicsLayer {
                            rotationZ = PrismStickerTurn
                            transformOrigin = TransformOrigin(1f, 0f)
                        },
                )
            }
        }
        Spacer(Modifier.height(EchoPrismLayout.TitleGap.dp))
        PrismTitle(title, card.title, plan, TextAlign.Center)
        if (plan.showPublisher) PrismPublisher(card.author.orEmpty())
        PrismHook(card.hook, plan.hookSp, TextAlign.Center)
    }
}

/** Short screens and large text: the art beside the tag and name, the pitch underneath. */
@Composable
private fun PrismCompactFace(card: OnboardingProbeCard, title: String, index: Int, count: Int, plan: PrismPlan, art: Dp, sweep: () -> Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // On the very tightest screens the art gives way to the words.
            if (plan.art > 0) PrismArtwork(card, art, tilt = false, sweep = sweep)
            Column(
                Modifier.padding(start = if (plan.art > 0) EchoPrismLayout.CompactGap.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(EchoPrismLayout.TagGap.dp),
            ) {
                PrismTag(card, index, count, sticker = false)
                PrismTitle(title, card.title, plan, TextAlign.Start)
            }
        }
        PrismHook(card.hook, plan.hookSp, TextAlign.Start)
    }
}

/** The square art, tilted on the stacked card, in its ringed frame with a moving sheen. */
@Composable
private fun PrismArtwork(card: OnboardingProbeCard, size: Dp, tilt: Boolean, sweep: () -> Float, sticker: @Composable BoxScope.() -> Unit = {}) {
    val podcast = remember(card) { card.toPodcast() }
    Box(
        Modifier
            .size(size)
            .graphicsLayer { rotationZ = if (tilt) PrismArtTurn else 0f }
            .drawBehind { drawPrismArtFrame(PrismArtCorner.toPx()) },
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = EchoDeckArtworkProxyWidth,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .shadow(PrismArtShadow, PrismArtShape)
                .clip(PrismArtShape)
                .drawWithContent {
                    drawContent()
                    drawPrismSheen(sweep())
                },
            contentScale = ContentScale.Crop,
        )
        sticker()
    }
}

/**
 * What the card is for and where it sits in the deck, as a foil sticker: "Taste test · 01/03" for
 * shows that fit what was said, "Wild card" for one a step away. On the stacked card it is slapped
 * on the art's corner with a white sticker edge; on the compact card it sits above the name and
 * shows the role alone. Screen readers hear the role and the card's place either way.
 */
@Composable
private fun PrismTag(card: OnboardingProbeCard, index: Int, count: Int, sticker: Boolean, modifier: Modifier = Modifier) {
    val related = card.role == "related"
    val role = stringResource(if (related) R.string.echo_deck_related else R.string.echo_deck_core)
    val position = stringResource(R.string.echo_deck_position, index + 1, count)
    val edge = if (sticker) Modifier.shadow(PrismStickerShadow, CircleShape).border(PrismStickerEdge, Color.White, CircleShape) else Modifier
    Row(
        modifier
            .then(edge)
            .background(PrismColors.Tag, CircleShape)
            .heightIn(min = EchoPrismLayout.TagMinHeight.dp)
            .padding(start = 8.dp, end = 11.dp, top = 3.dp, bottom = 3.dp)
            .clearAndSetSemantics { contentDescription = "$role, $position" }
            .testTag("echo_deck_tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (related) Icons.Rounded.AutoAwesome else Icons.Rounded.Star, null, Modifier.size(14.dp), tint = PrismColors.TagInk)
        Text(
            // The compact card (short screens, large text) has little room beside the art: the role alone.
            if (sticker) stringResource(R.string.echo_deck_tag, role.uppercase(), index + 1, count) else role.uppercase(),
            Modifier.padding(start = 4.dp),
            style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, lineHeight = EchoPrismLayout.TagLineSp.sp, letterSpacing = 1.4.sp),
            color = PrismColors.TagInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Beside a small art on the compact card the tag shrinks a little before it would cut.
            autoSize = if (sticker) null else TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.5.sp, stepSize = 0.5.sp),
        )
    }
}

/** The name in wide display capitals with a pastel foil gradient, at the size the plan fitted. */
@Composable
private fun PrismTitle(upper: String, title: String, plan: PrismPlan, align: TextAlign) {
    Text(
        upper,
        Modifier
            .fillMaxWidth()
            .semantics {
                heading()
                contentDescription = title
            }
            .testTag("echo_deck_title"),
        style = prismTitleStyle(plan.titleSp).copy(textAlign = align),
        maxLines = plan.titleLines.coerceAtMost(EchoPrismLayout.MaxTitleLines),
        overflow = TextOverflow.Ellipsis,
    )
}

/** The publisher matters least: one spaced line, only under a short name. */
@Composable
private fun PrismPublisher(author: String) {
    Text(
        author.uppercase(),
        Modifier.padding(top = EchoPrismLayout.PublisherGap.dp).widthIn(max = PrismPublisherMaxWidth),
        style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = 10.5.sp, lineHeight = EchoPrismLayout.PublisherLineSp.sp, letterSpacing = 1.6.sp, textAlign = TextAlign.Center),
        color = PrismColors.Muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Echo's one-sentence pitch in a dark box with a rainbow edge, always shown whole. */
@Composable
private fun PrismHook(hook: String, sizeSp: Float, align: TextAlign) {
    Text(
        hook,
        Modifier
            .padding(top = EchoPrismLayout.HookGap.dp)
            .fillMaxWidth()
            .border(EchoPrismLayout.HookBorder.dp, PrismColors.HookEdge, PrismHookShape)
            .background(PrismColors.HookGround, PrismHookShape)
            .padding(horizontal = (EchoPrismLayout.HookPaddingH + EchoPrismLayout.HookBorder).dp, vertical = (EchoPrismLayout.HookPaddingV + EchoPrismLayout.HookBorder).dp)
            .testTag("echo_deck_hook"),
        style = prismHookStyle().withSize(sizeSp).copy(textAlign = align),
        color = PrismColors.HookInk,
    )
}

/** A card face down: the turning edge, the ground and, when it can be seen whole, Echo's sparkle. */
@Composable
internal fun EchoPrismBack(modifier: Modifier, mark: Boolean = false) {
    Box(
        modifier.drawWithCache {
            val border = EchoPrismLayout.Border.dp.toPx()
            val corner = PrismInnerCorner.toPx()
            val centre = Offset(size.width / 2f, size.height / 2f)
            val reach = PrismRayReach.toPx()
            val rays = prismRays(centre, reach)
            onDrawBehind {
                drawPrismEdge(PrismBackEdgeDegrees)
                drawRoundRect(PrismColors.GroundMiddle, Offset(border, border), size.copy(size.width - border * 2, size.height - border * 2), CornerRadius(corner))
                drawPrismRays(rays, centre, reach, 0f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (mark) Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(72.dp), tint = PrismColors.Mark)
    }
}

internal val EchoPrismShape = RoundedCornerShape(26.dp)
private val PrismInnerCorner = 23.dp
private val EchoPrismInnerShape = RoundedCornerShape(PrismInnerCorner)
private val PrismArtCorner = 16.dp
private val PrismArtShape = RoundedCornerShape(PrismArtCorner)
private val PrismHookShape = RoundedCornerShape(14.dp)
private val PrismArtShadow = 18.dp
private val PrismRayReach = 220.dp
private val PrismStickerOverhang = 12.dp
private val PrismStickerDrop = 14.dp
private val PrismStickerShadow = 8.dp
private val PrismStickerEdge = 2.5.dp
private val PrismPublisherMaxWidth = 280.dp
private const val PrismStickerTurn = 12f
private const val PrismArtTurn = -2f
private const val PrismDust = 70
private const val PrismGlare = 0.1f
private const val PrismEdgeDegreesPerSecond = 360f / 7f
private const val PrismRayDegreesPerSecond = 360f / 50f
private const val PrismBackEdgeDegrees = 30f

/** Artwork request width for deck cards; the deck prefetch uses the same width so it hits the cache. */
internal const val EchoDeckArtworkProxyWidth = 600
