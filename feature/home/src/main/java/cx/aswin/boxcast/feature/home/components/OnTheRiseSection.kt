package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily

@Composable
fun OnTheRiseSection(
    podcasts: List<Podcast>,
    onPodcastClick: (Podcast) -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) { 
        OnTheRiseHeader(onHeaderClick = onHeaderClick)
        OnTheRiseRail(podcasts = podcasts, onPodcastClick = onPodcastClick)
    }
}

@Composable
fun OnTheRiseHeader(
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp), // 8dp + 16dp grid padding = 24dp edge
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Context Icon
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            // Title
            Text(
                text = "On The Rise", 
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = SectionHeaderFontFamily
                ),
                letterSpacing = 0.sp 
            )
        }

        // Action / Decorator
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onHeaderClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "See All",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun OnTheRiseRail(
    podcasts: List<Podcast>,
    onPodcastClick: (Podcast) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = podcasts.size,
            key = { i -> podcasts[i].id }
        ) { i ->
            RisingCard(
                podcast = podcasts[i],
                onClick = { onPodcastClick(podcasts[i]) }
            )
        }
    }
}
