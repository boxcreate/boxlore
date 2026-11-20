package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.annotation.DrawableRes
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.pages.SupportTierCardData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** The visit's analytics session, read by the Support page in every world. */
internal val LocalSupportPageSession = staticCompositionLocalOf<SupportPageSession?> { null }

/** Payments-off notice in today's power-cell style, so the control arm behaves like every world. */
internal fun supportControlNoticeContent(
    @DrawableRes art: Int,
    accent: Color,
    priceLabel: String,
): SupportNoticeContent = SupportNoticeContent(
    art = art,
    accent = accent,
    surface = Color(0xFF0D0D14),
    content = Color.White,
    muted = Color(0xFFA6A6B4),
    scrim = Color(0xBD000000),
    chip = R.string.settings_support_notice_control_chip,
    chipColor = Color(0xFFFFB347),
    chipFont = null,
    title = R.string.settings_support_notice_control_title,
    titleFont = null,
    body = UiText.res(R.string.settings_support_notice_control_body, priceLabel),
    dismiss = R.string.settings_support_notice_control_dismiss,
    badge = Icons.Rounded.PowerOff,
    badgeContainer = Color(0xFF1E1E26),
    badgeContent = Color.White,
)

/**
 * Reports each tier the visitor lands on, skipping the one the visit opened on. Only settled pages
 * count, so tapping across several tiers reports where it stops, not every tier it passes.
 */
@Composable
internal fun TrackSupportTierChanges(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    session: SupportPageSession?,
) {
    LaunchedEffect(pagerState, tiers, session) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                val tier = tiers[page]
                session?.tierSelected(
                    offer = SUPPORT_OFFERS[page],
                    tierName = tier.title,
                    codename = "${tier.codenameLine1} ${tier.codenameLine2}",
                )
            }
    }
}
