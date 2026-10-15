package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.home.R

@Composable
internal fun MixtapeSelectorCover(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(YourShowsLayout.TileWidth).height(YourShowsLayout.TileHeight),
) {
    val shape = rememberYourShowsSelectorShape(isSelected)
    val surface by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        YourShowsMotion.effects(),
        label = "mix_surface",
    )
    val ink by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        YourShowsMotion.effects(),
        label = "mix_ink",
    )
    val description = stringResource(R.string.home_shows_mix)
    Box(
        modifier = modifier.clip(RoundedCornerShape(22.dp))
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = RoundedCornerShape(22.dp), onClick = onClick)
            .semantics {
                selected = isSelected
                role = Role.Button
                contentDescription = description
            }
            .testTag("home_show_selector_mix").padding(YourShowsLayout.TilePadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.widthIn(max = YourShowsLayout.CoverSize).fillMaxWidth().aspectRatio(1f)
                .clip(shape).background(surface).testTag("home_mix_selector_visual"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = ink, modifier = Modifier.fillMaxSize(0.6f))
        }
    }
}
