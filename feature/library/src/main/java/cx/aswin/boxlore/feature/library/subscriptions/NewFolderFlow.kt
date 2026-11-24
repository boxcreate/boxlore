package cx.aswin.boxlore.feature.library.subscriptions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.icon.findExactGenreIconKey
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.FolderSaveParams
import cx.aswin.boxlore.feature.library.R

/** What the New folder flow needs from Subscriptions. */
internal data class NewFolderFlowData(
    val podcasts: List<Podcast>,
    val folders: List<SubscriptionFolder>,
    val autoOrganizeOn: Boolean,
)

internal data class NewFolderFlowActions(
    val onClose: () -> Unit,
    val onCreate: (FolderSaveParams, List<String>) -> Unit,
    /** All genres: null lets boxlore choose each folder's look. */
    val onTurnOnAutoOrganize: (FolderDisplaySize?, Boolean) -> Unit,
    val onManageAutoOrganize: () -> Unit,
)

/**
 * New folder, drawn over Subscriptions. Step 1 asks what goes in it: shows the listener picks
 * (then a name, then a look), one genre (then a look), or all genres, which turns on auto-organize.
 */
@Composable
internal fun NewFolderFlow(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
    actions: NewFolderFlowActions,
    modifier: Modifier = Modifier,
) {
    val onBack = { NewFolderFlowLogic.back(draft)?.let(onDraftChange) ?: actions.onClose() }
    val stepScreen =
        when {
            draft.step == 0 -> "new_folder_contents"
            draft.mode == NewFolderMode.PickShows && draft.step == 1 -> "new_folder_name"
            else -> "new_folder_look"
        }
    TrackScreenView(stepScreen) { previous ->
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackScreenViewed(stepScreen, previous, mapOf("mode" to draft.mode.name.lowercase()))
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).imePadding()) {
            FolderFlowTopBar(
                title = stringResource(R.string.library_folder_new),
                onBack = onBack,
                showClose = draft.step == 0,
                step = draft.step,
                stepCount = draft.stepCount,
            )
            Box(modifier = Modifier.weight(1f)) {
                when {
                    draft.step == 0 -> WhatGoesInStep(draft, onDraftChange, data)
                    draft.mode == NewFolderMode.PickShows && draft.step == 1 -> NameStep(draft, onDraftChange)
                    else -> LookStep(draft, onDraftChange, data.podcasts)
                }
            }
            NewFolderBottomBar(draft, onDraftChange, data, actions)
        }
    }
}

@Composable
private fun NewFolderBottomBar(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
    actions: NewFolderFlowActions,
) {
    val canGoNext = NewFolderFlowLogic.canGoNext(draft)
    when {
        draft.mode == NewFolderMode.AllGenres && data.autoOrganizeOn -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_manage),
            primaryEnabled = true,
            onPrimary = actions.onManageAutoOrganize,
            title = stringResource(R.string.library_auto_organize_is_on),
        )
        draft.mode == NewFolderMode.AllGenres -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_auto_organize_turn_on),
            primaryEnabled = true,
            onPrimary = { actions.onTurnOnAutoOrganize(draft.autoFixedLook, draft.tileShowsCovers) },
        )
        draft.isLastStep -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_folder_create),
            primaryEnabled = canGoNext,
            onPrimary = {
                val ids = if (draft.mode == NewFolderMode.PickShows) draft.pickedIds else emptyList()
                actions.onCreate(NewFolderFlowLogic.saveParams(draft), ids)
            },
        )
        draft.mode == NewFolderMode.PickShows && draft.step == 0 -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_next),
            primaryEnabled = canGoNext,
            onPrimary = { onDraftChange(NewFolderFlowLogic.next(draft)) },
            title = pluralStringResource(R.plurals.library_shows_picked, draft.pickedIds.size, draft.pickedIds.size),
            subtitle = stringResource(if (draft.pickedIds.isEmpty()) R.string.library_folder_pick_at_least_one else R.string.library_folder_pick_stays_elsewhere),
        )
        else -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_next),
            primaryEnabled = canGoNext,
            onPrimary = { onDraftChange(NewFolderFlowLogic.next(draft)) },
            title = draft.genre?.let { genre ->
                stringResource(
                    R.string.library_folder_genre_and_count,
                    subscriptionGenreDisplayName(genre),
                    showCountLabel(matchingShows(data.podcasts, genre).size).asString(),
                )
            },
        )
    }
}

/** Shows a genre folder for [genre] would hold: the same matching the folder fills by. */
internal fun matchingShows(podcasts: List<Podcast>, genre: String): List<Podcast> =
    podcasts.filter { podcast -> podcast.effectiveGenre.split(",").any { token -> PodcastGenres.tokenMatches(token, genre) } }

