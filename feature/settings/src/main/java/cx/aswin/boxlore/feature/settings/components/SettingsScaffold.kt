package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SETTINGS_CONTENT_BOTTOM_PADDING = 220.dp

@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    onUnconsumedTap: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsScaffoldFrame(title, onBack, modifier, actions, onUnconsumedTap) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(scrollState)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = SETTINGS_CONTENT_BOTTOM_PADDING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsLazyScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    SettingsScaffoldFrame(title, onBack, Modifier, {}, null) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = SETTINGS_CONTENT_BOTTOM_PADDING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScaffoldFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
    onUnconsumedTap: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentOnUnconsumedTap = rememberUpdatedState(onUnconsumedTap)
    val focusManager = LocalFocusManager.current

    val titlePresentation = settingsHeaderTitlePresentation(
        typography = MaterialTheme.typography,
        collapsedFraction = scrollBehavior.state.collapsedFraction,
    )
    val compactTitlePresentation = settingsHeaderTitlePresentation(
        typography = MaterialTheme.typography,
        collapsedFraction = 0f,
        expanded = false,
    )

    Scaffold(
        modifier =
        modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TwoRowsTopAppBar(
                title = { expanded ->
                    val presentation = if (expanded) titlePresentation else compactTitlePresentation
                    Text(
                        text = title,
                        modifier = if (expanded) Modifier.padding(bottom = 16.dp) else Modifier,
                        style = presentation.style,
                        maxLines = presentation.maxLines,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = true)
                        if (waitForUpOrCancellation() != null) {
                            currentOnUnconsumedTap.value?.invoke() ?: focusManager.clearFocus()
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            content()
        }
    }
}
