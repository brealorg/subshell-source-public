package app.subshell.prototype.update

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val UPDATE01_MANIFEST_SCHEMA_VERSION = 1
internal const val UPDATE01_BETA_CHANNEL = "beta"
internal const val UPDATE01_PRODUCTION_APPLICATION_ID = "io.github.brealorg.subshell"
internal const val UPDATE01_MANIFEST_URL =
    "https://brealorg.github.io/subshell-update-beta.json"

internal const val UPDATE04_GITHUB_RELEASES_URL =
    "https://github.com/brealorg/subshell/releases"

internal const val UPDATE01_MAX_MANIFEST_BYTES = 64 * 1024
internal const val UPDATE01_MAX_HEADLINE_CHARS = 160

// RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: from 0.79 a
// release may carry 12 highlights of 240 characters. A client before it
// (0.78.0-beta.1 and older) drops a highlight over 180 characters and shows
// the first six, so the release tooling keeps those six within 180; the
// headline stays at 160 because an older client rejects the whole release.
internal const val UPDATE01_MAX_HIGHLIGHT_CHARS = 240
internal const val UPDATE01_MAX_HIGHLIGHTS = 12
internal const val UPDATE01_LEGACY_CLIENT_HIGHLIGHT_CHARS = 180
internal const val UPDATE01_LEGACY_CLIENT_HIGHLIGHTS = 6

// UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1 (owner 2026-10-16): a release may
// carry an advisory naming older versions with a known problem. A reader on one
// of them sees the problem in the update dialog and notification, and the
// dialog comes back at most once a day until they update (a plain update is
// offered once). Optional: 0.77-0.81 ignore the field (ignoreUnknownKeys), and
// schemaVersion stays 1 so they keep the rest of the manifest.
internal const val UPDATE09_MAX_ADVISORY_CHARS = 240
internal const val UPDATE09_MAX_ADVISORY_VERSIONS = 32
internal const val UPDATE09_ADVISORY_REPEAT_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

/**
 * SECAUDIT01: how far ahead of the device clock a manifest's publishedAt may
 * be. Room for a clock that runs a little behind; a release from further in
 * the future is not one we published.
 */
internal const val SECAUDIT01_MAX_PUBLISHED_AHEAD_MILLIS = 2L * 24L * 60L * 60L * 1000L

@Serializable
internal data class UpdateAdvisory(
    val affectedVersionCodes: List<Int> = emptyList(),
    val message: String = "",
)

@Serializable
internal data class UpdateManifestRelease(
    val schemaVersion: Int,
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String,
    val releaseUrl: String,
    val apkSha256: String,
    val headline: String,
    val highlights: List<String> = emptyList(),
    val advisory: UpdateAdvisory? = null,
)

internal enum class InstalledVersionTransition {
    FRESH_INSTALL,
    SAME_OR_DOWNGRADE,
    UPGRADE,
}

internal object UpdateRuntimePolicy {
    fun enabled(
        applicationId: String,
        debug: Boolean,
    ): Boolean =
        !debug && applicationId == UPDATE01_PRODUCTION_APPLICATION_ID
}

internal fun installedVersionTransition(
    lastObservedVersionCode: Int,
    currentVersionCode: Int,
): InstalledVersionTransition = when {
    lastObservedVersionCode <= 0 ->
        InstalledVersionTransition.FRESH_INSTALL

    currentVersionCode > lastObservedVersionCode ->
        InstalledVersionTransition.UPGRADE

    else ->
        InstalledVersionTransition.SAME_OR_DOWNGRADE
}

internal fun normalizeUpdateManifest(
    raw: UpdateManifestRelease,
): UpdateManifestRelease? {
    if (raw.schemaVersion != UPDATE01_MANIFEST_SCHEMA_VERSION) return null
    if (raw.channel.trim().lowercase() != UPDATE01_BETA_CHANNEL) return null
    if (raw.versionCode <= 0) return null

    val versionName = raw.versionName.trim()
    if (!versionName.matches(Regex("[0-9A-Za-z][0-9A-Za-z._+-]{0,63}"))) return null

    val publishedAt = raw.publishedAt.trim()
    if (publishedAt.isEmpty() || publishedAt.length > 64 || '\u0000' in publishedAt) return null

    val releaseUrl = raw.releaseUrl.trim()
    val parsedRelease = releaseUrl.toHttpUrlOrNull() ?: return null
    if (!parsedRelease.isHttps) return null
    if (parsedRelease.host != "github.com") return null
    // UPDATE04_RELEASE_URL_MATCHES_VERSION_V1: the release tooling writes
    // exactly .../releases/tag/v<versionName>; anything else would open a
    // different release than the dialog names.
    if (parsedRelease.encodedPath != "/brealorg/subshell/releases/tag/v$versionName") return null
    if (parsedRelease.query != null || parsedRelease.fragment != null) return null

    val apkSha = raw.apkSha256.trim().lowercase()
    if (!apkSha.matches(Regex("[0-9a-f]{64}"))) return null

    val headline = raw.headline.trim()
    if (
        headline.isEmpty() ||
        headline.length > UPDATE01_MAX_HEADLINE_CHARS ||
        '\u0000' in headline
    ) {
        return null
    }

    val highlights = raw.highlights
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { value ->
            value.length <= UPDATE01_MAX_HIGHLIGHT_CHARS &&
                '\u0000' !in value
        }
        .take(UPDATE01_MAX_HIGHLIGHTS)
        .toList()

    return raw.copy(
        channel = UPDATE01_BETA_CHANNEL,
        versionName = versionName,
        publishedAt = publishedAt,
        // UPDATE02: store the URL exactly as it was validated.
        releaseUrl = parsedRelease.toString(),
        apkSha256 = apkSha,
        headline = headline,
        highlights = highlights,
        advisory = raw.advisory?.let { normalizeUpdateAdvisory(it, raw.versionCode) },
    )
}

