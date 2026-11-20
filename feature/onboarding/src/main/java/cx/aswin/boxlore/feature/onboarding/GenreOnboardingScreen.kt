package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedGenres.isNotEmpty(),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            if (selectedGenres.isEmpty()) {
                                stringResource(R.string.onboarding_pick_at_least_one)
                            } else {
                                pluralStringResource(R.plurals.onboarding_continue_selected, selectedGenres.size, selectedGenres.size)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.semiBold,
                        )
                    }
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
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier =
        Modifier
            .height(64.dp)
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = MaterialTheme.shapes.extraLarge, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                genre.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor,
            )
            Text(
                stringResource(genre.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
                maxLines = 1,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onContinue,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_continue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.semiBold,
                        )
                    }
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
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier =
        Modifier
            .height(56.dp)
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = MaterialTheme.shapes.extraLarge, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
                maxLines = 1,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedActivities.isNotEmpty(),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text =
                            if (selectedActivities.isEmpty()) {
                                stringResource(R.string.onboarding_select_at_least_one)
                            } else {
                                pluralStringResource(R.plurals.onboarding_continue_selected, selectedActivities.size, selectedActivities.size)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.semiBold,
                        )
                    }
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

            activities.forEach { item ->
                val isSelected = item.value in selectedActivities
                val cardColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = MaterialTheme.shapes.extraLarge,
                    border =
                    if (isSelected) {
                        BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .animateContentSize(),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .expressiveClickable { onToggleActivity(item.value) }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        androidx.compose.foundation.shape.CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(item.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = GoogleSansWeight.bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (isSelected) {
                            val mappedGenres = activityGenreMap[item.value] ?: emptySet()

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.onboarding_target_genres),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = GoogleSansWeight.semiBold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    allSelectedGenres.forEach { genre ->
                                        val isGenreAssigned = genre in mappedGenres
                                        val chipContainerColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            }
                                        val chipContentColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        val chipBorderColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            }

                                        Surface(
                                            color = chipContainerColor,
                                            contentColor = chipContentColor,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, chipBorderColor),
                                            modifier =
                                            Modifier
                                                .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = RoundedCornerShape(12.dp)) {
                                                    val newGenres =
                                                        if (isGenreAssigned) {
                                                            mappedGenres - genre
                                                        } else {
                                                            mappedGenres + genre
                                                        }
                                                    onSetGenresForActivity(item.value, newGenres)
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (isGenreAssigned) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    text = genreDisplayName(genre),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isGenreAssigned) GoogleSansWeight.bold else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedLengths.isNotEmpty(),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = stringResource(if (selectedLengths.isEmpty()) R.string.onboarding_select_at_least_one else R.string.onboarding_lengths_generate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.semiBold,
                        )
                    }
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

            lengths.forEach { item ->
                val isSelected = item.value in selectedLengths
                val cardColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = MaterialTheme.shapes.extraLarge,
                    border =
                    if (isSelected) {
                        BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .animateContentSize(),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .expressiveClickable { onToggleLength(item.value) }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        androidx.compose.foundation.shape.CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = GoogleSansWeight.bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = item.descriptionRes?.let { stringResource(it) }.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (isSelected) {
                            val mappedGenres = lengthGenreMap[item.value] ?: emptySet()

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.onboarding_target_genres),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = GoogleSansWeight.semiBold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    allSelectedGenres.forEach { genre ->
                                        val isGenreAssigned = genre in mappedGenres
                                        val chipContainerColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            }
                                        val chipContentColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        val chipBorderColor =
                                            if (isGenreAssigned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            }

                                        Surface(
                                            color = chipContainerColor,
                                            contentColor = chipContentColor,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, chipBorderColor),
                                            modifier =
                                            Modifier
                                                .expressiveClickable(feedback = ExpressivePressFeedback.Control, shape = RoundedCornerShape(12.dp)) {
                                                    val newGenres =
                                                        if (isGenreAssigned) {
                                                            mappedGenres - genre
                                                        } else {
                                                            mappedGenres + genre
                                                        }
                                                    onSetGenresForLength(item.value, newGenres)
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (isGenreAssigned) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    text = genreDisplayName(genre),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isGenreAssigned) GoogleSansWeight.bold else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A genre key's full name in the app's language; a key the picker doesn't know shows as it is. */
@Composable
private fun genreDisplayName(value: String): String = onboardingGenreNameRes(value)?.let { stringResource(it) } ?: value
