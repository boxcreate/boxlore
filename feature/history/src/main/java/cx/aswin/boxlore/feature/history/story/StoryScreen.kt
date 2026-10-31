package cx.aswin.boxlore.feature.history.story

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.echo.rememberEchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.layout.boxloreFoldSafeViewport
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.R
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The story's frame: the boards' 400 × 890, scaled as a whole to fit the window. */
private const val FRAME_WIDTH = 400f
private const val FRAME_HEIGHT = 890f

/**
 * The frame never shrinks below this (its 48dp buttons stay near touch size): in a short window
 * (a phone on its side) it keeps this size and scrolls instead.
 */
private const val MIN_SCALE = 0.8f

/** The monthly story, full screen over everything (Back closes it). */
@Composable
fun StoryDialog(story: MonthStory, startAtShare: Boolean, onClose: () -> Unit, onPlay: (ListeningHistoryItem) -> Unit, onShare: (Bitmap) -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StoryPlayer(story, if (startAtShare) StoryCard.SHARE else StoryCard.HELLO, onClose, onPlay, onShare)
    }
}

/**
 * The monthly story player (A7–A12 with A14's motion), from [startCard]: a card every 8 seconds, tap the right of the
 * frame for the next and the left for the previous, hold to pause; Echo hops from card to card. The
 * whole 400 × 890 frame scales to fit any window (phones, tablets, folded or not, split screen) with
 * the card's colour filling around it, and the card on show survives resizing and rotation. With
 * TalkBack on, cards wait to be moved on; with animations off, every card shows its final state.
 */
@Composable
fun StoryPlayer(story: MonthStory, startCard: StoryCard, onClose: () -> Unit, onPlay: (ListeningHistoryItem) -> Unit, onShare: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberStoryPalette()
    val spec = rememberEchoCharacterSpec()
    val cards = remember(story) { StoryLogic.cards(story) }
    var index by rememberSaveable(story.month) { mutableIntStateOf(cards.indexOf(startCard).coerceAtLeast(0)) }
    var plays by rememberSaveable(story.month) { mutableIntStateOf(0) }
    // Picks Echo's lines: a new seed each time the story opens, kept through rotation and resizing.
    val lineSeed = rememberSaveable(story.month) { Random.nextInt() }
    var hopFrom by remember { mutableStateOf<StoryEchoBox?>(null) }
    var held by remember { mutableStateOf(false) }
    val card = cards[index.coerceIn(0, cards.lastIndex)]
    val clock = rememberStoryClock(key = plays, paused = held, stopAtMillis = StoryMotion.CARD_MS)
    val go: (Int) -> Unit = { target ->
        val next = target.coerceIn(0, cards.lastIndex)
        if (next != index) {
            hopFrom = card.echo(story, palette).box
            index = next
            plays++
        }
    }
    val goTo by rememberUpdatedState(go)
    val touchExploration = rememberTouchExploration()
    LaunchedEffect(plays, touchExploration) {
        if (index >= cards.lastIndex || touchExploration) return@LaunchedEffect
        snapshotFlow { clock.elapsed }.first { it >= StoryMotion.CARD_MS }
        goTo(index + 1)
    }
    val (background, content) = card.colors(story, palette)
    val bg by animateColorAsState(background, tween(StoryMotion.RISE_MS, easing = StoryMotion.Standard), label = "storyBackground")
    val fg by animateColorAsState(content, tween(StoryMotion.RISE_MS, easing = StoryMotion.Standard), label = "storyContent")
    val frame = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val share: () -> Unit = {
        scope.launch { onShare(cropShareCard(frame.toImageBitmap().asAndroidBitmap(), density.density)) }
    }
    Box(modifier.fillMaxSize().background(bg)) {
        // On a spanned foldable the frame keeps to one side of the hinge, as other full-screen layers do.
        BoxWithConstraints(Modifier.fillMaxSize().boxloreFoldSafeViewport().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            val fit = minOf(maxWidth / FRAME_WIDTH.dp, maxHeight / FRAME_HEIGHT.dp)
            val scale = if (fit >= MIN_SCALE) fit else minOf(maxWidth / FRAME_WIDTH.dp, MIN_SCALE)
            // The frame is artwork: its type is sized with it, not with the font scale, so nothing reflows out of place.
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(
                    Modifier
                        .then(if (scale > fit) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .size(FRAME_WIDTH.dp * scale, FRAME_HEIGHT.dp * scale),
                    contentAlignment = Alignment.Center,
                ) {
                Box(
                    Modifier
                        .requiredSize(FRAME_WIDTH.dp, FRAME_HEIGHT.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawWithContent {
                            frame.record { this@drawWithContent.drawContent() }
                            drawLayer(frame)
                        },
                ) {
                    StoryCardContent(card, story, palette, clock, spec, StoryActions(onPlay, share) { go(0) }, storyEchoLine(card, story, lineSeed))
                    if (spec != null) {
                        val next = cards.getOrNull(index + 1)?.echo(story, palette)?.sequence
                        StoryEcho(spec, card.echo(story, palette), StoryEchoMoment(hopFrom, plays, held), clock, listOfNotNull(StoryEchoStage.HOP, next), Modifier.zIndex(StoryLayers.ECHO))
                    }
                    StoryTapAreas(onPrevious = { go(index - 1) }, onNext = { go(index + 1) }, onHold = { held = it })
                    StoryChrome(cards.size, index, fg, clock, onClose)
                }
                }
            }
        }
    }
}

/** What a card's buttons do: Resume plays, Share sends the share card, Watch again starts over. */
private class StoryActions(val onPlay: (ListeningHistoryItem) -> Unit, val onShare: () -> Unit, val onWatchAgain: () -> Unit)

/** A card's own content, by kind. */
@Composable
private fun BoxScope.StoryCardContent(
    card: StoryCard,
    story: MonthStory,
    palette: StoryPalette,
    clock: StoryClock,
    spec: EchoCharacterSpec?,
    actions: StoryActions,
    line: String,
) {
    when (card) {
        StoryCard.HELLO -> StoryHelloCard(story, palette, clock, java.time.LocalDate.now().year, line)
        StoryCard.TIME -> StoryTimeCard(story, palette, clock, spec, card.echo(story, palette), line)
        StoryCard.TOP_SHOW -> StoryTopShowCard(story, palette, clock, actions.onPlay, line)
        StoryCard.HOW -> StoryHowCard(story, palette, clock, line)
        StoryCard.TYPE -> StoryTypeCard(story, palette, clock, line)
        StoryCard.RECORDS -> StoryRecordsCard(story, palette, clock, line)
        StoryCard.SHARE -> StoryShareCard(story, palette, clock, actions.onShare, actions.onWatchAgain, line)
    }
}

/** The card's colours: background and content. */
internal fun StoryCard.colors(story: MonthStory, palette: StoryPalette): Pair<Color, Color> = when (this) {
    StoryCard.HELLO, StoryCard.SHARE -> palette.container to palette.onContainer
    StoryCard.TIME -> palette.vivid to Color.White
    StoryCard.TOP_SHOW -> palette.green to palette.onGreen
    StoryCard.HOW -> palette.onContainer to palette.deep
    StoryCard.TYPE -> if (story.type?.time?.isNight() != false) palette.deep to palette.onContainer else palette.onContainer to palette.deep
    StoryCard.RECORDS -> palette.primary to palette.onPrimary
}

/** Echo on the card. */
internal fun StoryCard.echo(story: MonthStory, palette: StoryPalette): StoryEchoCast = when (this) {
    StoryCard.HELLO -> StoryEchoStage.hello(palette)
    StoryCard.TIME -> StoryEchoStage.time(palette, StoryTimeChart.biggestCenter(story.perDay.size, story.biggestDay.first.dayOfMonth - 1))
    StoryCard.TOP_SHOW -> StoryEchoStage.topShow(palette, story.showOutfit)
    StoryCard.HOW -> StoryEchoStage.how(palette)
    StoryCard.TYPE -> StoryEchoStage.type(palette, StoryLogic.timeOutfit(story.type?.time))
    StoryCard.RECORDS -> StoryEchoStage.records(palette)
    StoryCard.SHARE -> StoryEchoStage.share(palette)
}

/**
 * Tap the left 30% for the previous card and the rest for the next; hold anywhere to pause. They
 * start below the progress bar and stop above the cards' lower 220dp, where the buttons are.
 */
@Composable
private fun BoxScope.StoryTapAreas(onPrevious: () -> Unit, onNext: () -> Unit, onHold: (Boolean) -> Unit) {
    val previous = stringResource(R.string.history_story_previous)
    val next = stringResource(R.string.history_story_next)
    StoryTapArea(previous, Modifier.at(0f, 84f).size(120.dp, 586.dp), onPrevious, onHold)
    StoryTapArea(next, Modifier.at(120f, 84f).size(280.dp, 586.dp), onNext, onHold)
}

@Composable
private fun BoxScope.StoryTapArea(label: String, modifier: Modifier, onTap: () -> Unit, onHold: (Boolean) -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    Box(
        modifier
            .zIndex(StoryLayers.TAPS)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hold(true)
                        tryAwaitRelease()
                        hold(false)
                    },
                    // A long press is a hold: letting go resumes this card instead of moving on.
                    onLongPress = { },
                    onTap = { tap() },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    tap()
                    true
                }
            },
    )
}

