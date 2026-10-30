package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.library.SubscriptionSort

/**
 * Inter-folder sorting options (how folders are arranged relative to each other at the top).
 */
enum class FolderInterSort(val label: String) {
    Inherit("Same as shows"),
    SmartRank("Smart"),
    RecentlyUpdated("Recently updated"),
    Alphabetical("A–Z"),
    MostShows("Most shows"),
    Manual("Manual"),
}

/**
 * Intra-folder sorting options (how shows inside a folder are ordered for preview slots and sheet).
 */
enum class FolderIntraSort(val label: String) {
    Inherit("Same as shows"),
    SmartRank("Smart"),
    RecentlyUpdated("Recently updated"),
    Alphabetical("A–Z"),
    MostListened("Most listened"),
    Manual("Your order"),
}

/**
 * Slot allocation result for a folder card in the Subscriptions grid.
 */
internal data class FolderSlots(
    val visibleShows: List<Podcast>,
    val overflowShows: List<Podcast>,
    val totalCount: Int,
) {
    val hasOverflow: Boolean get() = overflowShows.isNotEmpty()
    val overflowCount: Int get() = overflowShows.size
}

/**
 * Result of partitioning subscribed podcasts into:
 * 1. Pinned enlarged folders (pinned to top of the grid).
 * 2. Compact 1×1 folders (can be placed in the grid).
 * 3. Unfiled podcasts (podcasts that do not belong to any active folder).
 */
internal data class PartitionedSubscriptionItems(
    val folders: List<SubscriptionFolder>,
    val unfiledPodcasts: List<Podcast>,
    val podcastsByFolderId: Map<String, List<Podcast>>,
) {
    val pinnedFolders: List<SubscriptionFolder> get() = folders.filter { it.displaySize.isPinnedToTop }
    val compactFolders: List<SubscriptionFolder> get() = folders.filter { !it.displaySize.isPinnedToTop }

    constructor(
        pinnedFolders: List<SubscriptionFolder>,
        compactFolders: List<SubscriptionFolder>,
        unfiledPodcasts: List<Podcast>,
        podcastsByFolderId: Map<String, List<Podcast>>,
    ) : this(
        folders = pinnedFolders + compactFolders,
        unfiledPodcasts = unfiledPodcasts,
        podcastsByFolderId = podcastsByFolderId,
    )
}

/**
 * Data bundle for folder items in the subscriptions tab, keeping parameter lists concise.
 */
internal data class ShowsFolderItems(
    val folders: List<SubscriptionFolder>,
    val podcastsByFolderId: Map<String, List<Podcast>>,
) {
    val pinnedFolders: List<SubscriptionFolder> get() = folders.filter { it.displaySize.isPinnedToTop }
    val compactFolders: List<SubscriptionFolder> get() = folders.filter { !it.displaySize.isPinnedToTop }

    constructor(
        pinnedFolders: List<SubscriptionFolder>,
        compactFolders: List<SubscriptionFolder>,
        podcastsByFolderId: Map<String, List<Podcast>>,
    ) : this(
        folders = pinnedFolders + compactFolders,
        podcastsByFolderId = podcastsByFolderId,
    )
}

/**
 * Unified item representation for the 1×1 slots in the Subscriptions grid,
 * allowing 1×1 compact folders and unfiled podcasts to share the same drag-reorderable sequence.
 */
internal sealed class SubscriptionGridItem {
    abstract val key: String

    data class PodcastItem(
        val podcast: Podcast,
    ) : SubscriptionGridItem() {
        override val key: String get() = podcast.id
    }

    data class FolderItem(
        val folder: SubscriptionFolder,
        val podcasts: List<Podcast>,
    ) : SubscriptionGridItem() {
        override val key: String get() = "folder:${folder.id}"
    }
}

/**
 * Builds the unified sequence of 1×1 grid items (compact folders and unfiled podcasts).
 * When in Manual sort mode, items follow [manualOrder] with unplaced items appended.
 */
internal fun buildUnifiedGridItems(
    compactFolders: List<SubscriptionFolder>,
    unfiledPodcasts: List<Podcast>,
    podcastsByFolderId: Map<String, List<Podcast>>,
    manualOrder: List<String> = emptyList(),
    isManualSort: Boolean = false,
): List<SubscriptionGridItem> {
    val podcastItems = unfiledPodcasts.map { SubscriptionGridItem.PodcastItem(it) }
    val folderItems = compactFolders.map { folder ->
        SubscriptionGridItem.FolderItem(folder, podcastsByFolderId[folder.id].orEmpty())
    }

    val allItems = folderItems + podcastItems
    if (!isManualSort || manualOrder.isEmpty()) {
        return allItems
    }

    val itemsByKey = allItems.associateBy { it.key }
    val seen = LinkedHashSet<String>()
    val result = ArrayList<SubscriptionGridItem>(allItems.size)

    for (key in manualOrder) {
        val item = itemsByKey[key] ?: continue
        if (seen.add(key)) {
            result.add(item)
        }
    }

    allItems.filter { it.key !in seen }.forEach { result.add(it) }
    return result
}

