package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LibraryWidgetSnapshotStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writesAndReadsSnapshotJson() {
        val store = LibraryWidgetSnapshotStore(context)
        val snapshot =
            LibraryWidgetSnapshot(
                subscriptions =
                    listOf(
                        WidgetShowRow(
                            podcastId = "p1",
                            title = "Show",
                            subtitle = "Artist",
                            deepLinkUri = "boxlore://podcast/p1",
                            isNew = true,
                        ),
                    ),
                newEpisodes =
                    listOf(
                        WidgetEpisodeRow(
                            episodeId = "e1",
                            episodeTitle = "Episode",
                            podcastId = "p1",
                            podcastTitle = "Show",
                            deepLinkUri = "boxlore://episode/e1",
                        ),
                    ),
                updatedAtMs = 42L,
            )
        store.write(snapshot)
        assertEquals(snapshot, store.read())
    }

    @Test
    fun clearRemovesPersistedSnapshot() {
        val store = LibraryWidgetSnapshotStore(context)
        store.write(LibraryWidgetSnapshot(updatedAtMs = 1L))
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun usesStablePrefsFileName() {
        assertEquals("boxlore_library_widget", LibraryWidgetSnapshotStore.PREFS_NAME)
    }

    @Test
    fun malformedSnapshotJsonReturnsNull() {
        val prefs =
            context.getSharedPreferences(
                LibraryWidgetSnapshotStore.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        prefs.edit().putString("snapshot", "{not-valid-json").commit()

        assertNull(LibraryWidgetSnapshotStore(context).read())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LibraryWidgetRemoteViewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun subscriptionsEmptyUsesListLayout() {
        val views =
            LibraryWidgetRenderer.build(
                context = context,
                appWidgetId = 1,
                snapshot = LibraryWidgetSnapshot(),
                kind = LibraryWidgetKind.SUBSCRIPTIONS,
            )
        assertEquals(R.layout.library_widget_list, views.layoutId)
        views.apply(context, FrameLayout(context))
    }

    @Test
    fun newEpisodesWithRowsInflates() {
        val views =
            LibraryWidgetRenderer.build(
                context = context,
                appWidgetId = 2,
                snapshot =
                    LibraryWidgetSnapshot(
                        newEpisodes =
                            listOf(
                                WidgetEpisodeRow(
                                    episodeId = "e1",
                                    episodeTitle = "Episode",
                                    podcastId = "p1",
                                    podcastTitle = "Show",
                                    deepLinkUri = "boxlore://episode/e1",
                                ),
                            ),
                    ),
                kind = LibraryWidgetKind.NEW_EPISODES,
            )
        assertEquals(R.layout.library_widget_list, views.layoutId)
        views.apply(context, FrameLayout(context))
    }

    @Test
    fun populatedListResizeFloorLeavesRoomForACompleteRow() {
        val views =
            LibraryWidgetRenderer.build(
                context = context,
                appWidgetId = 3,
                snapshot =
                    LibraryWidgetSnapshot(
                        subscriptions =
                            listOf(
                                WidgetShowRow(
                                    podcastId = "p1",
                                    title = "A show with a long title",
                                    subtitle = "Publisher",
                                    deepLinkUri = "boxlore://podcast/p1",
                                ),
                            ),
                    ),
                kind = LibraryWidgetKind.SUBSCRIPTIONS,
            )
        val root = views.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((245 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((180 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val minimumRowHeight = (56 * density).toInt()
        val listHeight = root.findViewById<View>(R.id.widget_list_content).height
        assertTrue(
            "list content was ${listHeight}px; expected at least ${minimumRowHeight}px",
            listHeight >= minimumRowHeight,
        )
    }

    @Test
    fun newEpisodeRowsAllowTwoTitleLinesWithoutChangingSubscriptionRows() {
        val store = LibraryWidgetSnapshotStore(context)
        store.write(
            LibraryWidgetSnapshot(
                subscriptions =
                    listOf(
                        WidgetShowRow(
                            podcastId = "p1",
                            title = "A subscription title that remains on one line",
                            subtitle = "Publisher",
                            deepLinkUri = "boxlore://podcast/p1",
                        ),
                    ),
                newEpisodes =
                    listOf(
                        WidgetEpisodeRow(
                            episodeId = "e1",
                            episodeTitle =
                                "An exceptionally long episode title that needs a second line " +
                                    "before it is truncated",
                            podcastId = "p1",
                            podcastTitle = "A show name",
                            deepLinkUri = "boxlore://episode/e1",
                        ),
                    ),
            ),
        )

        val episodeFactory =
            LibraryWidgetRemoteViewsFactory(context, LibraryWidgetKind.NEW_EPISODES).apply {
                onDataSetChanged()
            }
        val episodeViews = episodeFactory.getViewAt(0)
        val episodeRoot = episodeViews.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        episodeRoot.measure(
            View.MeasureSpec.makeMeasureSpec((245 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((68 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        episodeRoot.layout(0, 0, episodeRoot.measuredWidth, episodeRoot.measuredHeight)

        val episodeTitle = episodeRoot.findViewById<TextView>(R.id.widget_row_title)
        val episodeSubtitle = episodeRoot.findViewById<TextView>(R.id.widget_row_subtitle)
        val episodeMetadata = episodeRoot.findViewById<View>(R.id.widget_row_metadata)
        assertEquals(R.layout.library_widget_episode_list_item, episodeViews.layoutId)
        assertEquals(2, episodeTitle.maxLines)
        assertTrue(episodeTitle.top >= 0)
        assertTrue(episodeSubtitle.bottom <= episodeMetadata.height)

        val subscriptionFactory =
            LibraryWidgetRemoteViewsFactory(context, LibraryWidgetKind.SUBSCRIPTIONS).apply {
                onDataSetChanged()
            }
        val subscriptionViews = subscriptionFactory.getViewAt(0)
        val subscriptionRoot = subscriptionViews.apply(context, FrameLayout(context))
        assertEquals(R.layout.library_widget_list_item, subscriptionViews.layoutId)
        assertEquals(1, subscriptionRoot.findViewById<TextView>(R.id.widget_row_title).maxLines)

        episodeFactory.onDestroy()
        subscriptionFactory.onDestroy()
        store.clear()
    }
}
