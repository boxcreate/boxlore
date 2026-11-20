package cx.aswin.boxlore.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A genre on the manual picker. [value] is the English key sent to the server and stored in
 * `selectedGenres`; [labelRes] is the chip label and [nameRes] the full name shown on later steps.
 */
data class GenreItem(
    @StringRes val labelRes: Int,
    val value: String,
    val icon: ImageVector,
    @StringRes val nameRes: Int = labelRes,
)

val ONBOARDING_GENRES =
    listOf(
        GenreItem(R.string.onboarding_genre_news, "News", Icons.Rounded.Newspaper),
        GenreItem(R.string.onboarding_genre_tech, "Technology", Icons.Rounded.Computer, R.string.onboarding_genre_technology),
        GenreItem(R.string.onboarding_genre_business, "Business", Icons.Rounded.Work),
        GenreItem(R.string.onboarding_genre_comedy, "Comedy", Icons.Rounded.SentimentVerySatisfied),
        GenreItem(R.string.onboarding_genre_true_crime, "True Crime", Icons.Rounded.Fingerprint),
        GenreItem(R.string.onboarding_genre_sports, "Sports", Icons.Rounded.EmojiEvents),
        GenreItem(R.string.onboarding_genre_health, "Health", Icons.Rounded.MonitorHeart),
        GenreItem(R.string.onboarding_genre_history, "History", Icons.Rounded.AccountBalance),
        GenreItem(R.string.onboarding_genre_arts, "Arts", Icons.Rounded.Palette),
        GenreItem(R.string.onboarding_genre_society, "Society & Culture", Icons.Rounded.Groups, R.string.onboarding_genre_society_culture),
        GenreItem(R.string.onboarding_genre_education, "Education", Icons.Rounded.School),
        GenreItem(R.string.onboarding_genre_science, "Science", Icons.Rounded.Science),
        GenreItem(R.string.onboarding_genre_tv_film, "TV & Film", Icons.Rounded.Movie),
        GenreItem(R.string.onboarding_genre_fiction, "Fiction", Icons.Rounded.AutoStories),
        GenreItem(R.string.onboarding_genre_music, "Music", Icons.Rounded.MusicNote),
        GenreItem(R.string.onboarding_genre_religion, "Religion & Spirituality", Icons.Rounded.SelfImprovement, R.string.onboarding_genre_religion_spirituality),
        GenreItem(R.string.onboarding_genre_family, "Kids & Family", Icons.Rounded.ChildCare, R.string.onboarding_genre_kids_family),
        GenreItem(R.string.onboarding_genre_leisure, "Leisure", Icons.Rounded.Weekend),
        GenreItem(R.string.onboarding_genre_government_short, "Government", Icons.Rounded.Gavel, R.string.onboarding_genre_government),
    )

/** The full display name for a genre key from [ONBOARDING_GENRES], or null for a key it doesn't know. */
@StringRes
internal fun onboardingGenreNameRes(value: String): Int? = ONBOARDING_GENRES.firstOrNull { it.value == value }?.nameRes

/** A sub-genre chip: [value] is the English key sent to the server, [labelRes] what the chip shows. */
internal data class SubGenreItem(
    val value: String,
    @StringRes val labelRes: Int,
)

