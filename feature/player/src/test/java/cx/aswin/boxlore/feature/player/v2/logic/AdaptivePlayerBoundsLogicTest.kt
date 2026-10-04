package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeOverlayOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptivePlayerBoundsLogicTest {
    private val input = AdaptivePlayerBoundsInput(
        containerWidth = 360.dp,
        containerHeight = 800.dp,
        collapsedHorizontalPadding = 16.dp,
        collapsedTargetY = 640f,
        compactTargetY = 714f,
        miniPlayerHeight = 64.dp,
        compactFraction = 0f,
        expansionFraction = 0f,
    )

    @Test
    fun normalEndpointRetainsMiniPlayerBounds() {
        val bounds = calculateAdaptivePlayerBounds(input)

        assertEquals(328.dp, bounds.width)
        assertEquals(64.dp, bounds.height)
        assertEquals(16.dp, bounds.offsetX)
        assertEquals(640f, bounds.offsetY)
    }

    @Test
    fun compactEndpointOccupiesExternalActionSlot() {
        val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f))

        assertEquals(52.dp, bounds.width)
        assertEquals(52.dp, bounds.height)
        assertEquals(292.dp, bounds.offsetX)
        assertEquals(714f, bounds.offsetY)
        assertEquals(52.dp, bounds.collapsedHeight)
    }

    @Test
    fun rtlCircleOccupiesPhysicalLeftWithSameGutter() {
        val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f, isRtl = true))

        assertEquals(16.dp, bounds.offsetX)
        assertEquals(52.dp, bounds.width)
    }

    @Test
    fun compactMorphContractsAboveNavigationBeforeDescending() {
        val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 0.325f))

        assertEquals(190.dp, bounds.width)
        assertEquals(58.dp, bounds.height)
        assertEquals(154.dp, bounds.offsetX)
        assertEquals(640f, bounds.offsetY)
        assertEquals(0.5f, bounds.compactFraction)
    }

    @Test
    fun descentStartsOnlyAfterCircleFitsExternalActionSlot() {
        val contracted = calculateAdaptivePlayerBounds(input.copy(compactFraction = 0.65f))
        val descending = calculateAdaptivePlayerBounds(input.copy(compactFraction = 0.825f))

        assertEquals(52.dp, contracted.width)
        assertEquals(52.dp, contracted.height)
        assertEquals(292.dp, contracted.offsetX)
        assertEquals(640f, contracted.offsetY)
        assertEquals(1f, contracted.compactFraction)
        assertEquals(0f, contracted.cookieFraction)
        assertEquals(52.dp, descending.width)
        assertEquals(52.dp, descending.height)
        assertEquals(292.dp, descending.offsetX)
        assertEquals(677f, descending.offsetY, 0.001f)
        assertEquals(1f, descending.compactFraction)
        assertEquals(0.5f, descending.cookieFraction, 0.001f)
    }

    @Test
    fun playerNeverCoversNavigationOutsideItsExternalCircleSlot() {
        val navbarTop = input.compactTargetY - 2f
        for (width in listOf(320, 360, 600)) {
            for (rtl in listOf(false, true)) {
                val slotLeft = if (rtl) 16f else width - 16f - 52f
                for (step in 0..100) {
                    val bounds = calculateAdaptivePlayerBounds(
                        input.copy(
                            containerWidth = width.dp,
                            compactFraction = step / 100f,
                            isRtl = rtl,
                        ),
                    )
                    val aboveNavbar = bounds.offsetY + bounds.height.value <= navbarTop + 0.001f
                    val insideExternalSlot = bounds.offsetX.value >= slotLeft - 0.001f &&
                        bounds.offsetX.value + bounds.width.value <= slotLeft + 52f + 0.001f

                    assertTrue(aboveNavbar || insideExternalSlot, "width=$width, RTL=$rtl, progress=${step / 100f}")
                }
            }
        }
    }

    @Test
    fun floatingControlsStayAbovePlayerAndNavigationThroughoutTheMorph() {
        for (density in listOf(1f, 2f, 3.5f)) {
            val scaledInput = input.copy(
                collapsedTargetY = input.collapsedTargetY * density,
                compactTargetY = input.compactTargetY * density,
            )
            val navigationTop = scaledInput.compactTargetY - 2f * density
            val expandedControlBottom = scaledInput.collapsedTargetY - 16f * density
            for (step in 0..100) {
                val progress = step / 100f
                val bounds = calculateAdaptivePlayerBounds(scaledInput.copy(compactFraction = progress))
                val displacement = appBottomChromeOverlayOffset(NavigationStyle.Floating, true, progress).value * density
                val controlBottom = expandedControlBottom + displacement

                assertTrue(
                    controlBottom <= minOf(bounds.offsetY, navigationTop) - 16f * density + 0.001f,
                    "Control overlaps chrome at density=$density progress=$progress",
                )
            }
        }
    }

    @Test
    fun circleExpandsContinuouslyFromItsOwnVisualBounds() {
        val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f, expansionFraction = 0.5f))

        assertEquals(206.dp, bounds.width)
        assertEquals(426.dp, bounds.height)
        assertEquals(146.dp, bounds.offsetX)
        assertEquals(357f, bounds.offsetY)
    }

    @Test
    fun fullPlayerContentOpensWithRegularSheetClipping() {
        val starting = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f, expansionFraction = 0.03f))
        assertEquals(0.5f, starting.cookieFraction, 0.001f)
        for (expanded in listOf(0.06f, 0.25f, 0.5f, 1f)) {
            val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f, expansionFraction = expanded))
            assertEquals(0f, bounds.cookieFraction)
        }
    }

    @Test
    fun scallopedClippingIsGoneWheneverFullPlayerContentIsVisible() {
        for (step in 0..100) {
            val expansion = step / 100f
            if (calculatePlayerSheetReveal(expansion).fullAlpha > 0f) {
                val bounds = calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f, expansionFraction = expansion))
                assertEquals(0f, bounds.cookieFraction)
            }
        }
    }

    @Test
    fun fullPlayerIgnoresCompactnessAndLayoutDirection() {
        for (compact in listOf(0f, 0.5f, 1f)) {
            for (rtl in listOf(false, true)) {
                val bounds = calculateAdaptivePlayerBounds(
                    input.copy(
                        compactFraction = compact,
                        expansionFraction = 1f,
                        isRtl = rtl,
                    ),
                )

                assertEquals(360.dp, bounds.width)
                assertEquals(800.dp, bounds.height)
                assertEquals(0.dp, bounds.offsetX)
                assertEquals(0f, bounds.offsetY)
                assertEquals(0f, bounds.cookieFraction)
            }
        }
    }

    @Test
    fun fullscreenVideoAlwaysRetainsFullBounds() {
        val bounds = calculateAdaptivePlayerBounds(
            input.copy(
                compactFraction = 1f,
                expansionFraction = 0.4f,
                isFullscreenVideo = true,
            ),
        )

        assertEquals(360.dp, bounds.width)
        assertEquals(800.dp, bounds.height)
        assertEquals(0.dp, bounds.offsetX)
        assertEquals(0f, bounds.offsetY)
    }

    @Test
    fun classicPresentationDoesNotCompact() {
        val classic = input.copy(miniPlayerHeight = 72.dp, adaptiveEnabled = false)

        assertEquals(calculateAdaptivePlayerBounds(classic), calculateAdaptivePlayerBounds(classic.copy(compactFraction = 1f)))
    }

    @Test
    fun sameFixedAnchorCanOpenFromEitherCollapsedPresentation() {
        val logical = calculatePlayerSheetGeometry(
            PlayerSheetGeometryInput(
                sheetOffset = 480f,
                collapsedTargetY = input.collapsedTargetY,
                containerHeight = input.containerHeight,
                collapsedHorizontalPadding = input.collapsedHorizontalPadding,
                fullEntranceOffsetPx = 24f,
            ),
        )
        val normal = calculateAdaptivePlayerBounds(input.copy(expansionFraction = logical.expansionFraction))
        val compact = calculateAdaptivePlayerBounds(
            input.copy(
                compactFraction = 1f,
                expansionFraction = logical.expansionFraction,
            ),
        )

        assertEquals(0.25f, logical.expansionFraction)
        assertEquals(480f, normal.offsetY)
        assertEquals(535.5f, compact.offsetY)
        assertEquals(640f, input.collapsedTargetY)
    }

    @Test
    fun constrainedWidthsKeepVisualBoundsInsideContainer() {
        for (width in listOf(0, 20, 80, 320)) {
            for (compact in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val bounds = calculateAdaptivePlayerBounds(input.copy(containerWidth = width.dp, compactFraction = compact))

                assertTrue(bounds.width.value >= 0f)
                assertTrue(bounds.offsetX.value >= 0f)
                assertTrue(bounds.offsetX.value + bounds.width.value <= width)
                assertTrue(bounds.height.value >= 0f)
            }
        }
    }

    @Test
    fun invalidProgressAndOffsetsCannotCreateNonFiniteBounds() {
        val bounds = calculateAdaptivePlayerBounds(
            input.copy(
                compactFraction = Float.NaN,
                expansionFraction = Float.POSITIVE_INFINITY,
                collapsedTargetY = Float.NaN,
                compactTargetY = Float.NEGATIVE_INFINITY,
            ),
        )

        assertEquals(0f, bounds.compactFraction)
        assertEquals(328.dp, bounds.width)
        assertEquals(0f, bounds.offsetY)
    }

    @Test
    fun progressOutsideUnitIntervalIsClamped() {
        assertEquals(
            calculateAdaptivePlayerBounds(input.copy(compactFraction = 1f)),
            calculateAdaptivePlayerBounds(input.copy(compactFraction = 2f, expansionFraction = -1f)),
        )
    }

    @Test
    fun regularPlayerRetainsFloatingAndClassicCornerRadii() {
        for (radius in listOf(32.dp, 26.dp, 14.dp)) {
            assertEquals(radius, calculateAdaptivePlayerCornerRadius(radius, 0f, 0f, false))
            assertEquals(radius / 2, calculateAdaptivePlayerCornerRadius(radius / 2, 0.5f, 0f, false))
        }
    }

    @Test
    fun compactCornerRadiusShrinksAsTheFullPlayerExpands() {
        assertEquals(26.dp, calculateAdaptivePlayerCornerRadius(32.dp, 0f, 1f, false))
        assertEquals(13.dp, calculateAdaptivePlayerCornerRadius(16.dp, 0.5f, 1f, false))
        assertEquals(0.dp, calculateAdaptivePlayerCornerRadius(0.dp, 1f, 1f, false))
    }

    @Test
    fun intermediateMorphKeepsClassicTopAndBottomCornersDistinct() {
        val top = calculateAdaptivePlayerCornerRadius(13.dp, 0.5f, 0.5f, false)
        val bottom = calculateAdaptivePlayerCornerRadius(7.dp, 0.5f, 0.5f, false)

        assertEquals(13.dp, top)
        assertEquals(10.dp, bottom)
    }

    @Test
    fun fullscreenVideoHasSquareCornersRegardlessOfThePreviousMorphState() {
        for (compact in listOf(0f, 0.5f, 1f)) {
            assertEquals(0.dp, calculateAdaptivePlayerCornerRadius(32.dp, 0.25f, compact, true))
        }
    }

    @Test
    fun browseScrollIsBlockedForEverySheetInteractionMode() {
        assertFalse(isPlayerSheetInteractionActive(0f, false, false, false))
        assertTrue(isPlayerSheetInteractionActive(0.1f, false, false, false))
        assertTrue(isPlayerSheetInteractionActive(0f, true, false, false))
        assertTrue(isPlayerSheetInteractionActive(0f, false, true, false))
        assertTrue(isPlayerSheetInteractionActive(0f, false, false, true))
    }
}
