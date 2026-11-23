package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerLayoutLogicTest {
    @Test
    fun compactAudioHeroUsesAvailableHeight() {
        val layout = calculateResponsiveHeroLayout(400.dp, 540.dp, isVideo = false)

        assertTrue(layout.isCompact)
        assertEquals(151.2f, layout.dimensions.width.value, 0.001f)
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
        val layout = calculateResponsiveHeroLayout(800.dp, 540.dp, isVideo = true)

        assertEquals(151.2f, layout.dimensions.height.value, 0.001f)
        assertEquals(151.2f * 16f / 9f, layout.dimensions.width.value, 0.001f)
    }

    @Test
    fun `cover grows smoothly across cover-screen heights and phones keep today's size`() {
        val sizes = (540..700 step 5).map { height -> calculateResponsiveHeroLayout(443.dp, height.dp, isVideo = false).dimensions.width.value }
        sizes.zipWithNext().forEach { (shorter, taller) ->
            assertTrue(taller >= shorter, "the cover never shrinks as the window grows")
            assertTrue(taller - shorter < 8f, "no jump between $shorter and $taller")
        }
        // 412x915 and 360x800 phones (no system bars in the fixture): the width cap, as before.
        assertEquals(280.16f, calculateResponsiveHeroLayout(412.dp, 915.dp, isVideo = false).dimensions.width.value, 0.01f)
        assertEquals(244.8f, calculateResponsiveHeroLayout(360.dp, 800.dp, isVideo = false).dimensions.width.value, 0.01f)
        assertEquals(14f, calculateResponsiveHeroLayout(412.dp, 843.dp, isVideo = false).topSpacing.value, 0.01f)
    }

    @Test
    fun `the Cast panel keeps room for its controls on short windows and matches the cover on phones`() {
        listOf(443 to 577, 915 to 370).forEach { (width, height) ->
            val art = calculateResponsiveHeroLayout(width.dp, height.dp, isVideo = false).dimensions
            val panel = castHeroDimensions(art, width.dp, canControlVolume = true)
            assertTrue(panel.height.value >= 200f, "${width}x$height panel ${panel.height}")
            assertTrue(panel.width.value <= width - 48f)
        }
        val phoneArt = calculateResponsiveHeroLayout(412.dp, 843.dp, isVideo = false).dimensions
        assertEquals(phoneArt, castHeroDimensions(phoneArt, 412.dp, canControlVolume = true))
    }

    @Test
    fun `sideways phones use two columns and upright phones and tablets keep one`() {
        assertTrue(usesSidewaysPhoneColumns(915f, 412f, tabletLayout = false))
        assertTrue(usesSidewaysPhoneColumns(695f, 475f, tabletLayout = false))
        assertTrue(usesSidewaysPhoneColumns(800f, 360f, tabletLayout = false))
        assertFalse(usesSidewaysPhoneColumns(412f, 915f, tabletLayout = false))
        assertFalse(usesSidewaysPhoneColumns(443f, 649f, tabletLayout = false))
        assertFalse(usesSidewaysPhoneColumns(412f, 450f, tabletLayout = false))
        assertFalse(usesSidewaysPhoneColumns(932f, 650f, tabletLayout = true))
        assertTrue(sidewaysHeroSize(433.dp, 388.dp).value in 96f..280f)
    }

    @Test
    fun videoHeroUsesTargetWidthWhenHeightFits() {
        val layout = calculateResponsiveHeroLayout(400.dp, 900.dp, isVideo = true)

        assertEquals(332f, layout.dimensions.width.value, 0.001f)
        assertEquals(186.75f, layout.dimensions.height.value, 0.001f)
    }

    @Test
    fun collapsedFloatingSheetUsesFullCapsuleGeometry() {
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
        assertEquals(0.95f, geometry.fullScale, 0.001f)
    }

    @Test
    fun expandingPlayerRetainsEachNavigationStylesCornerProportions() {
        val input = PlayerSheetGeometryInput(
            sheetOffset = 500f,
            collapsedTargetY = 1_000f,
            containerHeight = 800.dp,
            collapsedHorizontalPadding = 12.dp,
            fullEntranceOffsetPx = 24f,
        )
        val floating = calculatePlayerSheetGeometry(input)
        val classic = calculatePlayerSheetGeometry(
            input.copy(
                miniPlayerHeight = 72.dp,
                collapsedTopCornerRadius = 26.dp,
                collapsedBottomCornerRadius = 14.dp,
            ),
        )

        // Halfway open, both styles still keep their full collapsed rounding.
        assertEquals(32.dp, floating.topCornerRadius)
        assertEquals(32.dp, floating.bottomCornerRadius)
        assertEquals(26.dp, classic.topCornerRadius)
        assertEquals(14.dp, classic.bottomCornerRadius)
        assertEquals(floating.expansionFraction, classic.expansionFraction)
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
        assertEquals(1f, geometry.fullScale, 0.001f)
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