/**
 * Calculates slot assignments for direct clickable podcast slots and an optional overflow slot.
 *
 * Sizing rules:
 * - Shelf (3×1): up to 4 shows. If > 4, 3 shows are directly clickable and 4th is the overflow slot.
 * - Compact (1×1): up to 4 shows in a 2×2 mini-grid. If > 4, 3 shows are clickable and 4th is overflow.
 * - Panel (3×2): up to 6 shows. If > 6, 5 shows clickable and 6th is overflow.
 * - Showcase (3×3): up to 9 shows. If > 9, 8 shows clickable and 9th is overflow.
 */
internal fun calculateFolderSlots(
    shows: List<Podcast>,
    displaySize: FolderDisplaySize,
): FolderSlots {
    val maxSlots = folderSlotCount(displaySize)

    if (shows.size <= maxSlots) {
        return FolderSlots(
            visibleShows = shows,
            overflowShows = emptyList(),
            totalCount = shows.size,
        )
    }

    val visibleCount = maxSlots - 1
    return FolderSlots(
        visibleShows = shows.take(visibleCount),
        overflowShows = shows.drop(visibleCount),
        totalCount = shows.size,
    )
}

/**
 * Partitions subscribed podcasts into pinned shelves, compact folders, and unfiled podcasts.
 * Shows inside each folder are sorted according to [intraFolderSort] (defaulting to follow [sort]).
 * Pinned and compact folders are sorted according to [folderSort] (defaulting to follow [sort]).
 */
internal fun partitionSubscribedShows(
    podcasts: List<Podcast>,
    folders: List<SubscriptionFolder>,
    sort: SubscriptionSort? = null,
    folderSort: FolderInterSort = FolderInterSort.Inherit,
    intraFolderSort: FolderIntraSort = FolderIntraSort.Inherit,
    smartOrderIds: List<String> = emptyList(),
    folderManualOrder: List<String> = emptyList(),
): PartitionedSubscriptionItems {
    val podcastsById = podcasts.associateBy { it.id }
    val effectiveIntraSort = resolveEffectiveIntraSort(intraFolderSort, sort)

    val smartRankMap = if (smartOrderIds.isNotEmpty()) {
        smartOrderIds.mapIndexed { index, id -> id to index }.toMap()
    } else {
        podcasts.mapIndexed { index, pod -> pod.id to index }.toMap()
    }
    val totalPodcasts = if (smartOrderIds.isNotEmpty()) smartOrderIds.size else podcasts.size

    val podcastsByFolderId = folders.associate { folder ->
        val memberIds = folder.podcastIds.toSet()
        val members = podcasts.filter { it.id in memberIds }
        val missingMembers = folder.podcastIds.filter { it !in memberIds }.mapNotNull(podcastsById::get)
        val allMembers = members + missingMembers
        folder.id to sortFolderMembers(allMembers, effectiveIntraSort, smartRankMap, folder)
    }

    val filedPodcastIds = folders.flatMap { it.podcastIds }.toSet()
    val unfiledPodcasts = podcasts.filter { it.id !in filedPodcastIds }

    val effectiveInterSort = resolveEffectiveInterSort(folderSort, sort)
    val orderedFolders = if (effectiveInterSort == FolderInterSort.Manual) {
        sortFolders(
            folders = folders,
            effectiveInterSort = effectiveInterSort,
            podcastsByFolderId = podcastsByFolderId,
            smartRankMap = smartRankMap,
            totalPodcasts = totalPodcasts,
            folderManualOrder = folderManualOrder,
        )
    } else {
        val pinned = sortFolders(
            folders = folders.filter { it.displaySize.isPinnedToTop },
            effectiveInterSort = effectiveInterSort,
            podcastsByFolderId = podcastsByFolderId,
            smartRankMap = smartRankMap,
            totalPodcasts = totalPodcasts,
            folderManualOrder = folderManualOrder,
        )
        val compact = sortFolders(
            folders = folders.filter { !it.displaySize.isPinnedToTop },
            effectiveInterSort = effectiveInterSort,
            podcastsByFolderId = podcastsByFolderId,
            smartRankMap = smartRankMap,
            totalPodcasts = totalPodcasts,
            folderManualOrder = folderManualOrder,
        )
        pinned + compact
    }

    return PartitionedSubscriptionItems(
        folders = orderedFolders,
        unfiledPodcasts = unfiledPodcasts,
        podcastsByFolderId = podcastsByFolderId,
    )
}

