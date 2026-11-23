package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.android.awaitFrame

internal data class EchoAnswerCallbacks(
    val toggle: (String) -> Unit,
    val changeDraft: (String) -> Unit,
    val startWriting: () -> Unit,
    val useChoices: () -> Unit,
    val submit: () -> Unit,
)

/**
 * Choices or the text box, in one slot. For choices (and their skeleton) the screen hands the slot
 * a fixed height: the fit's minimum plus a share of the spare space, which the rows split evenly,
 * so cards grow on roomy screens and nothing ever scrolls. Long labels shrink to fit, and on tight
 * windows the answers follow the [fit]: no icons, other columns, Something else in the footer.
 */
@Composable
internal fun EchoAnswerArea(
    state: OnboardingUiState,
    kind: EchoScreenKind,
    fit: EchoFit,
    callbacks: EchoAnswerCallbacks,
    modifier: Modifier = Modifier,
    preview: EchoShowPreview? = null,
) {
    val key = when (kind) {
        EchoScreenKind.Asking -> "choices:${state.aiCurrentTurn}:${state.aiAssistantMessage}"
        EchoScreenKind.Writing -> "writing:${state.aiCurrentTurn}"
        EchoScreenKind.Waiting -> "waiting"
        EchoScreenKind.Deck -> "deck"
        else -> "none"
    }
    AnimatedContent(targetState = key, modifier = modifier.fillMaxWidth(), transitionSpec = EchoMotion.handoff(), label = "echoAnswers") { target ->
        when {
            target == "waiting" -> EchoChoiceSkeleton(fit)
            target == "deck" -> EchoDeckCard(state.aiDeck, fit.metrics, preview, withClip = !fit.sideBySide)
            target.startsWith("choices") && EchoScreenModel.isOpeningTurn(state) -> EchoTopicGrid(target, state.aiSelectedOptions, fit, callbacks)
            target.startsWith("choices") -> EchoChoiceList(target, state.aiCurrentTurn, EchoScreenModel.choices(state), state.aiSelectedOptions, fit, callbacks)
            target.startsWith("writing") -> EchoWrittenAnswer(state, hasChoices = EchoScreenModel.choices(state).isNotEmpty(), fit.metrics, callbacks)
        }
    }
}

/**
 * Opening topics: columns of equal tiles (two, or as the fit says), connected like the later rows
 * (outer corners round, inner corners tight), sharing the slot's height. Tall screens stack the badge
 * above a one-line label; shorter ones set them side by side, and the tightest drop the badge.
 */
