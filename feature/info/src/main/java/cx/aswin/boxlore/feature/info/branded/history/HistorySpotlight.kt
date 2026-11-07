package cx.aswin.boxlore.feature.info.branded.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedEpisodeActions
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.brandedDetails
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import java.util.Locale

private val CardShape = RoundedCornerShape(4.dp)
private val CardFrame = FrameStyle(oval = false, moulding = 6.dp, mount = 6.dp, shadows = CardShadows)

/**
 * The latest episode on a piece of the same velvet wall: its own picture in the wall's bronze frame
 * under a picture light, with its number on a brass nameplate, and ivory text beside it: "Latest
 * episode", the episode's name, its series and part, and when it came out. The episode cards'
 * buttons follow, ivory Play by night and claret with an ivory ring by day.
 */
@Composable
internal fun HistorySpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val fonts = rememberHistoryFonts()
    val palette = historyPalette()
    val dark = LocalEffectiveDarkTheme.current
    val episode = spotlight.episode
    val title = remember(episode.title, episode.episodeNumber) { historyTitle(episode.title, episode.episodeNumber) }
    val velvet = painterResource(R.drawable.trih_velvet)
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(CardShape)
            .drawBehind {
                // The velvet, a little dimmer than the wall, covering the card.
                val s = maxOf(size.width / velvet.intrinsicSize.width, size.height / velvet.intrinsicSize.height)
                val w = velvet.intrinsicSize.width * s
                val h = velvet.intrinsicSize.height * s
                drawContext.canvas.save()
                drawContext.transform.translate((size.width - w) / 2f, (size.height - h) / 2f)
                with(velvet) { draw(Size(w, h), colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.78f, 0.78f, 0.78f, 1f) })) }
                drawContext.canvas.restore()
                // The picture light over the frame.
                val unit = 1.dp.toPx()
                val light = Offset(80f * unit, 36f * unit)
                scale(scaleX = 1f, scaleY = 95f / 100f, pivot = light) {
                    drawCircle(Brush.radialGradient(0f to Color(0x33FFE2D6), 0.7f to Color(0x00FFE2D6), center = light, radius = 100f * unit), 300f * unit, light)
                }
            }
            .drawWithContent {
                drawContent()
                // The card sits into the wall: shade at its top and bottom edges, and a faint rim.
                val unit = 1.dp.toPx()
                drawRect(Brush.verticalGradient(0f to Color(0x73140409), 1f to Color(0x00140409), endY = 30f * unit), size = Size(size.width, 30f * unit))
                drawRect(
                    Brush.verticalGradient(0f to Color(0x00140409), 1f to Color(0x59140409), startY = size.height - 26f * unit, endY = size.height),
                    topLeft = Offset(0f, size.height - 26f * unit),
                )
            }
            .border(1.dp, Color(0x14FFDCE6), CardShape)
            .then(if (spotlight.isSelected) Modifier.border(BorderStroke(3.dp, HistoryColors.Ivory), CardShape) else Modifier)
            .expressiveClickable(shape = CardShape, onLongClickLabel = selectLabel, onLongClick = spotlight.onLongPress, onClick = spotlight.onOpen)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FramedPicture(spotlight, title.number, fonts)
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 2.dp)) {
                Text(
                    stringResource(R.string.branded_latest_episode).uppercase(Locale.getDefault()),
                    style = brandedTextStyle(fonts.cinzel, 10.5.sp, 13.sp, letterSpacing = 1.8.sp).copy(fontWeight = FontWeight.Bold),
                    color = HistoryColors.CardKicker,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title.headline,
                    style = brandedTextStyle(fonts.cormorant, 22.sp, 22.sp).copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color(0x73140409), Offset(0f, 1f), 3f),
                    ),
                    color = HistoryColors.CardTitle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // Shown split into name and series; screen readers hear the episode's own title.
                    modifier = Modifier.padding(top = 5.dp).clearAndSetSemantics { text = AnnotatedString(episode.title) },
                )
                title.series?.let {
                    Text(
                        it,
                        style = brandedTextStyle(fonts.cormorant, 14.sp, 15.sp).copy(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic),
                        color = HistoryColors.CardText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    brandedDetails(formatEpisodeRelativeDate(episode.publishedDate), episode.duration, spotlight.isCompleted, stringResource(R.string.episode_info_played)),
                    style = brandedSansStyle(12.sp, 14.sp),
                    color = HistoryColors.CardText,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BrandedEpisodeActions(
            spotlight,
            BrandedActionColors(
                toggle = HistoryColors.CardToggle,
                onToggle = HistoryColors.OnCardToggle,
                checked = palette.primary,
                onChecked = palette.onPrimary,
                play = palette.primary,
                onPlay = palette.onPrimary,
                // By day the claret Play would sink into the velvet, so it gets an ivory ring.
                playRing = if (dark) null else BorderStroke(1.5.dp, Color(0xD9FFF6F2)),
            ),
            showProgress = true,
        )
    }
}

/** The episode's picture in the bronze frame, its number on a brass plate hanging over the frame's foot. */
@Composable
private fun FramedPicture(spotlight: BrandedSpotlight, number: Int?, fonts: HistoryFonts) {
    Box(Modifier.width(104.dp).padding(bottom = 8.dp)) {
        BronzeFrame(spotlight.episodePicture ?: spotlight.showPicture, spotlight.showPicture, CardFrame, Modifier.size(104.dp))
        if (number != null) {
            Nameplate(stringResource(R.string.trih_episode_number, number), fonts, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
        }
    }
}

/** A small brass plate with a screw at each end. */
@Composable
private fun Nameplate(text: String, fonts: HistoryFonts, modifier: Modifier) {
    Row(
        modifier
            .height(16.dp)
            .drawBehind {
                val unit = 1.dp.toPx()
                val corner = androidx.compose.ui.geometry.CornerRadius(2f * unit)
                drawRoundRect(Color(0x8C0A0205), topLeft = Offset(0f, 2f * unit), size = size, cornerRadius = corner)
                drawRoundRect(Brush.verticalGradient(0f to Color(0xFFE2C78F), 0.55f to Color(0xFFBE9B62), 1f to Color(0xFFA5834E)), cornerRadius = corner)
                drawRoundRect(Color(0xB33C280F), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(unit))
                drawLine(Color(0x99FFF0C8), Offset(2f * unit, 1.5f * unit), Offset(size.width - 2f * unit, 1.5f * unit), strokeWidth = unit)
            }
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Screw()
        Text(
            text.uppercase(Locale.getDefault()),
            style = brandedTextStyle(fonts.cinzel, 9.sp, 10.sp, letterSpacing = 1.sp).copy(
                fontWeight = FontWeight.Bold,
                shadow = Shadow(Color(0x80FFF0C8), Offset(0f, 1f), 0f),
            ),
            color = HistoryColors.PlateInk,
            maxLines = 1,
        )
        Screw()
    }
}

@Composable
private fun Screw() {
    Spacer(
        Modifier.size(3.dp).drawBehind {
            drawCircle(Brush.radialGradient(0f to Color(0xFFF0DCAE), 0.7f to Color(0xFF6E5228), center = Offset(size.width * 0.35f, size.height * 0.35f), radius = size.width))
        },
    )
}
