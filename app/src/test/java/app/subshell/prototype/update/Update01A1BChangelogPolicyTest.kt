package app.subshell.prototype.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Update01A1BChangelogPolicyTest {
    @Test
    fun bundledChangelogNormalizesNewestFirstAndBoundsHighlights() {
        val normalized = normalizeUpdateChangelog(
            UpdateChangelogDocument(
                schemaVersion = 1,
                releases = listOf(
                    release(235, "0.77.0-beta.1"),
                    release(
                        236,
                        "0.77.0-beta.2",
                        highlights = List(20) { index -> "Item $index" },
                    ),
                ),
            ),
        )

        assertEquals(listOf(236, 235), normalized?.releases?.map { it.versionCode })
        assertEquals(
            UPDATE01_MAX_CHANGELOG_HIGHLIGHTS,
            normalized?.releases?.first()?.highlights?.size,
        )
    }

    @Test
    fun unsupportedSchemaAndUntrustedReleaseUrlFailClosed() {
        assertNull(
            normalizeUpdateChangelog(
                UpdateChangelogDocument(
                    schemaVersion = 2,
                    releases = emptyList(),
                ),
            ),
        )

        val normalized = normalizeUpdateChangelog(
            UpdateChangelogDocument(
                schemaVersion = 1,
                releases = listOf(
                    release(
                        236,
                        "0.77.0-beta.2",
                        releaseUrl = "https://example.com/release",
                    ),
                ),
            ),
        )

        assertTrue(normalized?.releases?.isEmpty() == true)
    }

    @Test
    fun autoUpdatePresentationIsOneShotPerRemoteVersion() {
        val remote = UpdateManifestRelease(
            schemaVersion = 1,
            channel = "beta",
            versionCode = 237,
            versionName = "0.78.0-beta.1",
            publishedAt = "2026-09-20T18:00:00Z",
            releaseUrl =
                "https://github.com/brealorg/subshell/releases/tag/v0.78.0-beta.1",
            apkSha256 = "a".repeat(64),
            headline = "Update",
        )

        assertTrue(
            shouldAutoPresentUpdate(
                remote,
                currentVersionCode = 236,
                lastPresentedVersionCode = 0,
            ),
        )
        assertFalse(
            shouldAutoPresentUpdate(
                remote,
                currentVersionCode = 236,
                lastPresentedVersionCode = 237,
            ),
        )
        assertFalse(
            shouldAutoPresentUpdate(
                remote,
                currentVersionCode = 237,
                lastPresentedVersionCode = 0,
            ),
        )
    }

    private fun release(
        versionCode: Int,
        versionName: String,
        highlights: List<String> = listOf("One"),
        releaseUrl: String =
            "https://github.com/brealorg/subshell/releases/tag/v$versionName",
    ) = UpdateChangelogRelease(
        versionCode = versionCode,
        versionName = versionName,
        date = "2026-09-06",
        headline = "Release",
        highlights = highlights,
        releaseUrl = releaseUrl,
    )
}
