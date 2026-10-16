package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal val ImportCorner = RoundedCornerShape(24.dp)

/** Room for the close button above the page, which sits over the top end corner. */
private val ImportTopClearance = 56.dp

/**
 * One import screen: Echo on its stage with the headline, centred in the space above the actions,
 * which sit at the bottom. On short windows (landscape phones, large text) the whole page scrolls
 * and the gaps collapse instead of squeezing anything. Echo's size follows the window height.
 */
@Composable
internal fun ImportPage(
    echo: ImportEchoMoment,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("import_page")) {
        val echoSize = importEchoSize(maxHeight)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(top = ImportTopClearance, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ImportEcho(echo, Modifier.size(echoSize))
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

/** Echo on its stage. The stage is quieter when an import failed. Decorative: the headline says what happened. */
@Composable
internal fun ImportEcho(moment: ImportEchoMoment, modifier: Modifier = Modifier) {
    val stage = if (moment == ImportEchoMoment.Failed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer
    EchoCharacter(
        sequence = moment.sequence,
        modifier = modifier.testTag("import_echo"),
        stageColor = stage,
    )
}

@Composable
internal fun ImportTitle(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = GoogleSansWeight.bold,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ImportBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ImportPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ImportQuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = GoogleSansWeight.semiBold,
            textAlign = TextAlign.Center,
        )
    }
}
