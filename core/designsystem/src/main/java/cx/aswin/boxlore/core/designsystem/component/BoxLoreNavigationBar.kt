package cx.aswin.boxlore.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import kotlin.math.roundToInt

enum class NavigationStyle(val key: String, val label: String,) {
    Floating(key = "floating", label = "Floating"),
    Classic(key = "classic", label = "Classic"),
    ;

    companion object {
        fun fromKey(key: String?): NavigationStyle = entries.firstOrNull { it.key == key } ?: Floating
    }
}

val LocalNavigationStyle = staticCompositionLocalOf { NavigationStyle.Floating }

data class NavigationChromeMetrics(
    val navigationBarHeight: androidx.compose.ui.unit.Dp,
    val navigationBottomInset: androidx.compose.ui.unit.Dp,
    val miniPlayerHeight: androidx.compose.ui.unit.Dp,
    val miniPlayerNavigationGap: androidx.compose.ui.unit.Dp,
    val miniPlayerTopCornerRadius: androidx.compose.ui.unit.Dp,
    val miniPlayerBottomCornerRadius: androidx.compose.ui.unit.Dp,
) {
    val bottomNavigationClearance: androidx.compose.ui.unit.Dp
        get() = navigationBarHeight + navigationBottomInset
}

private val FloatingNavigationChromeMetrics =
    NavigationChromeMetrics(
        navigationBarHeight = 56.dp,
        navigationBottomInset = 12.dp,
        miniPlayerHeight = 64.dp,
        miniPlayerNavigationGap = 8.dp,
        miniPlayerTopCornerRadius = 32.dp,
        miniPlayerBottomCornerRadius = 32.dp,
    )

private val ClassicNavigationChromeMetrics =
    NavigationChromeMetrics(
        navigationBarHeight = 80.dp,
        navigationBottomInset = 0.dp,
        miniPlayerHeight = 72.dp,
        miniPlayerNavigationGap = 8.dp,
        miniPlayerTopCornerRadius = 26.dp,
        miniPlayerBottomCornerRadius = 14.dp,
    )

fun navigationChromeMetrics(style: NavigationStyle): NavigationChromeMetrics = when (style) {
    NavigationStyle.Floating -> FloatingNavigationChromeMetrics
    NavigationStyle.Classic -> ClassicNavigationChromeMetrics
}

fun navigationStyleUsesExternalSystemNavigationInset(style: NavigationStyle): Boolean = style == NavigationStyle.Floating

/** Height of the floating Home / Explore / Library pill. */
val AppNavigationBarHeight = FloatingNavigationChromeMetrics.navigationBarHeight

/** Diameter of the separate circular Lore action. */
val AppLoreNavigationActionSize = 52.dp

/** Shared spacing between the floating navigation pill and its circular action slot. */
val AppFloatingNavigationActionGap = 14.dp

/** Horizontal space between screen edge and floating navigation chrome. */
val AppNavigationBarHorizontalInset = 16.dp

/** Gap between the system navigation area and the floating navigation chrome. */
val AppNavigationBarBottomInset = FloatingNavigationChromeMetrics.navigationBottomInset

/**
 * Vertical space a screen must reserve for floating navigation chrome, excluding
 * Android system navigation insets (which are supplied independently by each surface).
 */
val AppBottomNavigationClearance = FloatingNavigationChromeMetrics.bottomNavigationClearance

/**
 * Collapsed mini-player height. Keep in sync with
 * `feature.player.v2.MiniPlayerHeight`.
 */
val AppMiniPlayerHeight = FloatingNavigationChromeMetrics.miniPlayerHeight

/** Gap between collapsed mini-player and the app navbar. */
val AppMiniPlayerNavGap = FloatingNavigationChromeMetrics.miniPlayerNavigationGap

/** Content clearance for either navigation presentation, optionally including mini-player chrome. */
fun appBottomChromeContentPadding(style: NavigationStyle, isMiniPlayerVisible: Boolean,): androidx.compose.ui.unit.Dp {
    val metrics = navigationChromeMetrics(style)
    return metrics.bottomNavigationClearance +
        if (isMiniPlayerVisible) metrics.miniPlayerHeight + metrics.miniPlayerNavigationGap else 0.dp
}

@Composable
fun appBottomChromeContentPadding(isMiniPlayerVisible: Boolean): androidx.compose.ui.unit.Dp = appBottomChromeContentPadding(
    style = LocalNavigationStyle.current,
    isMiniPlayerVisible = isMiniPlayerVisible,
)

/** Explore For You / Top segmented control (padding + pill). */
val ExploreTabSelectorFabHeight = 44.dp

private val NavPillShape = RoundedCornerShape(28.dp)
private val NavSelectionShape = RoundedCornerShape(22.dp)

data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @DrawableRes val selectedIconRes: Int? = null,
    @DrawableRes val unselectedIconRes: Int? = null,
)