/**
 * Resolves and sorts member shows belonging to a [folder] according to [intraFolderSort] and [sort],
 * matching the exact order displayed in the folder preview slots in the subscriptions grid.
 */
internal fun resolveSortedFolderShows(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    intraFolderSort: FolderIntraSort = FolderIntraSort.Inherit,
    sort: SubscriptionSort? = null,
    smartOrderIds: List<String> = emptyList(),
): List<Podcast> {
    val podcastsById = podcasts.associateBy { it.id }
    val effectiveIntraSort = resolveEffectiveIntraSort(intraFolderSort, sort)
    val smartRankMap = if (smartOrderIds.isNotEmpty()) {
        smartOrderIds.mapIndexed { index, id -> id to index }.toMap()
    } else {
        podcasts.mapIndexed { index, pod -> pod.id to index }.toMap()
    }
    val memberIds = folder.podcastIds.toSet()
    val members = podcasts.filter { it.id in memberIds }
    val missingMembers = folder.podcastIds.filter { it !in memberIds }.mapNotNull(podcastsById::get)
    val allMembers = members + missingMembers
    return sortFolderMembers(allMembers, effectiveIntraSort, smartRankMap, folder)
}

internal fun resolveEffectiveIntraSort(
    intraFolderSort: FolderIntraSort,
    sort: SubscriptionSort?,
): FolderIntraSort =
    if (intraFolderSort == FolderIntraSort.Inherit) {
        when (sort) {
            SubscriptionSort.RecentlyUpdated -> FolderIntraSort.RecentlyUpdated
            SubscriptionSort.Alphabetical -> FolderIntraSort.Alphabetical
            SubscriptionSort.SmartRank -> FolderIntraSort.SmartRank
            SubscriptionSort.MostListened -> FolderIntraSort.MostListened
            SubscriptionSort.Manual -> FolderIntraSort.Manual
            else -> FolderIntraSort.RecentlyUpdated
        }
    } else {
        intraFolderSort
    }

internal fun sortFolderMembers(
    allMembers: List<Podcast>,
    effectiveIntraSort: FolderIntraSort,
    smartRankMap: Map<String, Int>,
    folder: SubscriptionFolder,
): List<Podcast> =
    when (effectiveIntraSort) {
        FolderIntraSort.Alphabetical ->
            allMembers.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        FolderIntraSort.RecentlyUpdated ->
            allMembers.sortedByDescending { it.latestEpisode?.publishedDate ?: 0L }
        FolderIntraSort.SmartRank, FolderIntraSort.MostListened -> {
            allMembers.sortedBy { smartRankMap[it.id] ?: Int.MAX_VALUE }
        }
        FolderIntraSort.Manual -> {
            val manualMap = folder.podcastIds.mapIndexed { index, id -> id to index }.toMap()
            allMembers.sortedBy { manualMap[it.id] ?: Int.MAX_VALUE }
        }
        FolderIntraSort.Inherit -> allMembers
    }

private fun resolveEffectiveInterSort(
    folderSort: FolderInterSort,
    sort: SubscriptionSort?,
): FolderInterSort =
    if (folderSort == FolderInterSort.Inherit) {
        when (sort) {
            SubscriptionSort.SmartRank -> FolderInterSort.SmartRank
            SubscriptionSort.Alphabetical -> FolderInterSort.Alphabetical
            SubscriptionSort.RecentlyUpdated -> FolderInterSort.RecentlyUpdated
            SubscriptionSort.MostListened -> FolderInterSort.SmartRank
            SubscriptionSort.Manual -> FolderInterSort.Manual
            else -> FolderInterSort.RecentlyUpdated
        }
    } else {
        folderSort
    }

/**
 * Calculates a folder's engagement score using the Decayed Top-3 (Diminishing Returns) strategy:
 * Score = Top Show + (0.5 * 2nd Show) + (0.25 * 3rd Show).
 *
 * This avoids both the "hoarder bias" of unbounded sums and the "dilution penalty" of averages.
 */
internal fun calculateFolderSmartScore(
    shows: List<Podcast>,
    smartOrderMap: Map<String, Int>,
    totalPodcasts: Int,
): Double {
    if (shows.isEmpty()) return 0.0
    val total = if (totalPodcasts > 0) totalPodcasts.toDouble() else 1.0

    val rankedScores = shows.mapNotNull { podcast ->
        val rankIndex = smartOrderMap[podcast.id] ?: return@mapNotNull null
        (total - rankIndex).coerceAtLeast(0.0) / total
    }.sortedDescending()

    val top1 = rankedScores.getOrNull(0) ?: 0.0
    val top2 = rankedScores.getOrNull(1) ?: 0.0
    val top3 = rankedScores.getOrNull(2) ?: 0.0

    return top1 + (0.5 * top2) + (0.25 * top3)
}

