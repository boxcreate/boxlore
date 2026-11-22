package cx.aswin.boxlore.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.AppLanguage

/**
 * The language question, the first screen some listeners see, built like Welcome: the brand panel,
 * where a greeting cycles through every language boxlore speaks, an English headline, then the
 * suggested language as the main button (Echo peeks over it) and the other languages as one joined
 * block of tiles, each named in its own language. A pick applies at once and onboarding
 * moves on to Welcome. The page scrolls, so short and split windows reach every choice.
 */
@Composable
internal fun AppLanguageQuestionContent(
    question: AppLanguageQuestion,
    onChoose: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = rememberWelcomeMotionClock()
    val colors = MaterialTheme.colorScheme
    // The same rich panel as Welcome, so the two screens read as one opening.
    val panel = openingPanelColors()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface)) {
        if (languageQuestionTwoPane(maxWidth, maxHeight)) {
            WelcomeStatusBars(panel.container, coverVisible = false)
            TwoPaneLanguageQuestion(clock, question, panel, maxWidth, maxHeight, onChoose)
        } else {
            SingleColumnLanguageQuestion(clock, question, panel, onChoose)
        }
    }
}

/** The greeting's size: large, but never so tall it crowds the choices on a short window. */
internal fun languageGreetingSize(width: Dp, height: Dp): Float =
    minOf(width.value * 0.19f, height.value * 0.1f).coerceIn(40f, 92f)

@Composable
private fun SingleColumnLanguageQuestion(
    clock: WelcomeMotionClock,
    question: AppLanguageQuestion,
    panel: LanguagePanelColors,
    onChoose: (AppLanguage) -> Unit,
) {
    val scroll = rememberScrollState()
    var panelHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val panelVisible by remember(scroll, statusBarHeight) {
        derivedStateOf { scroll.value + statusBarHeight < panelHeight }
    }
    WelcomeStatusBars(panel.container, panelVisible && !LocalBoxloreWindowLayout.current.usesTabletLayout)

    BoxWithConstraints(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val compact = viewportHeight < LanguageCompactHeight
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = viewportHeight)) {
            LanguageGreetingPanel(
                clock,
                question.all,
                greetingSize = languageGreetingSize(viewportWidth, viewportHeight),
                panel = panel,
                shape = RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp),
                // On tall phones the panel grows instead of leaving a gap above the choices.
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = if (compact) 0.dp else viewportHeight * LanguagePanelShare)
                    .onSizeChanged { panelHeight = it.height },
                compact = compact,
            )
            LanguageHeadline(clock, viewportWidth, Modifier.padding(horizontal = 26.dp).padding(top = if (compact) 16.dp else 24.dp))
            // Echo's peek borrows the free space above the main button, as on Welcome.
            val density = LocalDensity.current
            var echoRoom by remember { mutableStateOf(0.dp) }
            Spacer(Modifier.weight(1f).onSizeChanged { echoRoom = with(density) { it.height.toDp() } + LanguageChoicesGap })
            LanguageChoices(
                clock,
                question,
                echoCell = welcomeEchoCell(echoRoom),
                onChoose = onChoose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = LanguageChoicesGap)
                    .navigationBarsPadding().padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun TwoPaneLanguageQuestion(
    clock: WelcomeMotionClock,
    question: AppLanguageQuestion,
    panel: LanguagePanelColors,
    width: Dp,
    height: Dp,
    onChoose: (AppLanguage) -> Unit,
) {
    OnboardingOpeningFrame(
        panel,
        width,
        height,
        rememberScrollState(),
        panelBottomPadding = 30.dp,
        panelContent = { pane ->
            LanguageGreeting(
                clock,
                question.all,
                languageGreetingSize(pane.width, pane.height * 1.6f),
                panel.content,
                Modifier.padding(horizontal = 26.dp).padding(top = 28.dp),
            )
        },
    ) { pane ->
        // Echo peeks over the main button when the pane has room above it.
        val echoRoom = if (pane.height >= OpeningPaneEchoHeight) OpeningPaneEchoRoom else 0.dp
        LanguageHeadline(clock, minOf(pane.width, OpeningPaneContentWidth), Modifier)
        Spacer(Modifier.height(maxOf(echoRoom, LanguageChoicesGap)))
        LanguageChoices(clock, question, echoCell = welcomeEchoCell(echoRoom), onChoose = onChoose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LanguageGreetingPanel(
    clock: WelcomeMotionClock,
    languages: List<AppLanguage>,
    greetingSize: Float,
    panel: LanguagePanelColors,
    shape: RoundedCornerShape,
    modifier: Modifier,
    compact: Boolean,
) {
    // The logo sits at the top and the greeting at the bottom, however tall the panel grows.
    OpeningPanel(panel, shape, bottomPadding = if (compact) 18.dp else 30.dp, modifier, Arrangement.SpaceBetween) {
        LanguageGreeting(
            clock,
            languages,
            greetingSize,
            panel.content,
            Modifier.padding(horizontal = 26.dp).padding(top = if (compact) 10.dp else 28.dp),
        )
    }
}

/**
 * One greeting at a time ("Hola"), with "Pick your language" in that language under it, both rising
 * into masked lines together. Decorative: screen readers get the English headline instead.
 */
@Composable
private fun LanguageGreeting(clock: WelcomeMotionClock, languages: List<AppLanguage>, greetingSize: Float, color: Color, modifier: Modifier) {
    val words = languages.map { stringResource(it.greetingRes()) }
    val prompts = languages.map { stringResource(it.promptRes()) }
    val fontSize = greetingSize.sp
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.08f,
        fontWeight = GoogleSansWeight.bold,
        letterSpacing = (-1.2).sp,
    )
    val promptStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.medium)
    val index by remember(clock, words.size) {
        derivedStateOf { LanguageGreetingMotion.frame(clock.elapsedMillis.value, words.size, clock.animationsDisabled.value).index }
    }
    val motion = Modifier.graphicsLayer {
        val frame = LanguageGreetingMotion.frame(clock.elapsedMillis.value, words.size, clock.animationsDisabled.value)
        alpha = frame.alpha
        translationY = size.height * frame.translationFraction
    }
    Column(modifier.fillMaxWidth().testTag("language_greeting").clearAndSetSemantics {}) {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Text(words[index], style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = motion)
        }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Text(prompts[index], style = promptStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = motion)
        }
    }
}

