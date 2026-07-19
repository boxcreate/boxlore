package cx.aswin.boxlore.feature.onboarding

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.toPodcast

@Composable
internal fun HeroPodcastCard(
    podcast: Podcast,
    categoryName: String,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardBgColor = if (isSubscribed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f).compositeOver(MaterialTheme.colorScheme.surface)
    }

    val cardBorder = if (isSubscribed) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    val cardModifier = if (expanded) {
        modifier.expressiveClickable(shape = RoundedCornerShape(24.dp)) {
            expanded = false
        }
    } else {
        modifier
            .height(390.dp) // Enforce a uniform height when collapsed
            .expressiveClickable(shape = RoundedCornerShape(24.dp)) {
                expanded = true
            }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (expanded) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(120.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 240,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AI TOP PICK",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = podcast.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            val rawDescription = podcast.description?.stripHtml()
            val description = if (!rawDescription.isNullOrBlank()) {
                rawDescription
            } else {
                "Explore episodes, discussions, and topics from ${podcast.title} by ${podcast.artist}."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp,
                        fontSize = 13.sp
                    ),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                )
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val buttonBgColor = if (isSubscribed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
            val buttonTextColor = if (isSubscribed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
            val buttonText = if (isSubscribed) "Selected" else "Select Show"
            val buttonIcon = if (isSubscribed) Icons.Rounded.CheckCircle else Icons.Rounded.Add

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(buttonBgColor, RoundedCornerShape(24.dp))
                    .expressiveClickable(shape = RoundedCornerShape(24.dp)) {
                        onToggleSubscription(podcast.id)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = buttonIcon,
                    contentDescription = null,
                    tint = buttonTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    color = buttonTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
internal fun SuggestedPodcastRowItem(
    podcast: Podcast,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardBgColor = if (isSubscribed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (isSubscribed) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 160,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (podcast.genre.isNotBlank() && podcast.genre != "Podcast") {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = podcast.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val rawDescription = podcast.description?.stripHtml()
                val description = if (!rawDescription.isNullOrBlank()) {
                    rawDescription
                } else {
                    "Explore episodes, discussions, and topics from ${podcast.title} by ${podcast.artist}."
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .animateContentSize()
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 15.sp,
                            fontSize = 12.sp
                        ),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(40.dp)
                    .expressiveClickable(shape = androidx.compose.foundation.shape.CircleShape) {
                        onToggleSubscription(podcast.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (isSubscribed) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSubscribed) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun getCategoryIcon(title: String): ImageVector {
    val lower = title.lowercase()
    return when {
        lower.contains("crime") || lower.contains("murder") || lower.contains("detective") || lower.contains("mystery") || lower.contains("thriller") || lower.contains("spooky") || lower.contains("horror") || lower.contains("investigat") -> Icons.Rounded.Fingerprint
        lower.contains("tech") || lower.contains("computer") || lower.contains("digital") || lower.contains("ai") || lower.contains("innovation") || lower.contains("future") -> Icons.Rounded.Computer
        lower.contains("comedy") || lower.contains("funny") || lower.contains("laugh") || lower.contains("humor") || lower.contains("joke") || lower.contains("conversation") || lower.contains("talk") || lower.contains("chat") -> Icons.Rounded.SentimentVerySatisfied
        lower.contains("news") || lower.contains("daily") || lower.contains("politics") || lower.contains("world") || lower.contains("current") -> Icons.Rounded.Newspaper
        lower.contains("business") || lower.contains("money") || lower.contains("finance") || lower.contains("work") || lower.contains("career") || lower.contains("startup") || lower.contains("investing") || lower.contains("investment") -> Icons.Rounded.Work
        lower.contains("sports") || lower.contains("game") || lower.contains("ball") || lower.contains("football") || lower.contains("basketball") || lower.contains("match") -> Icons.Rounded.EmojiEvents
        lower.contains("health") || lower.contains("mind") || lower.contains("body") || lower.contains("meditation") || lower.contains("sleep") || lower.contains("relax") || lower.contains("yoga") || lower.contains("wellness") || lower.contains("heart") -> Icons.Rounded.MonitorHeart
        lower.contains("history") || lower.contains("ancient") || lower.contains("past") || lower.contains("museum") || lower.contains("heritage") -> Icons.Rounded.AccountBalance
        lower.contains("arts") || lower.contains("design") || lower.contains("paint") || lower.contains("creative") || lower.contains("culture") -> Icons.Rounded.Palette
        lower.contains("science") || lower.contains("physics") || lower.contains("bio") || lower.contains("space") || lower.contains("lab") || lower.contains("research") -> Icons.Rounded.Science
        lower.contains("fiction") || lower.contains("story") || lower.contains("stories") || lower.contains("drama") || lower.contains("book") || lower.contains("read") || lower.contains("novel") -> Icons.Rounded.AutoStories
        lower.contains("music") || lower.contains("song") || lower.contains("audio") || lower.contains("rhythm") || lower.contains("instrument") -> Icons.Rounded.MusicNote
        lower.contains("religion") || lower.contains("spirit") || lower.contains("faith") || lower.contains("god") || lower.contains("soul") -> Icons.Rounded.SelfImprovement
        lower.contains("kids") || lower.contains("family") || lower.contains("parent") || lower.contains("child") -> Icons.Rounded.ChildCare
        lower.contains("leisure") || lower.contains("weekend") || lower.contains("chill") || lower.contains("hobby") -> Icons.Rounded.Weekend
        lower.contains("government") || lower.contains("law") || lower.contains("court") || lower.contains("gavel") -> Icons.Rounded.Gavel
        else -> Icons.Rounded.AutoAwesome
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val CondensedGoogleSans = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            cx.aswin.boxlore.core.designsystem.R.font.google_sans_variable,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(700),
                FontVariation.Setting("wdth", 75f)
            )
        )
    )
} else {
    FontFamily.Default
}

internal fun String.stripHtml(): String {
    val withoutTags = this.replace(Regex("<[^>]*>"), "")
    return withoutTags
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .trim()
}
