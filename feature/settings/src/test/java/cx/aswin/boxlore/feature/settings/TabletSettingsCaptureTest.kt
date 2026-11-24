package cx.aswin.boxlore.feature.settings

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.SupportApi
import cx.aswin.boxlore.core.network.model.SupportConversationResponse
import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportMetaDto
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import cx.aswin.boxlore.core.network.model.SupportPhotoResponse
import cx.aswin.boxlore.core.network.model.SupportReadRequest
import cx.aswin.boxlore.core.network.model.SupportReplyRequest
import cx.aswin.boxlore.core.network.model.SupportReplyResponse
import cx.aswin.boxlore.core.network.model.SupportStatusDto
import cx.aswin.boxlore.core.network.model.SupportThreadDto
import cx.aswin.boxlore.core.network.model.SupportThreadResponse
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.support.PhotoUpload
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportLocalStore
import cx.aswin.boxlore.core.support.SupportMeta
import cx.aswin.boxlore.core.support.SupportRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.downloads.AutoDownloadSettingsScreen
import cx.aswin.boxlore.feature.settings.downloads.SmartDownloadsSettingsScreen
import cx.aswin.boxlore.feature.settings.feedback.FEEDBACK_MESSAGE_TAG
import cx.aswin.boxlore.feature.settings.feedback.FeedbackComposeActions
import cx.aswin.boxlore.feature.settings.feedback.FeedbackContent
import cx.aswin.boxlore.feature.settings.feedback.FeedbackScreen
import cx.aswin.boxlore.feature.settings.feedback.FeedbackSupport
import cx.aswin.boxlore.feature.settings.feedback.FeedbackViewModel
import cx.aswin.boxlore.feature.settings.feedback.LogsPreviewDialog
import cx.aswin.boxlore.feature.settings.pages.AboutSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AccountSettingsDialogs
import cx.aswin.boxlore.feature.settings.pages.AccountSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppInfo
import cx.aswin.boxlore.feature.settings.pages.AppearanceHomePage
import cx.aswin.boxlore.feature.settings.pages.AppearanceLanguagePage
import cx.aswin.boxlore.feature.settings.pages.AppearanceNavigationPage
import cx.aswin.boxlore.feature.settings.pages.AppearancePlayerPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceShowPagesPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceTextPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceWidgetsPage
import cx.aswin.boxlore.feature.settings.pages.AutoBackupActions
import cx.aswin.boxlore.feature.settings.pages.AutoBackupPage
import cx.aswin.boxlore.feature.settings.pages.AutoBackupUiState
import cx.aswin.boxlore.feature.settings.pages.CustomThemeEditor
import cx.aswin.boxlore.feature.settings.pages.DownloadsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.LibraryBackupActions
import cx.aswin.boxlore.feature.settings.pages.LibraryDiscoveryPreferences
import cx.aswin.boxlore.feature.settings.pages.LibrarySettingsPage
import cx.aswin.boxlore.feature.settings.pages.PlaybackSettingsPage
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsActions
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsPage
import cx.aswin.boxlore.feature.settings.pages.RecommendationsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.SettingsHub
import cx.aswin.boxlore.feature.settings.pages.SignedInAuthRepository
import cx.aswin.boxlore.feature.settings.pages.SupportDevelopmentPage
import cx.aswin.boxlore.feature.settings.pages.SyncAndBackupsPage
import cx.aswin.boxlore.feature.settings.pages.WearOsSettings
import cx.aswin.boxlore.feature.settings.pages.WearOsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.supportWorldPageStyle
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.supportWorldSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import retrofit2.Response

