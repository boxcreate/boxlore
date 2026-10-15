package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback

/**
 * Shared layout for the experiment worlds. A [SupportWorldSkin] supplies the look; offers, analytics
 * and the payments-off notice behave identically in every world so results stay comparable.
 */
@Composable
internal fun SupportWorldPage(
    skin: SupportWorldSkin,
    session: SupportPageSession,
    onBack: () -> Unit,
    totalListeningHours: Long?,
    modifier: Modifier = Modifier,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    SupportDarkSystemBars()
    val reducedMotion = rememberSupportReducedMotion()
    var selected by rememberSaveable { mutableIntStateOf(SUPPORT_DEFAULT_OFFER_INDEX) }
    var noticeVisible by rememberSaveable { mutableStateOf(false) }
    val offer = SUPPORT_OFFERS[selected]
    val tone = SUPPORT_TIER_TONES[selected]
    val accent by animateColorAsState(tone.color, tween(420), label = "support_world_accent")

    BackHandler(enabled = noticeVisible) { noticeVisible = false }

    Box(modifier.fillMaxSize().background(skin.palette.background)) {
        SupportWorldBackdrop(skin, accent)
        Scaffold(
            topBar = { SupportWorldTopBar(skin, onBack, topBarActions) },
            containerColor = Color.Transparent,
            modifier = if (noticeVisible) Modifier.clearAndSetSemantics { } else Modifier,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SupportWorldStage(
                        skin = skin,
                        selected = selected,
                        accent = accent,
                        reducedMotion = reducedMotion,
                        signalLost = noticeVisible,
                    )
                    SupportWorldTierInfo(skin, selected, accent)
                    Spacer(Modifier.height(16.dp))
                    SupportWorldPicker(
                        skin = skin,
                        selected = selected,
                        onSelect = { index ->
                            if (index != selected) {
                                selected = index
                                session.tierSelected(SUPPORT_OFFERS[index], skin.tierNames[index], skin.codename(index))
                            }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    SupportWorldCta(
                        skin = skin,
                        selected = selected,
                        accent = accent,
                        onPay = {
                            session.payTapped(offer, skin.tierNames[selected], skin.codename(selected))
                            noticeVisible = true
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    SupportWorldMissionCard(skin, accent, totalListeningHours)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        SupportPaymentNotice(
            visible = noticeVisible,
            content = skin.noticeContent(selected, accent),
            reducedMotion = reducedMotion,
            onDismiss = { noticeVisible = false },
        )
    }
}

/** Analytics codename for a world tier, e.g. "A3 RACK BAR" or "TIER 3 CARGO SHUTTLE". */
internal fun SupportWorldSkin.codename(index: Int): String = "${levelLabel(index)} ${tierNames[index].uppercase()}"

internal fun SupportWorldSkin.noticeContent(selected: Int, accent: Color): SupportNoticeContent = SupportNoticeContent(
    art = tierArt[selected],
    accent = accent,
    surface = palette.dialogSurface,
    content = palette.content,
    muted = palette.muted,
    scrim = palette.scrim,
    chip = notice.chip,
    chipColor = notice.chipColor,
    chipFont = label,
    title = notice.title,
    titleFont = name,
    body = notice.body(SUPPORT_OFFERS[selected].priceLabel),
    dismiss = notice.dismiss,
    badge = notice.badge,
    badgeContainer = notice.badgeContainer,
    badgeContent = notice.badgeContent,
)

@Composable
private fun SupportWorldBackdrop(skin: SupportWorldSkin, accent: Color) {
    val background = skin.palette.background
    Box(Modifier.fillMaxSize()) {
        val fixedHeight = skin.backdropHeight
        Image(
            painter = painterResource(skin.backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = if (fixedHeight != null) Modifier.fillMaxWidth().height(fixedHeight) else Modifier.fillMaxSize(),
        )
        if (fixedHeight != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .background(Brush.verticalGradient(0.78f to Color.Transparent, 1f to background)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.15f),
                            0.38f to Color.Transparent,
                            0.66f to background.copy(alpha = 0.72f),
                            1f to background.copy(alpha = 0.94f),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(560.dp)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height * 0.4f)
                    val radius = size.height * 0.44f
                    scale(scaleX = 1.3f, scaleY = 1f, pivot = center) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent), center, radius),
                            radius = radius,
                            center = center,
                        )
                    }
                },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportWorldTopBar(
    skin: SupportWorldSkin,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Support boxlore",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold, fontSize = 18.sp),
                color = skin.palette.content,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = skin.palette.content)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SupportWorldTierInfo(skin: SupportWorldSkin, selected: Int, accent: Color) {
    val offer = SUPPORT_OFFERS[selected]
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${skin.levelLabel(selected)} · ${SUPPORT_RARITIES[selected]}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = skin.label.family,
                fontWeight = skin.label.weight,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
            ),
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.13f))
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = skin.tierNames[selected],
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = skin.name?.family,
                fontWeight = skin.name?.weight ?: GoogleSansWeight.bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
            color = skin.palette.content,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Keeps the servers running for ${offer.serverTime}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.semiBold, fontSize = 14.5.sp),
                color = accent,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(SUPPORT_OFFERS.size) { segment ->
                val filled = segment <= selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (filled) accent else skin.palette.content.copy(alpha = 0.12f)),
                )
            }
        }
    }
}