/** The progress bar (a segment a card, the current one filling over its 8 seconds) and Close. */
@Composable
private fun BoxScope.StoryChrome(cards: Int, index: Int, color: Color, clock: StoryClock, onClose: () -> Unit) {
    val progress = stringResource(R.string.history_story_card_of, index + 1, cards)
    Row(
        Modifier.zIndex(StoryLayers.CHROME).at(16f, 16f).width(368.dp).height(4.dp).clearAndSetSemantics { contentDescription = progress },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(cards) { card ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (card < index) 1f else 0.3f))
                    .drawWithContent {
                        drawContent()
                        if (card == index) {
                            val filled = if (clock.motionOff) 1f else clock.linear(0, StoryMotion.CARD_MS.toInt())
                            drawRect(color, size = Size(size.width * filled, size.height))
                        }
                    },
            )
        }
    }
    Box(
        Modifier
            .zIndex(StoryLayers.CHROME)
            .at(344f, 30f)
            .size(48.dp)
            .clip(CircleShape)
            .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClose)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_story_close), tint = color, modifier = Modifier.size(24.dp))
    }
}

/** The share card, cut from the frame as drawn (Echo included), at the screen's resolution. */
private fun cropShareCard(frame: Bitmap, density: Float): Bitmap {
    val x = (StoryShareSpot.X * density).toInt().coerceIn(0, frame.width - 1)
    val y = (StoryShareSpot.Y * density).toInt().coerceIn(0, frame.height - 1)
    val width = (StoryShareSpot.WIDTH * density).toInt().coerceAtMost(frame.width - x)
    val height = (StoryShareSpot.HEIGHT * density).toInt().coerceAtMost(frame.height - y)
    return Bitmap.createBitmap(frame, x, y, width, height)
}

/** Whether TalkBack (touch exploration) is on, so cards wait to be moved on; follows it being turned on or off while the story plays. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
