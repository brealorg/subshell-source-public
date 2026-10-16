package app.subshell.prototype.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Update01A1PolicyTest {
    @Test
    fun runtimeIsReleasePackageOnlyAndNeverDebug() {
        assertTrue(
            UpdateRuntimePolicy.enabled(
                applicationId = "io.github.brealorg.subshell",
                debug = false,
            ),
        )
        assertFalse(
            UpdateRuntimePolicy.enabled(
                applicationId = "io.github.brealorg.subshell.debug.dev1",
                debug = false,
            ),
        )
        assertFalse(
            UpdateRuntimePolicy.enabled(
                applicationId = "io.github.brealorg.subshell",
                debug = true,
            ),
        )
    }

    @Test
    fun installTransitionDoesNotTreatFreshInstallAsUpgrade() {
        assertEquals(
            InstalledVersionTransition.FRESH_INSTALL,
            installedVersionTransition(0, 237),
        )
        assertEquals(
            InstalledVersionTransition.UPGRADE,
            installedVersionTransition(236, 237),
        )
        assertEquals(
            InstalledVersionTransition.SAME_OR_DOWNGRADE,
            installedVersionTransition(237, 237),
        )
        assertEquals(
            InstalledVersionTransition.SAME_OR_DOWNGRADE,
            installedVersionTransition(238, 237),
        )
    }

    @Test
    fun manifestIsStrictlyBoundedAndGithubReleaseOnly() {
        val valid = release(versionCode = 237)
        val normalized = normalizeUpdateManifest(valid)
        assertEquals(237, normalized?.versionCode)
        assertEquals("beta", normalized?.channel)

        assertNull(
            normalizeUpdateManifest(
                valid.copy(releaseUrl = "http://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1"),
            ),
        )
        assertNull(
            normalizeUpdateManifest(
                valid.copy(releaseUrl = "https://example.com/releases/tag/v0.78.0-beta.1"),
            ),
        )
        assertNull(normalizeUpdateManifest(valid.copy(channel = "stable")))
        assertNull(normalizeUpdateManifest(valid.copy(schemaVersion = 2)))
        assertNull(normalizeUpdateManifest(valid.copy(apkSha256 = "bad")))
    }

    @Test
    fun newerVersionNotifiesOncePerVersionCode() {
        val remote = release(versionCode = 237)
        assertTrue(shouldNotifyUpdate(remote, currentVersionCode = 236, lastNotifiedVersionCode = 0))
        assertFalse(shouldNotifyUpdate(remote, currentVersionCode = 237, lastNotifiedVersionCode = 0))
        assertFalse(shouldNotifyUpdate(remote, currentVersionCode = 236, lastNotifiedVersionCode = 237))
    }

    @Test
    fun excessHighlightsAreBounded() {
        val normalized = normalizeUpdateManifest(
            release(
                versionCode = 237,
                highlights = List(20) { index -> "Highlight $index" },
            ),
        )
        assertEquals(UPDATE01_MAX_HIGHLIGHTS, normalized?.highlights?.size)
    }

    private fun release(
        versionCode: Int,
        highlights: List<String> = listOf("One", "Two"),
    ) = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = versionCode,
        versionName = "0.78.0-beta.1",
        publishedAt = "2026-09-20T18:00:00Z",
        releaseUrl =
            "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1",
        apkSha256 = "a".repeat(64),
        headline = "A Subshell beta update",
        highlights = highlights,
    )
}
