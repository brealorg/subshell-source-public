package app.subshell.prototype.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SECAUDIT05 (security audit 2026-10-22, #5): an advisory is shown first, in
 * the error colour, so its text must not send readers anywhere but the
 * release's own page. Text that looks like a link or a domain, or says to
 * uninstall or reinstall, drops the advisory; the release stays.
 */
class SecAudit05AdvisoryTextTest {
    @Test
    fun anAdvisoryThatPointsElsewhereIsDropped() {
        listOf(
            "Get the fixed build at https://example.invalid",
            "See www.example for the fix.",
            "Install the fix from subshell-fix.app today.",
            "Uninstall Subshell, then install 0.85.",
            "Please REINSTALL the app.",
        ).forEach { message ->
            assertNull(message, normalizeUpdateAdvisory(UpdateAdvisory(listOf(245), message), releaseVersionCode = 246))
        }
    }

    @Test
    fun anOrdinaryAdvisoryIsKept() {
        listOf(
            "0.81 can lose a draft.",
            "0.84.1 stops videos after 30 s. Update to 0.85.",
            "Drafts, e.g. replies, may be lost.",
        ).forEach { message ->
            assertEquals(message, normalizeUpdateAdvisory(UpdateAdvisory(listOf(245), message), releaseVersionCode = 246)?.message)
        }
    }

    @Test
    fun theReleaseStaysWhenItsAdvisoryIsDropped() {
        val release = UpdateManifestRelease(
            schemaVersion = 1,
            channel = "beta",
            versionCode = 246,
            versionName = "0.82-beta",
            publishedAt = "2026-10-16T10:00:00Z",
            releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v0.82-beta",
            apkSha256 = "a".repeat(64),
            headline = "A Subshell beta update",
            advisory = UpdateAdvisory(listOf(245), "Uninstall first and get it from example.com"),
        )
        val normalized = normalizeUpdateManifest(release)
        assertNotNull(normalized)
        assertNull(normalized?.advisory)
    }
}
