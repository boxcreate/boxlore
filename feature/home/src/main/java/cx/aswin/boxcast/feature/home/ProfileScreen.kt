package cx.aswin.boxcast.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentRegion: String = "us",
    onSetRegion: (String) -> Unit = {},
    onBack: () -> Unit,
    onResetAnalytics: () -> Unit,
    appInstanceId: String? = null,
    currentThemeConfig: String = "system",
    isDynamicColorEnabled: Boolean = true,
    currentThemeBrand: String = "violet",
    onSetThemeConfig: (String) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onSetThemeBrand: (String) -> Unit = {},
    onExportJson: (android.net.Uri) -> Unit = {},
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {}
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var isDeletionExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { onExportJson(it) } }
    )
    val importJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportJson(it) } }
    )
    val importOpmlLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpml(it) } }
    )
    
    // Calculate enabled state at top level to ensure recomposition
    val isDataCollectionEnabled = true

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            
            // SECTION: Content Preferences (Region)
            item {
                CollapsibleSection("Global Content", initiallyExpanded = false) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                             ListItem(
                                headlineContent = { Text("Content Region") },
                                supportingContent = { Text("Choose region for trending charts.") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Public, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                            )
                            // Region Toggle Row
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isGlobal = currentRegion != "in"
                                
                                // INDIA Option
                                FilterChip(
                                    selected = !isGlobal,
                                    onClick = { onSetRegion("in") },
                                    label = { Text("India (IN)") },
                                    leadingIcon = { 
                                        if (!isGlobal) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // GLOBAL Option
                                FilterChip(
                                    selected = isGlobal,
                                    onClick = { onSetRegion("us") },
                                    label = { Text("Global (US)") },
                                    leadingIcon = { 
                                        if (isGlobal) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION: Theming
            item {
                CollapsibleSection("Appearance", initiallyExpanded = false) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            // Theme Mode
                            ListItem(
                                headlineContent = { Text("Theme Mode") },
                                leadingContent = {
                                    Icon(
                                        imageVector = when(currentThemeConfig) {
                                            "light" -> Icons.Rounded.LightMode
                                            "dark" -> Icons.Rounded.DarkMode
                                            else -> Icons.Rounded.BrightnessAuto
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            
                            // Segmented Button Row
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val modes = listOf(
                                    "system" to "System",
                                    "light" to "Light",
                                    "dark" to "Dark"
                                )
                                
                                modes.forEach { (mode, label) ->
                                    FilterChip(
                                        selected = currentThemeConfig == mode,
                                        onClick = { onSetThemeConfig(mode) },
                                        label = { Text(label) },
                                        leadingIcon = {
                                            if (currentThemeConfig == mode) {
                                                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            HorizontalDivider()

                            // Dynamic Color Toggle
                            ListItem(
                                headlineContent = { Text("Dynamic Color") },
                                supportingContent = { Text("Use wallpaper colors (Android 12+).") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isDynamicColorEnabled,
                                        onCheckedChange = { 
                                            onToggleDynamicColor(it)
                                            // Optional: If turning ON dynamic color, we might want to reset brand? 
                                            // No, let's keep it independent.
                                        }
                                    )
                                }
                            )

                            // Static Theme Picker (Only if Dynamic Color is OFF)
                            androidx.compose.animation.AnimatedVisibility(visible = !isDynamicColorEnabled) {
                                Column {
                                    HorizontalDivider()
                                    
                                    Text(
                                        text = "Color Palette",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                    )
                                    
                                    // Theme Brands
                                    val brands = listOf(
                                        Triple("violet", "Violet", androidx.compose.ui.graphics.Color(0xFF6750A4)),
                                        Triple("emerald", "Emerald", androidx.compose.ui.graphics.Color(0xFF006C4C)),
                                        Triple("ocean", "Ocean", androidx.compose.ui.graphics.Color(0xFF0061A4)),
                                        Triple("sakura", "Sakura", androidx.compose.ui.graphics.Color(0xFFBC004B)),
                                        Triple("tangerine", "Tangerine", androidx.compose.ui.graphics.Color(0xFF964900)),
                                        Triple("crimson", "Crimson", androidx.compose.ui.graphics.Color(0xFFB91823)),
                                        Triple("canary", "Canary", androidx.compose.ui.graphics.Color(0xFF725C00))
                                    )
                                    
                                    FlowRow(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        brands.forEach { (id, label, color) ->
                                            val isSelected = currentThemeBrand == id
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.width(60.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(color)
                                                        .clickable { onSetThemeBrand(id) }
                                                        .then(
                                                            if (isSelected) {
                                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                                                            } else Modifier
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = androidx.compose.ui.graphics.Color.White,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: Data & Backup
            item {
                CollapsibleSection("Backup & Restore", initiallyExpanded = false) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Export Library Backup") },
                                supportingContent = { Text("Save subscriptions & liked episodes to JSON") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable { 
                                    exportJsonLauncher.launch("boxcast_backup_${System.currentTimeMillis()}.json") 
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                            ListItem(
                                headlineContent = { Text("Import Library Backup") },
                                supportingContent = { Text("Restore a previous JSON backup") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Upload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable { 
                                    importJsonLauncher.launch(arrayOf("application/json"))
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            
                            ListItem(
                                headlineContent = { Text("Import from OPML") },
                                supportingContent = { Text("Migrate subscriptions from other apps") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.ImportExport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable { 
                                    importOpmlLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                }
            }

            // SECTION: Privacy & Data (Unified)
            item {
                CollapsibleSection("Privacy & Data", initiallyExpanded = false) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                            // Data Usage Explanation
                            ListItem(
                                headlineContent = { Text("How We Use Data") },
                                supportingContent = { 
                                    Text("BoxCast uses anonymous, first-party telemetry to improve your recommendations and identify bugs. No personal data is collected or shared with third parties.") 
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Info, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            HorizontalDivider()

                            // Privacy Policy
                            ListItem(
                                headlineContent = { Text("Privacy Policy") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aswin.cx/boxcast/privacy"))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                            
                            // --- UNIFIED DATA MANAGEMENT SECTION ---
                             HorizontalDivider(thickness = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                             
                             // Reset Identity
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Reset Identity",
                                        color = if (isDataCollectionEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    ) 
                                },
                                supportingContent = { Text("Generate new anonymous ID.") },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.Delete, 
                                        contentDescription = null,
                                        tint = if (isDataCollectionEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                 modifier = Modifier.clickable(enabled = isDataCollectionEnabled) { showResetDialog = true }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 56.dp)
                            )
                            
                            // Request Immediate Deletion (Expandable)
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Request Immediate Deletion", 
                                        color = if (isDataCollectionEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    ) 
                                },
                                supportingContent = { 
                                    if (!isDataCollectionEnabled) {
                                        Text("Data collection is currently disabled.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    } else {
                                        Text("Permanently erase server data.")
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.Email, 
                                        contentDescription = null,
                                        tint = if (isDataCollectionEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                trailingContent = {
                                    if (isDataCollectionEnabled) {
                                        Icon(
                                            if (isDeletionExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.clickable(enabled = isDataCollectionEnabled) { 
                                    isDeletionExpanded = !isDeletionExpanded 
                                }
                            )

                            // Expanded Content (Instance ID + Email)
                            androidx.compose.animation.AnimatedVisibility(visible = isDeletionExpanded && isDataCollectionEnabled) {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                    Text(
                                        text = "To request deletion, please email us your Instance ID below. This is the only way we can identify your anonymous data.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // ID Display
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            appInstanceId?.let {
                                                clipboardManager.setText(AnnotatedString(it))
                                                Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = appInstanceId ?: "Generating ID...",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Email Button
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:")
                                                putExtra(Intent.EXTRA_EMAIL, arrayOf("privacy@aswin.cx"))
                                                putExtra(Intent.EXTRA_SUBJECT, "Data Deletion Request")
                                                putExtra(Intent.EXTRA_TEXT, "Please delete data associated with Instance ID: ${appInstanceId ?: "UNKNOWN"}")
                                            }
                                            try { context.startActivity(intent) } catch(_: Exception) {
                                                Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Email privacy@aswin.cx")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: Project
            item {
                CollapsibleSection(title = "Project & Community", initiallyExpanded = false) {
                    ElevatedCard(
                         modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                             ListItem(
                                headlineContent = { Text("GitHub Repository") },
                                supportingContent = { Text("Open Source. Star, fork, or contribute!") },
                                leadingContent = {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(cx.aswin.boxcast.core.designsystem.R.drawable.ic_github),
                                        contentDescription = "GitHub",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                 modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashwkun/box.cast.android"))
                                    try { context.startActivity(intent) } catch(_:Exception){}
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                             ListItem(
                                headlineContent = { Text("Podcast Index") },
                                supportingContent = { Text("Powered by the decentralized index.") },
                                leadingContent = {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFFE22828)) // Red for Index
                                },
                                 modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org"))
                                    try { context.startActivity(intent) } catch(_:Exception){}
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                             ListItem(
                                headlineContent = { Text("Apple Podcasts") },
                                supportingContent = { Text("Catalog reference.") },
                                leadingContent = {
                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF8E8E93))
                                },
                                 modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://podcasts.apple.com"))
                                    try { context.startActivity(intent) } catch(_:Exception){}
                                }
                            )
                        }
                    }
                }
            }

             item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "boxcast v1.0.0 (Beta)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Made with ❤️",
                        style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Analytics Identity?") },
            text = {
                Column {
                    Text("This acts as a 'Forget Me' for our cloud servers.")
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                         color = MaterialTheme.colorScheme.surfaceContainer,
                         shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Local Data Safe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Your listening history, subscriptions, and downloads are stored ON THIS DEVICE and WILL NOT be deleted.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Your cloud analytics ID will be regenerated.")
                    Text("• Orphaned data is auto-deleted after 14 months.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAnalytics()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset & Re-Onboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        // Content
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
             content()
        }
    }
}
