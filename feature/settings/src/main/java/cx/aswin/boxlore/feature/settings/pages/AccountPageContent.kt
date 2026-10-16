package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.feature.settings.R

/**
 * The Account & sync page frame: a top bar (the page title in Settings; on the onboarding route no title
 * and, while signed out, **Not now**), one scrolling column at most 600dp wide, and an optional bar
 * pinned at the bottom for the onboarding route's Continue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPageScaffold(
    onBack: () -> Unit,
    isOnboarding: Boolean,
    scroll: ScrollState,
    showNotNow: Boolean = false,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag(if (isOnboarding) "account_onboarding_page" else "account_settings_page"),
        topBar = {
            TopAppBar(
                title = { if (!isOnboarding) Text(stringResource(R.string.account_page_title), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.account_back)) } },
                actions = {
                    if (showNotNow) {
                        TextButton(onBack, Modifier.heightIn(min = 48.dp).testTag("account_not_now")) { Text(stringResource(R.string.account_not_now)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (bottomBar != null) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 600.dp).fillMaxWidth()) { bottomBar() }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).navigationBarsPadding()
                    .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = if (isOnboarding) 24.dp else 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** A large headline (and an optional line under it) beside Echo on its stage. */
@Composable
internal fun AccountHeader(title: String, subtitle: String?, syncing: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        AccountEcho(syncing, Modifier.size(AccountEchoSize))
    }
}

/** How each of the three things that sync is doing, when the page shows it. */
internal enum class AccountSyncTileState { Syncing, Done }

/** The three things sync carries: shows, queue and listening progress, each optionally with its state. */
@Composable
internal fun AccountSyncTiles(state: AccountSyncTileState?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.account_syncs_description)
    Row(modifier.fillMaxWidth().semantics { contentDescription = description }.testTag("account_sync_tiles"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Icons.Rounded.Podcasts to R.string.account_syncs_shows,
            Icons.AutoMirrored.Rounded.QueueMusic to R.string.account_syncs_queue,
            Icons.Rounded.History to R.string.account_syncs_progress,
        ).forEachIndexed { order, (icon, label) -> AccountSyncTile(icon, stringResource(label), state, order, Modifier.weight(1f)) }
    }
}

/**
 * One thing that syncs. While syncing, its icon turns inside a wavy ring and the tile glows softly, each
 * tile a beat after the one before, so the row reads as work going on. When sync finishes the tiles turn
 * done one after another: the tile fills and a check grows over the icon. The Syncing / Done label
 * changes at once, so screen readers never wait on the animation.
 */