/**
 * Local offline page review; never writes phone baselines or submits feedback.
 * `-PtabletCaptures=audit` renders every Settings scene at the shared audit sizes (plus 360x800 and
 * the large-text cover sizes, narrowed by `-PauditSizes`); `-PtabletCaptures=after` keeps the older
 * tablet set. Without the property every case skips, so CI and the phone goldens never run it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletSettingsCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        val mode = System.getProperty("boxlore.tabletCaptures")
        assumeTrue(mode == "after" || mode == "audit")
        val prefs = capturePreferences(scene.startsWith("smart-enabled") || scene == "smart-off-confirm" || scene == "automatic-enabled")
        fixture.prepare()
        val popup = scene in POPUP_SCENES
        fixture.setContent(foldSafeViewport = true) {
            CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                    Scene(prefs)
                }
            }
        }
        settle()
        interact()
        sendInsetsToDialogs()
        check(fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()) { "settings-$scene drew nothing" }
        fixture.capture("settings-$scene", allWindows = popup)
    }

    @Composable
    @Suppress("CyclomaticComplexMethod") // One branch per scene.
    private fun Scene(prefs: UserPreferencesRepository) {
        when {
            scene.startsWith("automatic") -> AutoDownloadSettingsScreen(prefs, {})
            scene.startsWith("smart") -> SmartDownloadsSettingsScreen(prefs, {})
            scene == "feedback" || scene == "feedback-more-menu" -> FeedbackScreen(
                SupportRepository(mock(SupportApi::class.java), { _, _, _, _, _ -> false }),
                mock(BoxcastPrefs::class.java),
                {},
            )
            scene == "feedback-list-loading" -> FeedbackScreen(loadingSupport(conversationLoads = false), boxcastPrefs(), {})
            scene == "feedback-thread-loading" -> FeedbackScreen(loadingSupport(conversationLoads = true), boxcastPrefs(), {}, openThreadId = THREAD_ID)
            scene.startsWith("feedback-typing") -> TypingFeedback()
            scene == "logs-dialog" -> LogsPreviewDialog(CAPTURE_LOGS, isLoading = false, diagnosticInfo = null, onShare = {}, onDismissRequest = {})
            scene == "delete-account-dialog" -> AccountSettingsDialogs(
                showDeleteConfirmation = true,
                showReauthRequiredDialog = false,
                onDismissDeleteConfirmation = {},
                onConfirmDelete = {},
                onDismissReauthRequired = {},
                onSignOutToReauth = {},
            )
            scene.startsWith("support-") -> SupportDevelopmentPage({}, 42L, style = supportWorldPageStyle(requireNotNull(supportWorldSkin(world(scene)))))
            else -> {
                val destination = captureDestination(scene)
                AdaptiveSettingsLayout(destination, SettingsScreenConfig({}, {}, onCheckForUpdates = {}), {}) { current -> CaptureSettingsPage(current, scene) }
            }
        }
    }

    @Composable
    private fun TypingFeedback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val boxcastPrefs = boxcastPrefs()
        val viewModel = androidx.compose.runtime.remember {
            FeedbackViewModel(
                FeedbackSupport(MutableStateFlow(SupportMeta.Offline), {}, { _, _ -> PhotoUpload.Unavailable }, { SendResult.Sent("t1") }),
                boxcastPrefs,
                context,
                ioDispatcher = Dispatchers.Unconfined,
            ).also { it.onMessageChanged(TYPED_MESSAGE) }
        }
        val state by viewModel.uiState.collectAsState()
        FeedbackContent(
            state = state,
            actions = FeedbackComposeActions(
                onBack = {},
                onCategorySelected = viewModel::onCategorySelected,
                onMessageChanged = viewModel::onMessageChanged,
                onAttachDiagnosticsChanged = viewModel::onAttachDiagnosticsChanged,
                onPhotosPicked = viewModel::onPhotosPicked,
                onRemovePhoto = viewModel::onRemovePhoto,
                onSubmit = {},
                onPreviewDiagnostics = {},
                onDiscardDraft = {},
            ),
            boxcastPrefs = boxcastPrefs,
            bottomContentPadding = 0.dp,
            imeBottom = (fixture.configuration.height * KEYBOARD_SHARE).dp,
        )
    }

    /** Opens what the scene shows beyond its page: a scrolled position, a menu or the typing layout. */
    private fun interact() {
        when (scene) {
            "hub-end" -> scrollTo(hasText(string(R.string.settings_check_for_updates)))
            // Turning off asks first; the question is the subject (before it existed, the plain page).
            "smart-off-confirm" -> tap(fixture.compose.onAllNodes(hasText(string(R.string.settings_smart_downloads_enable))).onFirst())
            "sync-import" -> scrollTo(hasText(string(R.string.settings_sync_import_title)))
            "feedback-more-menu" -> tap(fixture.compose.onAllNodes(hasContentDescription(string(R.string.feedback_more_options))).onFirst())
            "feedback-typing", "feedback-typing-menu" -> {
                tap(fixture.compose.onNode(hasTestTag(FEEDBACK_MESSAGE_TAG)))
                if (scene == "feedback-typing-menu") {
                    val prefix = fixture.compose.activity.getString(R.string.feedback_type_change_description, "").substringBefore(':')
                    tap(fixture.compose.onAllNodes(hasContentDescription(prefix, substring = true)).onFirst())
                }
            }
        }
    }

    /** Scrolling animates, so the clock runs while it does; the page has no endless motion to wait for. */
    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.onAllNodes(matcher).onFirst().performScrollTo()
        fixture.compose.waitForIdle()
        fixture.compose.mainClock.autoAdvance = false
        settle()
    }

    /** Clicks through semantics (popups open on the paused clock) and lets the result draw. */
    private fun tap(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Dialogs and menus open in their own windows, which the capture's fake system bars don't reach. */
    private fun sendInsetsToDialogs() {
        val insets = fixture.configuration.systemInsets ?: return
        fixture.compose.runOnUiThread {
            ShadowDialog.getShownDialogs().forEach { dialog ->
                dialog.window?.decorView?.let { ViewCompat.dispatchApplyWindowInsets(it, insets.toWindowInsets()) }
            }
        }
        settle()
    }

    private fun settle() {
        repeat(6) {
            Snapshot.sendApplyNotifications()
            fixture.compose.mainClock.advanceTimeBy(100)
            fixture.compose.waitForIdle()
        }
    }

    private fun string(id: Int): String = fixture.compose.activity.getString(id)

    companion object {
        private const val KEYBOARD_SHARE = 0.4f
        private const val THREAD_ID = "a1b2c3d4e5f6a1b2c3d4e5f6"
        private const val TYPED_MESSAGE = "The sleep timer stops one episode early when the next episode is already downloaded."

        /** Scenes whose subject is a dialog or menu in its own window. */
        private val POPUP_SCENES = setOf("logs-dialog", "delete-account-dialog", "feedback-more-menu", "feedback-typing-menu", "smart-off-confirm")

        private val AUDIT_SCENES = listOf(
            "hub", "sync", "sync-signed-in", "account", "account-signed-in", "library", "recommendations",
            "appearance", "appearance-home", "navigation", "appearance-text", "widgets", "custom-theme",
            "playback", "wear-os", "downloads", "automatic", "smart", "smart-enabled", "privacy", "about", "support",
            "feedback", "feedback-typing", "feedback-list-loading", "feedback-thread-loading",
        )

        /** Scrolled pages, dialogs, menus and the Support worlds: the sizes where they change most. */
        private val STATE_SCENES = listOf(
            "hub-end", "sync-import", "logs-dialog", "delete-account-dialog", "feedback-more-menu", "feedback-typing-menu", "smart-off-confirm",
            "support-neon", "support-orbit", "support-alchemy", "support-abyss", "support-sky",
        )
        private val STATE_SIZES = setOf(
            "360x800", "412x915", "443x649", "915x412-insets", "475x695-de", "932x650-book", "932x650-tabletop", "1280x800", "443x649-fs170",
        )

        /** Large text on the cover screen and a small phone, for the hub and Support. */
        private val LARGE_TEXT_SCENES = listOf("hub", "support")
        private val LARGE_TEXT_SIZES = setOf("443x649-fs170", "443x649-fs200", "360x800-fs150")

        /** Solid panels and tips in dark. */
        private val DARK_SCENES = listOf("playback", "widgets", "wear-os", "privacy", "sync-signed-in", "delete-account-dialog")
        private val DARK_SIZES = setOf("412x915", "1280x800")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> {
            if (System.getProperty("boxlore.tabletCaptures") == "audit") {
                val sizes = PhoneGoldenConfiguration.auditConfigurations(
                    extra = listOf(
                        PhoneGoldenConfiguration(360, 800, false),
                        PhoneGoldenConfiguration(443, 649, false, fontScale = 1.7f),
                        PhoneGoldenConfiguration(443, 649, false, fontScale = 2f),
                        PhoneGoldenConfiguration(360, 800, false, fontScale = 1.5f),
                        PhoneGoldenConfiguration(412, 915, true),
                        PhoneGoldenConfiguration(1_280, 800, true),
                    ),
                )
                val base = sizes.filter { !it.dark && it.auditKey !in LARGE_TEXT_SIZES }
                return AUDIT_SCENES.flatMap { scene -> base.map { arrayOf<Any>(scene, it) } } +
                    STATE_SCENES.flatMap { scene -> sizes.filter { !it.dark && it.auditKey in STATE_SIZES }.map { arrayOf<Any>(scene, it) } } +
                    LARGE_TEXT_SCENES.flatMap { scene -> sizes.filter { it.auditKey in LARGE_TEXT_SIZES && it.auditKey !in STATE_SIZES }.map { arrayOf<Any>(scene, it) } } +
                    DARK_SCENES.flatMap { scene -> sizes.filter { it.dark && it.auditKey in DARK_SIZES }.map { arrayOf<Any>(scene, it) } }
            }
            return AFTER_SCENES.flatMap { scene ->
                listOf(1280 to 800, 840 to 1280).flatMap { (width, height) ->
                    listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) }
                }
            }
        }

        private val AFTER_SCENES = listOf(
            "sync", "library", "appearance", "appearance-home", "custom-theme", "playback", "downloads", "privacy", "about", "account", "support",
            "automatic", "automatic-enabled", "smart", "smart-enabled", "feedback",
        )
    }
}

