package cx.aswin.boxlore.feature.info

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.HtmlText
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.ShowNotes
import cx.aswin.boxlore.core.model.text.UiText
import java.util.Locale

@Composable
internal fun EpisodeDescriptionCard(notes: ShowNotes, modifier: Modifier = Modifier, location: String? = null, license: String? = null, persons: List<Person>? = null, onSeekTo: ((Long) -> Unit)? = null) {
    val context = LocalContext.current
    var expanded by rememberSaveable(notes.plainText) { mutableStateOf(false) }
    var overflows by remember(notes.plainText) { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.episode_info_about),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                modifier = Modifier.semantics { heading() },
            )
            if (expanded) {
                HtmlText(
                    notes.html,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    onLinkClicked = { url ->
                        val position = cx.aswin.boxlore.feature.info.logic.chapterLinkPositionMs(url, notes.chapters)
                        if (position != null) {
                            onSeekTo?.invoke(position)
                        } else {
                            openEpisodeLink(context, url)
                        }
                        true
                    }
                )
            } else {
                Text(
                    notes.plainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflows = it.hasVisualOverflow },
                    modifier = Modifier.fillMaxWidth().expressiveClickable(enabled = overflows) { expanded = true }
                )
            }
            if (expanded || overflows) {
                FilledTonalButton(onClick = { expanded = !expanded }, shape = CircleShape) {
                Text(stringResource(if (expanded) R.string.episode_info_show_less else R.string.episode_info_read_more))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
            }
            }
            if (!persons.isNullOrEmpty()) {
                Text(stringResource(R.string.episode_info_people), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(persons) { person -> PersonChip(person) { person.href?.let { openEpisodeLink(context, it) } } }
                }
            }
            val details = listOfNotNull(location?.takeIf(String::isNotBlank), license?.takeIf(String::isNotBlank)?.let { formatLicense(it).asString() })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatLicense(licenseCode: String): UiText {
    val clean = licenseCode.trim().lowercase(Locale.ROOT)
    if (clean.startsWith("cc-") || clean == "cc0" || clean.contains("creative-commons") || clean.contains("creative commons")) {
        val suffix =
            clean
                .removePrefix("cc-")
                .removePrefix("creative-commons-")
                .removePrefix("creative commons-")
                .uppercase(Locale.ROOT)
        // The Creative Commons names (CC BY-SA and so on) are the licences' own names, kept as they are.
        return when (suffix) {
            "0", "CC0", "ZERO" -> UiText.res(R.string.episode_info_license_cc0)
            "" -> UiText.res(R.string.episode_info_license_cc_generic)
            else -> UiText.res(R.string.episode_info_license_cc, suffix)
        }
    }
    return when (clean) {
        "all-rights-reserved", "copyright" -> UiText.res(R.string.episode_info_license_all_rights)
        "public-domain" -> UiText.res(R.string.episode_info_license_public_domain)
        // A licence the app doesn't know is the feed's own text.
        else -> UiText.raw(licenseCode.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
    }
}

// --- Person Chip (Premium avatar + name + role) ---

@Composable
internal fun PersonChip(
    person: Person,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.Pill,
        modifier =
        Modifier.expressiveClickable(
            feedback = ExpressivePressFeedback.Control,
            enabled = !person.href.isNullOrBlank(),
            shape = ExpressiveShapes.Pill,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier =
            Modifier.padding(
                start = 4.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Avatar
            if (!person.img.isNullOrBlank()) {
                OptimizedImage(
                    url = person.img,
                    proxyWidth = 80, // 32dp * ~2.5x density
                    contentDescription = person.name,
                    modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Fallback avatar with initial
                Box(
                    modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = person.name.firstOrNull()?.toString()?.uppercase(currentLocale()) ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Name + Role
            Column {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!person.role.isNullOrBlank()) {
                    val roleText = person.role!!
                    Text(
                        text = roleText.replaceFirstChar { c -> c.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
