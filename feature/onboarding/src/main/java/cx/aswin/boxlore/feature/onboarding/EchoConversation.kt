package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoFloorLine
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer

/**
 * What Echo says: a reaction, the opening line, or a slow-request line with its countdown. On a
 * show card it is the card's question, in bold, standing in for the question headline.
 */
private data class EchoSpeech(val line: String, val detail: String?, val emphasis: Boolean = false)

/**
 * Echo centred like a messaging profile, with its speech bubble underneath and a small tail
 * pointing up at it. The bubble fits its text and pops out of the tail. Echo, its stage and its
 * bubble share the primary container role. On a show card the card needs the height, so a smaller
 * Echo sits beside a smaller bubble whose tail points back at it.
 */
@Composable
internal fun EchoHeader(state: OnboardingUiState, kind: EchoScreenKind, metrics: EchoLayoutMetrics, paused: Boolean = false) {
    val opening = EchoScreenModel.isOpeningTurn(state)
    val waitKey = if (kind == EchoScreenKind.Waiting && !state.isSynthesizing) "turn:${state.aiHistory.size}" else null
    val wait = rememberEchoWaitCopy(EchoWaitStep.NextQuestion, waitKey)
    val intro = stringResource(R.string.echo_intro)
    val speech = when (kind) {
        EchoScreenKind.Asking, EchoScreenKind.Writing, EchoScreenKind.Recovery ->
            (if (opening) intro else state.aiGuidedTurn?.response)?.takeIf { it.isNotBlank() }?.let { EchoSpeech(it, null) }
        EchoScreenKind.Waiting -> wait?.let { EchoSpeech(it.line, it.detail) }
        EchoScreenKind.Deck -> state.aiDeck.current?.let { EchoSpeech(it.question, null, emphasis = true) }
        else -> null
    }
    if (kind == EchoScreenKind.Deck) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoFigure(state, kind, metrics.avatarWidth * EchoDeckAvatarShare, paused)
            AnimatedContent(
                targetState = speech?.line,
                modifier = Modifier.weight(1f, fill = false),
                transitionSpec = { echoBubbleTransform(TransformOrigin(0f, 0.5f)) },
                contentAlignment = Alignment.CenterStart,
                label = "Echo deck speech",
            ) { line ->
                if (line != null) EchoBubble(line, null, EchoDeckBubbleLines, emphasis = true, side = EchoBubbleSide.Start)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EchoFigure(state, kind, metrics.avatarWidth, paused)
        // Keyed on the line only, so the countdown ticking does not replay the pop.
        AnimatedContent(
            targetState = speech?.line,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { echoBubbleTransform(TransformOrigin(0.5f, 0f)) },
            contentAlignment = Alignment.TopCenter,
            label = "Echo speech",
        ) { line ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (line != null) {
                    val current = speech?.takeIf { it.line == line }
                    EchoBubble(line, current?.detail, metrics.bubbleLines, emphasis = current?.emphasis == true)
                }
            }
        }
    }
}

/**
 * Echo beside a smaller bubble, as on the show deck, for other screens of the Echo flow (the
 * suggestions screen): a line and an optional small detail such as a countdown.
 */
@Composable
internal fun EchoSays(state: OnboardingUiState, kind: EchoScreenKind, avatarWidth: Dp, line: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoFigure(state, kind, avatarWidth, paused = false)
        AnimatedContent(
            targetState = line,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = { echoBubbleTransform(TransformOrigin(0f, 0.5f)) },
            contentAlignment = Alignment.CenterStart,
            label = "Echo says",
        ) { text -> EchoBubble(text, detail, EchoDeckBubbleLines, side = EchoBubbleSide.Start) }
    }
}

