package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/** Open setup canvas; navigation, analytics and persisted dismissal stay with the feed. */
@Composable
fun HomeImportBanner(
    onAiOnboardingClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headingStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.semiBold)
    val density = LocalDensity.current
    var measuredFirstLineHeight by remember(headingStyle, density) { mutableFloatStateOf(Float.NaN) }
    val firstLineHeight = with(density) {
        if (measuredFirstLineHeight.isNaN()) headingStyle.lineHeight.toDp() else measuredFirstLineHeight.toDp()
    }
    val headingTop = ((48.dp - firstLineHeight) / 2).coerceAtLeast(0.dp)
    val dismissTop = ((firstLineHeight - 48.dp) / 2).coerceAtLeast(0.dp)
    Column(
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxWidth().padding(vertical = 8.dp).testTag("home_setup_banner"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.home_setup_title),
                style = headingStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(top = headingTop).semantics { heading() },
                onTextLayout = {
                    val height = it.getLineBottom(0) - it.getLineTop(0)
                    if (measuredFirstLineHeight != height) measuredFirstLineHeight = height
                },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.padding(top = dismissTop).size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.home_setup_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HomeSetupActions(onAiOnboardingClick, onSearchClick, onImportClick)
    }
}

@Composable
private fun HomeSetupActions(onSuggestions: () -> Unit, onSearch: () -> Unit, onImport: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val reflow = fontScale > 1.3f || maxWidth < 280.dp
        val singleColumn = maxWidth.value / fontScale < 180f
        if (reflow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeSetupSuggestions(
                    onClick = onSuggestions,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    horizontal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (singleColumn) {
                    HomeSetupSecondary(R.string.home_setup_search, Icons.Rounded.Search, onSearch, RoundedCornerShape(12.dp), Modifier.fillMaxWidth(), false)
                    HomeSetupSecondary(R.string.home_setup_import, Icons.Rounded.Download, onImport, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp), Modifier.fillMaxWidth(), false)
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeSetupSecondary(R.string.home_setup_search, Icons.Rounded.Search, onSearch, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 12.dp), Modifier.weight(1f).fillMaxHeight(), true)
                        HomeSetupSecondary(R.string.home_setup_import, Icons.Rounded.Download, onImport, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp), Modifier.weight(1f).fillMaxHeight(), true)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeSetupSuggestions(
                    onClick = onSuggestions,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
                    horizontal = false,
                    modifier = Modifier.weight(1.18f).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeSetupSecondary(R.string.home_setup_search, Icons.Rounded.Search, onSearch, RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp), Modifier.weight(1f).fillMaxWidth(), false)
                    HomeSetupSecondary(R.string.home_setup_import, Icons.Rounded.Download, onImport, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 28.dp), Modifier.weight(1f).fillMaxWidth(), false)
                }
            }
        }
    }
}

@Composable
private fun HomeSetupSuggestions(onClick: () -> Unit, shape: Shape, horizontal: Boolean, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier.testTag("home_setup_suggestions").semantics { role = Role.Button }.expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = shape, onClick = onClick),
    ) {
        if (horizontal) {
            Row(Modifier.padding(16.dp).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                HomeSetupSuggestionsLabel(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        } else {
            Column(Modifier.padding(16.dp).heightIn(min = 108.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    HomeSetupSuggestionsLabel(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeSetupSuggestionsLabel(modifier: Modifier) {
    Text(
        text = stringResource(R.string.home_setup_suggestions),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = GoogleSansWeight.semiBold),
        modifier = modifier,
    )
}

@Composable
private fun HomeSetupSecondary(label: Int, icon: ImageVector, onClick: () -> Unit, shape: Shape, modifier: Modifier, verticalIcon: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier.testTag(if (label == R.string.home_setup_search) "home_setup_search" else "home_setup_import").semantics { role = Role.Button }.expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = shape, onClick = onClick),
    ) {
        if (verticalIcon) {
            Column(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp).heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}