private val primaryNavDestinations = listOf(
    NavDestination(
        route = "home",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    NavDestination(
        route = "explore",
        label = "Explore",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    ),
    NavDestination(
        route = "library",
        label = "Library",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
    ),
)

private val loreNavDestination =
    NavDestination(
        route = "learn",
        label = "Lore",
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        selectedIconRes = cx.aswin.boxlore.core.designsystem.R.drawable.ic_neurology_filled,
        unselectedIconRes = cx.aswin.boxlore.core.designsystem.R.drawable.ic_neurology,
    )

private val classicNavDestinations = primaryNavDestinations + loreNavDestination

@Composable
fun BoxLoreNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    style: NavigationStyle,
    initialContentReady: Boolean,
    modifier: Modifier = Modifier,
    compactProgress: State<Float>? = null,
) {
    when (style) {
        NavigationStyle.Floating ->
            FloatingNavigationBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                initialContentReady = initialContentReady,
                modifier = modifier,
                compactProgress = compactProgress,
            )
        NavigationStyle.Classic ->
            ClassicNavigationBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                modifier = modifier,
            )
    }
}

/**
 * Google Photos-inspired 3+1 floating app navigation:
 * Home, Explore, and Library share a connected pill while Lore is a separate
 * circular action. Compact progress moves that same Lore action into a fourth
 * icon slot, leaving its trailing circle slot for the mini-player. Routes,
 * scroll policy, and click handling remain owned by the app shell.
 */
@Composable
private fun FloatingNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    initialContentReady: Boolean,
    modifier: Modifier = Modifier,
    compactProgress: State<Float>? = null,
) {
    val selectedIndex = navigationIndicatorSelectedIndex(currentRoute)
    val lastSelectedIndex = remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }
    SideEffect {
        if (selectedIndex >= 0) lastSelectedIndex.intValue = selectedIndex
    }
    val indicatorIndex = animateFloatAsState(
        targetValue = navigationIndicatorTargetIndex(selectedIndex, lastSelectedIndex.intValue).toFloat(),
        animationSpec = FloatingNavigationIndicatorMotion,
        label = "navigationIndicatorIndex",
    )
    val indicatorVisibility = animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = tween(160),
        label = "navigationIndicatorVisibility",
    )
    val navbarLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontFamily = rememberGoogleSansFamily(weight = 600),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = (-0.1).sp,
    )
    val outline = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    Layout(
        content = {
            Surface(
                shape = NavPillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = outline,
                shadowElevation = 4.dp,
            ) {}
            Surface(
                modifier = Modifier.graphicsLayer {
                    val external = 1f - adaptivePlayerContractionProgress(compactProgress?.value ?: 0f)
                    alpha = external
                    shadowElevation = 4.dp.toPx() * external
                    shape = CircleShape
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = outline,
            ) {}
            // The only selection background, shared by primary tabs and Lore.
            Surface(
                modifier = Modifier.clearAndSetSemantics {},
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                FloatingNavigationIndicatorAurora(
                    indicatorIndex = indicatorIndex,
                    active = selectedIndex == 3,
                )
            }
            FloatingPrimaryNavItems(currentRoute, onNavigate, navbarLabelStyle, compactProgress)
            LoreNavActionFab(
                selected = selectedIndex == 3,
                initialContentReady = initialContentReady,
                onClick = { onNavigate(loreNavDestination.route) },
                modifier = Modifier.fillMaxSize(),
            )
        },
        modifier = modifier.fillMaxWidth().padding(
            start = AppNavigationBarHorizontalInset,
            end = AppNavigationBarHorizontalInset,
            bottom = AppNavigationBarBottomInset,
        ).height(AppNavigationBarHeight).selectableGroup(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val progress = compactProgress?.value ?: 0f
        val geometry = floatingNavigationLayout(width.toDp(), progress)
        val indicatorBounds = floatingNavigationIndicatorBounds(width.toDp(), progress, indicatorIndex.value)
        val pillConstraints = Constraints.fixed(geometry.pillWidth.roundToPx(), height)
        val loreSize = geometry.loreSize.roundToPx()
        val loreConstraints = Constraints.fixed(loreSize, loreSize)
        val pillBackground = measurables[0].measure(pillConstraints)
        val loreBackground = measurables[1].measure(loreConstraints)
        val indicator = measurables[2].measure(Constraints.fixed(indicatorBounds.width.roundToPx(), indicatorBounds.height.roundToPx()))
        val primaryItems = measurables[3].measure(pillConstraints)
        val loreAction = measurables[4].measure(loreConstraints)
        layout(width, height) {
            val loreX = geometry.loreStart.roundToPx()
            val loreY = (height - loreSize) / 2
            pillBackground.placeRelative(0, 0)
            loreBackground.placeRelative(loreX, loreY)
            val indicatorX = indicatorBounds.start.toPx()
            val roundedIndicatorX = indicatorX.roundToInt()
            indicator.placeRelativeWithLayer(roundedIndicatorX, (height - indicator.height) / 2) {
                translationX = (indicatorX - roundedIndicatorX) * if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                alpha = indicatorVisibility.value
            }
            primaryItems.placeRelative(0, 0)
            loreAction.placeRelative(loreX, loreY)
        }
    }
}

@Composable
private fun FloatingPrimaryNavItem(
    destination: NavDestination,
    selected: Boolean,
    labelStyle: TextStyle,
    compactProgress: State<Float>?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberNavigationPressScale(interactionSource)
    val selection = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(160),
        label = "navigationItemSelection",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "navigationContentColor",
    )
    Surface(
        modifier =
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = destination.label },
        shape = NavSelectionShape,
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - adaptivePlayerContractionProgress(compactProgress?.value ?: 0f)
                        translationY = -4.dp.toPx() * adaptivePlayerContractionProgress(compactProgress?.value ?: 0f)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Layout(
                    content = { Icon(destination.iconFor(selected = true), contentDescription = null) },
                    modifier = Modifier.graphicsLayer {
                        alpha = selection.value
                        clip = true
                    },
                ) { measurables, _ ->
                    val icon = measurables.single().measure(Constraints.fixed(18.dp.roundToPx(), 18.dp.roundToPx()))
                    layout((22.dp.toPx() * selection.value).roundToInt(), icon.height) { icon.placeRelative(0, 0) }
                }
                Text(
                    text = destination.label,
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            Box(
                modifier =
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val progress = adaptivePlayerContractionProgress(compactProgress?.value ?: 0f)
                        alpha = progress
                        scaleX = 0.86f + 0.14f * progress
                        scaleY = scaleX
                        translationY = 4.dp.toPx() * (1f - progress)
                    },
            ) {
                Icon(
                    destination.iconFor(selected = false),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - selection.value },
                )
                Icon(
                    destination.iconFor(selected = true),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = selection.value },
                )
            }
        }
    }
}

