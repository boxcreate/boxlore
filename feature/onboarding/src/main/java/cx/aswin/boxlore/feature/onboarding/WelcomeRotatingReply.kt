package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

private val ReplyIconGap = 7.dp

/** One measured slot keeps the invitation steady while words and symbols take turns. */
@Composable
internal fun WelcomeRotatingReply(clock: WelcomeMotionClock) {
    val prefix = stringResource(R.string.welcome_reply_prefix)
    val accessibleReply = stringResource(R.string.welcome_lore)
    val words = stringArrayResource(R.array.welcome_reply_words).toList()
    val style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp)
    val wordStyle = style.copy(fontWeight = GoogleSansWeight.semiBold)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val iconSize = with(density) { 19.sp.toDp() * 1.16f }
    val prefixGap = 8.dp
    val index by remember(clock) {
        derivedStateOf {
            WelcomeWordLoopMotion.cue(clock.elapsedMillis.value, clock.animationsDisabled.value).index
        }
    }

    // Rotating decorative copy should not continuously interrupt a screen reader.
    BoxWithConstraints(Modifier.clearAndSetSemantics { contentDescription = accessibleReply }) {
        val prefixWidth = measurer.measure(prefix, style).size.width
        val availableWordWidth = (constraints.maxWidth - with(density) { (iconSize + ReplyIconGap).roundToPx() }).coerceAtLeast(1)
        val wordMeasurements = remember(measurer, words, wordStyle, availableWordWidth) {
            words.map { measurer.measure(it, wordStyle, constraints = Constraints(maxWidth = availableWordWidth)) }
        }
        val wordWidth = with(density) { wordMeasurements.maxOf { it.size.width }.toDp() }
        val slotHeight = with(density) { wordMeasurements.maxOf { it.size.height }.toDp() }.coerceAtLeast(iconSize + 8.dp)
        val slotWidth = iconSize + ReplyIconGap + wordWidth
        val fitsOneLine = prefixWidth + with(density) { (prefixGap + slotWidth).roundToPx() } <= constraints.maxWidth
        if (fitsOneLine) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(prefixGap)) {
                WelcomeReplyPrefix(prefix, style, clock)
                WelcomeReplySlot(clock, words[index], index, wordStyle, wordWidth, slotHeight, iconSize)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WelcomeReplyPrefix(prefix, style, clock)
                WelcomeReplySlot(clock, words[index], index, wordStyle, wordWidth, slotHeight, iconSize)
            }
        }
    }
}

@Composable
private fun WelcomeReplyPrefix(prefix: String, style: TextStyle, clock: WelcomeMotionClock) {
    Box(Modifier.clip(RoundedCornerShape(0.dp))) {
        Text(
            prefix,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                val eased = if (clock.revealComplete.value) 1f else WelcomeEntryTiming.lineProgress(clock.elapsedMillis.value, WelcomeEntryTiming.ReplyDelayMillis)
                alpha = eased
                translationY = size.height * (1f - eased)
            },
        )
    }
}

@Composable
private fun WelcomeReplySlot(
    clock: WelcomeMotionClock,
    word: String,
    index: Int,
    style: TextStyle,
    wordWidth: Dp,
    slotHeight: Dp,
    iconSize: Dp,
) {
    val icons = remember {
        listOf(
            Icons.Rounded.Fingerprint,
            Icons.Rounded.SentimentVerySatisfied,
            Icons.Rounded.Newspaper,
            Icons.Rounded.Lightbulb,
            Icons.Rounded.AutoStories,
            Icons.Rounded.Science,
            Icons.Rounded.AutoAwesome,
        )
    }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.width(iconSize + ReplyIconGap + wordWidth).height(slotHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icons[index],
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize).graphicsLayer {
                val frame = WelcomeWordLoopMotion.frame(WelcomeWordLoopMotion.cue(clock.elapsedMillis.value, clock.animationsDisabled.value))
                alpha = frame.iconAlpha
                scaleX = frame.iconScale
                scaleY = frame.iconScale
                rotationZ = frame.iconRotationDegrees
            },
        )
        Spacer(Modifier.width(ReplyIconGap))
        Box(Modifier.width(wordWidth).height(slotHeight).clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.CenterStart) {
            Text(
                text = word,
                style = style,
                color = accent,
                modifier = Modifier.graphicsLayer {
                    val frame = WelcomeWordLoopMotion.frame(WelcomeWordLoopMotion.cue(clock.elapsedMillis.value, clock.animationsDisabled.value))
                    alpha = frame.wordAlpha
                    translationY = size.height * frame.wordTranslationFraction
                },
            )
        }
    }
}