private const val CAPTURE_LOGS = "11-24 09:12:01.204 I/boxlore: Playback started\n11-24 09:12:03.880 W/boxlore: Feed refresh took 2.4 s\n11-24 09:12:05.117 I/boxlore: Sleep timer set for the end of the episode"

private fun world(scene: String): SupportWorld = SupportWorld.entries.first { it.variantKey == scene.removePrefix("support-") }

private fun boxcastPrefs(): BoxcastPrefs {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    return BoxcastPrefs(context)
}

/** Support answers that never arrive, so the list (or the open conversation) stays loading. */
private fun loadingSupport(conversationLoads: Boolean): SupportRepository = SupportRepository(
    api = LoadingSupportApi(conversationLoads),
    legacySubmit = { _, _, _, _, _ -> true },
    ioDispatcher = Dispatchers.Unconfined,
    local = SupportLocalStore.InMemory().apply { hasConversations = true },
)

private class LoadingSupportApi(private val conversationLoads: Boolean) : SupportApi {
    private val thread = SupportThreadDto(
        "a1b2c3d4e5f6a1b2c3d4e5f6",
        "bug",
        "Bug report",
        "Sleep timer stops early",
        SupportStatusDto(key = "replied", label = "Replied", showTime = true),
        1,
        false,
        true,
    )

