package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.logic.HomeMixMode

@Composable
internal fun AnimatedHomeMixTitle(mode: HomeMixMode) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = { yourShowsContentTransform(forward = targetState == HomeMixMode.OFFLINE) },
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
            style = YourShowsText.title(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AnimatedHomeMixSubtitle(subtitle: String) {
    AnimatedContent(
        targetState = subtitle,
        transitionSpec = {
            fadeIn(
                animationSpec = YourShowsMotion.EnterFade,
            ) togetherWith
                fadeOut(animationSpec = YourShowsMotion.ExitFade)
        },
        label = "home_mix_subtitle",
    ) { activeSubtitle ->
        Text(
            text = activeSubtitle,
            style = YourShowsText.subtitle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