/** Sub-genres under each genre key; keys and values never change, they are server inputs. */
internal val SUB_GENRES_MAP: Map<String, List<SubGenreItem>> =
    mapOf(
        "News" to listOf(SubGenreItem("Daily News", R.string.onboarding_subgenre_daily_news), SubGenreItem("Politics", R.string.onboarding_subgenre_politics), SubGenreItem("World News", R.string.onboarding_subgenre_world_news), SubGenreItem("Tech News", R.string.onboarding_subgenre_tech_news), SubGenreItem("Local News", R.string.onboarding_subgenre_local_news)),
        "Technology" to listOf(SubGenreItem("Software", R.string.onboarding_subgenre_software), SubGenreItem("Gadgets", R.string.onboarding_subgenre_gadgets), SubGenreItem("AI & Tech", R.string.onboarding_subgenre_ai_and_tech), SubGenreItem("Startups", R.string.onboarding_subgenre_startups), SubGenreItem("Cybersecurity", R.string.onboarding_subgenre_cybersecurity)),
        "Business" to listOf(SubGenreItem("Investing", R.string.onboarding_subgenre_investing), SubGenreItem("Marketing", R.string.onboarding_subgenre_marketing), SubGenreItem("Personal Finance", R.string.onboarding_subgenre_personal_finance), SubGenreItem("Management", R.string.onboarding_subgenre_management), SubGenreItem("Real Estate", R.string.onboarding_subgenre_real_estate)),
        "Comedy" to listOf(SubGenreItem("Standup", R.string.onboarding_subgenre_standup), SubGenreItem("Improv", R.string.onboarding_subgenre_improv), SubGenreItem("Pop Culture Comedy", R.string.onboarding_subgenre_pop_culture_comedy), SubGenreItem("Storytelling Comedy", R.string.onboarding_subgenre_storytelling_comedy)),
        "True Crime" to listOf(SubGenreItem("Investigative", R.string.onboarding_subgenre_investigative), SubGenreItem("Cold Cases", R.string.onboarding_subgenre_cold_cases), SubGenreItem("Serial Killers", R.string.onboarding_subgenre_serial_killers), SubGenreItem("History Crime", R.string.onboarding_subgenre_history_crime)),
        "Sports" to listOf(SubGenreItem("Football", R.string.onboarding_subgenre_football), SubGenreItem("Basketball", R.string.onboarding_subgenre_basketball), SubGenreItem("F1 & Racing", R.string.onboarding_subgenre_f1_and_racing), SubGenreItem("Fantasy Sports", R.string.onboarding_subgenre_fantasy_sports), SubGenreItem("Fitness", R.string.onboarding_subgenre_fitness)),
        "Health" to listOf(SubGenreItem("Mental Health", R.string.onboarding_subgenre_mental_health), SubGenreItem("Nutrition", R.string.onboarding_subgenre_nutrition), SubGenreItem("Medicine", R.string.onboarding_subgenre_medicine), SubGenreItem("Biohacking", R.string.onboarding_subgenre_biohacking), SubGenreItem("Yoga & Meditation", R.string.onboarding_subgenre_yoga_and_meditation)),
        "History" to listOf(SubGenreItem("Ancient History", R.string.onboarding_subgenre_ancient_history), SubGenreItem("Modern History", R.string.onboarding_subgenre_modern_history), SubGenreItem("Military History", R.string.onboarding_subgenre_military_history), SubGenreItem("Biographies", R.string.onboarding_subgenre_biographies)),
        "Arts" to listOf(SubGenreItem("Design", R.string.onboarding_subgenre_design), SubGenreItem("Literature", R.string.onboarding_subgenre_literature), SubGenreItem("Visual Arts", R.string.onboarding_subgenre_visual_arts), SubGenreItem("Performing Arts", R.string.onboarding_subgenre_performing_arts), SubGenreItem("Fashion", R.string.onboarding_subgenre_fashion)),
        "Society & Culture" to listOf(SubGenreItem("Philosophy", R.string.onboarding_subgenre_philosophy), SubGenreItem("Relationships", R.string.onboarding_subgenre_relationships), SubGenreItem("Pop Culture", R.string.onboarding_subgenre_pop_culture), SubGenreItem("Personal Journals", R.string.onboarding_subgenre_personal_journals)),
        "Education" to listOf(SubGenreItem("Language Learning", R.string.onboarding_subgenre_language_learning), SubGenreItem("Self Improvement", R.string.onboarding_subgenre_self_improvement), SubGenreItem("How-To", R.string.onboarding_subgenre_how_to), SubGenreItem("Courses", R.string.onboarding_subgenre_courses)),
        "Science" to listOf(SubGenreItem("Space & Physics", R.string.onboarding_subgenre_space_and_physics), SubGenreItem("Biology", R.string.onboarding_subgenre_biology), SubGenreItem("Psychology", R.string.onboarding_subgenre_psychology), SubGenreItem("Nature", R.string.onboarding_subgenre_nature), SubGenreItem("Earth Sciences", R.string.onboarding_subgenre_earth_sciences)),
        "TV & Film" to listOf(SubGenreItem("Movie Reviews", R.string.onboarding_subgenre_movie_reviews), SubGenreItem("TV Show Reviews", R.string.onboarding_subgenre_tv_show_reviews), SubGenreItem("Anime", R.string.onboarding_subgenre_anime), SubGenreItem("Pop Culture", R.string.onboarding_subgenre_pop_culture)),
        "Fiction" to listOf(SubGenreItem("Sci-Fi & Fantasy", R.string.onboarding_subgenre_sci_fi_and_fantasy), SubGenreItem("Drama", R.string.onboarding_subgenre_drama), SubGenreItem("Mystery & Thriller", R.string.onboarding_subgenre_mystery_and_thriller), SubGenreItem("Comedy Fiction", R.string.onboarding_subgenre_comedy_fiction)),
        "Music" to listOf(SubGenreItem("Music History", R.string.onboarding_subgenre_music_history), SubGenreItem("Songwriting", R.string.onboarding_subgenre_songwriting), SubGenreItem("Interviews", R.string.onboarding_subgenre_interviews), SubGenreItem("Music Commentary", R.string.onboarding_subgenre_music_commentary)),
        "Religion & Spirituality" to listOf(SubGenreItem("Mindfulness", R.string.onboarding_subgenre_mindfulness), SubGenreItem("Spirituality", R.string.onboarding_subgenre_spirituality), SubGenreItem("Religion", R.string.onboarding_subgenre_religion), SubGenreItem("Philosophy", R.string.onboarding_subgenre_philosophy)),
        "Kids & Family" to listOf(SubGenreItem("Parenting", R.string.onboarding_subgenre_parenting), SubGenreItem("Bedtime Stories", R.string.onboarding_subgenre_bedtime_stories), SubGenreItem("Education for Kids", R.string.onboarding_subgenre_education_for_kids), SubGenreItem("Family Activity", R.string.onboarding_subgenre_family_activity)),
        "Leisure" to listOf(SubGenreItem("Gaming", R.string.onboarding_subgenre_gaming), SubGenreItem("Hobbies", R.string.onboarding_subgenre_hobbies), SubGenreItem("Travel", R.string.onboarding_subgenre_travel), SubGenreItem("Food & Drink", R.string.onboarding_subgenre_food_and_drink), SubGenreItem("Automotive", R.string.onboarding_subgenre_automotive)),
        "Government" to listOf(SubGenreItem("Law & Justice", R.string.onboarding_subgenre_law_and_justice), SubGenreItem("Public Policy", R.string.onboarding_subgenre_public_policy), SubGenreItem("Municipal News", R.string.onboarding_subgenre_municipal_news), SubGenreItem("National Politics", R.string.onboarding_subgenre_national_politics)),
    )