@Composable
private fun SupportWorldPicker(skin: SupportWorldSkin, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SUPPORT_OFFERS.forEach { option ->
            val index = option.index
            val isSelected = index == selected
            val tileTone = SUPPORT_TIER_TONES[index]
            val shape = RoundedCornerShape(skin.tileCorner)
            val interaction = remember { MutableInteractionSource() }
            val fill = if (isSelected) tileTone.color.copy(alpha = 0.18f).compositeOver(skin.palette.surface) else skin.palette.tileFill
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 92.dp)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(14.dp, shape, ambientColor = tileTone.color, spotColor = tileTone.color)
                        } else {
                            Modifier
                        },
                    )
                    .clip(shape)
                    .background(fill)
                    .border(1.5.dp, if (isSelected) tileTone.color else skin.palette.tileStroke, shape)
                    .selectable(
                        selected = isSelected,
                        interactionSource = interaction,
                        indication = ripple(color = tileTone.color),
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .tabletInputFeedback(interaction, shape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${skin.tierNames[index]}, ${option.serverTime}, ${option.priceLabel}"
                    }
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Image(
                    painter = painterResource(skin.tierArt[index]),
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { alpha = if (isSelected) 1f else 0.72f },
                )
                Text(
                    text = option.shortTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = skin.label.family,
                        fontWeight = skin.label.weight,
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = if (isSelected) tileTone.color else skin.palette.idleLabel,
                )
                Text(
                    text = option.priceLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold, fontSize = 12.5.sp),
                    color = skin.palette.content,
                )
            }
        }
    }
}

@Composable
private fun SupportWorldCta(skin: SupportWorldSkin, selected: Int, accent: Color, onPay: () -> Unit) {
    val offer = SUPPORT_OFFERS[selected]
    val tone = SUPPORT_TIER_TONES[selected]
    val shape = RoundedCornerShape(skin.ctaCorner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "support_world_cta_scale",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(18.dp, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(tone.color, tone.highlight, tone.color)))
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = tone.onColor),
                    role = Role.Button,
                    onClick = onPay,
                )
                .tabletInputFeedback(interaction, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (tone.onColor == Color.White) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(skin.ctaIcon, contentDescription = null, tint = tone.onColor, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${skin.ctaVerb} ${skin.tierNames[selected]} · ${offer.priceLabel}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold, fontSize = 18.5.sp),
                    color = tone.onColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "One-time contribution · No subscription",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.medium),
            color = skin.palette.content.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SupportWorldMissionCard(skin: SupportWorldSkin, accent: Color, totalListeningHours: Long?) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = skin.palette.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Keeping boxlore fast, open, and ad-free",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold, fontSize = 15.sp),
                    color = skin.palette.content,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = supportWorldMissionText(skin.missionNoun, totalListeningHours),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = skin.palette.muted,
            )
        }
    }
}
