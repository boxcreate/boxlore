package cx.aswin.boxlore.feature.onboarding

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberCondensedGoogleSansFamily
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlin.math.PI
import kotlin.math.sin

/**
 * The deck's one deliberate break from flat Material: a holographic trading card. A chrome frame
 * with fine etching, a rainbow band that slides as the card turns, glitter that twinkles with the
 * angle and a glare that follows the finger. The name bar and text box stay solid, so the words
 * read cleanly whatever the foil is doing. The frame's chrome is tinted from the theme; only the
 * holo spectrum is fixed, as printed foil would be.
 */
@Composable
internal fun EchoHoloCard(
    card: OnboardingProbeCard,
    index: Int,
    count: Int,
    minArtwork: Dp,
    modifier: Modifier = Modifier,
    reveal: EchoCardReveal? = null,
) {
    val foil = echoFoil()
    val motion = rememberEchoCardMotion()
    val podcast = remember(card) { card.toPodcast() }
    val sparkles = remember(card.id) { echoSparkles(card.id, EchoFrameSparkles) }
    val artSparkles = remember(card.id) { echoSparkles(card.id + 1, EchoArtSparkles) }
    val clock = remember { Animatable(0f) }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        if (reveal == null) return@LaunchedEffect
        clock.animateTo(EchoCardRevealTiming.landMillis(reveal), tween(EchoCardRevealTiming.landMillis(reveal).toInt(), easing = LinearEasing))
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val rest = EchoCardRevealTiming.totalMillis(reveal)
        clock.animateTo(rest, tween((rest - clock.value).toInt(), easing = LinearEasing))
    }
    val frame = { EchoCardRevealTiming.frame(reveal, clock.value) }
    // While the light runs across after landing, it leads the glare, the rainbow and the glitter.
    val light = { frame().sweep?.let { Offset(-0.25f + 1.5f * it, 0.35f) } ?: motion.light }
    val lean = { frame().sweep?.let { motion.lean.copy(y = motion.lean.y + it * 18f) } ?: motion.lean }
    val glare = { foil.glare * (1f + 1.8f * (frame().sweep?.let { sin(it * PI.toFloat()) } ?: 0f)) }
    Box(
        modifier
            .testTag("echo_deck_card")
            .echoCardTilt(motion, EchoHoloShape, frame)
            .drawWithCache {
                val etching = echoEtching(size.width, size.height, 4.dp.toPx())
                val hairline = Stroke(1.dp.toPx())
                onDrawBehind {
                    drawChrome(foil, light())
                    drawPath(etching, Color.White.copy(alpha = 0.14f), style = hairline)
                    drawHolo(light(), alpha = 0.7f)
                    drawHolo(light(), alpha = 0.22f, blendMode = BlendMode.SrcOver)
                    drawSparkles(sparkles, lean(), foil.sparkle)
                }
            }
            .drawWithContent {
                drawContent()
                drawGlare(light(), glare())
            },
    ) {
        EchoHoloLayout(
            minArtwork = minArtwork,
            name = { EchoHoloNameBar(card, foil) },
            art = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(EchoHoloInnerShape)
                        .border(1.5.dp, foil.shade, EchoHoloInnerShape)
                        .drawWithContent {
                            drawContent()
                            drawHolo(light(), alpha = 0.3f)
                            drawSparkles(artSparkles, lean(), Color.White)
                        },
                ) {
                    OptimizedImage(url = podcast.imageUrl, proxyWidth = 600, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // Badges ride on the artwork, like a card's set mark, so the text box keeps its room. They
                    // say what the card is for: a taste test inside the chat, not a recommendation yet.
                    EchoHoloBadge(Modifier.align(Alignment.BottomStart).padding(6.dp), foil) {
                        val related = card.role == "related"
                        Icon(if (related) Icons.Rounded.Explore else Icons.Rounded.Quiz, null, Modifier.size(14.dp), tint = foil.accent)
                        Text(
                            stringResource(if (related) R.string.echo_deck_related else R.string.echo_deck_core),
                            Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = foil.accent,
                            maxLines = 1,
                        )
                    }
                    EchoHoloBadge(Modifier.align(Alignment.TopEnd).padding(6.dp), foil) {
                        Text(
                            stringResource(R.string.echo_deck_position, index + 1, count),
                            style = MaterialTheme.typography.labelMedium,
                            color = foil.onPanelVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            info = { EchoHoloInfo(card, foil) },
        )
        if (reveal != null) {
            // Past the half-turn the back faces the listener; it is mirrored back so its mark reads true.
            EchoHoloBack(
                Modifier.matchParentSize().graphicsLayer {
                    alpha = if (frame().turn > 90f) 1f else 0f
                    scaleX = -1f
                },
                mark = true,
            )
        }
    }
}

/** A card face down: chrome edge, a plain back and, when it can be seen whole, Echo's sparkle. */
@Composable
internal fun EchoHoloBack(modifier: Modifier, mark: Boolean = false) {
    val foil = echoFoil()
    Box(
        modifier.drawWithCache {
            val inset = EchoHoloFrame.toPx()
            onDrawBehind {
                drawChrome(foil, Offset(0.5f, 0.4f))
                drawRoundRect(foil.back, Offset(inset, inset), size.copy(size.width - inset * 2, size.height - inset * 2), androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
                drawHolo(Offset(0.5f, 0.4f), alpha = 0.18f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (mark) Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(72.dp), tint = foil.light)
    }
}

/**
 * The name bar, like a card's title strip: a softly tinted panel with a fine foil edge, the name in
 * the condensed onboarding face, and the rarity star in a small filled seal.
 */
@Composable
private fun EchoHoloNameBar(card: OnboardingProbeCard, foil: EchoFoil) {
    val style = MaterialTheme.typography.titleLarge.copy(fontFamily = rememberCondensedGoogleSansFamily(), fontWeight = GoogleSansWeight.bold, letterSpacing = (-0.2).sp)
    Row(
        Modifier.fillMaxWidth().echoHoloPanel(foil).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            card.title,
            Modifier.weight(1f).semantics { heading() }.testTag("echo_deck_title"),
            style = style.copy(lineHeight = 1.12.em),
            color = foil.onPanel,
            // Long names get a second line before the type shrinks; the publisher matters less and stays on one.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
        // Rarity: a full star for shows that fit what was said, half a star for one step away.
        Box(Modifier.size(30.dp).background(foil.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (card.role == "related") Icons.Rounded.StarHalf else Icons.Rounded.Star, null, Modifier.size(18.dp), tint = foil.onAccent)
        }
    }
}

/**
 * The text box, read like a card's flavour text: the publisher as a small spaced caps line (it
 * matters least, so one line and "…"), a fading foil rule, then Echo's one-sentence pitch.
 */
@Composable
private fun EchoHoloInfo(card: OnboardingProbeCard, foil: EchoFoil) {
    Column(Modifier.fillMaxWidth().echoHoloPanel(foil).padding(horizontal = 12.dp, vertical = 10.dp)) {
        card.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = GoogleSansWeight.semiBold),
                color = foil.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 7.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(foil.shade.copy(alpha = 0.55f), foil.shade.copy(alpha = 0f)))),
            )
        }
        val hook = MaterialTheme.typography.bodyMedium.copy(lineHeight = 1.35.em)
        Text(
            card.hook,
            Modifier.weight(1f, fill = false).testTag("echo_deck_hook"),
            style = hook,
            color = foil.onPanel,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = hook.fontSize, stepSize = 0.5.sp),
        )
    }
}

