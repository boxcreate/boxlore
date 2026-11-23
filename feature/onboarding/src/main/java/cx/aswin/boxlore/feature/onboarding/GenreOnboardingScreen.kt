package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun GenrePickerScreen(
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val scrollBehavior = rememberPickerScrollBehavior()

    Scaffold(
        modifier =
        Modifier
            .fillMaxSize()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_genres_title),
                        modifier = Modifier.padding(start = PickerTitleInset),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = GoogleSansWeight.bold,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.onboarding_back),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = pickerFooterPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OnboardingPrimaryButton(
                        label =
                        if (selectedGenres.isEmpty()) {
                                stringResource(R.string.onboarding_pick_at_least_one)
                            } else {
                                pluralStringResource(R.plurals.onboarding_continue_selected, selectedGenres.size, selectedGenres.size)
                            },
                        enabled = selectedGenres.isNotEmpty(),
                        onClick = onContinue,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_genres_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ONBOARDING_GENRES.forEach { genre ->
                    val isSelected = genre.value in selectedGenres
                    GenreChip(
                        genre = genre,
                        isSelected = isSelected,
                        onClick = { onToggleGenre(genre.value) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GenreChip(
    genre: GenreItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // One strong highlight: a picked genre takes the primary fill and a check in place of its icon.
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier =
        Modifier
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSelected)
            }
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = MaterialTheme.shapes.extraLarge, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (isSelected) Icons.Rounded.Check else genre.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Text(
                stringResource(genre.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SubGenrePickerScreen(
    selectedGenres: Set<String>,
    selectedSubGenres: Set<String>,
    onToggleSubGenre: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val scrollBehavior = rememberPickerScrollBehavior()

    Scaffold(
        modifier =
        Modifier
            .fillMaxSize()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_subgenres_title),
                        modifier = Modifier.padding(start = PickerTitleInset),
                        fontWeight = GoogleSansWeight.bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = pickerFooterPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OnboardingPrimaryButton(
                        label =
                        stringResource(R.string.onboarding_continue),
                        enabled = true,
                        onClick = onContinue,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_subgenres_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            val parentGenres = selectedGenres.toList()
            if (parentGenres.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.onboarding_subgenres_empty))
                }
            } else {
                parentGenres.forEach { parentGenre ->
                    val subGenres = SUB_GENRES_MAP[parentGenre] ?: emptyList()
                    if (subGenres.isNotEmpty()) {
                        Text(
                            text = genreDisplayName(parentGenre),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        ) {
                            subGenres.forEach { subGenre ->
                                val isSelected = subGenre.value in selectedSubGenres
                                SubGenreChip(
                                    label = stringResource(subGenre.labelRes),
                                    isSelected = isSelected,
                                    onClick = { onToggleSubGenre(subGenre.value) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubGenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier =
        Modifier
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSelected)
            }
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = MaterialTheme.shapes.extraLarge, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityPickerScreen(
    selectedActivities: Set<String>,
    activityGenreMap: Map<String, Set<String>>,
    allSelectedGenres: Set<String>,
    onToggleActivity: (String) -> Unit,
    onSetGenresForActivity: (String, Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val scrollBehavior = rememberPickerScrollBehavior()

    val activities = ONBOARDING_ACTIVITIES

    Scaffold(
        modifier =
        Modifier
            .fillMaxSize()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_activities_title),
                        modifier = Modifier.padding(start = PickerTitleInset),
                        fontWeight = GoogleSansWeight.bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = pickerFooterPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OnboardingPrimaryButton(
                        label =
                        if (selectedActivities.isEmpty()) {
                                stringResource(R.string.onboarding_select_at_least_one)
                            } else {
                                pluralStringResource(R.plurals.onboarding_continue_selected, selectedActivities.size, selectedActivities.size)
                            },
                        enabled = selectedActivities.isNotEmpty(),
                        onClick = onContinue,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_select_all_that_apply),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            activities.forEachIndexed { index, item ->
                ListeningChoiceCard(
                    item = item,
                    index = index,
                    selected = item.value in selectedActivities,
                    mappedGenres = activityGenreMap[item.value].orEmpty(),
                    allSelectedGenres = allSelectedGenres,
                    onToggle = { onToggleActivity(item.value) },
                    onSetGenres = { onSetGenresForActivity(item.value, it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LengthPickerScreen(
    selectedLengths: Set<String>,
    lengthGenreMap: Map<String, Set<String>>,
    allSelectedGenres: Set<String>,
    onToggleLength: (String) -> Unit,
    onSetGenresForLength: (String, Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val scrollBehavior = rememberPickerScrollBehavior()

    val lengths = ONBOARDING_LENGTHS

    Scaffold(
        modifier =
        Modifier
            .fillMaxSize()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_lengths_title),
                        modifier = Modifier.padding(start = PickerTitleInset),
                        fontWeight = GoogleSansWeight.bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = pickerFooterPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OnboardingPrimaryButton(
                        label =
                        stringResource(if (selectedLengths.isEmpty()) R.string.onboarding_select_at_least_one else R.string.onboarding_lengths_generate),
                        enabled = selectedLengths.isNotEmpty(),
                        onClick = onContinue,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_select_all_that_apply),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            lengths.forEachIndexed { index, item ->
                ListeningChoiceCard(
                    item = item,
                    index = index,
                    selected = item.value in selectedLengths,
                    mappedGenres = lengthGenreMap[item.value].orEmpty(),
                    allSelectedGenres = allSelectedGenres,
                    onToggle = { onToggleLength(item.value) },
                    onSetGenres = { onSetGenresForLength(item.value, it) },
                )
            }
        }
    }
}

/** A genre key's full name in the app's language; a key the picker doesn't know shows as it is. */
@Composable
private fun genreDisplayName(value: String): String = onboardingGenreNameRes(value)?.let { stringResource(it) } ?: value

/**
 * The pickers' big header. In a short space (a sideways phone, a split-screen half) it starts
 * collapsed so the choices get the room, and it opens out again when the window grows tall; the
 * listener's own scrolling is left alone in between.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberPickerScrollBehavior(): TopAppBarScrollBehavior {
    val short = boxloreIsShortPane()
    // Opened in a short space, the bar starts collapsed on its very first frame.
    val collapsible = with(LocalDensity.current) { (TopAppBarDefaults.LargeAppBarExpandedHeight - TopAppBarDefaults.LargeAppBarCollapsedHeight).toPx() }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(initialHeightOffsetLimit = -collapsible, initialHeightOffset = if (short) -collapsible else 0f),
    )
    var wasShort by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(short) {
        val previous = wasShort
        wasShort = short
        if (previous == null || previous == short) return@LaunchedEffect
        val state = behavior.state
        state.heightOffset = if (short) state.heightOffsetLimit else 0f
    }
    return behavior
}

/** Room above and below the pickers' main button: less in a short space. */
@Composable
private fun pickerFooterPadding(): Dp = if (boxloreIsShortPane()) 12.dp else 24.dp

/** The large title sits 16dp in; this lines it up with the 24dp subtitle and choices under it. */
private val PickerTitleInset = 8.dp

/**
 * One listening moment or length: a solid card that fills like Echo's answers when picked, pressed
 * as a whole and read as a checkbox, with Echo's badge for its icon. A picked card opens the genres
 * it should bring, as the app's filter chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList") // The choice, its state and its two callbacks.
private fun ListeningChoiceCard(
    item: ListeningChoiceItem,
    index: Int,
    selected: Boolean,
    mappedGenres: Set<String>,
    allSelectedGenres: Set<String>,
    onToggle: () -> Unit,
    onSetGenres: (Set<String>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(selected)
                    }
                    .expressiveClickable(shape = shape, onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoSelectionBadge(selected, item.icon, size = 48.dp, shapeIndex = index)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(item.labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.bold)
                    item.descriptionRes?.let {
                        Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
                    }
                }
            }
            if (selected) {
                // The genres sit on the page's own surface, so their chips read as chips on the picked card's fill.
                Surface(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surface,
                    contentColor = colors.onSurface,
                ) {
                    Column(Modifier.padding(12.dp)) { TargetGenreChips(allSelectedGenres, mappedGenres, onSetGenres) }
                }
            }
        }
    }
}

/** The genres a picked moment or length should bring; each chip adds or removes one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetGenreChips(allSelectedGenres: Set<String>, mapped: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_target_genres),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = GoogleSansWeight.semiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        allSelectedGenres.forEach { genre ->
            val assigned = genre in mapped
            PillFilterChip(
                label = genreDisplayName(genre),
                selected = assigned,
                onClick = { onChange(if (assigned) mapped - genre else mapped + genre) },
                icon = if (assigned) Icons.Rounded.Check else null,
            )
        }
    }
}
