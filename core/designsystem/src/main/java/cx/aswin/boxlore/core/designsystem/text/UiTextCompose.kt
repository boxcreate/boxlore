package cx.aswin.boxlore.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import java.util.Locale

/**
 * The words for this [UiText] in the screen's language. Reading [LocalConfiguration] makes text
 * follow a language or configuration change like `stringResource` does.
 */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

/**
 * The language the screen is shown in, for `LocaleFormats` and `uppercase(locale)`: the app language
 * when one is set, else the system's.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