@Composable
private fun WhatGoesInStep(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
) {
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.library_folder_what_goes_in),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 18.dp),
            )
            ModeSelector(selected = draft.mode, onSelect = { onDraftChange(NewFolderFlowLogic.withMode(draft, it)) })
        }
    }
    when (draft.mode) {
        NewFolderMode.PickShows -> PickShowsContent(draft, onDraftChange, data, header)
        NewFolderMode.OneGenre -> ScrollingStep(header) { OneGenreContent(draft, onDraftChange, data) }
        NewFolderMode.AllGenres -> ScrollingStep(header) { AllGenresContent(draft, onDraftChange, data) }
    }
}

@Composable
private fun ScrollingStep(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        header()
        content()
    }
}

/** The app's own pill switcher (as on Subscriptions and Explore), not outlined segments. */
@Composable
private fun ModeSelector(selected: NewFolderMode, onSelect: (NewFolderMode) -> Unit) {
    val modes = NewFolderMode.entries
    DiscoveryTabSwitcher(
        tabs = modes.map { mode -> DiscoveryTab(label = stringResource(newFolderModeLabel(mode)), icon = newFolderModeIcon(mode)) },
        selectedIndex = modes.indexOf(selected),
        onTabSelected = { index -> onSelect(modes[index]) },
        floating = false,
    )
}

@StringRes
internal fun newFolderModeLabel(mode: NewFolderMode): Int = when (mode) {
    NewFolderMode.PickShows -> R.string.library_folder_mode_shows
    NewFolderMode.OneGenre -> R.string.library_folder_mode_genre
    NewFolderMode.AllGenres -> R.string.library_folder_mode_auto
}

private fun newFolderModeIcon(mode: NewFolderMode) = when (mode) {
    NewFolderMode.PickShows -> Icons.Rounded.Checklist
    NewFolderMode.OneGenre -> Icons.Rounded.Category
    NewFolderMode.AllGenres -> Icons.Rounded.AutoAwesome
}

@Composable
private fun AllGenresContent(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
) {
    if (data.autoOrganizeOn) {
        AutoAddNote(text = stringResource(R.string.library_auto_organize_already_on_note))
        return
    }
    AutoOrganizeSetupContent(
        podcasts = data.podcasts,
        folders = data.folders,
        fixedLook = draft.autoFixedLook,
        onFixedLookChange = { onDraftChange(draft.copy(autoFixedLook = it)) },
        tileShowsCovers = draft.tileShowsCovers,
        onTileShowsCoversChange = { onDraftChange(draft.copy(tileShowsCovers = it)) },
    )
    Text(
        text = stringResource(R.string.library_auto_organize_change_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameStep(draft: NewFolderDraft, onDraftChange: (NewFolderDraft) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.library_folder_name_it), style = MaterialTheme.typography.headlineSmall)
        FolderNameField(
            name = draft.name,
            onNameChange = { onDraftChange(NewFolderFlowLogic.withName(draft, it, findExactGenreIconKey(it))) },
            iconKey = draft.icon,
            helper = NewFolderFlowLogic.savedNameNote(draft.name)?.let { stringResource(it) },
        )
        if (NewFolderFlowLogic.nameIsGenre(draft.name)) {
            // A folder named after a genre links to it (a folder rule), so it says it is an auto folder.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoFolderMark(size = 18.dp)
                Text(
                    text = stringResource(R.string.library_folder_name_is_genre, draft.name.trim()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = stringResource(R.string.library_folder_icon), style = MaterialTheme.typography.titleSmall)
        FolderIconGrid(selectedKey = draft.icon, onSelect = { onDraftChange(NewFolderFlowLogic.withIcon(draft, it)) })
    }
}

@Composable
private fun LookStep(draft: NewFolderDraft, onDraftChange: (NewFolderDraft) -> Unit, podcasts: List<Podcast>) {
    val shows = remember(podcasts, draft.pickedIds, draft.genre, draft.mode) {
        when (draft.mode) {
            NewFolderMode.OneGenre -> draft.genre?.let { matchingShows(podcasts, it) }.orEmpty()
            else -> draft.pickedIds.mapNotNull { id -> podcasts.firstOrNull { it.id == id } }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = stringResource(R.string.library_folder_choose_look), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = draft.name.trim().takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.library_folder_look_intro_named, it) }
                ?: stringResource(R.string.library_folder_look_intro_unnamed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FolderLookCards(
            selected = draft.size,
            onSelect = { onDraftChange(draft.copy(size = it)) },
            shows = shows,
            iconKey = draft.icon,
            tileShowsCovers = draft.tileShowsCovers,
        )
        if (draft.size == FolderDisplaySize.COMPACT) {
            TileCoverChoice(
                showsCovers = draft.tileShowsCovers,
                onChange = { onDraftChange(draft.copy(tileShowsCovers = it)) },
                iconKey = draft.icon,
            )
        }
        Text(
            text = stringResource(R.string.library_folder_look_overflow_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