private fun sortFolders(
    folders: List<SubscriptionFolder>,
    effectiveInterSort: FolderInterSort,
    podcastsByFolderId: Map<String, List<Podcast>>,
    smartRankMap: Map<String, Int>,
    totalPodcasts: Int,
    folderManualOrder: List<String> = emptyList(),
): List<SubscriptionFolder> {
    val sortFolderByRecency: (SubscriptionFolder) -> Long = { folder ->
        podcastsByFolderId[folder.id].orEmpty().maxOfOrNull { it.latestEpisode?.publishedDate ?: 0L } ?: 0L
    }
    return when (effectiveInterSort) {
        FolderInterSort.SmartRank -> {
            folders.sortedWith(
                compareByDescending<SubscriptionFolder> { folder ->
                    val memberShows = podcastsByFolderId[folder.id].orEmpty()
                    calculateFolderSmartScore(memberShows, smartRankMap, totalPodcasts)
                }.thenByDescending(sortFolderByRecency)
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        }
        FolderInterSort.Alphabetical ->
            folders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        FolderInterSort.RecentlyUpdated ->
            folders.sortedByDescending(sortFolderByRecency)
        FolderInterSort.MostShows ->
            folders.sortedByDescending { it.podcastIds.size }
        FolderInterSort.Manual -> {
            if (folderManualOrder.isEmpty()) {
                folders
            } else {
                val manualMap = folderManualOrder.mapIndexed { index, id -> id to index }.toMap()
                folders.sortedBy { manualMap[it.id] ?: Int.MAX_VALUE }
            }
        }
        else -> folders
    }
}

/**
 * Returns the count of shows within the overflow cluster that have a new episode.
 */
internal fun countFolderOverflowNew(
    overflowShows: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
): Int = overflowShows.count { it.isLatestEpisodeNew(lastSeenEpisodes[it.id]) }

/**
 * Returns true if any show within the overflow cluster has a new episode.
 */
internal fun hasFolderOverflowNew(
    overflowShows: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
): Boolean = countFolderOverflowNew(overflowShows, lastSeenEpisodes) > 0

/**
 * Returns true if any show within the folder has a new episode.
 */
internal fun hasAnyFolderShowNew(
    podcasts: List<Podcast>,
    lastSeenEpisodes: Map<String, String>,
): Boolean = podcasts.any { it.isLatestEpisodeNew(lastSeenEpisodes[it.id]) }

/**
 * Filters folders by the selected genre chip.
 * If "All" is selected, all folders are returned.
 * Otherwise, returns folders whose name, linkedGenre, or member podcasts match the genre.
 */
internal fun filterFoldersByGenre(
    folders: List<SubscriptionFolder>,
    selectedGenre: String,
    podcasts: List<Podcast>,
): List<SubscriptionFolder> {
    if (selectedGenre.isBlank() || selectedGenre.equals("All", ignoreCase = true)) {
        return folders
    }

    val podcastsById = podcasts.associateBy { it.id }
    val resolved = resolveSubscriptionGenreItem(selectedGenre, podcasts)

    return folders.filter { folder ->
        folderMatchesGenre(
            folder = folder,
            selectedGenre = selectedGenre,
            resolvedLabel = resolved.label,
            resolvedValue = resolved.value,
            podcastsById = podcastsById,
        )
    }
}

private fun folderMatchesGenre(
    folder: SubscriptionFolder,
    selectedGenre: String,
    resolvedLabel: String,
    resolvedValue: String,
    podcastsById: Map<String, Podcast>,
): Boolean {
    val matchesToken: (String?) -> Boolean = { candidate ->
        if (candidate == null) {
            false
        } else {
            val trimmed = candidate.trim()
            val isTechSynonym = (selectedGenre.equals("Tech", ignoreCase = true) && trimmed.equals("Technology", ignoreCase = true)) ||
                (selectedGenre.equals("Technology", ignoreCase = true) && trimmed.equals("Tech", ignoreCase = true))
            trimmed.equals(selectedGenre, ignoreCase = true) ||
                trimmed.equals(resolvedValue, ignoreCase = true) ||
                trimmed.equals(resolvedLabel, ignoreCase = true) ||
                isTechSynonym
        }
    }
    val nameMatches = matchesToken(folder.name)
    val genreMatches = matchesToken(folder.linkedGenre)
    if (nameMatches || genreMatches) return true

    return folder.podcastIds.any { podId ->
        val pod = podcastsById[podId] ?: return@any false
        val genreListMatches = pod.effectiveGenre.split(",")
            .map { it.trim() }
            .any(matchesToken)
        genreListMatches || matchesToken(pod.genre)
    }
}