/**
 * UPDATE09: a malformed advisory is dropped, never the release. It may only
 * name versions older than the release that carries it.
 */
internal fun normalizeUpdateAdvisory(
    raw: UpdateAdvisory,
    releaseVersionCode: Int,
): UpdateAdvisory? {
    val message = raw.message.trim()
    if (message.isEmpty() || message.length > UPDATE09_MAX_ADVISORY_CHARS || '\u0000' in message) {
        return null
    }
    if (advisoryMessageIsUnsafe(message)) return null
    if (raw.affectedVersionCodes.size > UPDATE09_MAX_ADVISORY_VERSIONS) return null
    val affected = raw.affectedVersionCodes
        .filter { code -> code in 1 until releaseVersionCode }
        .distinct()
    if (affected.isEmpty()) return null
    return UpdateAdvisory(affectedVersionCodes = affected, message = message)
}

/**
 * SECAUDIT05_ADVISORY_TEXT_NAMES_NO_LINK_V1 (security audit 2026-10-22, #5):
 * the advisory is shown first, in the error colour, so a forged one could
 * send readers elsewhere ("uninstall and get the fixed build from …"), and
 * uninstalling first defeats Android's same-signer upgrade. An advisory
 * that looks like a link or a domain, or says to uninstall or reinstall, is
 * dropped. The only way to the fix stays the release's own GitHub page.
 * Release Stage B refuses the same text, so a real advisory is never lost.
 */
internal fun advisoryMessageIsUnsafe(message: String): Boolean {
    val text = message.lowercase()
    return "://" in text ||
        "www." in text ||
        "uninstall" in text ||
        "reinstall" in text ||
        ADVISORY_DOMAIN_PATTERN.containsMatchIn(text)
}

// A label with a letter, a dot, then two or more letters: "example.com"
// matches, version numbers such as "0.84.1" do not.
private val ADVISORY_DOMAIN_PATTERN = Regex("[a-z0-9-]*[a-z][a-z0-9-]*\\.[a-z]{2,}")

/** UPDATE09: the advisory when it concerns the installed version and the release fixes it. */
internal fun UpdateManifestRelease.advisoryFor(currentVersionCode: Int): UpdateAdvisory? =
    advisory?.takeIf { isNewerThan(currentVersionCode) && currentVersionCode in it.affectedVersionCodes }

internal fun UpdateManifestRelease.isNewerThan(
    currentVersionCode: Int,
): Boolean = versionCode > currentVersionCode

internal fun shouldNotifyUpdate(
    release: UpdateManifestRelease,
    currentVersionCode: Int,
    lastNotifiedVersionCode: Int,
): Boolean =
    release.isNewerThan(currentVersionCode) &&
        release.versionCode > lastNotifiedVersionCode

/**
 * UPDATE03_MANIFEST_FRESHNESS_V1
 * Whether [incoming] may replace what is known ([known], the last stored
 * release). A higher or equal versionCode always may. A lower one may only
 * when it was published later: that is a correction or a withdrawn release.
 * A lower one published earlier is a stale copy (a CDN edge, a rollback, a
 * replay) and must not make a known newer release disappear. When either
 * publishedAt cannot be read, the incoming manifest wins, as before.
 * [nowEpochMillis] is the device clock (SECAUDIT01, below).
 */
internal fun updateManifestSupersedes(
    incoming: UpdateManifestRelease,
    known: UpdateManifestRelease?,
    nowEpochMillis: Long,
): Boolean {
    // SECAUDIT01_MANIFEST_FROM_THE_FUTURE_V1 (security audit 2026-10-22, #1):
    // a manifest published far ahead of now is refused, and a stored one is
    // no longer trusted to hold back the next manifest. Otherwise one forged
    // manifest (highest versionCode, publishedAt in year 9999) would be kept
    // for good: no real release is higher or later.
    if (publishedFarAhead(incoming, nowEpochMillis)) return false
    if (known == null || incoming.versionCode >= known.versionCode) return true
    if (publishedFarAhead(known, nowEpochMillis)) return true
    val incomingPublished = publishedInstantOrNull(incoming.publishedAt) ?: return true
    val knownPublished = publishedInstantOrNull(known.publishedAt) ?: return true
    return incomingPublished.isAfter(knownPublished)
}

private fun publishedFarAhead(
    release: UpdateManifestRelease,
    nowEpochMillis: Long,
): Boolean {
    val published = publishedInstantOrNull(release.publishedAt) ?: return false
    // Compared as instants: toEpochMilli() throws for a year far beyond 9999.
    return published.isAfter(
        Instant.ofEpochMilli(nowEpochMillis).plusMillis(SECAUDIT01_MAX_PUBLISHED_AHEAD_MILLIS),
    )
}

/** A superseding manifest that replaces a known release with a lower version. */
internal fun updateManifestCorrects(
    incoming: UpdateManifestRelease,
    known: UpdateManifestRelease?,
): Boolean = known != null && incoming.versionCode < known.versionCode

private fun publishedInstantOrNull(publishedAt: String): Instant? =
    try {
        Instant.parse(publishedAt)
    } catch (_: DateTimeParseException) {
        null
    }