    override suspend fun threads(): Response<SupportListResponse> =
        if (conversationLoads) Response.success(SupportListResponse(meta = SupportMetaDto(), threads = listOf(thread))) else awaitCancellation()
    override suspend fun startThread(request: SupportNewThreadRequest): Response<SupportThreadResponse> = awaitCancellation()
    override suspend fun uploadPhoto(photo: RequestBody): Response<SupportPhotoResponse> = awaitCancellation()
    override suspend fun conversation(id: String, before: String?): Response<SupportConversationResponse> = awaitCancellation()
    override suspend fun reply(id: String, request: SupportReplyRequest): Response<SupportReplyResponse> = awaitCancellation()
    override suspend fun markRead(id: String, request: SupportReadRequest): Response<Unit> = awaitCancellation()
    override suspend fun solve(id: String): Response<SupportConversationResponse> = awaitCancellation()
    override suspend fun photo(id: String): Response<ResponseBody> = awaitCancellation()
}

@Suppress("CyclomaticComplexMethod") // One branch per scene.
private fun captureDestination(scene: String): ProfileSettingsDestination = when (scene) {
    "hub", "hub-end" -> ProfileSettingsDestination.Hub
    "sync", "sync-signed-in", "sync-import" -> ProfileSettingsDestination.SyncAndBackups
    "library" -> ProfileSettingsDestination.Library
    "recommendations" -> ProfileSettingsDestination.Recommendations
    "appearance" -> ProfileSettingsDestination.Appearance
    "appearance-home" -> ProfileSettingsDestination.AppearanceHome
    "navigation" -> ProfileSettingsDestination.AppearanceNavigation
    "appearance-text" -> ProfileSettingsDestination.AppearanceText
    "widgets" -> ProfileSettingsDestination.AppearanceWidgets
    "custom-theme" -> ProfileSettingsDestination.CustomTheme
    "playback" -> ProfileSettingsDestination.Playback
    "wear-os" -> ProfileSettingsDestination.WearOs
    "downloads" -> ProfileSettingsDestination.Downloads
    "privacy" -> ProfileSettingsDestination.Privacy
    "about" -> ProfileSettingsDestination.About
    "account", "account-signed-in" -> ProfileSettingsDestination.Account
    "support" -> ProfileSettingsDestination.Support
    else -> error(scene)
}

