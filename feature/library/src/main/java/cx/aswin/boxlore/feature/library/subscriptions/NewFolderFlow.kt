package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.icon.findExactGenreIconKey
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.FolderSaveParams

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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            FolderFlowTopBar(
                title = "New folder",
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
            primaryLabel = "Manage",
            primaryEnabled = true,
            onPrimary = actions.onManageAutoOrganize,
            title = "Auto-organize is on",
        )
        draft.mode == NewFolderMode.AllGenres -> FolderFlowBottomBar(
            primaryLabel = "Turn on auto-organize",
            primaryEnabled = true,
            onPrimary = { actions.onTurnOnAutoOrganize(draft.autoFixedLook, draft.tileShowsCovers) },
        )
        draft.isLastStep -> FolderFlowBottomBar(
            primaryLabel = "Create folder",
            primaryEnabled = canGoNext,
            onPrimary = {
                val ids = if (draft.mode == NewFolderMode.PickShows) draft.pickedIds else emptyList()
                actions.onCreate(NewFolderFlowLogic.saveParams(draft), ids)
            },
        )
        draft.mode == NewFolderMode.PickShows && draft.step == 0 -> FolderFlowBottomBar(
            primaryLabel = "Next",
            primaryEnabled = canGoNext,
            onPrimary = { onDraftChange(NewFolderFlowLogic.next(draft)) },
            title = pickedShowsTitle(draft.pickedIds.size),
            subtitle = if (draft.pickedIds.isEmpty()) "Pick at least one" else "They stay in any other folder too",
        )
        else -> FolderFlowBottomBar(
            primaryLabel = "Next",
            primaryEnabled = canGoNext,
            onPrimary = { onDraftChange(NewFolderFlowLogic.next(draft)) },
            title = draft.genre?.let { genre -> "$genre · ${matchingShows(data.podcasts, genre).size} shows" },
        )
    }
}

private fun pickedShowsTitle(count: Int): String = if (count == 1) "1 show picked" else "$count shows picked"

internal fun matchingShows(podcasts: List<Podcast>, genre: String): List<Podcast> = filterPodcastsByGenre(podcasts, genre)

@Composable
private fun WhatGoesInStep(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
) {
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "What goes in it?",
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
        tabs = modes.map { mode -> DiscoveryTab(label = newFolderModeLabel(mode), icon = newFolderModeIcon(mode)) },
        selectedIndex = modes.indexOf(selected),
        onTabSelected = { index -> onSelect(modes[index]) },
        floating = false,
    )
}

internal fun newFolderModeLabel(mode: NewFolderMode): String = when (mode) {
    NewFolderMode.PickShows -> "Pick shows"
    NewFolderMode.OneGenre -> "One genre"
    NewFolderMode.AllGenres -> "Auto"
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
        Text(
            text = "Your shows are already sorted into a folder for each genre. Manage changes how they look, or turns it off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        text = "You can change this or turn it off any time in Sort and organize.",
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
        Text(text = "Name it", style = MaterialTheme.typography.headlineSmall)
        FolderNameField(
            name = draft.name,
            onNameChange = { onDraftChange(NewFolderFlowLogic.withName(draft, it, findExactGenreIconKey(it))) },
            iconKey = draft.icon,
            helper = NewFolderFlowLogic.savedNameNote(draft.name),
        )
        if (NewFolderFlowLogic.nameIsGenre(draft.name)) {
            // A folder named after a genre links to it (a folder rule), so it says it is an auto folder.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoFolderMark(size = 18.dp)
                Text(
                    text = "${draft.name.trim()} is a genre, so this is an auto folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = "Icon", style = MaterialTheme.typography.titleSmall)
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
        Text(text = "Choose a look", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "How ${draft.name.trim().ifEmpty { "this folder" }} shows up on your Shows tab.",
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
            text = "More shows than that? The last spot becomes +N and opens the folder. You can change the look later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
