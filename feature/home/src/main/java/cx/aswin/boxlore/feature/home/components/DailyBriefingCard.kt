package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.BRIEFING_COVER_ASPECT_RATIO
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCover
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCoverMetadata
import cx.aswin.boxlore.core.designsystem.briefing.BriefingEditorialTheme
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.home.HomeSectionsUiState
import cx.aswin.boxlore.feature.home.LocalHomeSectionsUiState
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

// Kept as a public state name for callers and existing Home contracts.
enum class DailyBriefingCardState { NORMAL, CONFIRM_DISMISS, CONFIRM_FOREVER }

@Suppress("LongParameterList") // Existing Home callback API; composition is split below.
@Composable
fun DailyBriefingCard(
    briefing: Briefing,
    chapters: List<Chapter>,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    playbackStatus: EpisodeStatus? = null,
    playbackProgress: Float? = null,
    isBuffering: Boolean = false,
    onDismissForever: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
) {
    val briefState = rememberBriefCardState(briefing)
    val expanded = briefState.expanded
    val cardState = briefState.step
    val setExpanded = briefState.setExpanded
    val setCardState = briefState.setStep
    val locale = currentLocale()
    val displayDate = remember(briefing.date, locale) { briefingCardDate(briefing.date, locale) }
    val durationMinutes = if (chapters.isNotEmpty()) ((chapters.last().startTime + 30) / 60).toInt().coerceAtLeast(1) else null
    val remainingMinutes = briefingRemainingMinutes(durationMinutes, playbackProgress)
    fun track(action: String) = AnalyticsHelper.trackDailyBriefingInteraction(action, briefing.region, briefing.date)

    val window = LocalBoxloreWindowLayout.current
    val tablet = window.usesTabletLayout
    // On a sideways phone the cover is cropped to half the window's height, so the card fits on screen.
    val phoneCoverCap = briefCoverCap(window)
    val metadata = briefingCardMetadata(displayDate, chapters, durationMinutes)
    val presentation = DailyBriefingPresentation(briefing, chapters, isPlaying, isBuffering, playbackStatus, playbackProgress, remainingMinutes)
    BriefingEditorialTheme {
        Surface(onClick = { if (cardState == DailyBriefingCardState.NORMAL) onClick() }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp, modifier = modifier.boxloreCardWidth(if (tablet) BoxloreCardFamily.Reading else BoxloreCardFamily.Hero).fillMaxWidth()) {
            Box {
                BriefingCardLayout(tablet = tablet, cover = {
                    BriefingCover(
                        region = briefing.region,
                        editionDate = briefing.date,
                        modifier = if (tablet) Modifier.fillMaxSize() else Modifier.fillMaxWidth().briefCoverSize(phoneCoverCap).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).testTag("home_brief_cover"),
                        artworkContentScale = if (tablet || phoneCoverCap != null) ContentScale.Crop else ContentScale.Fit,
                        trailing = {
                            if (!tablet) {
                            Surface(Modifier.align(AbsoluteAlignment.TopRight).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                BriefingDismissButton(cardState, setCardState, ::track)
                            }
                            }
                        },
                        footer = {
                            if (!tablet) BriefingCoverMetadata(metadata, Modifier.align(Alignment.BottomStart))
                        },
                    )
                }, body = {
                    Surface(shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        AnimatedContent(cardState, label = "briefHomeState") { state ->
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (tablet) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(metadata, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        BriefingDismissButton(cardState, setCardState, ::track)
                                    }
                                }
                                when (state) {
                                    DailyBriefingCardState.NORMAL -> DailyBriefingNormalContent(
                                        presentation,
                                        onPlayPauseClick,
                                        onClick,
                                        expanded,
                                        onExpandedChange = setExpanded,
                                    )
                                    DailyBriefingCardState.CONFIRM_DISMISS -> {
                                        Text(stringResource(R.string.briefing_hide_today_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.briefing_hide_today_description), style = MaterialTheme.typography.bodyMedium)
                                        BriefingButtonPair(
                                            primary = stringResource(R.string.briefing_hide_today),
                                            onPrimary = {
                                                track("dismissed_today")
                                                briefState.confirmHide()
                                                onDismiss()
                                            },
                                            primaryModifier = Modifier.testTag("home_brief_hide_today"),
                                            secondary = stringResource(R.string.briefing_always_hide),
                                            onSecondary = {
                                                track("dismiss_forever_initiated")
                                                setCardState(DailyBriefingCardState.CONFIRM_FOREVER)
                                            },
                                            secondaryModifier = Modifier.testTag("home_brief_always_hide"),
                                        )
                                    }
                                    DailyBriefingCardState.CONFIRM_FOREVER -> {
                                        Text(stringResource(R.string.briefing_always_hide_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.briefing_always_hide_description), style = MaterialTheme.typography.bodyMedium)
                                        BriefingButtonPair(
                                            primary = stringResource(R.string.briefing_confirm_always_hide),
                                            onPrimary = {
                                                track("dismissed_forever")
                                                briefState.confirmHide()
                                                onDismissForever()
                                            },
                                            primaryModifier = Modifier.testTag("home_brief_confirm_always_hide"),
                                            secondary = stringResource(R.string.briefing_send_feedback),
                                            onSecondary = {
                                                track("feedback_clicked")
                                                onFeedbackClick()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun briefingCardMetadata(date: String, chapters: List<Chapter>, duration: Int?): String = listOfNotNull(
    date,
    chapters.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.briefing_story_count, it.size, it.size) },
    duration?.let { stringResource(R.string.briefing_listen_minutes, it) },
).joinToString(" · ")

private fun briefingRemainingMinutes(duration: Int?, progress: Float?): Int? = duration?.let { minutes ->
    (minutes * (1 - (progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f))).toInt().coerceAtLeast(1)
}

@Composable
private fun BriefingDismissButton(state: DailyBriefingCardState, onStateChange: (DailyBriefingCardState) -> Unit, track: (String) -> Unit) {
    val normal = state == DailyBriefingCardState.NORMAL
    IconButton(onClick = {
        track(if (normal) "dismiss_initiated" else "dismiss_cancelled")
        onStateChange(if (normal) DailyBriefingCardState.CONFIRM_DISMISS else DailyBriefingCardState.NORMAL)
    }, Modifier.testTag("home_brief_hide")) {
        Icon(Icons.Rounded.Close, stringResource(if (normal) R.string.briefing_hide else R.string.briefing_cancel_hide), Modifier.size(20.dp))
    }
}

private data class DailyBriefingPresentation(
    val briefing: Briefing,
    val chapters: List<Chapter>,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val playbackStatus: EpisodeStatus?,
    val playbackProgress: Float?,
    val remainingMinutes: Int?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyBriefingNormalContent(
    state: DailyBriefingPresentation,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val briefing = state.briefing
    val chapters = state.chapters
    val isPlaying = state.isPlaying
    val isBuffering = state.isBuffering
    val playbackStatus = state.playbackStatus
    val playbackProgress = state.playbackProgress
    val remainingMinutes = state.remainingMinutes

    Text(briefing.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 30.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onPlayPauseClick,
            shapes = ButtonDefaults.shapes(shape = BriefingLeadingShape, pressedShape = BriefingPressedShape),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) {
            if (isBuffering) {
                BoxLoreLoader.CircularWavy(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (isPlaying) {
                    stringResource(R.string.briefing_card_pause)
                } else if (playbackStatus == EpisodeStatus.IN_PROGRESS) {
                    stringResource(R.string.briefing_card_resume)
                } else {
                    stringResource(R.string.briefing_card_listen)
                },
            )
        }
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(shape = BriefingTrailingShape, pressedShape = BriefingPressedShape),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.briefing_open))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(16.dp))
        }
    }
    if (playbackStatus == EpisodeStatus.IN_PROGRESS && playbackProgress != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { playbackProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f }, modifier = Modifier.weight(1f).height(4.dp), gapSize = 0.dp, drawStopIndicator = {})
            remainingMinutes?.let { Text(stringResource(R.string.briefing_remaining_minutes, it), style = MaterialTheme.typography.labelSmall) }
        }
    }
    DailyBriefingStories(briefing, chapters, expanded, onExpandedChange)
}

@Composable
private fun DailyBriefingStories(
    briefing: Briefing,
    chapters: List<Chapter>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val visible = chapters.take(if (expanded) chapters.size else 3)
    val expandLabel = if (expanded) {
        stringResource(R.string.briefing_show_fewer_stories)
    } else {
        stringResource(R.string.briefing_show_all_stories, chapters.size)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEachIndexed { index, chapter ->
            val canExpand = chapters.size > 3 && index == visible.lastIndex
            // Every row sits 4dp in from the card, so the numbers line up and the toggle row's rounded
            // press stays clear of the card's edge.
            val rowModifier = Modifier.padding(horizontal = 4.dp).then(
                if (canExpand) {
                    Modifier.semantics {
                        contentDescription = expandLabel
                        role = Role.Button
                    }
                        .expressiveClickable(shape = BoxLoreShapes.medium) {
                            onExpandedChange(!expanded)
                            AnalyticsHelper.trackDailyBriefingInteraction("card_chapters_toggled", briefing.region, briefing.date, mapOf("expanded" to !expanded))
                        }
                } else {
                    Modifier
                },
            )
            Row(
                rowModifier.fillMaxWidth().heightIn(min = if (canExpand) 48.dp else 32.dp).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    String.format(Locale.ROOT, "%02d", index + 1),
                    modifier = Modifier.width((28 * LocalDensity.current.fontScale).dp),
                    color = MaterialTheme.colorScheme.primary,
                    // The app's UI font, with equal-width digits so the numbers stay aligned.
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = GoogleSansWeight.medium,
                    fontSize = 18.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    chapter.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (canExpand) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) {
                            stringResource(R.string.briefing_less_inline)
                        } else {
                            stringResource(R.string.briefing_all_stories_inline, chapters.size)
                        },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/** The brief's `yyyy-MM-dd` [date] as a short day and month in [locale] ("Nov 20"); as sent when unreadable. */
internal fun briefingCardDate(date: String, locale: Locale): String = runCatching {
    val millis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    LocaleFormats.date(millis, LocaleFormats.DateStyle.DAY_MONTH, locale, TimeZone.getTimeZone("UTC"))
}.getOrDefault(date)

/** The Brief's paired buttons: rounded at the outer ends, square-ish where they meet. */
private val BriefingLeadingShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp)
private val BriefingTrailingShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp)

/** Both buttons of a pair squash to this while pressed, as boxlore's other paired buttons do. */
private val BriefingPressedShape = RoundedCornerShape(18.dp)

/**
 * A hide step's two choices laid out like the card's Listen / Open pair: the confirm action filled,
 * the other tonal, side by side across the card and growing together with the text.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongParameterList") // Two buttons' labels, actions and test hooks.
private fun BriefingButtonPair(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    primaryModifier: Modifier = Modifier,
    secondaryModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("home_brief_confirm_pair"),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPrimary,
            shapes = ButtonDefaults.shapes(shape = BriefingLeadingShape, pressedShape = BriefingPressedShape),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = primaryModifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) { Text(primary, textAlign = TextAlign.Center) }
        FilledTonalButton(
            onClick = onSecondary,
            shapes = ButtonDefaults.shapes(shape = BriefingTrailingShape, pressedShape = BriefingPressedShape),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = secondaryModifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        ) { Text(secondary, textAlign = TextAlign.Center) }
    }
}

/** On a phone held sideways, the cover's height cap: half the window. Null elsewhere (the cover keeps its 1.6 ratio). */
internal fun briefCoverCap(window: cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout): Dp? =
    if (!window.usesTabletLayout && window.widthDp > window.heightDp) (window.heightDp * 0.5f).dp else null

/** The cover at its 1.6 ratio across the card, no taller than [cap]. */
private fun Modifier.briefCoverSize(cap: Dp?): Modifier = layout { measurable, constraints ->
    val natural = (constraints.maxWidth / BRIEFING_COVER_ASPECT_RATIO).toInt()
    val height = if (cap == null) natural else minOf(natural, cap.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** The Brief card's hide step and story list, with their setters. */
private class BriefCardState(
    val step: DailyBriefingCardState,
    val expanded: Boolean,
    val setStep: (DailyBriefingCardState) -> Unit,
    val setExpanded: (Boolean) -> Unit,
    /** The listener confirmed a hide: the saved step resets once the card leaves. */
    val confirmHide: () -> Unit,
)

/**
 * Home keeps the hide step and the story list above its grid ([LocalHomeSectionsUiState]), so they
 * survive window changes; elsewhere (a single card in a test or preview) the card keeps its own.
 */
@Composable
private fun rememberBriefCardState(briefing: Briefing): BriefCardState {
    val sections = LocalHomeSectionsUiState.current
    val edition = HomeSectionsUiState.briefKey(briefing.region, briefing.date)
    var localExpanded by remember(briefing.region, briefing.date) { mutableStateOf(false) }
    var localStep by remember(briefing.region, briefing.date) { mutableStateOf(DailyBriefingCardState.NORMAL) }
    val setStep: (DailyBriefingCardState) -> Unit = if (sections != null) { value -> sections.setBriefStep(edition, value) } else { value -> localStep = value }
    val setExpanded: (Boolean) -> Unit = if (sections != null) { value -> sections.setBriefExpanded(edition, value) } else { value -> localExpanded = value }
    // A confirmed hide resets the saved step once the card leaves, so the Brief comes back (turned on
    // again in Settings) as its normal card, not on the question it was hidden from.
    var hideConfirmed by remember(briefing.region, briefing.date) { mutableStateOf(false) }
    val resetOnLeave by rememberUpdatedState(hideConfirmed)
    val currentSetStep by rememberUpdatedState(setStep)
    val currentSetExpanded by rememberUpdatedState(setExpanded)
    DisposableEffect(briefing.region, briefing.date) {
        onDispose {
            if (resetOnLeave) {
                currentSetStep(DailyBriefingCardState.NORMAL)
                currentSetExpanded(false)
            }
        }
    }
    return BriefCardState(
        step = sections?.briefStep(edition) ?: localStep,
        expanded = sections?.briefExpanded(edition) ?: localExpanded,
        setStep = setStep,
        setExpanded = setExpanded,
        confirmHide = { hideConfirmed = true },
    )
}
