package cx.aswin.boxcast.core.data.content

import cx.aswin.boxcast.core.model.PodcastGenres
import cx.aswin.boxcast.core.network.model.ContentDurationPreferenceDto
import cx.aswin.boxcast.core.network.model.ContentTasteSignalDto
import cx.aswin.boxcast.core.network.model.HistoryItem
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class ContentSignalProfile(
    val tasteSignals: List<ContentTasteSignalDto>,
    val durationPreference: ContentDurationPreferenceDto?,
    val historyMaturity: Int,
    val noveltyPreference: Double,
)

internal fun buildContentSignalProfile(
    explicitInterests: List<String>,
    subscribedGenres: List<String>,
    recentHistory: List<HistoryItem>,
    subscribedPodcastIds: Set<String>,
    learnedGenreAffinities: Map<String, Double>,
): ContentSignalProfile {
    val boundedHistory = recentHistory.take(MAX_SIGNAL_HISTORY)
    val meaningfulHistory = boundedHistory.filter(HistoryItem::isMeaningfulForSignals)
    val weights = PodcastGenres.all.associateWith { 0.0 }.toMutableMap()

    explicitInterests.asSequence()
        .mapNotNull(PodcastGenres::canonicalize)
        .distinct()
        .forEach { genre -> weights[genre] = weights.getValue(genre) + EXPLICIT_INTEREST_WEIGHT }

    subscribedGenres.asSequence()
        .mapNotNull(PodcastGenres::canonicalize)
        .groupingBy { it }
        .eachCount()
        .forEach { (genre, count) ->
            val subscriptionWeight = (
                SUBSCRIPTION_BASE_WEIGHT +
                    (count - 1).coerceIn(0, 4) * SUBSCRIPTION_REPEAT_WEIGHT
                ).coerceAtMost(SUBSCRIPTION_MAX_WEIGHT)
            weights[genre] = weights.getValue(genre) + subscriptionWeight
        }

    val historyEvidence = mutableMapOf<String, Double>()
    meaningfulHistory.forEachIndexed { index, item ->
        val genre = PodcastGenres.canonicalize(item.genre) ?: return@forEachIndexed
        val recency = 1.0 - index.toDouble() / MAX_SIGNAL_HISTORY
        historyEvidence[genre] = historyEvidence.getOrDefault(genre, 0.0) +
            item.historyEvidenceWeight() * recency
    }
    historyEvidence.forEach { (genre, evidence) ->
        weights[genre] = weights.getValue(genre) +
            (evidence * HISTORY_EVIDENCE_SCALE).coerceAtMost(HISTORY_MAX_WEIGHT)
    }

    learnedGenreAffinities.forEach { (rawGenre, affinity) ->
        val genre = PodcastGenres.canonicalize(rawGenre) ?: return@forEach
        if (!affinity.isFinite()) return@forEach
        weights[genre] = weights.getValue(genre) +
            affinity.coerceIn(-1.0, 1.0) * LEARNED_AFFINITY_WEIGHT
    }

    val tasteSignals = PodcastGenres.all.mapNotNull { genre ->
        val weight = weights.getValue(genre).coerceIn(-1.0, 1.0)
        if (kotlin.math.abs(weight) < MIN_SIGNAL_MAGNITUDE) {
            null
        } else {
            ContentTasteSignalDto(genre = genre, weight = weight)
        }
    }.take(PodcastGenres.all.size)

    return ContentSignalProfile(
        tasteSignals = tasteSignals,
        durationPreference = deriveDurationPreference(meaningfulHistory),
        historyMaturity = historyMaturityBucket(meaningfulHistory.size),
        noveltyPreference = deriveNoveltyPreference(
            meaningfulHistory = meaningfulHistory,
            subscribedPodcastIds = subscribedPodcastIds,
        ),
    )
}

internal fun deriveDurationPreference(
    meaningfulHistory: List<HistoryItem>,
): ContentDurationPreferenceDto? {
    val durations = meaningfulHistory.asSequence()
        .take(MAX_SIGNAL_HISTORY)
        .mapNotNull { it.durationMs }
        .filter { it in MIN_VALID_DURATION_MS..MAX_VALID_DURATION_MS }
        .map { (it.toDouble() / MILLIS_PER_MINUTE).roundToInt() }
        .sorted()
        .toList()
    if (durations.size < MIN_DURATION_SAMPLES) return null

    val lower = durations.percentile(0.25)
    val upper = durations.percentile(0.75)
    val minimum = (lower - DURATION_LOWER_PADDING_MINUTES)
        .coerceIn(MIN_PREFERRED_DURATION_MINUTES, MAX_PREFERRED_MINIMUM_MINUTES)
    val maximum = (upper + DURATION_UPPER_PADDING_MINUTES)
        .coerceIn(
            minimumValue = minimum + MIN_DURATION_RANGE_MINUTES,
            maximumValue = MAX_PREFERRED_DURATION_MINUTES,
        )
    return ContentDurationPreferenceDto(
        minimumMinutes = minimum,
        maximumMinutes = maximum,
    )
}

