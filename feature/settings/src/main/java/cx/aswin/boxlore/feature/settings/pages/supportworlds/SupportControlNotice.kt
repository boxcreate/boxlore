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
import cx.aswin.boxlore.feature.settings.pages.SUPPORT_TIER_CARDS
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** The visit's analytics session, read by today's power-cell page (the control arm). */
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
    chip = "DEPLOYMENT PAUSED",
    chipColor = Color(0xFFFFB347),
    chipFont = null,
    title = "The power grid isn't connected yet",
    titleFont = null,
    body = "Your $priceLabel is still yours. Payments aren't set up yet, so nothing was charged.",
    dismiss = "Stand by",
    badge = Icons.Rounded.PowerOff,
    badgeContainer = Color(0xFF1E1E26),
    badgeContent = Color.White,
)

/** Reports each swipe or tap to another power cell, skipping the page the visit opened on. */
@Composable
internal fun TrackSupportTierChanges(pagerState: PagerState, session: SupportPageSession?) {
    LaunchedEffect(pagerState, session) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                val tier = SUPPORT_TIER_CARDS[page]
                session?.tierSelected(
                    offer = SUPPORT_OFFERS[page],
                    tierName = tier.title,
                    codename = "${tier.codenameLine1} ${tier.codenameLine2}",
                )
            }
    }
}
