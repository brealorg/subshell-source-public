package app.subshell.prototype.update

import app.subshell.prototype.data.network.SubshellNetworkJson
import java.io.File
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled changelog asset is the source of the public update manifest
 * (RELEASETOOL02B copies the top release's headline and highlights into
 * `subshell-update-beta.json`, fail-closed on the UPDATE01 limits). Beta.5
 * reached Stage B with a 189-character highlight and could not be promoted;
 * this test fails the unit suite, and therefore delta qualification, before
 * an APK with such an entry can be signed.
 */
class BundledChangelogPromotionPolicyTest {
    private val asset = File(requireNotNull(System.getProperty("user.dir")))
        .let { appDir -> appDir.parentFile ?: appDir }
        .resolve("app/src/main/assets/changelog/releases.json")

    private val document: UpdateChangelogDocument by lazy {
        SubshellNetworkJson.providerJson.decodeFromString<UpdateChangelogDocument>(asset.readText())
    }

    @Test
    fun topReleaseSurvivesUpdateManifestNormalizationUnchanged() {
        val top = document.releases.first()
        val manifest = normalizeUpdateManifest(
            UpdateManifestRelease(
                schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
                channel = UPDATE01_BETA_CHANNEL,
                versionCode = top.versionCode,
                versionName = top.versionName,
                publishedAt = "2026-01-01T00:00:00Z",
                releaseUrl = requireNotNull(top.releaseUrl),
                apkSha256 = "0".repeat(64),
                headline = top.headline,
                highlights = top.highlights,
            ),
        )
        assertNotNull("top release must be a valid update manifest", manifest)
        assertEquals("headline must not be altered", top.headline, manifest?.headline)
        assertEquals("no highlight may be dropped or trimmed", top.highlights, manifest?.highlights)
    }

    @Test
    fun everyReleaseFitsTheManifestLimits() {
        for (release in document.releases) {
            val id = "${release.versionName} (${release.versionCode})"
            assertTrue("$id headline too long", release.headline.trim().length <= UPDATE01_MAX_HEADLINE_CHARS)
            assertTrue("$id too many highlights", release.highlights.size <= UPDATE01_MAX_HIGHLIGHTS)
            for (highlight in release.highlights) {
                assertTrue(
                    "$id highlight exceeds $UPDATE01_MAX_HIGHLIGHT_CHARS chars: ${highlight.take(40)}…",
                    highlight.trim().length <= UPDATE01_MAX_HIGHLIGHT_CHARS,
                )
                assertEquals("$id highlight has surrounding whitespace", highlight, highlight.trim())
            }
        }
    }

    // RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: an installed
    // 0.78.0-beta.1 or older reads the manifest with its own 6 x 180 rule; the
    // update it is offered must keep its headline and first six highlights.
    @Test
    fun topReleaseReadsTheSameForAnOlderClient() {
        val top = document.releases.first()
        val olderClientHighlights = top.highlights
            .map(String::trim)
            .filter { it.isNotEmpty() && it.length <= UPDATE01_LEGACY_CLIENT_HIGHLIGHT_CHARS }
            .take(UPDATE01_LEGACY_CLIENT_HIGHLIGHTS)
        assertTrue("headline over the older clients' limit", top.headline.trim().length <= UPDATE01_MAX_HEADLINE_CHARS)
        assertEquals(
            "an older client must see the first six highlights unchanged",
            top.highlights.take(UPDATE01_LEGACY_CLIENT_HIGHLIGHTS),
            olderClientHighlights,
        )
    }

    @Test
    fun releasesAreNewestFirstWithMatchingTags() {
        val codes = document.releases.map(UpdateChangelogRelease::versionCode)
        assertEquals("version codes must be strictly descending", codes.sortedDescending().distinct(), codes)
        for (release in document.releases) {
            assertEquals(
                "releaseUrl tag must match versionName for ${release.versionName}",
                "https://github.com/brealorg/subshell/releases/tag/v${release.versionName}",
                release.releaseUrl,
            )
        }
    }
}
