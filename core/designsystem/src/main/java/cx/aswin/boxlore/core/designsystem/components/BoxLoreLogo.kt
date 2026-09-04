package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable boxlore wordmark from the vector drawable.
 *
 * @param height Visual height of the mark; width follows the logo aspect ratio.
 */
@Composable
fun BoxLoreLogo(modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.primary, height: Dp = 20.dp,) {
    Icon(
        painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_logo),
        contentDescription = "boxlore",
        tint = textColor,
        modifier =
        modifier
            .height(height)
            .aspectRatio(805f / 110f),
    )
}
