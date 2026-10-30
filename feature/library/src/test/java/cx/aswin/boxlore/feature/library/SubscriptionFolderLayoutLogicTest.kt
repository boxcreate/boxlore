package cx.aswin.boxlore.feature.library

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.buildUnifiedGridItems
import cx.aswin.boxlore.feature.library.subscriptions.calculateFolderSlots
import cx.aswin.boxlore.feature.library.subscriptions.calculateFolderSmartScore
import cx.aswin.boxlore.feature.library.subscriptions.countFolderOverflowNew
import cx.aswin.boxlore.feature.library.subscriptions.filterFoldersByGenre
import cx.aswin.boxlore.feature.library.subscriptions.hasAnyFolderShowNew
import cx.aswin.boxlore.feature.library.subscriptions.hasFolderOverflowNew
import cx.aswin.boxlore.feature.library.subscriptions.partitionSubscribedShows
import cx.aswin.boxlore.feature.library.subscriptions.resolveSortedFolderShows
import cx.aswin.boxlore.feature.library.subscriptions.truncateCompactFolderName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFolderLayoutLogicTest {

    private fun mockPodcast(
        id: String,
        title: String = "Podcast $id",
        genre: String = "Technology",
        customGenre: String? = null,
        latestPubDate: Long = 0L,
        rssHasNewEpisodes: Boolean = false,
    ) = Podcast(
        id = id,
        title = title,
        artist = "Host $id",
        imageUrl = "https://example.com/$id.jpg",
        genre = genre,
        customGenre = customGenre,
        rssHasNewEpisodes = rssHasNewEpisodes,
        latestEpisode = if (latestPubDate > 0L) {
            Episode(
                id = "ep-$id",
                podcastId = id,
                title = "Episode $id",
                description = "Description $id",
                audioUrl = "https://example.com/audio-$id.mp3",
                duration = 1800,
                publishedDate = latestPubDate,
            )
        } else {
            null
        },
    )

    @Test
    fun `calculateFolderSlots compact size fits up to 4 shows`() {
        val shows = (1..3).map { mockPodcast(it.toString()) }
        val slots = calculateFolderSlots(shows, FolderDisplaySize.COMPACT)

        assertEquals(3, slots.visibleShows.size)
        assertEquals(0, slots.overflowShows.size)
        assertFalse(slots.hasOverflow)
        assertEquals(3, slots.totalCount)

        // Exactly 4 shows
        val fourShows = (1..4).map { mockPodcast(it.toString()) }
        val fourSlots = calculateFolderSlots(fourShows, FolderDisplaySize.COMPACT)
        assertEquals(4, fourSlots.visibleShows.size)
        assertFalse(fourSlots.hasOverflow)

        // 6 shows -> 3 visible + 3 overflow
        val sixShows = (1..6).map { mockPodcast(it.toString()) }
        val sixSlots = calculateFolderSlots(sixShows, FolderDisplaySize.COMPACT)
        assertEquals(3, sixSlots.visibleShows.size)
        assertEquals(3, sixSlots.overflowShows.size)
        assertTrue(sixSlots.hasOverflow)
        assertEquals(3, sixSlots.overflowCount)
        assertEquals(6, sixSlots.totalCount)
    }

    @Test
    fun `calculateFolderSlots shelf 3x1 size fits 3 shows before overflow`() {
        val twoShows = (1..2).map { mockPodcast(it.toString()) }
        val twoSlots = calculateFolderSlots(twoShows, FolderDisplaySize.SHELF)
        assertEquals(2, twoSlots.visibleShows.size)
        assertFalse(twoSlots.hasOverflow)

        val threeShows = (1..3).map { mockPodcast(it.toString()) }
        val threeSlots = calculateFolderSlots(threeShows, FolderDisplaySize.SHELF)
        assertEquals(3, threeSlots.visibleShows.size)
        assertFalse(threeSlots.hasOverflow)

        val fiveShows = (1..5).map { mockPodcast(it.toString()) }
        val fiveSlots = calculateFolderSlots(fiveShows, FolderDisplaySize.SHELF)
        assertEquals(2, fiveSlots.visibleShows.size)
        assertEquals(3, fiveSlots.overflowShows.size)
        assertTrue(fiveSlots.hasOverflow)
        assertEquals(3, fiveSlots.overflowCount)
    }

    @Test
    fun `calculateFolderSlots panel 3x2 size fits 6 shows before overflow`() {
        val sixShows = (1..6).map { mockPodcast(it.toString()) }
        val sixSlots = calculateFolderSlots(sixShows, FolderDisplaySize.PANEL)
        assertEquals(6, sixSlots.visibleShows.size)
        assertFalse(sixSlots.hasOverflow)

        val tenShows = (1..10).map { mockPodcast(it.toString()) }
        val tenSlots = calculateFolderSlots(tenShows, FolderDisplaySize.PANEL)
        assertEquals(5, tenSlots.visibleShows.size)
        assertEquals(5, tenSlots.overflowShows.size)
        assertTrue(tenSlots.hasOverflow)
        assertEquals(5, tenSlots.overflowCount)
    }

    @Test
    fun `calculateFolderSlots showcase 3x3 size fits 9 shows before overflow`() {
        val nineShows = (1..9).map { mockPodcast(it.toString()) }
        val nineSlots = calculateFolderSlots(nineShows, FolderDisplaySize.SHOWCASE)
        assertEquals(9, nineSlots.visibleShows.size)
        assertFalse(nineSlots.hasOverflow)

        val twelveShows = (1..12).map { mockPodcast(it.toString()) }
        val twelveSlots = calculateFolderSlots(twelveShows, FolderDisplaySize.SHOWCASE)
        assertEquals(8, twelveSlots.visibleShows.size)
        assertEquals(4, twelveSlots.overflowShows.size)
        assertTrue(twelveSlots.hasOverflow)
    }

    @Test
    fun `partitionSubscribedShows correctly separates pinned folders, compact folders, and unfiled shows`() {
        val podcasts = (1..10).map { mockPodcast(it.toString()) }

        val pinnedFolder = SubscriptionFolder(
            id = "pinned-1",
            name = "Tech Shelf",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("1", "2", "3"),
        )
        val compactFolder = SubscriptionFolder(
            id = "compact-1",
            name = "Comedy 1x1",
            displaySize = FolderDisplaySize.COMPACT,
            podcastIds = listOf("4", "5"),
        )

        val partition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(pinnedFolder, compactFolder),
        )

        assertEquals(1, partition.pinnedFolders.size)
        assertEquals("pinned-1", partition.pinnedFolders.first().id)

        assertEquals(1, partition.compactFolders.size)
        assertEquals("compact-1", partition.compactFolders.first().id)

        // Shows 1, 2, 3, 4, 5 are filed into folders. Shows 6..10 remain unfiled in root grid.
        assertEquals(5, partition.unfiledPodcasts.size)
        assertEquals(listOf("6", "7", "8", "9", "10"), partition.unfiledPodcasts.map { it.id })

        // Check podcast map per folder
        assertEquals(3, partition.podcastsByFolderId["pinned-1"]?.size)
        assertEquals(2, partition.podcastsByFolderId["compact-1"]?.size)
    }

    @Test
    fun `filterFoldersByGenre matches by folder name, linked genre, or contained podcasts`() {
        val p1 = mockPodcast("1", genre = "Technology")
        val p2 = mockPodcast("2", genre = "Comedy")
        val p3 = mockPodcast("3", genre = "News", customGenre = "Tech")
        val allPodcasts = listOf(p1, p2, p3)

        val techFolderByName = SubscriptionFolder(
            id = "f-1",
            name = "Technology",
            podcastIds = listOf("1"),
        )
        val comedyFolderByLinkedGenre = SubscriptionFolder(
            id = "f-2",
            name = "Funny Stuff",
            linkedGenre = "Comedy",
            podcastIds = listOf("2"),
        )
        val customTagFolderByMember = SubscriptionFolder(
            id = "f-3",
            name = "My Mixed Bag",
            podcastIds = listOf("3"), // Member p3 has customGenre = "Tech"
        )
        val folders = listOf(techFolderByName, comedyFolderByLinkedGenre, customTagFolderByMember)

        // "All" returns all folders
        assertEquals(3, filterFoldersByGenre(folders, "All", allPodcasts).size)
        assertEquals(3, filterFoldersByGenre(folders, "", allPodcasts).size)

        // "Comedy" matches f-2
        val comedyMatches = filterFoldersByGenre(folders, "Comedy", allPodcasts)
        assertEquals(1, comedyMatches.size)
        assertEquals("f-2", comedyMatches.first().id)

        // "Tech" matches f-1 (by name) and f-3 (by member podcast custom tag)
        val techMatches = filterFoldersByGenre(folders, "Tech", allPodcasts)
        assertEquals(2, techMatches.size)
        assertTrue(techMatches.any { it.id == "f-1" })
        assertTrue(techMatches.any { it.id == "f-3" })
    }

    @Test
    fun `active folder show resolution preserves folder order and filters missing shows`() {
        val p1 = mockPodcast("p-1", genre = "Tech")
        val p2 = mockPodcast("p-2", genre = "Tech")
        val allPodcasts = listOf(p1, p2)
        val podcastsById = allPodcasts.associateBy { it.id }

        val folder = SubscriptionFolder(
            id = "f-1",
            name = "Tech Favorites",
            podcastIds = listOf("p-2", "p-999", "p-1"), // "p-999" is not subscribed/missing
        )

        val resolvedShows = folder.podcastIds.mapNotNull(podcastsById::get)
        assertEquals(2, resolvedShows.size)
        assertEquals("p-2", resolvedShows[0].id)
        assertEquals("p-1", resolvedShows[1].id)

        // Empty folder
        val emptyFolder = SubscriptionFolder(id = "f-empty", name = "Empty", podcastIds = emptyList())
        val emptyShows = emptyFolder.podcastIds.mapNotNull(podcastsById::get)
        assertTrue(emptyShows.isEmpty())
    }

    @Test
    fun `buildUnifiedGridItems interleaves compact folders and podcasts in manual order`() {
        val p1 = mockPodcast("p-1", title = "Alpha")
        val p2 = mockPodcast("p-2", title = "Beta")
        val p3 = mockPodcast("p-3", title = "Gamma")

        val folder1 = SubscriptionFolder(id = "f-1", name = "Tech", displaySize = FolderDisplaySize.COMPACT)
        val folder2 = SubscriptionFolder(id = "f-2", name = "Comedy", displaySize = FolderDisplaySize.COMPACT)

        val unfiled = listOf(p1, p2, p3)
        val folders = listOf(folder1, folder2)
        val byFolderId = mapOf("f-1" to emptyList<Podcast>(), "f-2" to emptyList<Podcast>())

        // Default order (non-manual): folders first, then unfiled podcasts
        val defaultItems = buildUnifiedGridItems(
            compactFolders = folders,
            unfiledPodcasts = unfiled,
            podcastsByFolderId = byFolderId,
            isManualSort = false,
        )
        assertEquals(
            listOf("folder:f-1", "folder:f-2", "p-1", "p-2", "p-3"),
            defaultItems.map { it.key },
        )

        // Manual order: custom interleaving
        val manualOrder = listOf("p-2", "folder:f-2", "p-1", "folder:f-1")
        val manualItems = buildUnifiedGridItems(
            compactFolders = folders,
            unfiledPodcasts = unfiled,
            podcastsByFolderId = byFolderId,
            manualOrder = manualOrder,
            isManualSort = true,
        )
        // p-3 was not in manualOrder, so it appends at the end
        assertEquals(
            listOf("p-2", "folder:f-2", "p-1", "folder:f-1", "p-3"),
            manualItems.map { it.key },
        )
    }

    @Test
    fun `partitionSubscribedShows sorts in-folder shows to match active podcast sort order`() {
        // Feed in sorted podcasts (e.g. sorted by Recently Updated: p3 newest, then p1, then p2)
        val p3 = mockPodcast("p-3", latestPubDate = 3000L)
        val p1 = mockPodcast("p-1", latestPubDate = 2000L)
        val p2 = mockPodcast("p-2", latestPubDate = 1000L)
        val sortedPodcasts = listOf(p3, p1, p2)

        // Folder has podcastIds in arbitrary unsorted order: p1, p2, p3
        val folder = SubscriptionFolder(
            id = "f-1",
            name = "Folder",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-1", "p-2", "p-3"),
        )

        val partition = partitionSubscribedShows(
            podcasts = sortedPodcasts,
            folders = listOf(folder),
            sort = SubscriptionSort.RecentlyUpdated,
        )

        val inFolderShows = partition.podcastsByFolderId["f-1"].orEmpty()
        assertEquals(listOf("p-3", "p-1", "p-2"), inFolderShows.map { it.id })
    }

    @Test
    fun `partitionSubscribedShows sorts pinned and compact folders according to active sort`() {
        val pOld = mockPodcast("p-old", title = "Old Show", latestPubDate = 1000L)
        val pNew = mockPodcast("p-new", title = "New Show", latestPubDate = 5000L)
        val podcasts = listOf(pNew, pOld)

        val fShelfZ = SubscriptionFolder(
            id = "f-z",
            name = "Zeta Shelf",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-old"), // max pub date 1000L
        )
        val fShelfA = SubscriptionFolder(
            id = "f-a",
            name = "Alpha Shelf",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-new"), // max pub date 5000L
        )
        val fCompactZ = SubscriptionFolder(
            id = "c-z",
            name = "Zeta Compact",
            displaySize = FolderDisplaySize.COMPACT,
            podcastIds = listOf("p-old"),
        )
        val fCompactA = SubscriptionFolder(
            id = "c-a",
            name = "Alpha Compact",
            displaySize = FolderDisplaySize.COMPACT,
            podcastIds = listOf("p-new"),
        )

        val allFolders = listOf(fShelfZ, fShelfA, fCompactZ, fCompactA)

        // 1. RecentlyUpdated: newest episode date first
        val recentPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = allFolders,
            sort = SubscriptionSort.RecentlyUpdated,
        )
        assertEquals(listOf("f-a", "f-z"), recentPartition.pinnedFolders.map { it.id })
        assertEquals(listOf("c-a", "c-z"), recentPartition.compactFolders.map { it.id })

        // 2. Alphabetical: A-Z by name
        val alphaPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = allFolders,
            sort = SubscriptionSort.Alphabetical,
        )
        assertEquals(listOf("f-a", "f-z"), alphaPartition.pinnedFolders.map { it.id })
        assertEquals(listOf("c-a", "c-z"), alphaPartition.compactFolders.map { it.id })
    }

    @Test
    fun `hasFolderOverflowNew and hasAnyFolderShowNew detect new episodes correctly`() {
        val showWithNew = mockPodcast("p-new", rssHasNewEpisodes = true)
        val showWithoutNew = mockPodcast("p-old", rssHasNewEpisodes = false)

        val lastSeenEpisodes = mapOf("p-old" to "ep-p-old")

        // Overflow shows detection
        val overflowWithNew = listOf(showWithoutNew, showWithNew)
        val overflowWithoutNew = listOf(showWithoutNew)
        val overflowWithMultipleNew = listOf(showWithNew, showWithNew.copy(id = "p-new2"), showWithoutNew)

        assertTrue(hasFolderOverflowNew(overflowWithNew, lastSeenEpisodes))
        assertFalse(hasFolderOverflowNew(overflowWithoutNew, lastSeenEpisodes))
        assertEquals(1, countFolderOverflowNew(overflowWithNew, lastSeenEpisodes))
        assertEquals(0, countFolderOverflowNew(overflowWithoutNew, lastSeenEpisodes))
        assertEquals(2, countFolderOverflowNew(overflowWithMultipleNew, lastSeenEpisodes))

        // Any folder show detection (for 1x1 compact folders)
        assertTrue(hasAnyFolderShowNew(listOf(showWithNew), lastSeenEpisodes))
        assertFalse(hasAnyFolderShowNew(listOf(showWithoutNew), lastSeenEpisodes))
    }

    @Test
    fun `truncateCompactFolderName limits names exceeding max length with ellipsis`() {
        // <= 10 chars remain untouched
        assertEquals("Tech", truncateCompactFolderName("Tech"))
        assertEquals("Technology", truncateCompactFolderName("Technology"))
        assertEquals("1234567890", truncateCompactFolderName("1234567890"))

        // > 10 chars are truncated to first 10 + ellipsis
        assertEquals("Technology…", truncateCompactFolderName("Technology News"))
        assertEquals("My Favorit…", truncateCompactFolderName("My Favorite Shows"))
    }

    @Test
    fun `partitionSubscribedShows respects FolderInterSort options`() {
        val p1 = mockPodcast("p-1", latestPubDate = 5000L)
        val p2 = mockPodcast("p-2", latestPubDate = 2000L)
        val p3 = mockPodcast("p-3", latestPubDate = 8000L)
        val podcasts = listOf(p1, p2, p3)

        val folderA = SubscriptionFolder(
            id = "f-a",
            name = "Alpha",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-1"), // pub date 5000
        )
        val folderB = SubscriptionFolder(
            id = "f-b",
            name = "Beta",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-2", "p-3"), // pub date 8000, 2 shows
        )
        val folders = listOf(folderA, folderB)

        // Inter-sort: RecentlyUpdated -> folderB (8000) then folderA (5000)
        val recentPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = folders,
            folderSort = FolderInterSort.RecentlyUpdated,
        )
        assertEquals(listOf("f-b", "f-a"), recentPartition.pinnedFolders.map { it.id })

        // Inter-sort: Alphabetical -> folderA (Alpha) then folderB (Beta)
        val alphaPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = folders,
            folderSort = FolderInterSort.Alphabetical,
        )
        assertEquals(listOf("f-a", "f-b"), alphaPartition.pinnedFolders.map { it.id })

        // Inter-sort: MostShows -> folderB (2 shows) then folderA (1 show)
        val mostShowsPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = folders,
            folderSort = FolderInterSort.MostShows,
        )
        assertEquals(listOf("f-b", "f-a"), mostShowsPartition.pinnedFolders.map { it.id })

        // Inter-sort: SmartRank -> Decayed Top-3 score
        // p-1 is rank 0 (score 1.0) -> folderA score = 1.0
        // p-2 is rank 1 (score 0.667), p-3 is rank 2 (score 0.333) -> folderB score = 0.667 + 0.5*0.333 = 0.833
        // So folderA (1.0) beats folderB (0.833)
        val smartPartition = partitionSubscribedShows(
            podcasts = podcasts, // order: p-1, p-2, p-3
            folders = folders,
            folderSort = FolderInterSort.SmartRank,
        )
        assertEquals(listOf("f-a", "f-b"), smartPartition.pinnedFolders.map { it.id })

        // Inter-sort: Inherit with sort = SmartRank inherits SmartRank
        val inheritSmartPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = folders,
            sort = SubscriptionSort.SmartRank,
            folderSort = FolderInterSort.Inherit,
        )
        assertEquals(listOf("f-a", "f-b"), inheritSmartPartition.pinnedFolders.map { it.id })
    }

    @Test
    fun `calculateFolderSmartScore implements Decayed Top-3 diminishing returns`() {
        val p1 = mockPodcast("p-1")
        val p2 = mockPodcast("p-2")
        val p3 = mockPodcast("p-3")
        val p4 = mockPodcast("p-4")
        val p5 = mockPodcast("p-5")

        val rankMap = mapOf(
            "p-1" to 0,
            "p-2" to 1,
            "p-3" to 2,
            "p-4" to 3,
            "p-5" to 4,
        )
        val total = 5

        // Empty shows -> 0.0
        assertEquals(0.0, calculateFolderSmartScore(emptyList(), rankMap, total), 0.001)

        // 1 show at rank 0: score = (5 - 0) / 5 = 1.0 -> 1.0
        assertEquals(1.0, calculateFolderSmartScore(listOf(p1), rankMap, total), 0.001)

        // 2 shows: rank 1 (4/5 = 0.8) and rank 2 (3/5 = 0.6)
        // Score = 0.8 + 0.5 * 0.6 = 1.1 (beats single show at rank 0!)
        assertEquals(1.1, calculateFolderSmartScore(listOf(p2, p3), rankMap, total), 0.001)

        // 3 shows: rank 1 (0.8), rank 2 (0.6), rank 3 (2/5 = 0.4)
        // Score = 0.8 + 0.5 * 0.6 + 0.25 * 0.4 = 0.8 + 0.3 + 0.1 = 1.2
        assertEquals(1.2, calculateFolderSmartScore(listOf(p2, p3, p4), rankMap, total), 0.001)

        // 5 shows: rank 1, 2, 3, 4, 5
        // Shows 4 and 5 beyond the top 3 contribute 0 (diminishing returns prevents hoarder runaway)
        assertEquals(1.2, calculateFolderSmartScore(listOf(p2, p3, p4, p5), rankMap, total), 0.001)
    }

    @Test
    fun `partitionSubscribedShows respects FolderIntraSort options`() {
        val pA = mockPodcast("p-a", title = "Zebra", latestPubDate = 1000L)
        val pB = mockPodcast("p-b", title = "Apple", latestPubDate = 9000L)
        val pC = mockPodcast("p-c", title = "Mango", latestPubDate = 5000L)
        val podcasts = listOf(pA, pB, pC)

        val folder = SubscriptionFolder(
            id = "f-test",
            name = "Test Folder",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-c", "p-a", "p-b"), // Manual order: C, A, B
        )

        // Intra-sort: Alphabetical -> Apple (B), Mango (C), Zebra (A)
        val alphaPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            intraFolderSort = FolderIntraSort.Alphabetical,
        )
        assertEquals(listOf("p-b", "p-c", "p-a"), alphaPartition.podcastsByFolderId["f-test"]?.map { it.id })

        // Intra-sort: RecentlyUpdated -> Apple (9000), Mango (5000), Zebra (1000)
        val recentPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            intraFolderSort = FolderIntraSort.RecentlyUpdated,
        )
        assertEquals(listOf("p-b", "p-c", "p-a"), recentPartition.podcastsByFolderId["f-test"]?.map { it.id })

        // Intra-sort: Manual -> folder.podcastIds order: C, A, B
        val manualPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            intraFolderSort = FolderIntraSort.Manual,
        )
        assertEquals(listOf("p-c", "p-a", "p-b"), manualPartition.podcastsByFolderId["f-test"]?.map { it.id })

        // Intra-sort: Inherit with Alphabetical show sort
        val inheritAlpha = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            sort = SubscriptionSort.Alphabetical,
            intraFolderSort = FolderIntraSort.Inherit,
        )
        assertEquals(listOf("p-b", "p-c", "p-a"), inheritAlpha.podcastsByFolderId["f-test"]?.map { it.id })
    }

    @Test
    fun `resolveSortedFolderShows matches partitionSubscribedShows order in SmartRank and other sorts`() {
        val pA = mockPodcast("p-a", title = "Zebra", latestPubDate = 1000L)
        val pB = mockPodcast("p-b", title = "Apple", latestPubDate = 9000L)
        val pC = mockPodcast("p-c", title = "Mango", latestPubDate = 5000L)
        val podcasts = listOf(pA, pB, pC)
        val folder = SubscriptionFolder(
            id = "f-test",
            name = "Test Folder",
            displaySize = FolderDisplaySize.SHELF,
            podcastIds = listOf("p-c", "p-a", "p-b"), // DB insertion/manual order: C, A, B
        )
        val smartOrder = listOf("p-a", "p-c", "p-b") // Smart rank order: A, C, B

        // 1. In SmartRank mode (inheriting SubscriptionSort.SmartRank)
        val smartPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            sort = SubscriptionSort.SmartRank,
            intraFolderSort = FolderIntraSort.Inherit,
            smartOrderIds = smartOrder,
        )
        val resolvedSmartShows = resolveSortedFolderShows(
            folder = folder,
            podcasts = podcasts,
            intraFolderSort = FolderIntraSort.Inherit,
            sort = SubscriptionSort.SmartRank,
            smartOrderIds = smartOrder,
        )
        assertEquals(listOf("p-a", "p-c", "p-b"), resolvedSmartShows.map { it.id })
        assertEquals(smartPartition.podcastsByFolderId["f-test"]?.map { it.id }, resolvedSmartShows.map { it.id })

        // 2. In Alphabetical mode
        val alphaPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            sort = SubscriptionSort.Alphabetical,
            intraFolderSort = FolderIntraSort.Inherit,
        )
        val resolvedAlphaShows = resolveSortedFolderShows(
            folder = folder,
            podcasts = podcasts,
            intraFolderSort = FolderIntraSort.Inherit,
            sort = SubscriptionSort.Alphabetical,
        )
        assertEquals(listOf("p-b", "p-c", "p-a"), resolvedAlphaShows.map { it.id })
        assertEquals(alphaPartition.podcastsByFolderId["f-test"]?.map { it.id }, resolvedAlphaShows.map { it.id })

        // 3. In RecentlyUpdated mode
        val recentPartition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder),
            sort = SubscriptionSort.RecentlyUpdated,
            intraFolderSort = FolderIntraSort.Inherit,
        )
        val resolvedRecentShows = resolveSortedFolderShows(
            folder = folder,
            podcasts = podcasts,
            intraFolderSort = FolderIntraSort.Inherit,
            sort = SubscriptionSort.RecentlyUpdated,
        )
        assertEquals(listOf("p-b", "p-c", "p-a"), resolvedRecentShows.map { it.id })
        assertEquals(recentPartition.podcastsByFolderId["f-test"]?.map { it.id }, resolvedRecentShows.map { it.id })
    }

    @Test
    fun `partitionSubscribedShows with FolderInterSort Manual respects folderManualOrder`() {
        val podcasts = (1..6).map { mockPodcast(it.toString()) }
        val folder1 = SubscriptionFolder(id = "f-1", name = "Folder 1", podcastIds = listOf("1", "2"))
        val folder2 = SubscriptionFolder(id = "f-2", name = "Folder 2", podcastIds = listOf("3", "4"))
        val folder3 = SubscriptionFolder(id = "f-3", name = "Folder 3", podcastIds = listOf("5", "6"))

        val partition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder1, folder2, folder3),
            folderSort = FolderInterSort.Manual,
            folderManualOrder = listOf("f-3", "f-1"),
        )

        val compactOrder = partition.compactFolders.map { it.id }
        assertEquals(listOf("f-3", "f-1", "f-2"), compactOrder)
    }

    @Test
    fun `partitionSubscribedShows with FolderInterSort Inherit when sort is Manual respects folderManualOrder`() {
        val podcasts = (1..6).map { mockPodcast(it.toString()) }
        val folder1 = SubscriptionFolder(id = "f-1", name = "Folder 1", podcastIds = listOf("1", "2"))
        val folder2 = SubscriptionFolder(id = "f-2", name = "Folder 2", podcastIds = listOf("3", "4"))
        val folder3 = SubscriptionFolder(id = "f-3", name = "Folder 3", podcastIds = listOf("5", "6"))

        val partition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(folder1, folder2, folder3),
            sort = SubscriptionSort.Manual,
            folderSort = FolderInterSort.Inherit,
            folderManualOrder = listOf("f-2", "f-3", "f-1"),
        )

        val compactOrder = partition.compactFolders.map { it.id }
        assertEquals(listOf("f-2", "f-3", "f-1"), compactOrder)
    }

    @Test
    fun `resolveSortedFolderShows with FolderIntraSort Manual preserves exact folder podcastIds order`() {
        val podcasts = listOf(
            mockPodcast("pod-z", title = "Zeta"),
            mockPodcast("pod-a", title = "Alpha"),
            mockPodcast("pod-m", title = "Mike"),
        )
        val folder = SubscriptionFolder(
            id = "f-manual",
            name = "Manual Folder",
            podcastIds = listOf("pod-m", "pod-z", "pod-a"),
        )

        val resolved = resolveSortedFolderShows(
            folder = folder,
            podcasts = podcasts,
            intraFolderSort = FolderIntraSort.Manual,
        )

        assertEquals(listOf("pod-m", "pod-z", "pod-a"), resolved.map { it.id })
    }

    @Test
    fun `partitionSubscribedShows with FolderInterSort Manual allows compact folders to be positioned above shelf folders`() {
        val podcasts = (1..10).map { mockPodcast(it.toString()) }
        val shelf1 = SubscriptionFolder(
            id = "shelf-1",
            name = "Shelf 1",
            podcastIds = listOf("1", "2"),
            displaySize = cx.aswin.boxlore.core.model.FolderDisplaySize.SHELF,
        )
        val shelf2 = SubscriptionFolder(
            id = "shelf-2",
            name = "Shelf 2",
            podcastIds = listOf("3", "4"),
            displaySize = cx.aswin.boxlore.core.model.FolderDisplaySize.SHELF,
        )
        val compact1 = SubscriptionFolder(
            id = "compact-1",
            name = "Compact 1",
            podcastIds = listOf("5"),
            displaySize = cx.aswin.boxlore.core.model.FolderDisplaySize.COMPACT,
        )
        val compact2 = SubscriptionFolder(
            id = "compact-2",
            name = "Compact 2",
            podcastIds = listOf("6"),
            displaySize = cx.aswin.boxlore.core.model.FolderDisplaySize.COMPACT,
        )
        val compact3 = SubscriptionFolder(
            id = "compact-3",
            name = "Compact 3",
            podcastIds = listOf("7"),
            displaySize = cx.aswin.boxlore.core.model.FolderDisplaySize.COMPACT,
        )

        val partition = partitionSubscribedShows(
            podcasts = podcasts,
            folders = listOf(shelf1, shelf2, compact1, compact2, compact3),
            folderSort = FolderInterSort.Manual,
            folderManualOrder = listOf("compact-1", "compact-2", "compact-3", "shelf-1", "shelf-2"),
        )

        // Verifies the user scenario: 3 1x1 compact folders positioned above the 2 3x1 shelf folders
        val unifiedOrder = partition.folders.map { it.id }
        assertEquals(listOf("compact-1", "compact-2", "compact-3", "shelf-1", "shelf-2"), unifiedOrder)
    }

    @Test
    fun `smart sort label is unified across folder inter and intra sort enums`() {
        assertEquals("Smart", FolderInterSort.SmartRank.label)
        assertEquals("Smart", FolderIntraSort.SmartRank.label)
        assertEquals("Same as shows", FolderInterSort.Inherit.label)
        assertEquals("Your order", FolderIntraSort.Manual.label)
    }
}
