package cx.aswin.boxlore.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.ui.theme.TvColors

/**
 * A text box for the remote. Moving across it never opens the keyboard; OK does, and the TV's own
 * keyboard (with its voice key) takes over. Done returns the remote to the box. The TV's first Back
 * only closes its keyboard and leaves the box still being typed in, so the box keeps its outline,
 * OK opens the keyboard again, and Back, Up or Down hand the remote back to the box.
 */
@Composable
@Suppress("LongParameterList")
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
) {
    var editing by remember { mutableStateOf(false) }
    val box = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val stopEditing = {
        editing = false
        keyboard?.hide()
        runCatching { box.requestFocus() }
        Unit
    }
    Surface(
        onClick = { editing = true },
        modifier = modifier.height(height).focusRequester(box).then(if (editing) Modifier.border(3.dp, TvColors.focusOutline, CircleShape) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = TvColors.surfaceHighest,
            contentColor = TvColors.onSurface,
            focusedContainerColor = TvColors.surfaceHighest,
            focusedContentColor = TvColors.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.focusOutline), shape = CircleShape)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = TvColors.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = TvColors.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = textStyle.copy(color = TvColors.onSurface),
                    cursorBrush = SolidColor(TvColors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { stopEditing() }, onDone = { stopEditing() }),
                    modifier =
                    Modifier.fillMaxWidth().focusRequester(field).focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (TextFieldKeys.action(event.key)) {
                                TextFieldKeys.Action.Leave -> stopEditing()
                                TextFieldKeys.Action.Reopen -> keyboard?.show()
                                TextFieldKeys.Action.Type -> return@onPreviewKeyEvent false
                            }
                            true
                        },
                )
            }
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
}

/** What the remote's keys do in a box being typed in once the TV's keyboard is closed. */
internal object TextFieldKeys {
    enum class Action { Leave, Reopen, Type }

    fun action(key: Key): Action =
        when (key) {
            Key.Back, Key.DirectionUp, Key.DirectionDown -> Action.Leave
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> Action.Reopen
            else -> Action.Type
        }
}
