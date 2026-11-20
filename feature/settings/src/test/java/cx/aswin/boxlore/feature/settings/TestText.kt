package cx.aswin.boxlore.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve

/** English text for assertions in Robolectric tests, read from the module's resources. */
internal fun UiText.text(): String = resolve(ApplicationProvider.getApplicationContext<Context>())

internal fun text(@StringRes id: Int, vararg args: Any): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)
