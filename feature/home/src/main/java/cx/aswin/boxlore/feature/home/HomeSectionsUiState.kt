package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import cx.aswin.boxlore.feature.home.components.DailyBriefingCardState

/**
 * Where the listener is inside Your shows and the Brief: the Daily and Offline mix rails' scroll, each
 * selected show's episode list scroll, the Brief's hide step and story list, and which menu is open.
 * Home's grid swaps its items when a window crosses the tablet size, a foldable opens or closes, or
 * the Brief appears beside Your shows on a tablet, which used to rebuild these sections from scratch.
 * Home holds this above the grid and saves it, so they stay put in both directions and after the
 * activity is recreated.
 */
@Stable
internal class HomeSectionsUiState(
    val dailyRail: LazyListState,
    val offlineRail: LazyListState,
    showScroll: Map<String, Int>,
    briefKey: String?,
    briefStep: DailyBriefingCardState,
    briefExpanded: Boolean,
    mixMenuOpen: Boolean,
    pinMenuShowId: String?,
    heroIndex: Int = 0,
) {
    /** Most recently used last; at most [MaxShowLists] shows, so unsubscribed shows don't pile up. */
    private val showLists = object : LinkedHashMap<String, ScrollState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScrollState>?) = size > MaxShowLists
    }.apply { showScroll.forEach { (id, offset) -> put(id, ScrollState(offset)) } }

    private var briefFor by mutableStateOf(briefKey)
    private var step by mutableStateOf(briefStep)
    private var expanded by mutableStateOf(briefExpanded)

    /** Whether the Daily/Offline mix menu is open. */
    val mixMenu: MutableState<Boolean> = mutableStateOf(mixMenuOpen)

    /**
     * The hero card the listener is on. Marquee's pager (phones) and the carousel (tablets) are
     * different views of the same items, so both start from it and write back when they settle.
     */
    val heroIndex: MutableIntState = mutableIntStateOf(heroIndex)

    /** The show whose Pin/Unpin menu is open, or null. */
    val pinMenu: MutableState<String?> = mutableStateOf(pinMenuShowId)

    /** The scroll of [showId]'s episode list in Your shows. */
    fun showListScroll(showId: String): ScrollState = showLists.getOrPut(showId) { ScrollState(0) }

    /** The Brief's step for the edition [key]; a new edition starts on its normal card. */
    fun briefStep(key: String): DailyBriefingCardState = if (key == briefFor) step else DailyBriefingCardState.NORMAL

    fun setBriefStep(key: String, value: DailyBriefingCardState) {
        claimBrief(key)
        step = value
    }

    /** Whether the Brief's full story list is open for the edition [key]. */
    fun briefExpanded(key: String): Boolean = key == briefFor && expanded

    fun setBriefExpanded(key: String, value: Boolean) {
        claimBrief(key)
        expanded = value
    }

    private fun claimBrief(key: String) {
        if (key == briefFor) return
        briefFor = key
        step = DailyBriefingCardState.NORMAL
        expanded = false
    }

    companion object {
        private const val MaxShowLists = 20

        fun initial() = HomeSectionsUiState(LazyListState(), LazyListState(), emptyMap(), null, DailyBriefingCardState.NORMAL, false, false, null)

        /** The Brief's edition key: its region and date. */
        fun briefKey(region: String, date: String) = "$region|$date"

        val Saver: Saver<HomeSectionsUiState, Any> = Saver(
            save = { state ->
                arrayListOf<Any?>(
                    state.dailyRail.firstVisibleItemIndex, state.dailyRail.firstVisibleItemScrollOffset,
                    state.offlineRail.firstVisibleItemIndex, state.offlineRail.firstVisibleItemScrollOffset,
                    ArrayList(state.showLists.keys), ArrayList(state.showLists.values.map { it.value }),
                    state.briefFor, state.step.name, state.expanded, state.mixMenu.value, state.pinMenu.value, state.heroIndex.intValue,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val list = saved as List<Any?>
                val ids = list[4] as List<String>
                val offsets = list[5] as List<Int>
                HomeSectionsUiState(
                    dailyRail = LazyListState(list[0] as Int, list[1] as Int),
                    offlineRail = LazyListState(list[2] as Int, list[3] as Int),
                    showScroll = ids.zip(offsets).toMap(),
                    briefKey = list[6] as String?,
                    briefStep = DailyBriefingCardState.valueOf(list[7] as String),
                    briefExpanded = list[8] as Boolean,
                    mixMenuOpen = list[9] as Boolean,
                    pinMenuShowId = list[10] as String?,
                    heroIndex = list[11] as Int,
                )
            },
        )
    }
}

/** Home's sections state, saved across recreation. */
@Composable
internal fun rememberHomeSectionsUiState(): HomeSectionsUiState = rememberSaveable(saver = HomeSectionsUiState.Saver) { HomeSectionsUiState.initial() }

/**
 * The sections state Home provides to Your shows and the Brief. Null outside Home (previews, tests of
 * a single section): each section then keeps its own state.
 */
internal val LocalHomeSectionsUiState = staticCompositionLocalOf<HomeSectionsUiState?> { null }
