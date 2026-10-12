package cx.aswin.boxlore.feature.onboarding

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme

@Composable
internal fun WelcomeScreen(
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportClick: () -> Unit,
) {
    val clock = rememberWelcomeMotionClock()
    val scroll = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    // A rich cover panel retains the brand presence in dark mode without a pale billboard.
    val panelColor = if (LocalEffectiveDarkTheme.current) colors.primaryContainer else colors.primary
    val panelContent = if (LocalEffectiveDarkTheme.current) colors.onPrimaryContainer else colors.onPrimary
    var coverPanelHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val coverVisible by remember(scroll, statusBarHeight) {
        derivedStateOf { scroll.value + statusBarHeight < coverPanelHeight }
    }
    WelcomeStatusBars(panelColor, coverVisible)

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val coverSize = (viewportWidth * 0.315f).coerceIn(90.dp, 132.dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll, enabled = scroll.maxValue > 0, overscrollEffect = null).heightIn(min = viewportHeight),
        ) {
            WelcomeCoverPanel(clock, viewportWidth, coverSize, panelColor, panelContent, Modifier.onSizeChanged { coverPanelHeight = it.height })
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 24.dp)) {
                WelcomeInvitation(clock, viewportWidth)
            }
            Spacer(Modifier.weight(1f))
            WelcomeActions(
                onHelpMeFind,
                onSearch,
                onImportClick,
                onSkip,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp).navigationBarsPadding().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun WelcomeCoverPanel(clock: WelcomeMotionClock, width: Dp, coverSize: Dp, color: Color, contentColor: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp))
            .background(color).statusBarsPadding().padding(top = 16.dp, bottom = 26.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxLoreLogo(textColor = contentColor, height = 24.dp)
            Icon(painterResource(DesignR.drawable.ic_boxlore_app_mark), null, tint = contentColor, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        WelcomeCoverReel(clock, coverSize, width, color)
    }
}

@Composable
private fun WelcomeInvitation(clock: WelcomeMotionClock, width: Dp) {
    val headline = stringResource(R.string.welcome_curiosity)
    val size = (width.value * 0.11f).coerceIn(36f, 46f).sp
    val headlineStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = size,
        lineHeight = size * 1.05f,
        fontWeight = GoogleSansWeight.bold,
        letterSpacing = (-0.8).sp,
    )
    // Preserve the whole invitation as one heading for TalkBack; masks only affect its visual entry.
    Column(
        Modifier.clearAndSetSemantics {
        contentDescription = headline
        heading()
    }
    ) {
        WelcomeEntryText(stringResource(R.string.welcome_curiosity_first_line), clock, 100L, headlineStyle)
        WelcomeEntryText(stringResource(R.string.welcome_curiosity_second_line), clock, 230L, headlineStyle)
    }
    Spacer(Modifier.height(10.dp))
    WelcomeRotatingReply(clock)
}

@Composable
private fun WelcomeEntryText(
    text: String,
    clock: WelcomeMotionClock,
    delayMillis: Long,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(Modifier.clip(RoundedCornerShape(0.dp))) {
        Text(
            text,
            style = style,
            color = color,
            modifier = Modifier.graphicsLayer {
                val progress = if (clock.revealComplete.value) {
                    1f
                } else {
                    ((clock.elapsedMillis.value - delayMillis) / 650f).coerceIn(0f, 1f)
                }
                val eased = FastOutSlowInEasing.transform(progress)
                translationY = size.height * (1f - eased)
                alpha = eased
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeActions(
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onImportClick: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onHelpMeFind,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(30.dp), pressedShape = RoundedCornerShape(20.dp)),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(end = 10.dp).size(20.dp))
                Text(
                    stringResource(R.string.welcome_find_shows),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(start = 12.dp).size(22.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            FilledTonalButton(
                onClick = onSearch,
                modifier = Modifier.weight(1.15f).fillMaxHeight().heightIn(min = 52.dp),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 7.dp, bottomEnd = 7.dp),
                    pressedShape = RoundedCornerShape(18.dp),
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.welcome_search), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
                }
            }
            FilledTonalButton(
                onClick = onImportClick,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    pressedShape = RoundedCornerShape(18.dp),
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.welcome_import), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
                }
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.welcome_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeStatusBars(panelColor: Color, coverVisible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    val defaultLightAppearance by rememberUpdatedState(!LocalEffectiveDarkTheme.current)
    DisposableEffect(view) {
        onDispose { controller.isAppearanceLightStatusBars = defaultLightAppearance }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = if (coverVisible) panelColor.luminance() > 0.5f else defaultLightAppearance
    }
}