@Composable
private fun EchoTopicGrid(key: String, selected: Set<String>, fit: EchoFit, callbacks: EchoAnswerCallbacks) {
    val metrics = fit.metrics
    val topics = EchoInitialTopic.entries
    val columns = fit.columns
    val rows = topics.chunked(columns)
    val stagger = rememberEchoStagger(key, topics.size + 1)
    Column(Modifier.fillMaxSize().testTag("echo_topics")) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
                    row.forEachIndexed { column, topic ->
                        // Indexes follow the topics, so a new arrangement never replays the entry.
                        val index = rowIndex * columns + column
                        val picked = topic.answer in selected
                        EchoStaggeredEntry(stagger[index], Modifier.weight(1f).fillMaxHeight()) {
                            EchoAnswerSurface(
                                selected = picked,
                                corners = echoTileCorners(rowIndex, column, rows.size, row.size, picked),
                                minHeight = metrics.tileHeight,
                                onToggle = { callbacks.toggle(topic.answer) },
                            ) {
                                if (!fit.showIcons) {
                                    // The tightest windows drop the badge; the fill and rounding still show the pick.
                                    Box(Modifier.padding(horizontal = EchoAnswerTextOnlyPadding)) { EchoAnswerLabel(stringResource(topic.labelRes), maxLines = fit.labelLines, tight = fit.labelLines > 1) }
                                } else if (metrics.tileStacked) {
                                    Column(Modifier.padding(horizontal = EchoAnswerStackedPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EchoSelectionBadge(picked, topic.icon, metrics.badgeSize, shapeIndex = index)
                                        EchoAnswerLabel(stringResource(topic.labelRes), maxLines = 1)
                                    }
                                } else {
                                    EchoAnswerRow {
                                        EchoSelectionBadge(picked, topic.icon, metrics.badgeSize, shapeIndex = index)
                                        EchoAnswerLabel(stringResource(topic.labelRes), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (fit.somethingElseInline) EchoStaggeredEntry(stagger[topics.size]) { EchoSomethingElse(callbacks.startWriting) }
    }
}

/**
 * Later choices: one connected group of fixed-height rows, each with its own badge shape. Beside Echo
 * on the shortest windows they stand two to a row.
 */
@Composable
private fun EchoChoiceList(
    key: String,
    turn: Int,
    choices: List<String>,
    selected: Set<String>,
    fit: EchoFit,
    callbacks: EchoAnswerCallbacks,
) {
    val metrics = fit.metrics
    val icons = remember(choices) { EchoChoiceIcons.forChoices(choices) }
    val choice: @Composable (Int, EchoCorners) -> Unit = { index, corners ->
        val answer = choices[index]
        val picked = answer in selected
        EchoAnswerSurface(picked, corners, metrics.rowHeight, onToggle = { callbacks.toggle(answer) }) {
            if (fit.showIcons) {
                EchoAnswerRow {
                    // Shapes rotate through the set each turn, so every question looks a little new.
                    EchoSelectionBadge(picked, icons.getOrNull(index), metrics.badgeSize, shapeIndex = turn * 2 + index)
                    EchoAnswerLabel(answer.substringBefore('|').trim(), maxLines = 2)
                }
            } else {
                Box(Modifier.padding(horizontal = EchoAnswerTextOnlyPadding)) { EchoAnswerLabel(answer.substringBefore('|').trim(), maxLines = 2) }
            }
        }
    }
    val stagger = rememberEchoStagger(key, choices.size + 1)
    Column(Modifier.fillMaxSize().testTag("echo_choices")) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
            if (fit.columns <= 1) {
                choices.indices.forEach { index ->
                    val (top, bottom) = echoChoiceCorners(index, choices.size, choices[index] in selected)
                    EchoStaggeredEntry(stagger[index], Modifier.weight(1f).fillMaxWidth()) { choice(index, EchoCorners(top, top, bottom, bottom)) }
                }
            } else {
                val rows = choices.indices.chunked(fit.columns)
                rows.forEachIndexed { rowIndex, row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
                        row.forEachIndexed { column, index ->
                            val corners = echoTileCorners(rowIndex, column, rows.size, row.size, choices[index] in selected)
                            EchoStaggeredEntry(stagger[index], Modifier.weight(1f).fillMaxHeight()) { choice(index, corners) }
                        }
                    }
                }
            }
        }
        if (fit.somethingElseInline) EchoStaggeredEntry(stagger[choices.size]) { EchoSomethingElse(callbacks.startWriting) }
    }
}

@Composable
private fun EchoAnswerSurface(
    selected: Boolean,
    corners: EchoCorners,
    minHeight: Dp,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "answerFill")
    val shape = rememberEchoAnimatedShape(corners)
    Surface(
        shape = shape,
        color = container,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = minHeight)
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(selected)
            }
            .expressiveClickable(shape = shape, onClick = onToggle),
    ) {
        Box(contentAlignment = Alignment.CenterStart) { content() }
    }
}

@Composable
private fun EchoAnswerRow(content: @Composable () -> Unit) {
    Row(
        Modifier.padding(start = EchoAnswerRowStart, end = EchoAnswerRowEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EchoAnswerRowSpacing),
    ) {
        content()
    }
}

/** Labels never wrap past their limit or get cut: they shrink, down to 12sp, to fit. [tight] lines fit two in a 48dp tile. */
@Composable
private fun EchoAnswerLabel(text: String, maxLines: Int, tight: Boolean = false) {
    val style = MaterialTheme.typography.bodyLarge.let { if (tight) it.copy(lineHeight = 1.15.em) else it }
    Text(
        text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

@Composable
private fun EchoSomethingElse(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        EchoSomethingElseButton(onClick, enabled = true)
    }
}

/** The rows' skeleton, in the loaded geometry and place: as many columns as the answers will have. */
@Composable
private fun EchoChoiceSkeleton(fit: EchoFit) {
    val rows = (0 until EchoLayout.AnswerRows).chunked(fit.columns.coerceAtLeast(1))
    ShimmerScope(active = true) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(Modifier.weight(1f).fillMaxWidth().heightIn(min = fit.metrics.rowHeight), horizontalArrangement = Arrangement.spacedBy(EchoGroupGap)) {
                        row.forEachIndexed { column, _ ->
                            val corners = if (row.size == 1 && rows.size == EchoLayout.AnswerRows) {
                                echoChoiceCorners(rowIndex, rows.size, false).let { (top, bottom) -> EchoCorners(top, top, bottom, bottom) }
                            } else {
                                echoTileCorners(rowIndex, column, rows.size, row.size, false)
                            }
                            EchoSkeleton(
                                Modifier.weight(1f).fillMaxHeight().testTag("echo_skeleton_row"),
                                RoundedCornerShape(topStart = corners.topStart, topEnd = corners.topEnd, bottomEnd = corners.bottomEnd, bottomStart = corners.bottomStart),
                            )
                        }
                    }
                }
            }
            // Same geometry as the loaded choices, which end with Something else unless it waits in the footer.
            if (fit.somethingElseInline) Spacer(Modifier.height(EchoLayout.SomethingElseHeight))
        }
    }
}

/** Corner radii, in reading order: top start, top end, bottom end, bottom start. */
internal data class EchoCorners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp)