/** The card's solid text panels: a soft tint from top to bottom and a fine foil edge, like printed card stock. */
private fun Modifier.echoHoloPanel(foil: EchoFoil): Modifier =
    this
        .background(Brush.verticalGradient(listOf(foil.panel, foil.panelTint)), EchoHoloInnerShape)
        .border(1.dp, foil.edge, EchoHoloInnerShape)

/** A small solid tag on the artwork. */
@Composable
private fun EchoHoloBadge(modifier: Modifier, foil: EchoFoil, content: @Composable () -> Unit) {
    Row(
        modifier.background(foil.panel, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Card parts. Podcast artwork is square, so the window is always square, centred on the foil. */
private val EchoHoloMaxWidth = 420.dp
internal val EchoHoloFrame = 12.dp
private val EchoHoloGap = 6.dp
internal val EchoHoloShape = RoundedCornerShape(18.dp)
private val EchoHoloInnerShape = RoundedCornerShape(7.dp)
private const val EchoFrameSparkles = 90
private const val EchoArtSparkles = 26

/** The card sits a touch inside the room, so it reads as a card held up rather than a panel. */
private const val EchoHoloWidthShare = 0.92f

/** The artwork never takes more than this share of the card's inner width; the foil frames the rest. */
private const val EchoHoloMaxArtShare = 0.7f

/**
 * Name bar, a square artwork window, text box. The card takes nearly the full width; the text box
 * is measured first and the artwork takes the height that is left, up to [EchoHoloMaxArtShare] of
 * the width, centred with the foil showing either side. Short screens shrink only the artwork.
 */
@Composable
private fun EchoHoloLayout(
    minArtwork: Dp,
    name: @Composable () -> Unit,
    art: @Composable () -> Unit,
    info: @Composable () -> Unit,
) {
    Layout(contents = listOf(name, art, info)) { (nameParts, artParts, infoParts), constraints ->
        val frame = EchoHoloFrame.roundToPx()
        val gap = EchoHoloGap.roundToPx()
        val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE / 2
        val width = minOf((constraints.maxWidth * EchoHoloWidthShare).toInt(), EchoHoloMaxWidth.roundToPx()).coerceAtLeast(frame * 4)
        val inner = width - frame * 2
        val names = nameParts.map { it.measure(Constraints.fixedWidth(inner)) }
        val nameHeight = names.maxOfOrNull { it.height } ?: 0
        val maxArt = (inner * EchoHoloMaxArtShare).toInt()
        val minArt = minArtwork.roundToPx().coerceAtMost(maxArt)
        val infoRoom = (maxHeight - frame * 2 - nameHeight - gap * 2 - minArt).coerceAtLeast(0)
        val infos = infoParts.map { it.measure(Constraints(minWidth = inner, maxWidth = inner, maxHeight = infoRoom)) }
        val infoHeight = infos.maxOfOrNull { it.height } ?: 0
        val art = (maxHeight - frame * 2 - nameHeight - infoHeight - gap * 2).coerceIn(minArt, maxArt)
        val arts = artParts.map { it.measure(Constraints.fixed(art, art)) }
        val height = frame * 2 + nameHeight + gap + art + gap + infoHeight
        layout(width, height) {
            var y = frame
            names.forEach { it.placeRelative(frame, y) }
            y += nameHeight + gap
            arts.forEach { it.placeRelative((width - art) / 2, y) }
            y += art + gap
            infos.forEach { it.placeRelative(frame, y) }
        }
    }
}