@Composable
@Suppress("CyclomaticComplexMethod") // One branch per page.
private fun CaptureSettingsPage(destination: ProfileSettingsDestination, scene: String) {
    val appearance = AppearanceSettings()
    val playback = PlaybackSettings()
    when (destination) {
        ProfileSettingsDestination.Hub -> SettingsHub(onBack = {}, onNavigate = {}, onCheckForUpdates = {})
        ProfileSettingsDestination.SyncAndBackups -> SyncAndBackupsPage(
            if (scene == "sync-signed-in") CAPTURE_EMAIL else null,
            if (scene == "sync-signed-in") CloudSyncUiStatus.Success(1_765_789_500_000L) else CloudSyncUiStatus.Idle,
            LibraryBackupActions({}, {}, {}, {}),
            {},
            {},
            AutoBackupPage(
                AutoBackupUiState(AutoBackupSettings(enabled = true, folderUri = "content://tree/sync", folderName = "Sync", lastSuccessAt = 1_765_789_500_000L)),
                AutoBackupActions({}, {}, {}, {}, {}),
            ),
        )
        ProfileSettingsDestination.Library -> LibrarySettingsPage(LibraryDiscoveryPreferences("us", listOf("en"), {}, {}), {}, {})
        ProfileSettingsDestination.Recommendations -> RecommendationsSettingsPage(DiscoverySections(), { _, _ -> }, {})
        ProfileSettingsDestination.Appearance, ProfileSettingsDestination.Theme -> AppearanceSettingsPage(appearance.state, appearance.actions, {}, {})
        ProfileSettingsDestination.AppearanceHome,
        ProfileSettingsDestination.AppearanceShowPages,
        ProfileSettingsDestination.AppearancePlayer,
        ProfileSettingsDestination.AppearanceNavigation,
        ProfileSettingsDestination.AppearanceText,
        ProfileSettingsDestination.AppearanceWidgets,
        ProfileSettingsDestination.AppearanceLanguage,
        -> CaptureAppearanceSubpage(destination, appearance)
        ProfileSettingsDestination.CustomTheme -> CustomThemeEditor(appearance.state, {}, {})
        ProfileSettingsDestination.Playback -> PlaybackSettingsPage(playback.state, playback.actions, {})
        ProfileSettingsDestination.Downloads, ProfileSettingsDestination.WearOs -> CaptureDownloadsOrWearOs(destination)
        ProfileSettingsDestination.Privacy -> PrivacySettingsPage("test-deletion-id", false, PrivacySettingsActions({}, {}, {}, {}, {}), {})
        ProfileSettingsDestination.About -> AboutSettingsPage(AppInfo("1.0", 1L, "cx.aswin.boxlore", "13", 33), {}, {}, onBack = {}, onCheckForUpdates = {})
        ProfileSettingsDestination.Account -> if (scene == "account-signed-in") {
            AccountSettingsPage(
                authRepository = SignedInAuthRepository(BoxLoreUser(uid = "capture", email = CAPTURE_EMAIL, displayName = null, isEmailVerified = true, providerId = "google.com")),
                onBack = {},
                syncStatus = CloudSyncUiStatus.Syncing,
            )
        } else {
            AccountSettingsPage(authRepository = null, onBack = {})
        }
        ProfileSettingsDestination.Support -> SupportDevelopmentPage({}, 42L)
    }
}

