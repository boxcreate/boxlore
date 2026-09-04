package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/**
 * Persistent top-center status popup for foreground work that must finish before the app closes.
 * Visibility is entirely owned by the caller; this component has no timeout or dismiss action.
 */
@Suppress("FunctionName")
@Composable
fun RepairProgressPopup(visible: Boolean, modifier: Modifier = Modifier,) {
    AnimatedVisibility(
        visible = visible,
        enter =
        fadeIn(animationSpec = tween(220)) +
            scaleIn(
                initialScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = ExpressiveMotion.FormalSpring,
            ),
        exit =
        fadeOut(animationSpec = tween(180)) +
            scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF161618),
            contentColor = Color.White,
            shadowElevation = 20.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxLoreLoader.Expressive(
                    size = 44.dp,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.repair_progress_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = GoogleSansWeight.bold,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.repair_progress_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}
