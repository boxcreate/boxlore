package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/*
 * Display faces for the few surfaces that deliberately step outside the app's type (the show
 * deck's Prism card). They come through the Google Fonts provider like Roboto Flex, so nothing is
 * bundled; until a face arrives the system sans stands in. Never use these for regular UI text.
 */

private val unbounded = GoogleFont("Unbounded")
private val spaceMono = GoogleFont("Space Mono")

/** Wide, heavy display capitals. */
val UnboundedFamily = FontFamily(
    Font(googleFont = unbounded, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
)

/** Monospaced labels for collector details. */
val SpaceMonoFamily = FontFamily(
    Font(googleFont = spaceMono, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = spaceMono, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)
