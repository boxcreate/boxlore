package cx.aswin.boxlore.analytics

import cx.aswin.boxlore.util.InstallOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHogWiringTest {
    @Test
    fun releaseBuildsAreTaggedAsProduction() {
        assertEquals(
            mapOf("is_internal" to false, "app_environment" to "production"),
            PostHogWiring.environment(debug = false, isolatedTests = false),
        )
    }

    @Test
    fun debugBuildsAreInternal() {
        assertEquals(
            mapOf("is_internal" to true, "app_environment" to "debug"),
            PostHogWiring.environment(debug = true, isolatedTests = false),
        )
    }

    @Test
    fun isolatedTestBuildsAreInternalUpdateTests() {
        val expected = mapOf("is_internal" to true, "app_environment" to "update_test")
        assertEquals(expected, PostHogWiring.environment(debug = false, isolatedTests = true))
        assertEquals(expected, PostHogWiring.environment(debug = true, isolatedTests = true))
    }

    @Test
    fun flagsLoadedRunsEveryStepInOrder() {
        val calls = mutableListOf<String>()
        val callback = PostHogWiring.onFlagsLoaded({ calls += "rollout" }, { calls += "analytics" })

        callback.loaded()
        callback.loaded()

        assertEquals(listOf("rollout", "analytics", "rollout", "analytics"), calls)
    }

    @Test
    fun aFailingStepDoesNotSkipTheNextAndIsRethrownAfter() {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("rollout")
        val second = IllegalArgumentException("analytics")
        val callback =
            PostHogWiring.onFlagsLoaded(
                {
                    calls += "rollout"
                    throw first
                },
                {
                    calls += "analytics"
                    throw second
                },
                { calls += "last" },
            )

        val thrown = assertThrows(IllegalStateException::class.java) { callback.loaded() }

        assertEquals(listOf("rollout", "analytics", "last"), calls)
        assertSame(first, thrown)
        assertEquals(listOf<Throwable>(second), thrown.suppressed.toList())
    }

    @Test
    fun aPlayInstallOfThePlayBuildIsTaggedPlay() {
        assertEquals(
            mapOf("build_channel" to "play", "install_source" to "play_store", "installer_package" to "com.android.vending"),
            PostHogWiring.installTags(directUpdatesBuild = false, origin = InstallOrigin("com.android.vending", "com.android.vending")),
        )
    }

    @Test
    fun aDownloadedApkOpenedByHandIsTheGitHubBuildFromThePackageInstaller() {
        // Also what boxlore's own updater records: it hands the file to Android's installer.
        val origin = InstallOrigin("com.google.android.packageinstaller", "com.google.android.packageinstaller")
        assertEquals(
            mapOf("build_channel" to "github", "install_source" to "package_installer", "installer_package" to "com.google.android.packageinstaller"),
            PostHogWiring.installTags(directUpdatesBuild = true, origin = origin),
        )
    }

    @Test
    fun everyVendorPackageInstallerCountsAsThePackageInstaller() {
        listOf("com.android.packageinstaller", "com.miui.packageinstaller", "com.samsung.android.packageinstaller").forEach { installer ->
            assertEquals(installer, "package_installer", PostHogWiring.installSource(InstallOrigin(installer, installer)))
        }
    }

    @Test
    fun storesAreNamed() {
        mapOf(
            "dev.imranr.obtainium" to "obtainium",
            "dev.imranr.obtainium.fdroid" to "obtainium",
            "com.aurora.store" to "aurora_store",
            "com.sec.android.app.samsungapps" to "galaxy_store",
            "org.fdroid.fdroid" to "fdroid",
            "com.looker.droidify" to "fdroid",
            "cx.aswin.boxlore" to "boxlore_updater",
        ).forEach { (installer, source) ->
            assertEquals(installer, source, PostHogWiring.installSource(InstallOrigin(installer, null)))
        }
    }

    @Test
    fun anUnlistedInstallerIsAnotherStoreAndKeepsItsPackage() {
        val tags = PostHogWiring.installTags(directUpdatesBuild = true, origin = InstallOrigin("com.xiaomi.mipicks", null))
        assertEquals("other_store", tags["install_source"])
        assertEquals("com.xiaomi.mipicks", tags["installer_package"])
    }

    @Test
    fun noInstallerIsAComputerInstallOnlyWhenTheShellAskedForIt() {
        assertEquals("adb", PostHogWiring.installSource(InstallOrigin(null, "com.android.shell")))
        assertEquals("unknown", PostHogWiring.installSource(InstallOrigin(null, null)))
        assertEquals("none", PostHogWiring.installTags(directUpdatesBuild = true, origin = InstallOrigin(null, null))["installer_package"])
    }

    @Test
    fun thePlayBuildInstalledByAnotherStoreKeepsBothFacts() {
        // Aurora Store installs the Play bundle: the build says play, the installer says otherwise.
        val tags = PostHogWiring.installTags(directUpdatesBuild = false, origin = InstallOrigin("com.aurora.store", null))
        assertEquals("play", tags["build_channel"])
        assertEquals("aurora_store", tags["install_source"])
    }

    @Test
    fun aPlayInstallKeepsPlaysReferrer() {
        assertEquals(
            "share" to "type=episode&id=1",
            PostHogWiring.installAttribution(InstallOrigin("com.android.vending", null), channel = "share", referrerRaw = "type=episode&id=1"),
        )
    }

    @Test
    fun anyOtherInstallIsNotPlayAndDropsTheReferrer() {
        // Play answers "google-play organic" for a GitHub install too.
        val origin = InstallOrigin("com.google.android.packageinstaller", "com.google.android.packageinstaller")
        assertEquals(
            "not_play" to null,
            PostHogWiring.installAttribution(origin, channel = "organic", referrerRaw = "utm_source=google-play&utm_medium=organic"),
        )
    }

    @Test
    fun whenAndroidCantSayWhoInstalledItNothingIsSaved() {
        assertNull(PostHogWiring.installAttribution(origin = null, channel = "organic", referrerRaw = "utm_source=google-play&utm_medium=organic"))
        assertFalse(PostHogWiring.isConfirmedNonPlay(null))
        assertEquals(
            mapOf("build_channel" to "github", "install_source" to "unknown", "installer_package" to "none"),
            PostHogWiring.installTags(directUpdatesBuild = true, origin = null),
        )
    }

    @Test
    fun onlyAConfirmedNonPlayInstallIsSavedAsNotPlayAtStartUp() {
        assertTrue(PostHogWiring.isConfirmedNonPlay(InstallOrigin("dev.imranr.obtainium", null)))
        assertTrue(PostHogWiring.isConfirmedNonPlay(InstallOrigin(null, "com.android.shell")))
        assertFalse(PostHogWiring.isConfirmedNonPlay(InstallOrigin("com.android.vending", "com.android.vending")))
    }
}
