package cx.aswin.boxlore.wear.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.data.StoragePolicy
import cx.aswin.boxlore.wear.model.ShowsLayout
import cx.aswin.boxlore.wear.ui.WatchFormat

@Composable
internal fun SettingsScreen(
    showsLayout: ShowsLayout,
    onShowsLayout: (ShowsLayout) -> Unit,
    storageLimitMb: Int,
    onStorageLimitMb: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_settings))
                }
            }
            item {
                ListSubHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_shows_layout))
                }
            }
            ShowsLayout.entries.forEach { layout ->
                item(key = layout.name) {
                    RadioButton(
                        selected = layout == showsLayout,
                        onSelect = { onShowsLayout(layout) },
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = {
                            Text(
                                stringResource(
                                    when (layout) {
                                        ShowsLayout.Honeycomb -> R.string.wear_shows_layout_honeycomb
                                        ShowsLayout.List -> R.string.wear_shows_layout_list
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            item {
                ListSubHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_storage_limit))
                }
            }
            StoragePolicy.LIMIT_CHOICES_MB.forEach { choice ->
                item(key = "limit-$choice") {
                    RadioButton(
                        selected = choice == storageLimitMb,
                        onSelect = { onStorageLimitMb(choice) },
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(WatchFormat.megabytes(StoragePolicy.limitBytes(choice), locale)) },
                    )
                }
            }
        }
    }
}
