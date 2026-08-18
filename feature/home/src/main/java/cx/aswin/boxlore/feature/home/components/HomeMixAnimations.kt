package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.logic.HomeMixMode

@Composable
internal fun AnimatedHomeMixCard(
    index: Int,
    enterFromEnd: Boolean,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val delayMillis = (index * CARD_STAGGER_MILLIS).coerceAtMost(MAX_CARD_STAGGER_MILLIS)
    LaunchedEffect(index, enterFromEnd) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter =
            slideInHorizontally(
                animationSpec =
                    tween(
                        durationMillis = CARD_ENTER_DURATION_MILLIS,
                        delayMillis = delayMillis,
                    ),
                initialOffsetX = { width ->
                    if (enterFromEnd) width / 3 else -width / 3
                },
            ) +
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis = CARD_ENTER_DURATION_MILLIS,
                            delayMillis = delayMillis,
                        ),
                ) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec =
                        tween(
                            durationMillis = CARD_ENTER_DURATION_MILLIS,
                            delayMillis = delayMillis,
                        ),
                ),
    ) {
        content()
    }
}

@Composable
internal fun AnimatedHomeMixTitle(mode: HomeMixMode) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val direction = if (targetState == HomeMixMode.OFFLINE) 1 else -1
            (
                slideInVertically(
                    animationSpec = tween(durationMillis = 280),
                    initialOffsetY = { height -> direction * height / 2 },
                ) + fadeIn(animationSpec = tween(durationMillis = 220))
            ) togetherWith
                (
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 180),
                        targetOffsetY = { height -> -direction * height / 2 },
                    ) + fadeOut(animationSpec = tween(durationMillis = 140))
                )
        },
        label = "home_mix_title",
    ) { activeMode ->
        Text(
            text =
                stringResource(
                    when (activeMode) {
                        HomeMixMode.DAILY -> R.string.home_mix_daily_title
                        HomeMixMode.OFFLINE -> R.string.home_mix_offline_title
                    },
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = GoogleSansWeight.bold,
        )
    }
}

@Composable
internal fun AnimatedHomeMixSubtitle(subtitle: String) {
    AnimatedContent(
        targetState = subtitle,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(durationMillis = 240, delayMillis = 55),
            ) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "home_mix_subtitle",
    ) { activeSubtitle ->
        Text(
            text = activeSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val CARD_STAGGER_MILLIS = 42
private const val MAX_CARD_STAGGER_MILLIS = 210
private const val CARD_ENTER_DURATION_MILLIS = 340
