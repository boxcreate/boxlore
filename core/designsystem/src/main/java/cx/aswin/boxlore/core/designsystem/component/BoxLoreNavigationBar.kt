package cx.aswin.boxlore.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import kotlin.math.PI
import kotlin.math.sin

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
private val NavPillContentPadding = 6.dp
private val NavPillItemHeight = AppNavigationBarHeight - (NavPillContentPadding * 2)

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
) {
    when (style) {
        NavigationStyle.Floating ->
            FloatingNavigationBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                initialContentReady = initialContentReady,
                modifier = modifier,
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
 * circular action. Routes and click handling remain owned by the app shell.
 */
@Composable
private fun FloatingNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    initialContentReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val loreSelected = isNavDestinationSelected(currentRoute, loreNavDestination.route)
    val navbarLabelStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontFamily = rememberGoogleSansFamily(weight = 600),
            fontWeight = FontWeight.Normal,
        )

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(
                start = AppNavigationBarHorizontalInset,
                end = AppNavigationBarHorizontalInset,
                bottom = AppNavigationBarBottomInset,
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingPrimaryNavPill(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            labelStyle = navbarLabelStyle,
            modifier = Modifier.weight(1f).height(AppNavigationBarHeight),
        )
        LoreNavActionFab(
            selected = loreSelected,
            initialContentReady = initialContentReady,
            onClick = { onNavigate(loreNavDestination.route) },
        )
    }
}

@Composable
private fun FloatingPrimaryNavPill(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = NavPillShape,
        shadowElevation = 2.dp,
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(NavPillContentPadding)
                .selectableGroup(),
        ) {
            val selectedIndex =
                primaryNavDestinations.indexOfFirst { destination ->
                    isNavDestinationSelected(currentRoute, destination.route)
                }
            val itemWidth = maxWidth / primaryNavDestinations.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex.coerceAtLeast(0),
                animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "navigationIndicatorOffset",
            )

            if (selectedIndex >= 0) {
                Surface(
                    modifier =
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(NavPillItemHeight),
                    shape = NavSelectionShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {}
            }

            Row(modifier = Modifier.fillMaxSize()) {
                primaryNavDestinations.forEach { destination ->
                    val selected = isNavDestinationSelected(currentRoute, destination.route)
                    Surface(
                        onClick = { onNavigate(destination.route) },
                        modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                            },
                        shape = NavSelectionShape,
                        color = Color.Transparent,
                        contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = destination.iconFor(selected = true),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                            }
                            Text(
                                text = destination.label,
                                style = labelStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoreNavActionFab(selected: Boolean, initialContentReady: Boolean, onClick: () -> Unit,) {
    FloatingActionButton(
        onClick = onClick,
        modifier =
        Modifier
            .size(AppLoreNavigationActionSize)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        shape = RoundedCornerShape(50),
        containerColor =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        elevation =
        FloatingActionButtonDefaults.elevation(
            defaultElevation = 2.dp,
            pressedElevation = 3.dp,
        ),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoreAurora(
                isSelected = selected,
                initialContentReady = initialContentReady,
            )
            Icon(
                imageVector = loreNavDestination.iconFor(selected),
                contentDescription = loreNavDestination.label,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LoreAurora(isSelected: Boolean, initialContentReady: Boolean,) {
    val startupProgress = remember { Animatable(0f) }
    LaunchedEffect(initialContentReady) {
        if (initialContentReady) {
            startupProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3_000, easing = FastOutSlowInEasing),
            )
        }
    }

    if (isSelected) {
        LoreActiveAurora()
    } else {
        LoreAuroraSurface(
            progress = startupProgress.value,
            glowIntensity = sin(startupProgress.value * PI).toFloat() * 0.5f,
        )
    }
}

@Composable
private fun LoreActiveAurora() {
    val transition = rememberInfiniteTransition(label = "loreActiveAurora")
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 10_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loreActiveAuroraProgress",
        )

    LoreAuroraSurface(
        progress = progress,
        glowIntensity = 0.44f,
    )
}

@Composable
private fun LoreAuroraSurface(progress: Float, glowIntensity: Float,) {
    val glowColors =
        listOf(
            Color(0xFF4285F4).copy(alpha = glowIntensity),
            Color(0xFF8AB4F8).copy(alpha = glowIntensity),
            Color(0xFF9B72CB).copy(alpha = glowIntensity),
            Color(0xFFEA4335).copy(alpha = glowIntensity),
            Color(0xFFFBBC04).copy(alpha = glowIntensity),
            Color(0xFF34A853).copy(alpha = glowIntensity),
            Color(0xFF4285F4).copy(alpha = glowIntensity),
        )
    androidx.compose.foundation.layout.Box(
        modifier =
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .drawBehind {
                val shift = progress * size.width
                drawRect(
                    brush =
                    Brush.linearGradient(
                        colors = glowColors,
                        start = Offset(x = shift - size.width, y = size.height),
                        end = Offset(x = shift + size.width, y = 0f),
                    ),
                )
                drawRect(
                    brush =
                    Brush.radialGradient(
                        colors =
                        listOf(
                            Color.White.copy(alpha = glowIntensity * 0.38f),
                            Color.Transparent,
                        ),
                        center =
                        Offset(
                            x = size.width * (0.22f + (progress * 0.56f)),
                            y = size.height * 0.5f,
                        ),
                        radius = size.minDimension * 0.8f,
                    ),
                )
            },
    )
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

internal fun isNavDestinationSelected(currentRoute: String, destinationRoute: String): Boolean = currentRoute == destinationRoute || currentRoute.startsWith("$destinationRoute?")

@Composable
private fun NavDestination.iconFor(selected: Boolean): ImageVector = when {
    selectedIconRes != null && unselectedIconRes != null ->
        ImageVector.vectorResource(id = if (selected) selectedIconRes else unselectedIconRes)
    selected -> selectedIcon
    else -> unselectedIcon
}