/** A listening moment or episode length: [value] is the key sent to the server. */
internal data class ListeningChoiceItem(
    @StringRes val labelRes: Int,
    val value: String,
    val icon: ImageVector,
    @StringRes val descriptionRes: Int? = null,
)

internal val ONBOARDING_ACTIVITIES =
    listOf(
        ListeningChoiceItem(R.string.onboarding_activity_commuting, "commuting", Icons.Rounded.DirectionsCar),
        ListeningChoiceItem(R.string.onboarding_activity_working, "working", Icons.Rounded.Work),
        ListeningChoiceItem(R.string.onboarding_activity_meditating, "meditating", Icons.Rounded.SelfImprovement),
        ListeningChoiceItem(R.string.onboarding_activity_winding_down, "winding down", Icons.Rounded.Bedtime),
        ListeningChoiceItem(R.string.onboarding_activity_working_out, "working out", Icons.Rounded.FitnessCenter),
        ListeningChoiceItem(R.string.onboarding_activity_chores, "doing chores", Icons.Rounded.Brush),
    )

internal val ONBOARDING_LENGTHS =
    listOf(
        ListeningChoiceItem(R.string.onboarding_length_short, "short", Icons.Rounded.Timer10, R.string.onboarding_length_short_range),
        ListeningChoiceItem(R.string.onboarding_length_medium, "medium", Icons.Rounded.Timer, R.string.onboarding_length_medium_range),
        ListeningChoiceItem(R.string.onboarding_length_long, "long", Icons.Rounded.HourglassEmpty, R.string.onboarding_length_long_range),
    )
