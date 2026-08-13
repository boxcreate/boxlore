package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoSupplementSupportTest {
    @Test
    fun `refreshMissingEpisodes reloads page zero and reports sourceCount`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf())
            port.refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 2,
                    totalSupplementCount = 2,
                    newestFeedEpisode = TestFixtures.episode(id = "-1"),
                )
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, limit, offset, _ ->
                        PodcastRepository.EpisodePage(
                            episodes = listOf(TestFixtures.episode(id = "pi-1")),
                            hasMore = limit > 0,
                            sourceCount = if (offset == 0) 7 else 3,
                        )
                    },
                )
            val state =
                PodcastInfoUiState.Success(
                    podcast = TestFixtures.podcast(id = "123").copy(
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                    episodes = emptyList(),
                    isSubscribed = true,
                )
            val refresh = support.refreshMissingEpisodes(state, announceResult = true)
            assertEquals(7, refresh.pageSourceCount)
            assertEquals("Added 2 episodes", refresh.state.userMessage)
            assertEquals("-1", refresh.libraryTip?.id)
            assertEquals(DirectFeedChipState.Updated, refresh.state.directFeedChip)
        }

    @Test
    fun `autoOptIn returns null on NoDisconnect`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf())
            port.optInOutcome = EpisodeSupplementOutcome.NoDisconnect
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, _, _ -> error("should not reload") },
                )
            val state =
                PodcastInfoUiState.Success(
                    podcast = TestFixtures.podcast(id = "123").copy(
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                    episodes = emptyList(),
                    isSubscribed = true,
                )
            assertNull(support.autoOptInOnSubscribeIfDisconnected(state))
        }

    @Test
    fun `autoOptIn failure is silent unless announced`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf())
            port.optInOutcome =
                EpisodeSupplementOutcome.Failure("Couldn't update episodes from the feed")
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, _, _ -> error("should not reload") },
                )
            val state =
                PodcastInfoUiState.Success(
                    podcast = TestFixtures.podcast(id = "123").copy(
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                    episodes = emptyList(),
                    isSubscribed = true,
                )
            assertNull(support.autoOptInOnSubscribeIfDisconnected(state, announce = false))
            val announced = support.autoOptInOnSubscribeIfDisconnected(state, announce = true)
            assertEquals(
                "Couldn't update episodes from the feed",
                announced?.state?.userMessage,
            )
        }

    @Test
    fun `unionSearch prefers supplement matches`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf("123"))
            port.searchResults =
                listOf(
                    TestFixtures.episode(
                        id = "shared",
                        title = "Feed hit",
                        audioUrl = "https://cdn/shared.mp3",
                        publishedDate = 50,
                    ),
                )
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, _, _ -> error("unused") },
                )
            val union =
                support.unionSearch(
                    feedId = "123",
                    query = "hit",
                    networkResults = listOf(
                        TestFixtures.episode(
                            id = "shared",
                            title = "PI hit",
                            audioUrl = "https://cdn/shared.mp3",
                            publishedDate = 50,
                        ),
                    ),
                    meta = PodcastListMeta("Show", null, null, null),
                    isRss = false,
                )
            assertEquals("Feed hit", union.single().title)
        }

    @Test
    fun `refreshMissingEpisodes surfaces Failure message and still reloads`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf("123"))
            port.refreshOutcome =
                EpisodeSupplementOutcome.Failure("Couldn't update episodes from the feed")
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, offset, _ ->
                        PodcastRepository.EpisodePage(
                            episodes = listOf(TestFixtures.episode(id = "pi-1")),
                            hasMore = false,
                            sourceCount = if (offset == 0) 4 else 0,
                        )
                    },
                )
            val state =
                PodcastInfoUiState.Success(
                    podcast = TestFixtures.podcast(id = "123").copy(
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                    episodes = emptyList(),
                    isSubscribed = true,
                )
            val refresh = support.refreshMissingEpisodes(state, announceResult = true)
            assertEquals(
                "Couldn't update episodes from the feed",
                refresh.state.userMessage,
            )
            assertEquals(4, refresh.pageSourceCount)
            assertNull(refresh.libraryTip)
        }

    @Test
    fun `refreshMissingEpisodes matches against PI-only baseline not merged extras`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf("123"))
            port.refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 1,
                    totalSupplementCount = 1,
                    newestFeedEpisode = TestFixtures.episode(id = "-9"),
                )
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, _, _ ->
                        PodcastRepository.EpisodePage(
                            episodes = listOf(TestFixtures.episode(id = "merged-extra")),
                            hasMore = false,
                            sourceCount = 1,
                        )
                    },
                    loadPiBaseline = { listOf(TestFixtures.episode(id = "pi-only")) },
                )
            val state =
                PodcastInfoUiState.Success(
                    podcast = TestFixtures.podcast(id = "123").copy(
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                    episodes = emptyList(),
                    isSubscribed = true,
                )
            support.refreshMissingEpisodes(state, announceResult = false)
            assertEquals(listOf("pi-only"), port.capturedBaselineIds)
        }

    @Test
    fun `shouldRefreshOnOpen is true only for opted-in PI shows`() =
        runTest {
            val port = FakePort(optedIn = mutableSetOf("123"))
            val support =
                PodcastInfoSupplementSupport(
                    episodeSupplementPort = port,
                    loadPage = { _, _, _, _ -> error("unused") },
                )
            assertTrue(support.shouldRefreshOnOpen("123", isRss = false))
            assertTrue(!support.shouldRefreshOnOpen("123", isRss = true))
            assertTrue(!support.shouldRefreshOnOpen("999", isRss = false))
        }

    private class FakePort(
        var optedIn: MutableSet<String>,
    ) : EpisodeSupplementPort {
        var refreshOutcome: EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect
        var optInOutcome: EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect
        var searchResults: List<Episode> = emptyList()
        var capturedBaselineIds: List<String> = emptyList()

        override suspend fun refreshFromFeed(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome {
            optedIn.add(podcastIndexId)
            capturedBaselineIds = baselineEpisodes.map { it.id }
            return refreshOutcome
        }

        override suspend fun optInFromFeedIfDisconnected(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome {
            if (optInOutcome is EpisodeSupplementOutcome.Success) {
                optedIn.add(podcastIndexId)
            }
            return optInOutcome
        }

        override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean =
            podcastIndexId in optedIn

        override suspend fun listOptedInPodcastIds(): Set<String> = optedIn

        override suspend fun resolveNewestTipFromFeed(
            request: EpisodeSupplementPort.NewestTipRequest,
        ): Episode? = null

        override suspend fun getEpisodesForPodcast(
            podcastIndexId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = emptyList()

        override suspend fun getEpisode(
            episodeId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): Episode? = null

        override suspend fun search(
            podcastIndexId: String,
            query: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = searchResults
    }
}
