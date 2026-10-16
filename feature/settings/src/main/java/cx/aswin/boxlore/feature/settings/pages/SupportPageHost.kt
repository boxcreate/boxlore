package cx.aswin.boxlore.feature.settings.pages

import android.os.SystemClock
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.feature.settings.pages.supportworlds.AnalyticsHelperSupportSink
import cx.aswin.boxlore.feature.settings.pages.supportworlds.LocalSupportPageSession
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportAnalyticsSink
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportPageSession
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldDebugMenu
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldDebugOverride
import cx.aswin.boxlore.feature.settings.pages.supportworlds.findActivity
import cx.aswin.boxlore.feature.settings.pages.supportworlds.rememberIsDebuggableApp
import cx.aswin.boxlore.feature.settings.pages.supportworlds.resolveSupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.supportWorldSkin
import java.time.LocalDateTime

/**
 * What the Support page reads from the outside world. Production uses the defaults; tests swap in
 * a fixed flag, a recording sink, a fake clock or a simulated configuration change.
 */
internal data class SupportPageEnvironment(
    val variantProvider: () -> String? = { AnalyticsHelper.supportWorldVariant(SupportWorld.entries.map { it.variantKey }.toSet()) },
    val analyticsSink: SupportAnalyticsSink = AnalyticsHelperSupportSink,
    val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
    /** Null detects a debuggable build at runtime. */
    val debuggable: Boolean? = null,
    /** Null asks the hosting activity whether it is changing configurations. */
    val isChangingConfigurations: (() -> Boolean)? = null,
)

/**
 * Entry point for Support boxlore. Reads the `support-world` experiment once per visit, shows the
 * assigned world (or today's page as the control and fallback) and owns the visit's analytics.
 */
@Composable
internal fun SupportPageHost(
    onBack: () -> Unit,
    totalListeningHours: Long?,
    entryPoint: String = "settings",
    environment: SupportPageEnvironment = SupportPageEnvironment(),
) {
    val debuggable = environment.debuggable ?: rememberIsDebuggableApp()
    val analyticsSink = environment.analyticsSink
    val elapsedMs = environment.elapsedMs
    val flagVariant by rememberSaveable { mutableStateOf(environment.variantProvider()) }
    var debugOverride by remember { mutableStateOf(if (debuggable) SupportWorldDebugOverride.world else null) }
    val assignment = remember(flagVariant, debugOverride) { resolveSupportWorld(flagVariant, debugOverride) }
    val session = rememberSaveable(assignment, saver = SupportPageSession.saver(analyticsSink, elapsedMs)) {
        SupportPageSession(assignment, analyticsSink, elapsedMs)
    }

    LaunchedEffect(session) {
        val now = LocalDateTime.now()
        session.start(entryPoint = entryPoint, localHour = now.hour, weekday = now.dayOfWeek.value)
    }
    val context = LocalContext.current
    val changingConfigurations = environment.isChangingConfigurations
        ?: { context.findActivity()?.isChangingConfigurations == true }
    DisposableEffect(session) {
        // Rotation, resizing or folding recreates the activity; only a real exit ends the visit.
        onDispose { if (!changingConfigurations()) session.end() }
    }

    val skin = supportWorldSkin(assignment.world)
    val actions: @Composable RowScope.() -> Unit = {
        if (debuggable) {
            SupportWorldDebugMenu(
                current = assignment.world,
                override = debugOverride,
                tint = skin?.palette?.content ?: Color.White,
                onChoose = { world ->
                    SupportWorldDebugOverride.world = world
                    debugOverride = world
                },
            )
        }
    }

    CompositionLocalProvider(LocalSupportPageSession provides session) {
        key(assignment.world) {
            SupportDevelopmentPage(
                onBack = onBack,
                totalListeningHours = totalListeningHours,
                topBarActions = actions,
                style = skin?.let(::supportWorldPageStyle) ?: ControlSupportPageStyle,
            )
        }
    }
}