@Composable
private fun AccountSyncTile(icon: ImageVector, label: String, state: AccountSyncTileState?, order: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val done = state == AccountSyncTileState.Done
    val doneSpec = tween<Float>(AccountTileDoneMillis, delayMillis = order * AccountTileStaggerMillis)
    val doneness = animateFloatAsState(if (done) 1f else 0f, doneSpec, label = "account_tile_done")
    val content by animateColorAsState(
        if (done) colors.onTertiaryContainer else colors.onSurface,
        tween(AccountTileDoneMillis, delayMillis = order * AccountTileStaggerMillis),
        label = "account_tile_content",
    )
    val pulse = if (state == AccountSyncTileState.Syncing) rememberAccountTilePulse(order) else null
    val doneColor = colors.tertiaryContainer
    val glowColor = colors.primary
    Surface(modifier.heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Column(
            Modifier
                .drawBehind {
                    drawRect(doneColor.copy(alpha = doneness.value))
                    pulse?.let { drawRect(glowColor.copy(alpha = AccountTilePulseAlpha * it.value)) }
                }.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AccountSyncTileMarker(icon, state, doneness)
            Text(label, style = MaterialTheme.typography.titleSmall, color = content, textAlign = TextAlign.Center)
            if (state != null) {
                Text(
                    stringResource(if (done) R.string.account_tile_done else R.string.account_tile_syncing),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) content else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The tile's icon: inside a turning wavy ring while syncing, under a check that grows in once done. */
@Composable
private fun AccountSyncTileMarker(icon: ImageVector, state: AccountSyncTileState?, doneness: State<Float>) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(AccountTileMarkerSize), contentAlignment = Alignment.Center) {
        if (state == AccountSyncTileState.Syncing) {
            BoxLoreLoader.CircularWavy(size = AccountTileMarkerSize, color = colors.primary, trackColor = colors.primary.copy(alpha = 0.16f))
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(if (state == AccountSyncTileState.Syncing) 18.dp else 22.dp).graphicsLayer { alpha = 1f - doneness.value },
            tint = if (state == null) colors.tertiary else colors.primary,
        )
        if (state == AccountSyncTileState.Done) {
            Box(
                Modifier
                    .size(AccountTileMarkerSize)
                    .graphicsLayer {
                        scaleX = doneness.value
                        scaleY = doneness.value
                    }.background(colors.tertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A slow glow that rises and falls, each tile starting a third of a beat after the last. */
@Composable
private fun rememberAccountTilePulse(order: Int): State<Float> = rememberInfiniteTransition(label = "account_tile_pulse").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(AccountTilePulseMillis, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(order * AccountTilePulseMillis / 3),
    ),
    label = "account_tile_pulse",
)

/**
 * Signed in, from onboarding: "You're signed in" beside Echo; while the library syncs, a note right
 * under it that there's no need to wait (Continue sits in the bottom bar), then the library coming to
 * this phone, thing by thing (the tiles follow the overall sync state; sync reports no finer detail). A
 * failed sync explains itself and offers Try again.
 */
@Composable
internal fun AccountSignedInOnboarding(user: BoxLoreUser, syncStatus: CloudSyncUiStatus, onSyncNow: () -> Unit) {
    val syncing = syncStatus is CloudSyncUiStatus.Syncing
    val working = syncing || syncStatus == CloudSyncUiStatus.Idle
    AccountHeader(stringResource(R.string.account_signed_in_title), user.email ?: user.displayName, syncing)
    if (working) AccountContinueHint()
    Text(
        stringResource(if (working) R.string.account_bringing else R.string.account_on_phone),
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AccountSyncTiles(
        state = when (syncStatus) {
            is CloudSyncUiStatus.Success -> AccountSyncTileState.Done
            is CloudSyncUiStatus.Error -> null
            else -> AccountSyncTileState.Syncing
        },
    )
    if (syncStatus is CloudSyncUiStatus.Error) AccountSyncPanel(syncStatus, onSyncNow, showEcho = false)
}

/** While the library syncs: the listener can go on now; the sync carries on without them. */
@Composable
private fun AccountContinueHint() {
    Surface(
        Modifier.fillMaxWidth().testTag("account_continue_hint"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.account_continue_hint_title), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                Text(stringResource(R.string.account_continue_hint_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The onboarding route's way on once signed in. */
@Composable
internal fun AccountContinueButton(onContinue: () -> Unit) {
    Button(onContinue, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("account_continue"), shape = CircleShape) {
        Text(stringResource(R.string.account_continue), style = MaterialTheme.typography.titleMedium)
    }
}

/** Signed in, from Settings: the sync status panel, what syncs, then the account itself. */
@Composable
internal fun AccountSignedInSettings(user: BoxLoreUser, syncStatus: CloudSyncUiStatus, actions: SignedInActions) {
    AccountSyncPanel(syncStatus, actions.onSyncNow, showEcho = true)
    AccountSyncTiles(state = null)
    AccountManagementGroup(user, actions.onResetPassword, actions.onSignOut, actions.onDeleteAccountClick)
    AccountFooterLinks(actions.onOpenAccountDeletionInfo)
}

/** Keep both password choices readable at narrow widths and large text scales. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountAuthModeSelector(selected: AuthMode, onSelect: (AuthMode) -> Unit) {
    val options = listOf(AuthMode.SIGN_IN to R.string.account_sign_in, AuthMode.SIGN_UP to R.string.account_create)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, (mode, label) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { checked -> if (checked) onSelect(mode) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes(pressedShape = RoundedCornerShape(12.dp))
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes(pressedShape = RoundedCornerShape(12.dp))
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A tonal button that keeps its label readable at any text size. */
@Composable
internal fun AccountTonalButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, colors: Pair<Color, Color>? = null) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = colors?.let { (container, content) -> androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content) }
            ?: androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.medium, textAlign = TextAlign.Center)
    }
}

internal val AccountEchoSize = 128.dp
private val AccountTileMarkerSize = 36.dp
private const val AccountTileDoneMillis = 320
private const val AccountTileStaggerMillis = 140
private const val AccountTilePulseMillis = 1100
private const val AccountTilePulseAlpha = 0.14f
