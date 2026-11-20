package cx.aswin.boxlore.core.model.text

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text for the listener, decided where no [Context] is at hand (view models, pure logic, workers)
 * and turned into words where it is shown, in the language the screen is in.
 *
 * - [Res]: a string resource with its format arguments.
 * - [Plural]: a plurals resource picked by [Plural.count], with its format arguments. The count is
 *   not passed as an argument on its own: add it to [Plural.args] when the text shows it.
 * - [Raw]: text that is already final, such as server text, show and episode names, or user input.
 *
 * Arguments may themselves be [UiText]; they are resolved first. Equality compares the resource id
 * and arguments, so JVM tests can assert what logic chose without loading any resources.
 *
 * Compose resolves it with `UiText.asString()` in `:core:designsystem`; other code calls
 * [resolve] with the [Resources] or [Context] it already has.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())

        fun raw(value: String): UiText = Raw(value)
    }
}

/** The words for this text in the language of [resources]. */
fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res ->
        if (args.isEmpty()) {
            resources.getString(id)
        } else {
            resources.getString(id, *resolvedArgs(args, resources))
        }
    is UiText.Plural ->
        if (args.isEmpty()) {
            resources.getQuantityString(id, count)
        } else {
            resources.getQuantityString(id, count, *resolvedArgs(args, resources))
        }
}

/** The words for this text in the language of [context]'s resources. */
fun UiText.resolve(context: Context): String = resolve(context.resources)

private fun resolvedArgs(args: List<Any>, resources: Resources): Array<Any> = args
    .map { if (it is UiText) it.resolve(resources) else it }
    .toTypedArray()
