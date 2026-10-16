package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback
import cx.aswin.boxlore.feature.settings.R

/**
 * Echo on the Account & sync page, standing on its stage: holding the sync cloud, or hugging it while a
 * sync runs. Tapping Echo tries on its genre outfits in turn (not while syncing); the outfit survives
 * rotation and resizing. Pressing paints no highlight around Echo's transparent artwork.
 */
@Composable
internal fun AccountEcho(syncing: Boolean, modifier: Modifier = Modifier, stageColor: Color = MaterialTheme.colorScheme.primaryContainer) {
    var outfit by rememberSaveable { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val description = stringResource(R.string.account_echo_description)
    val changeLabel = stringResource(R.string.account_echo_change)
    val behaviour = if (syncing) {
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier.tabletInputFeedback(interactionSource).semantics { contentDescription = description }
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClickLabel = changeLabel) { outfit = nextAccountEchoOutfit(outfit) }
    }
    EchoCharacter(
        sequence = accountEchoSequence(syncing, outfit),
        modifier = modifier.testTag("account_echo_avatar").then(behaviour),
        idle = AccountEchoIdle,
        stageColor = stageColor,
    )
}

/** Echo's pose: hugging the cloud while syncing, otherwise the outfit picked by tapping (the first holds the cloud). */
internal fun accountEchoSequence(syncing: Boolean, outfit: Int): String =
    if (syncing) AccountEchoSyncing else AccountEchoOutfits[outfit.mod(AccountEchoOutfits.size)]

internal fun nextAccountEchoOutfit(outfit: Int): Int = (outfit + 1).mod(AccountEchoOutfits.size)

internal const val AccountEchoSyncing = "account-syncing"

internal val AccountEchoOutfits = listOf(
    "account-cloud",
    "account-outfit-comedy",
    "account-outfit-truecrime",
    "account-outfit-science",
    "account-outfit-history",
    "account-outfit-business",
    "account-outfit-wellness",
    "account-outfit-fiction",
)

/** Echo watches the listener, glances down at the page and off to the side, and smiles now and then. */
internal val AccountEchoIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(EchoGaze(0f, 0f), 2400, blinks = 1),
        EchoBeat(EchoGaze(-0.4f, 0.8f), 1200, blinks = 1),
        EchoBeat(EchoGaze(0f, 0f), 1600, EchoFace.Happy),
        EchoBeat(EchoGaze(0f, 0f), 2200),
        EchoBeat(EchoGaze(-0.7f, 0.2f), 1100, EchoFace.Curious, blinks = 1),
        EchoBeat(EchoGaze(0f, 0f), 2400, blinks = 2),
    ),
)