private const val CAPTURE_EMAIL = "a.listener.with.a.long.address@example.test"

@Composable
private fun CaptureDownloadsOrWearOs(destination: ProfileSettingsDestination) {
    if (destination == ProfileSettingsDestination.WearOs) WearOsSettingsPage(WearOsSettings(), {}) else DownloadsSettingsPage({}, {}, {})
}

@Composable
private fun CaptureAppearanceSubpage(destination: ProfileSettingsDestination, appearance: AppearanceSettings) {
    when (destination) {
        ProfileSettingsDestination.AppearanceHome -> AppearanceHomePage(appearance.state, appearance.actions, null, {})
        ProfileSettingsDestination.AppearanceShowPages -> AppearanceShowPagesPage(appearance.state, appearance.actions, {})
        ProfileSettingsDestination.AppearancePlayer -> AppearancePlayerPage(appearance.state, appearance.actions, {})
        ProfileSettingsDestination.AppearanceNavigation -> AppearanceNavigationPage(appearance.state, appearance.actions, {})
        ProfileSettingsDestination.AppearanceWidgets -> AppearanceWidgetsPage(appearance.state, appearance.actions, {})
        ProfileSettingsDestination.AppearanceLanguage -> AppearanceLanguagePage(appearance.state, appearance.actions, {})
        else -> AppearanceTextPage(appearance.state, appearance.actions, {})
    }
}

private fun capturePreferences(enabled: Boolean): UserPreferencesRepository = mock(UserPreferencesRepository::class.java).also { prefs ->
    `when`(prefs.autoDownloadWifiOnlyStream).thenReturn(flowOf(true))
    `when`(prefs.autoDownloadMaxEpisodesStream).thenReturn(flowOf(2))
    `when`(prefs.autoDownloadDeleteCompletedStream).thenReturn(flowOf(true))
    `when`(prefs.autoDownloadBackgroundSettingsStream).thenReturn(flowOf(AutoDownloadBackgroundSettings(enabled = enabled)))
    `when`(prefs.smartDownloadsEnabledStream).thenReturn(flowOf(enabled))
    `when`(prefs.smartDownloadsMaxEpisodesStream).thenReturn(flowOf(10))
    `when`(prefs.smartDownloadsStorageBudgetStream).thenReturn(flowOf(1000L))
    `when`(prefs.smartDownloadsWifiOnlyStream).thenReturn(flowOf(true))
    `when`(prefs.smartDownloadsChargingOnlyStream).thenReturn(flowOf(false))
    `when`(prefs.smartDownloadsCleanupRuleStream).thenReturn(flowOf("after_24h"))
}