/**
 * Echo's figure. Echo's canvas is [HeightShare] times as tall as the figure is wide, with Echo's square
 * frame drawn [FrameShare] times the figure's width, standing at [Floor] of the canvas: everything Echo
 * does, up to the top of its hop and the edge of a fanned card, stays inside it. Behind Echo, its stage
 * shape is [PlatformOverEcho] times Echo's resting height, centred on Echo, and stays inside its own
 * square canvas at any angle. The figure is as tall as the larger of the two ([TotalShare]), so the
 * layout around it always makes room for both (checked against every clip's reach in `EchoMomentsTest`).
 * All shares are of the figure's width.
 */
internal object EchoFigureGeometry {
    const val HeightShare = 0.84f
    const val FrameShare = 1.17f
    const val Floor = 0.975f
    const val PlatformOverEcho = 1.14f

    /** The top of Echo's head at rest, in its frame (the rest stills' reach). */
    const val RestTop = 0.30f

    /** Echo's resting height, as a share of the figure's width. */
    val restHeight: Float get() = (boxY(EchoFloorLine) - boxY(RestTop)) * HeightShare

    /** The stage's square canvas. */
    val PlatformShare: Float get() = restHeight * PlatformOverEcho / EchoPlatformFill

    val TotalShare: Float get() = maxOf(HeightShare, PlatformShare)

    /** Where Echo's canvas starts below the figure's top, so Echo's middle at rest sits on the stage's centre. */
    val EchoTop: Float get() = TotalShare / 2f - boxY((RestTop + EchoFloorLine) / 2f) * HeightShare

    /** Where a height in Echo's frame lands in the box, as a share of the box height (0 is its top). */
    fun boxY(frameY: Float): Float = Floor + (frameY - EchoFloorLine) * FrameShare / HeightShare

    /** Where a point across Echo's frame lands in the box, as a share of the box width. */
    fun boxX(frameX: Float): Float = 0.5f + (frameX - 0.5f) * FrameShare
}

/** On a show card Echo shrinks to this share of its usual width and sits beside its bubble. */
private const val EchoDeckAvatarShare = 0.85f
private const val EchoDeckBubbleLines = 3

/** Echo on its stage; the stage changes shape and turns with every new question and show card. */
@Composable
internal fun EchoFigure(state: OnboardingUiState, kind: EchoScreenKind, width: Dp, paused: Boolean, modifier: Modifier = Modifier) {
    val key = state.aiDeck.current?.takeIf { kind == EchoScreenKind.Deck }?.let { "deck:${it.id}" } ?: "${state.aiCurrentTurn}:${state.aiAssistantMessage}"
    Box(modifier.width(width).height(width * EchoFigureGeometry.TotalShare).testTag("echo_onboarding_avatar")) {
        // A little bigger than Echo, centred on it, inside the figure at any angle (see EchoFigureGeometry).
        EchoQuestionPlatform(key, Modifier.align(Alignment.Center).size(width * EchoFigureGeometry.PlatformShare))
        val moment = echoOnboardingMoment(EchoScreenModel.avatarPhase(state, kind))
        EchoCharacter(
            sequence = moment.sequence,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = width * EchoFigureGeometry.EchoTop).height(width * EchoFigureGeometry.HeightShare),
            playbackKey = key,
            paused = paused,
            idle = moment.idle,
            prefetch = EchoOnboardingSequences,
            frameScale = EchoFigureGeometry.FrameShare / EchoFigureGeometry.HeightShare,
            floor = EchoFigureGeometry.Floor,
        )
    }
}

/**
 * Results are ready (Back from suggestions, or Echo finished on its own): Echo, larger and centred
 * in its presenting pose, with the message centred beneath it. The footer carries the actions.
 */
