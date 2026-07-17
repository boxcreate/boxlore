package cx.aswin.boxlore.core.data.content

import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import java.security.MessageDigest

object ContentSectionsDaypartResolver {
    private data class DaypartRange(
        val id: String,
        val startMinute: Int,
        val endMinute: Int,
    ) {
        fun contains(minute: Int): Boolean {
            return if (startMinute < endMinute) {
                minute in startMinute until endMinute
            } else {
                minute >= startMinute || minute < endMinute
            }
        }
    }

    // Order intentionally matches the backend's overlap priority.
    private val prioritizedRanges = listOf(
        DaypartRange("early_morning", 300, 420),
        DaypartRange("commute", 420, 600),
        DaypartRange("morning", 420, 660),
        DaypartRange("afternoon", 660, 1_020),
        DaypartRange("evening", 1_020, 1_320),
        DaypartRange("late_night", 1_320, 300),
    )

    fun resolve(localMinuteOfDay: Int): String {
        require(localMinuteOfDay in 0 until MINUTES_PER_DAY)
        return prioritizedRanges.first { it.contains(localMinuteOfDay) }.id
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

internal fun contentSectionsCacheKey(
    catalogVersion: Int,
    country: String,
    surface: String,
    localMinuteOfDay: Int,
    localDate: String,
    profileFingerprint: String,
): String {
    return contentSectionsCacheKey(
        catalogVersion = catalogVersion,
        country = country,
        surface = surface,
        resolvedDaypart = ContentSectionsDaypartResolver.resolve(localMinuteOfDay),
        localDate = localDate,
        profileFingerprint = profileFingerprint,
    )
}

internal fun contentSectionsCacheKey(
    catalogVersion: Int,
    country: String,
    surface: String,
    resolvedDaypart: String,
    localDate: String,
    profileFingerprint: String,
): String {
    require(profileFingerprint.matches(PROFILE_FINGERPRINT_PATTERN))
    return contentSectionsStaleCachePrefix(
        catalogVersion = catalogVersion,
        country = country,
        surface = surface,
        resolvedDaypart = resolvedDaypart,
        localDate = localDate,
    ) + profileFingerprint
}

/**
 * Slot-scoped prefix used for stale-while-revalidate reads.
 * Ignores profile fingerprint so a prior slate can paint before a fresh network round-trip.
 */
internal fun contentSectionsStaleCachePrefix(
    catalogVersion: Int,
    country: String,
    surface: String,
    resolvedDaypart: String,
    localDate: String,
): String {
    val normalizedCountry = country.lowercase().takeIf { it.length in 2..3 } ?: "us"
    val normalizedSurface = surface.lowercase()
    require(normalizedSurface in setOf("home", "explore", "auto"))
    require(localDate.matches(LOCAL_DATE_PATTERN))
    return "content_sections_v1e:" +
        "$catalogVersion:$localDate:$normalizedCountry:$normalizedSurface:" +
        "$resolvedDaypart:"
}

internal fun contentSectionsProfileFingerprint(
    request: ContentSectionsV1Request,
): String {
    val canonical = buildString {
        appendValue(request.contractVersion.toString())
        appendValues(request.languages.sorted())
        appendValues(request.interests.map(String::trim).sortedBy(String::lowercase))
        appendValues(request.subscribedPodcastIds.sorted().map(Long::toString))
        appendValues(request.excludedPodcastIds.sorted().map(Long::toString))
        appendValues(request.excludedEpisodeIds.sorted().map(Long::toString))
        appendValues(
            request.recentSeeds.map { seed ->
                listOf(
                    seed.kind,
                    seed.id.toString(),
                    java.lang.Double.toHexString(seed.weight),
                ).joinToString(":")
            },
        )
        appendValues(
            request.tasteSignals.map { signal ->
                "${signal.genre}:${java.lang.Double.toHexString(signal.weight)}"
            },
        )
        appendValues(request.recentSectionIds)
        appendValue(request.durationPreference?.minimumMinutes?.toString().orEmpty())
        appendValue(request.durationPreference?.maximumMinutes?.toString().orEmpty())
        appendValue(request.historyMaturity?.toString().orEmpty())
        appendValue(request.noveltyPreference?.let(java.lang.Double::toHexString).orEmpty())
        appendValue(request.localDate.orEmpty())
        appendValue(request.timezoneOffsetMinutes?.toString().orEmpty())
        appendValue(request.candidateBudget.toString())
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .take(PROFILE_FINGERPRINT_BYTES)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun StringBuilder.appendValues(values: List<String>) {
    appendValue(values.size.toString())
    values.forEach(::appendValue)
}

private fun StringBuilder.appendValue(value: String) {
    append(value.length).append(':').append(value)
}

private val LOCAL_DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val PROFILE_FINGERPRINT_PATTERN = Regex("^[a-f0-9]{24}$")
private const val PROFILE_FINGERPRINT_BYTES = 12