@Composable
private fun FloatingPrimaryNavItems(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    labelStyle: TextStyle,
    compactProgress: State<Float>?,
) {
    Layout(
        content = {
            primaryNavDestinations.forEach { destination ->
                FloatingPrimaryNavItem(
                    destination = destination,
                    selected = isNavDestinationSelected(currentRoute, destination.route),
                    labelStyle = labelStyle,
                    compactProgress = compactProgress,
                    onClick = { onNavigate(destination.route) },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val padding = NavPillContentPadding.roundToPx()
        val tabWidth = floatingNavigationTabWidth(width.toDp(), compactProgress?.value ?: 0f).toPx()
        val itemHeight = CompactNavigationTabSize.roundToPx().coerceAtMost(height)
        val items = measurables.mapIndexed { index, measurable ->
            val start = (tabWidth * index).toInt()
            val end = (tabWidth * (index + 1)).toInt()
            measurable.measure(Constraints.fixed(end - start, itemHeight))
        }
        layout(width, height) {
            items.forEachIndexed { index, item ->
                item.placeRelative(padding + (tabWidth * index).toInt(), (height - itemHeight) / 2)
            }
        }
    }
}

@Composable
private fun LoreNavActionFab(
    selected: Boolean,
    initialContentReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberNavigationPressScale(interactionSource)
    val selection = animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "loreSelection")
    val launchScale = animateFloatAsState(if (initialContentReady) 1f else 0.96f, tween(180), label = "loreLaunchScale")
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(180),
        label = "loreContentColor",
    )
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = pressScale.value
            scaleY = pressScale.value
        }.selectable(
            selected = selected,
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ).semantics { contentDescription = loreNavDestination.label },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Box(
                modifier =
                Modifier.size(24.dp).graphicsLayer {
                    scaleX = launchScale.value
                    scaleY = launchScale.value
                },
            ) {
                Icon(
                    loreNavDestination.iconFor(selected = false),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - selection.value },
                )
                Icon(
                    loreNavDestination.iconFor(selected = true),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = selection.value },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicNavigationBar(currentRoute: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier,) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 3.dp,
    ) {
        ShortNavigationBar(
            modifier =
            Modifier.heightIn(
                min = navigationChromeMetrics(NavigationStyle.Classic).navigationBarHeight,
            ),
            containerColor = Color.Transparent,
            arrangement = ShortNavigationBarArrangement.EqualWeight,
        ) {
            classicNavDestinations.forEach { destination ->
                val isSelected = isNavDestinationSelected(currentRoute, destination.route)
                ShortNavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.iconFor(selected = isSelected),
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    iconPosition = NavigationItemIconPosition.Top,
                )
            }
        }
    }
}

@Composable
private fun NavDestination.iconFor(selected: Boolean): ImageVector = when {
    selectedIconRes != null && unselectedIconRes != null ->
        ImageVector.vectorResource(id = if (selected) selectedIconRes else unselectedIconRes)
    selected -> selectedIcon
    else -> unselectedIcon
}
