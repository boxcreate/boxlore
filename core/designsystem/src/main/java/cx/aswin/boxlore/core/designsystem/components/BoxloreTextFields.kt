package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding

/**
 * boxlore's outlined text field: Material's `OutlinedTextField` with the rounded corners of the
 * sheets and dialogs it sits in. The optional hint ([placeholder]) stays on one line and shortens
 * with an ellipsis instead of wrapping. Callers keep the value in saved state, as before.
 */
@Composable
@Suppress("LongParameterList") // Material's field parameters that boxlore's callers use.
fun BoxloreTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.large,
    )
}

/**
 * boxlore's one search box for screens to adopt: a 56dp pill on `surfaceContainerHigh` with a
 * Search icon, or Back while [active] when [onBack] is given, and a Clear button once something is
 * typed. Clear empties the query and leaves the keyboard up. The keyboard's search key calls
 * [onSearch]. The hint stays on one line and shortens with an ellipsis. It keeps clear of side
 * insets and is at most [maxWidth] wide (null fills the row). Callers keep the query in state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // The query, its callbacks and the search box's look.
fun BoxloreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onBack: (() -> Unit)? = null,
    maxWidth: Dp? = BoxloreSearchFieldMaxWidth,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .boxloreHorizontalSafePadding()
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxWidth()
            .heightIn(min = SearchFieldMinHeight),
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            expanded = active,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                if (active && onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.briefing_back))
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_field_clear))
                    }
                }
            } else {
                null
            },
        )
    }
}

/** Search boxes on tablets stop here, centred by their callers, so a query never runs edge to edge. */
val BoxloreSearchFieldMaxWidth = 720.dp
private val SearchFieldMinHeight = 56.dp
