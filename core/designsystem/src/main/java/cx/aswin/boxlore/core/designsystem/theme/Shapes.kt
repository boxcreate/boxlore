package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BoxCastShapes = Shapes(
    // Extra Small (4dp): Autocomplete menu, snackbar, text fields
    extraSmall = RoundedCornerShape(4.dp),

    // Small (8dp): Chips, rich tooltip, suggestion chip
    small = RoundedCornerShape(8.dp),

    // Medium (12dp): Card, small FAB
    medium = RoundedCornerShape(12.dp),

    // Large (16dp): Extended FAB, FAB, Nav drawer
    large = RoundedCornerShape(16.dp),

    // Extra Large (28dp): Large FAB, Bottom sheet, Dialog
    extraLarge = RoundedCornerShape(28.dp)
)
