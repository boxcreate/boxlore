package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Everything a world (or today's page) needs to explain that payments aren't live yet. */
internal data class SupportNoticeContent(
    @DrawableRes val art: Int,
    val accent: Color,
    val surface: Color,
    val content: Color,
    val muted: Color,
    val scrim: Color,
    val chip: String,
    val chipColor: Color,
    val chipFont: SupportWorldFont?,
    val title: String,
    val titleFont: SupportWorldFont?,
    val body: String,
    val dismiss: String,
    val badge: ImageVector,
    val badgeContainer: Color,
    val badgeContent: Color,
)

private val NoticeShape = RoundedCornerShape(28.dp)

/**
 * Shown after a pay tap while payments are off. It closes from its button, a tap outside the card
 * or Back (handled by the host), and keeps the page's selection untouched.
 */
@Composable
internal fun SupportPaymentNotice(
    visible: Boolean,
    content: SupportNoticeContent,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(content.scrim)
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
            NoticeCard(
                content = content,
                reducedMotion = reducedMotion,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                        ),
                        exit = scaleOut(targetScale = 0.96f, animationSpec = tween(180)),
                    ),
            )
        }
    }
}

@Composable
private fun NoticeCard(
    content: SupportNoticeContent,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bob by rememberSupportLoop(enabled = !reducedMotion, durationMillis = 900, label = "support_notice_badge")
    Column(
        modifier = modifier
            .shadow(30.dp, NoticeShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(NoticeShape)
            .background(content.surface)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(content.accent.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width / 2f, 0f),
                )
            }
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = content.title }
            .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.BottomCenter) {
            Image(
                painter = painterResource(content.art),
                contentDescription = null,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.65f) }),
                modifier = Modifier
                    .size(136.dp)
                    .graphicsLayer {
                        rotationZ = -10f
                        translationY = 6.dp.toPx()
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 58.dp, y = 30.dp)
                    .graphicsLayer { translationY = -10.dp.toPx() * bob }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(content.badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(content.badge, contentDescription = null, tint = content.badgeContent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = content.chip,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = content.chipFont?.family,
                fontWeight = content.chipFont?.weight ?: GoogleSansWeight.bold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
            ),
            color = content.chipColor,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(content.chipColor.copy(alpha = 0.12f))
                .border(BorderStroke(1.dp, content.chipColor.copy(alpha = 0.35f)), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = content.titleFont?.family,
                fontWeight = content.titleFont?.weight ?: GoogleSansWeight.bold,
                fontSize = 23.sp,
                lineHeight = 28.sp,
            ),
            color = content.content,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content.body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = content.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = content.content, contentColor = content.surface),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                text = content.dismiss,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold, fontSize = 16.sp),
            )
        }
    }
}