@Composable
private fun LanguageHeadline(clock: WelcomeMotionClock, width: Dp, modifier: Modifier) {
    val fontSize = (width.value * 0.08f).coerceIn(28f, 40f).sp
    val headline = MaterialTheme.typography.displaySmall.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.08f,
        fontWeight = GoogleSansWeight.bold,
        letterSpacing = (-0.6).sp,
    )
    Column(modifier) {
        Box(Modifier.semantics { heading() }) {
            WelcomeEntryText(stringResource(R.string.onboarding_app_language_title), clock, LanguageHeadlineDelayMillis, headline)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_app_language_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = clock.entry(LanguageBodyDelayMillis) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageChoices(
    clock: WelcomeMotionClock,
    question: AppLanguageQuestion,
    echoCell: Dp?,
    onChoose: (AppLanguage) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.graphicsLayer {
            val entry = clock.entry(LanguageChoicesDelayMillis)
            alpha = entry
            translationY = 24.dp.toPx() * (1f - entry)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            val press = remember { MutableInteractionSource() }
            if (echoCell != null) {
                Box(Modifier.matchParentSize().reachUp(echoCell * WelcomeEchoNeckFraction).clipToBounds()) {
                    WelcomeEchoPeek(clock, press, echoCell, Modifier.align(Alignment.TopEnd).padding(end = 10.dp))
                }
            }
            SuggestedLanguageButton(question.suggested, press, onClick = { onChoose(question.suggested) })
        }
        LanguageTiles(question.others, onChoose)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestedLanguageButton(language: AppLanguage, press: MutableInteractionSource, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        interactionSource = press,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(30.dp), pressedShape = RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.onboarding_app_language_suggested), style = MaterialTheme.typography.labelLarge)
                Text(
                    language.nativeName.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
                )
            }
            // The arrow sits in a disc of the screen's surface, so it reads as "go" on any theme.
            Surface(shape = CircleShape, color = LocalContentColor.current, modifier = Modifier.padding(start = 12.dp).size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** The other languages as one block of joined tiles, two to a row. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageTiles(languages: List<AppLanguage>, onChoose: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LanguageTileGap)) {
        languages.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(LanguageTileGap)) {
                pair.forEachIndexed { column, language ->
                    val corners = LanguageTileCorners.of(row * 2 + column, languages.size)
                    FilledTonalButton(
                        onClick = { onChoose(language) },
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 60.dp),
                        shapes = ButtonDefaults.shapes(
                            shape = RoundedCornerShape(corners.topStart.dp, corners.topEnd.dp, corners.bottomEnd.dp, corners.bottomStart.dp),
                            pressedShape = RoundedCornerShape(18.dp),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            language.nativeName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** "Hello" in [this] language, written in it. */
@StringRes
private fun AppLanguage.greetingRes(): Int = when (this) {
    AppLanguage.SPANISH -> R.string.onboarding_app_language_hello_es
    AppLanguage.PORTUGUESE_BRAZIL -> R.string.onboarding_app_language_hello_pt_br
    AppLanguage.GERMAN -> R.string.onboarding_app_language_hello_de
    AppLanguage.FRENCH -> R.string.onboarding_app_language_hello_fr
    AppLanguage.ENGLISH, AppLanguage.SYSTEM -> R.string.onboarding_app_language_hello_en
}

/** "Pick your language" in [this] language, written in it. */
@StringRes
private fun AppLanguage.promptRes(): Int = when (this) {
    AppLanguage.SPANISH -> R.string.onboarding_app_language_prompt_es
    AppLanguage.PORTUGUESE_BRAZIL -> R.string.onboarding_app_language_prompt_pt_br
    AppLanguage.GERMAN -> R.string.onboarding_app_language_prompt_de
    AppLanguage.FRENCH -> R.string.onboarding_app_language_prompt_fr
    AppLanguage.ENGLISH, AppLanguage.SYSTEM -> R.string.onboarding_app_language_prompt_en
}

/** Eased 0..1 entry for something that starts arriving at [delayMillis]; 1 once the reveal is done. */
private fun WelcomeMotionClock.entry(delayMillis: Long): Float =
    if (revealComplete.value || animationsDisabled.value) 1f else WelcomeEntryTiming.lineProgress(elapsedMillis.value, delayMillis)

/** Below this height (a phone on its side, a short split window) the panel and greeting shrink. */
internal val LanguageCompactHeight = 480.dp
private val LanguageChoicesGap = 24.dp

/** The stacked panel's share of a tall window, so the panel fills space a short column would leave empty. */
private const val LanguagePanelShare = 0.42f

private val LanguageTileGap = 4.dp
private const val LanguageHeadlineDelayMillis = 360L
private const val LanguageBodyDelayMillis = 560L
private const val LanguageChoicesDelayMillis = 700L