/** Tile corners in the connected grid: only the grid's own outer corners are round. */
internal fun echoTileCorners(row: Int, column: Int, rows: Int, columns: Int, selected: Boolean): EchoCorners {
    if (selected) return EchoCorners(EchoChoiceOuter, EchoChoiceOuter, EchoChoiceOuter, EchoChoiceOuter)
    fun corner(isOuter: Boolean) = if (isOuter) EchoChoiceOuter else EchoChoiceInner
    val top = row == 0
    val bottom = row == rows - 1
    val start = column == 0
    val end = column == columns - 1
    return EchoCorners(corner(top && start), corner(top && end), corner(bottom && end), corner(bottom && start))
}

/** Top and bottom corner radii for a row in the connected group. */
internal fun echoChoiceCorners(index: Int, count: Int, selected: Boolean): Pair<Dp, Dp> {
    if (selected) return EchoChoiceOuter to EchoChoiceOuter
    val top = if (index == 0) EchoChoiceOuter else EchoChoiceInner
    val bottom = if (index == count - 1) EchoChoiceOuter else EchoChoiceInner
    return top to bottom
}

@Composable
private fun rememberEchoAnimatedShape(target: EchoCorners): RoundedCornerShape {
    val shapeSpring = spring<Dp>(dampingRatio = 0.7f, stiffness = 500f)
    val topStart by animateDpAsState(target.topStart, shapeSpring, label = "cornerTopStart")
    val topEnd by animateDpAsState(target.topEnd, shapeSpring, label = "cornerTopEnd")
    val bottomEnd by animateDpAsState(target.bottomEnd, shapeSpring, label = "cornerBottomEnd")
    val bottomStart by animateDpAsState(target.bottomStart, shapeSpring, label = "cornerBottomStart")
    return RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)
}

private val EchoChoiceOuter = 24.dp
private val EchoChoiceInner = 6.dp
private val EchoGroupGap = EchoLayout.GroupGap

/** The soft Material shapes a picked badge blooms into, one per answer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val EchoBadgeShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Puffy,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.SoftBurst,
    MaterialShapes.Flower,
    MaterialShapes.PuffyDiamond,
)

internal fun echoBadgeShapeIndex(shapeIndex: Int): Int = Math.floorMod(shapeIndex, EchoBadgeShapes.size)

/**
 * The selection mark: a soft circle with the answer's icon that blooms into a filled, slightly
 * turned shape (each answer its own) when picked, the icon flipping to the on-primary colour. No
 * checkmarks; the row also fills and rounds, and screen readers hear the checkbox state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EchoSelectionBadge(selected: Boolean, icon: ImageVector?, size: Dp = 40.dp, shapeIndex: Int = 0) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "badgeBloom")
    val fill by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "badgeFill")
    val tint by animateColorAsState(if (selected) colors.onPrimary else colors.primary, label = "badgeIcon")
    val target = echoBadgeShapeIndex(shapeIndex)
    val morph = remember(target) { Morph(MaterialShapes.Circle.normalized(), EchoBadgeShapes[target].normalized()) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            morph.toPath(progress.coerceIn(0f, 1f), path)
            matrix.reset()
            matrix.scale(this.size.width, this.size.height)
            path.transform(matrix)
            path.translate(Offset(this.size.width / 2f, this.size.height / 2f) - path.getBounds().center)
            // The spring's small overshoot reads as a bloom; the turn settles at 30°.
            rotate(progress * 30f) { drawPath(path, fill) }
        }
        if (icon != null) Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

@Composable
private fun EchoWrittenAnswer(state: OnboardingUiState, hasChoices: Boolean, metrics: EchoLayoutMetrics, callbacks: EchoAnswerCallbacks) {
    val focus = remember { FocusRequester() }
    val showLanguageHint = EchoLanguageHint.show(state.aiCustomInputText, LocalConfiguration.current.locales[0].language)
    LaunchedEffect(Unit) {
        // Wait for the hand-off to place the field before asking for the keyboard.
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = state.aiCustomInputText,
            onValueChange = { callbacks.changeDraft(it.take(EchoMaxAnswerLength)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("echo_answer_input"),
            label = { Text(stringResource(R.string.echo_your_answer)) },
            placeholder = { Text(stringResource(R.string.echo_answer_placeholder)) },
            supportingText = if (showLanguageHint) {
                { Text(stringResource(R.string.echo_language_hint)) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = if (metrics.tier == EchoLayoutTier.Compact) 2 else 3,
            maxLines = if (metrics.tier == EchoLayoutTier.Compact) 3 else 5,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (state.aiCustomInputText.isNotBlank()) callbacks.submit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (hasChoices) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = callbacks.useChoices, modifier = Modifier.heightIn(min = 48.dp).testTag("echo_use_choices")) {
                    Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.echo_use_choices), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

internal const val EchoMaxAnswerLength = 500
