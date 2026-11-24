package cx.aswin.boxlore.core.designsystem.components

import android.os.Build

/**
 * Whether boxlore should confirm a copy to the clipboard itself. From Android 13 the system shows
 * its own preview of what was copied, so a second "copied" message would repeat it.
 */
fun boxloreShowsOwnCopyConfirmation(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU
