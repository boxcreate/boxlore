package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerLayoutLogicTest {
    @Test
    fun compactAudioHeroUsesAvailableHeight() {
        val layout = calculateResponsiveHeroLayout(400.dp, 600.dp, isVideo = false)

        assertTrue(layout.isCompact)
        assertEquals(168f, layout.dimensions.width.value, 0.001f)
        assertEquals(layout.dimensions.width, layout.dimensions.height)
    }

    @Test
    fun tallAudioHeroUsesWidthLimit() {
        val layout = calculateResponsiveHeroLayout(400.dp, 900.dp, isVideo = false)

        assertFalse(layout.isCompact)
        assertEquals(272f, layout.dimensions.width.value, 0.001f)
        assertEquals(272f, layout.dimensions.height.value, 0.001f)
    }

    @Test
    fun verySmallAudioHeroRespectsMinimum() {
        val layout = calculateResponsiveHeroLayout(100.dp, 200.dp, isVideo = false)

        assertEquals(138f, layout.dimensions.width.value, 0.001f)
        assertEquals(138f, layout.dimensions.height.value, 0.001f)
    }

    @Test
    fun videoHeroClampsToAvailableHeight() {
        val layout = calculateResponsiveHeroLayout(800.dp, 600.dp, isVideo = true)

        assertEquals(168f, layout.dimensions.height.value, 0.001f)
        assertEquals(168f * 16f / 9f, layout.dimensions.width.value, 0.001f)
    }

    @Test
    fun videoHeroUsesTargetWidthWhenHeightFits() {
        val layout = calculateResponsiveHeroLayout(400.dp, 900.dp, isVideo = true)

        assertEquals(332f, layout.dimensions.width.value, 0.001f)
        assertEquals(186.75f, layout.dimensions.height.value, 0.001f)
    }

    @Test
    fun collapsedSheetUsesPillGeometry() {
        val geometry =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = 1_000f,
                    collapsedTargetY = 1_000f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )

        assertEquals(0f, geometry.expansionFraction, 0.001f)
        assertEquals(64f, geometry.sheetHeight.value, 0.001f)
        assertEquals(32f, geometry.topCornerRadius.value, 0.001f)
        assertEquals(32f, geometry.bottomCornerRadius.value, 0.001f)
        assertEquals(12f, geometry.horizontalPadding.value, 0.001f)
        assertEquals(3f, geometry.sheetElevation.value, 0.001f)
        assertEquals(1f, geometry.miniAlpha, 0.001f)
        assertEquals(0f, geometry.fullAlpha, 0.001f)
        assertEquals(24f, geometry.fullTranslationY, 0.001f)
    }

    @Test
    fun collapsedSheetUsesClassicGeometryWhenRequested() {
        val geometry =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = 1_000f,
                    collapsedTargetY = 1_000f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    miniPlayerHeight = 72.dp,
                    collapsedTopCornerRadius = 26.dp,
                    collapsedBottomCornerRadius = 14.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )

        assertEquals(72f, geometry.miniPlayerHeight.value, 0.001f)
        assertEquals(72f, geometry.sheetHeight.value, 0.001f)
        assertEquals(26f, geometry.topCornerRadius.value, 0.001f)
        assertEquals(14f, geometry.bottomCornerRadius.value, 0.001f)
    }

    @Test
    fun expandedSheetUsesFullPlayerGeometry() {
        val geometry =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = 0f,
                    collapsedTargetY = 1_000f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )

        assertEquals(1f, geometry.expansionFraction, 0.001f)
        assertEquals(800f, geometry.sheetHeight.value, 0.001f)
        assertEquals(0f, geometry.topCornerRadius.value, 0.001f)
        assertEquals(0f, geometry.bottomCornerRadius.value, 0.001f)
        assertEquals(0f, geometry.horizontalPadding.value, 0.001f)
        assertEquals(16f, geometry.sheetElevation.value, 0.001f)
        assertEquals(0f, geometry.miniAlpha, 0.001f)
        assertEquals(1f, geometry.fullAlpha, 0.001f)
        assertEquals(0f, geometry.fullTranslationY, 0.001f)
    }

    @Test
    fun sheetFractionIsClampedAtBothEnds() {
        val beyondExpanded =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = -200f,
                    collapsedTargetY = 1_000f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )
        val beyondCollapsed =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = 1_200f,
                    collapsedTargetY = 1_000f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )

        assertEquals(1f, beyondExpanded.expansionFraction, 0.001f)
        assertEquals(0f, beyondCollapsed.expansionFraction, 0.001f)
    }

    @Test
    fun invalidCollapsedTargetFallsBackToCollapsedFraction() {
        val geometry =
            calculatePlayerSheetGeometry(
                PlayerSheetGeometryInput(
                    sheetOffset = 0f,
                    collapsedTargetY = 0f,
                    containerHeight = 800.dp,
                    collapsedHorizontalPadding = 12.dp,
                    fullEntranceOffsetPx = 24f,
                ),
            )

        assertEquals(0f, geometry.expansionFraction, 0.001f)
        assertEquals(64f, geometry.sheetHeight.value, 0.001f)
    }
}
