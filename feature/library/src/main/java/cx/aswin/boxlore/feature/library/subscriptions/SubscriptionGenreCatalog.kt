package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Genre pill metadata matched to Explore / Home / onboarding icons.
 * Kept in `:feature:library` (no feature→feature import of Explore).
 */
internal data class SubscriptionGenreItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
)

internal val SUBSCRIPTION_GENRE_CATALOG = listOf(
    SubscriptionGenreItem("News", "News", Icons.Rounded.Newspaper),
    SubscriptionGenreItem("Tech", "Technology", Icons.Rounded.Computer),
    SubscriptionGenreItem("Business", "Business", Icons.Rounded.Work),
    SubscriptionGenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
    SubscriptionGenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
    SubscriptionGenreItem("Sports", "Sports", Icons.Rounded.SportsBaseball),
    SubscriptionGenreItem("Health", "Health", Icons.Rounded.FavoriteBorder),
    SubscriptionGenreItem("History", "History", Icons.Rounded.AccountBalance),
    SubscriptionGenreItem("Arts", "Arts", Icons.Rounded.Palette),
    SubscriptionGenreItem("Society", "Society & Culture", Icons.Rounded.Person),
    SubscriptionGenreItem("Education", "Education", Icons.Rounded.School),
    SubscriptionGenreItem("Science", "Science", Icons.Rounded.Science),
    SubscriptionGenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    SubscriptionGenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    SubscriptionGenreItem("Music", "Music", Icons.Rounded.MusicNote),
    SubscriptionGenreItem("Religion", "Religion & Spirituality", Icons.Rounded.Star),
    SubscriptionGenreItem("Family", "Kids & Family", Icons.Rounded.Face),
    SubscriptionGenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    SubscriptionGenreItem("Govt", "Government", Icons.Rounded.Gavel),
)

internal val AllGenreIcon: ImageVector = Icons.Rounded.Apps

internal fun resolveSubscriptionGenreItem(genre: String): SubscriptionGenreItem {
    val match = SUBSCRIPTION_GENRE_CATALOG.find {
        it.value.equals(genre, ignoreCase = true) || it.label.equals(genre, ignoreCase = true)
    }
    return match ?: SubscriptionGenreItem(
        label = genre,
        value = genre,
        icon = Icons.Rounded.Category,
    )
}