internal fun historyMaturityBucket(historyCount: Int): Int = when {
    historyCount <= 0 -> 0
    historyCount < 5 -> 1
    historyCount < 15 -> 2
    historyCount < MAX_SIGNAL_HISTORY -> 3
    else -> 4
}

internal fun deriveNoveltyPreference(
    meaningfulHistory: List<HistoryItem>,
    subscribedPodcastIds: Set<String>,
): Double {
    val history = meaningfulHistory.take(MAX_SIGNAL_HISTORY)
        .filter { !it.podcastId.isNullOrBlank() }
    if (history.isEmpty()) return DEFAULT_NOVELTY_PREFERENCE
    val unsubscribedCount = history.count { it.podcastId !in subscribedPodcastIds }
    return (
        (unsubscribedCount + NOVELTY_PRIOR_SUCCESSES) /
            (history.size + NOVELTY_PRIOR_TOTAL)
        ).coerceIn(0.0, 1.0)
}

private fun HistoryItem.isMeaningfulForSignals(): Boolean {
    val duration = durationMs ?: return false
    if (duration !in MIN_VALID_DURATION_MS..MAX_VALID_DURATION_MS) return false
    val progress = progressMs?.coerceAtLeast(0L) ?: 0L
    val progressRatio = progress.toDouble() / duration
    return isCompleted == true ||
        progress >= MIN_MEANINGFUL_PROGRESS_MS ||
        progressRatio >= MIN_MEANINGFUL_PROGRESS_RATIO
}

private fun HistoryItem.historyEvidenceWeight(): Double {
    val duration = durationMs?.takeIf { it > 0L } ?: return HISTORY_PLAY_WEIGHT
    val ratio = ((progressMs ?: 0L).toDouble() / duration).coerceIn(0.0, 1.0)
    return when {
        isLiked == true -> HISTORY_LIKED_WEIGHT
        isCompleted == true -> HISTORY_COMPLETED_WEIGHT
        ratio >= 0.75 -> HISTORY_DEEP_PLAY_WEIGHT
        else -> HISTORY_PLAY_WEIGHT
    }
}

private fun List<Int>.percentile(percentile: Double): Int {
    require(isNotEmpty())
    val index = floor((lastIndex * percentile.coerceIn(0.0, 1.0))).toInt()
    return this[index]
}

private const val MAX_SIGNAL_HISTORY = 30
private const val EXPLICIT_INTEREST_WEIGHT = 0.70
private const val SUBSCRIPTION_BASE_WEIGHT = 0.30
private const val SUBSCRIPTION_REPEAT_WEIGHT = 0.05
private const val SUBSCRIPTION_MAX_WEIGHT = 0.50
private const val HISTORY_EVIDENCE_SCALE = 0.22
private const val HISTORY_MAX_WEIGHT = 0.60
private const val LEARNED_AFFINITY_WEIGHT = 0.65
private const val MIN_SIGNAL_MAGNITUDE = 0.01
private const val HISTORY_LIKED_WEIGHT = 1.0
private const val HISTORY_COMPLETED_WEIGHT = 0.85
private const val HISTORY_DEEP_PLAY_WEIGHT = 0.70
private const val HISTORY_PLAY_WEIGHT = 0.50
private const val MIN_MEANINGFUL_PROGRESS_MS = 60_000L
private const val MIN_MEANINGFUL_PROGRESS_RATIO = 0.20
private const val MILLIS_PER_MINUTE = 60_000.0
private const val MIN_VALID_DURATION_MS = 5L * 60L * 1_000L
private const val MAX_VALID_DURATION_MS = 4L * 60L * 60L * 1_000L
private const val MIN_DURATION_SAMPLES = 3
private const val MIN_PREFERRED_DURATION_MINUTES = 5
private const val MAX_PREFERRED_MINIMUM_MINUTES = 120
private const val MAX_PREFERRED_DURATION_MINUTES = 180
private const val MIN_DURATION_RANGE_MINUTES = 5
private const val DURATION_LOWER_PADDING_MINUTES = 5
private const val DURATION_UPPER_PADDING_MINUTES = 10
private const val DEFAULT_NOVELTY_PREFERENCE = 0.50
private const val NOVELTY_PRIOR_SUCCESSES = 2.0
private const val NOVELTY_PRIOR_TOTAL = 4.0
