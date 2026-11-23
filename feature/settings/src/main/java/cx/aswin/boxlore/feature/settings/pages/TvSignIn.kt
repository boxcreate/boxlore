package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.feature.settings.R
import kotlinx.coroutines.launch

/**
 * Signs boxlore on a TV in to this phone's account: the TV shows a code, and the listener enters it
 * here. The app provides it; without one, Settings shows no TV entry.
 */
fun interface TvSignInApprover {
    suspend fun approve(code: String): TvApproval
}

/** How approving a TV's code ended. */
enum class TvApproval { Approved, WrongCode, SignInNeeded, NoConnection }

/** Test tags for the TV sign-in dialog. */
object TvSignInTestTags {
    const val CODE_FIELD = "settings_tv_code"
    const val CONFIRM = "settings_tv_confirm"
}

/** The code a TV shows: eight letters and digits from an alphabet without look-alikes (no 0/O, 1/I/L). */
internal object TvCodeInput {
    const val LENGTH = 8
    private const val HALF = LENGTH / 2
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** What the field keeps of typed or pasted text: the code's own characters, upper case, at most eight. */
    fun clean(text: String): String = text.uppercase().filter { it in ALPHABET }.take(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH

    /** Shown as the TV shows it, in two groups of four. */
    val grouped =
        VisualTransformation { text ->
            val raw = text.text
            val shown = if (raw.length > HALF) raw.substring(0, HALF) + "-" + raw.substring(HALF) else raw
            val mapping =
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = if (offset > HALF) offset + 1 else offset

                    override fun transformedToOriginal(offset: Int): Int = if (offset > HALF) (offset - 1).coerceAtMost(raw.length) else offset
                }
            TransformedText(AnnotatedString(shown), mapping)
        }
}

@Composable
internal fun TvSignInDialog(approver: TvSignInApprover, onDismiss: () -> Unit) {
    TrackScreenView("tv_sign_in")
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<TvApproval?>(null) }
    val done = result == TvApproval.Approved
    val send = {
        if (TvCodeInput.isComplete(code) && !sending) {
            sending = true
            scope.launch {
                result = approver.approve(code)
                sending = false
            }
        }
    }
    BoxloreAlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = { Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.account_tv_sign_in)) },
        text = {
            Column(Modifier.fillMaxWidth().tabletSheetScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (done) {
                    Text(stringResource(R.string.account_tv_signed_in), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    return@Column
                }
                Text(
                    stringResource(R.string.account_tv_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = TvCodeInput.clean(it)
                        result = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag(TvSignInTestTags.CODE_FIELD),
                    enabled = !sending,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.account_tv_code)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center),
                    visualTransformation = TvCodeInput.grouped,
                    isError = result != null,
                    supportingText = result?.let { failure -> { Text(stringResource(failure.message()), color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                )
                Text(stringResource(R.string.account_tv_own_tv_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(visible = sending) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            if (done) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.account_tv_done)) }
            } else {
                Button(onClick = { send() }, enabled = TvCodeInput.isComplete(code) && !sending, modifier = Modifier.testTag(TvSignInTestTags.CONFIRM)) {
                    Text(stringResource(R.string.account_tv_confirm))
                }
            }
        },
        dismissButton = if (done) null else ({ TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.settings_cancel)) } }),
    )
}

private fun TvApproval.message(): Int =
    when (this) {
        TvApproval.WrongCode -> R.string.account_tv_wrong_code
        TvApproval.SignInNeeded -> R.string.account_tv_sign_in_first
        TvApproval.NoConnection, TvApproval.Approved -> R.string.account_tv_no_connection
    }