@Composable
internal fun EchoReadyStage(state: OnboardingUiState, metrics: EchoLayoutMetrics, paused: Boolean, modifier: Modifier = Modifier) {
    val hasRows = state.aiCurriculumRows.isNotEmpty()
    Column(
        modifier.fillMaxWidth().testTag("echo_ready"),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (metrics.showEcho) EchoFigure(state, EchoScreenKind.Ready, metrics.avatarWidth * 1.2f, paused)
        Text(
            if (hasRows) stringResource(R.string.echo_ready_title) else state.aiGuidedTurn?.response ?: stringResource(R.string.echo_ready_pending),
            Modifier.padding(top = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hasRows) {
            Text(
                stringResource(R.string.echo_ready_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun echoBubbleTransform(origin: TransformOrigin): ContentTransform = ContentTransform(
        targetContentEnter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 520f), initialScale = 0.82f, transformOrigin = origin) + fadeIn(tween(140)),
        initialContentExit = scaleOut(tween(110), targetScale = 0.94f, transformOrigin = origin) + fadeOut(tween(110)),
        sizeTransform = SizeTransform(clip = false),
    )

private val EchoBubbleTail = 8.dp

/** Where the bubble's tail points: up at Echo above it, or back at Echo beside it. */
private enum class EchoBubbleSide { Top, Start }

/** A rounded bubble with a small tail pointing at Echo: top centre, or the middle of the start edge. */
private class EchoBubbleShape(private val radius: Dp, private val tail: Dp, private val side: EchoBubbleSide = EchoBubbleSide.Top) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val t = with(density) { tail.toPx() }
        if (side == EchoBubbleSide.Start) return startTail(size, r, t, layoutDirection == LayoutDirection.Rtl)
        val centre = size.width / 2f
        val body = Path().apply { addRoundRect(RoundRect(0f, t, size.width, size.height, CornerRadius(r))) }
        val tip = Path().apply {
            // The base dips into the body so the union has no seam.
            moveTo(centre - t * 1.3f, t + t)
            lineTo(centre - t * 1.3f, t)
            quadraticTo(centre - t * 0.25f, t * 0.15f, centre, 0f)
            quadraticTo(centre + t * 0.25f, t * 0.15f, centre + t * 1.3f, t)
            lineTo(centre + t * 1.3f, t + t)
            close()
        }
        // One outline: two overlapping sub-paths with opposite winding leave a hairline gap.
        return Outline.Generic(Path().apply { op(body, tip, PathOperation.Union) })
    }

    private fun startTail(size: Size, r: Float, t: Float, rtl: Boolean): Outline {
        val middle = size.height / 2f
        val body = Path().apply { addRoundRect(RoundRect(t, 0f, size.width, size.height, CornerRadius(r))) }
        val tip = Path().apply {
            moveTo(t + t, middle - t * 1.3f)
            lineTo(t, middle - t * 1.3f)
            quadraticTo(t * 0.15f, middle - t * 0.25f, 0f, middle)
            quadraticTo(t * 0.15f, middle + t * 0.25f, t, middle + t * 1.3f)
            lineTo(t + t, middle + t * 1.3f)
            close()
        }
        val outline = Path().apply { op(body, tip, PathOperation.Union) }
        if (rtl) {
            outline.transform(
            androidx.compose.ui.graphics.Matrix().apply {
            scale(-1f, 1f)
            translate(-size.width, 0f)
        }
        )
        }
        return Outline.Generic(outline)
    }
}

@Composable
private fun EchoBubble(line: String, detail: String?, maxLines: Int, emphasis: Boolean = false, side: EchoBubbleSide = EchoBubbleSide.Top) {
    val beside = side == EchoBubbleSide.Start
    // Beside Echo the bubble is a size smaller, so the show card below keeps its height.
    val base = if (beside) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    Surface(
        // A small gap keeps the tail clear of Echo's stage, whatever shape and turn it is in.
        Modifier.padding(top = if (beside) 0.dp else 4.dp).testTag("echo_acknowledgment"),
        shape = EchoBubbleShape(if (beside) 18.dp else 22.dp, EchoBubbleTail, side),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            if (beside) {
                Modifier.padding(start = 14.dp + EchoBubbleTail, end = 14.dp, top = 10.dp, bottom = 10.dp)
            } else {
                Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp + EchoBubbleTail, bottom = 12.dp)
            },
            horizontalAlignment = if (beside) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                line,
                Modifier.then(if (emphasis) Modifier.semantics { heading() }.testTag("echo_deck_question") else Modifier),
                style = base.let { if (emphasis) it.copy(fontWeight = GoogleSansWeight.bold) else it },
                textAlign = if (beside) TextAlign.Start else TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = if (beside) 12.sp else 13.sp, maxFontSize = base.fontSize, stepSize = 0.5.sp),
            )
            if (detail != null) {
                Text(detail, Modifier.testTag("echo_wait_detail"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The question, or what stands in its place: a placeholder, an error or the ready message. */
@Composable
internal fun EchoQuestionArea(
    state: OnboardingUiState,
    kind: EchoScreenKind,
    metrics: EchoLayoutMetrics,
    modifier: Modifier = Modifier,
    onSearchSuggestion: (String) -> Unit,
) {
    val question = EchoScreenModel.question(state)
    val key = when (kind) {
        EchoScreenKind.Waiting -> "waiting"
        EchoScreenKind.Error -> "error"
        // The card's question rides in Echo's bubble; with Echo folded away it takes this slot instead.
        EchoScreenKind.Deck -> if (metrics.showEcho) "deck" else "deck:${state.aiDeck.current?.id}"
        else -> "question:${state.aiCurrentTurn}:${question.orEmpty()}"
    }
    AnimatedContent(
        targetState = key,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = EchoMotion.handoff(),
        contentAlignment = Alignment.TopStart,
        label = "Echo question",
    ) { target ->
        when {
            target == "waiting" -> EchoWaiting()
            target == "error" -> EchoErrorPanel()
            target == "deck" -> Box(Modifier.fillMaxWidth())
            target.startsWith("deck:") -> EchoDeckQuestion(state.aiDeck.current?.question.orEmpty(), metrics)
            else -> EchoQuestion(state, kind, question, metrics, onSearchSuggestion)
        }
    }
}

@Composable
private fun EchoQuestion(state: OnboardingUiState, kind: EchoScreenKind, question: String?, metrics: EchoLayoutMetrics, onSearchSuggestion: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The question takes the room it is given and shrinks its type to fit; nothing scrolls.
        Text(
            question ?: stringResource(R.string.echo_initial_question),
            Modifier.fillMaxWidth().weight(1f, fill = false).testTag("echo_question").semantics { heading() },
            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 1.15.em),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = metrics.questionMin, maxFontSize = metrics.questionMax, stepSize = 1.sp),
        )
        val suggestion = state.aiSearchSuggestion
        if (kind == EchoScreenKind.Asking && !suggestion.isNullOrBlank()) {
            FilledTonalButton(
                onClick = { onSearchSuggestion(suggestion) },
                modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp).testTag("echo_search_suggestion"),
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.echo_search_show, suggestion), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EchoDeckQuestion(question: String, metrics: EchoLayoutMetrics) {
    Text(
        question,
        Modifier.fillMaxWidth().testTag("echo_deck_question").semantics { heading() },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        autoSize = TextAutoSize.StepBased(minFontSize = metrics.questionMin, maxFontSize = MaterialTheme.typography.titleLarge.fontSize, stepSize = 1.sp),
    )
}

/** The question's skeleton; the answer rows' skeleton waits in the answer slot below. */
@Composable
private fun EchoWaiting() {
    val thinking = stringResource(R.string.echo_thinking)
    ShimmerScope(active = true) {
        Column(
            Modifier.fillMaxWidth().testTag("echo_waiting").clearAndSetSemantics { contentDescription = thinking },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EchoSkeleton(Modifier.fillMaxWidth(0.9f).height(30.dp), RoundedCornerShape(10.dp))
            EchoSkeleton(Modifier.fillMaxWidth(0.55f).height(30.dp), RoundedCornerShape(10.dp))
        }
    }
}

@Composable
internal fun EchoSkeleton(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(modifier.m3Shimmer(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest, shape))
}

@Composable
private fun EchoErrorPanel() {
    Surface(
        Modifier.fillMaxWidth().testTag("echo_error"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.echo_error_title),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.echo_error_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
