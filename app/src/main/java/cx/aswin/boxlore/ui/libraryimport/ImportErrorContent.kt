package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/**
 * An import that stopped: what went wrong in plain words, whether the library is untouched (only when
 * nothing was written), and a way back to the import options. Echo waits on a quieter stage.
 */
@Composable
internal fun ErrorContent(failure: ImportFailure, onRetry: (() -> Unit)?, onClose: () -> Unit) {
    val (title, body) = importFailureCopy(failure)
    ImportPage(
        echo = ImportEchoMoment.Failed,
        content = {
            ImportTitle(stringResource(title))
            ImportBody(stringResource(body))
            if (failure.libraryUnchanged) {
                Text(
                    stringResource(R.string.import_unchanged),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("import_unchanged"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        },
        actions = {
            if (failure.fileHint) {
                Text(
                    stringResource(R.string.import_file_hint),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            val retry = failure.retry.takeIf { it != ImportRetry.None }?.let { kind -> onRetry?.let { kind to it } }
            if (retry != null) {
                ImportPrimaryButton(
                    stringResource(if (retry.first == ImportRetry.AnotherFile) R.string.import_choose_another else R.string.import_try_again),
                    retry.second,
                    Modifier.testTag("import_retry"),
                )
                ImportQuietButton(stringResource(R.string.import_close), onClose, Modifier.testTag("import_error_close"))
            } else {
                ImportPrimaryButton(stringResource(R.string.import_close), onClose, Modifier.testTag("import_error_close"))
            }
        },
    )
}

/** The headline and explanation for each failure. */
internal fun importFailureCopy(failure: ImportFailure): Pair<Int, Int> = when (failure) {
    ImportFailure.FileUnreadable -> R.string.import_failed_unreadable_title to R.string.import_failed_unreadable_body
    ImportFailure.NoFeeds -> R.string.import_failed_no_feeds_title to R.string.import_failed_no_feeds_body
    ImportFailure.NothingReachable -> R.string.import_failed_unreachable_title to R.string.import_failed_unreachable_body
    ImportFailure.ImportStopped -> R.string.import_failed_stopped_title to R.string.import_failed_stopped_body
    ImportFailure.MarkingStopped -> R.string.import_failed_marking_title to R.string.import_failed_marking_body
    ImportFailure.BackupFailed -> R.string.import_failed_backup_title to R.string.import_failed_backup_body
}
