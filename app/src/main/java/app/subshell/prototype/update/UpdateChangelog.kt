package app.subshell.prototype.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val UPDATE01_CHANGELOG_SCHEMA_VERSION = 1
internal const val UPDATE01_CHANGELOG_ASSET_PATH = "changelog/releases.json"
internal const val UPDATE01_MAX_CHANGELOG_BYTES = 256 * 1024
internal const val UPDATE01_MAX_CHANGELOG_RELEASES = 50
// RELNOTES01_MORE_HIGHLIGHTS_STAY_READABLE_FOR_OLDER_CLIENTS_V1: at least the
// update manifest's limits, so What's new shows what the update dialog showed.
internal const val UPDATE01_MAX_CHANGELOG_HIGHLIGHTS = 12
internal const val UPDATE01_MAX_CHANGELOG_TEXT = 240

@Serializable
internal data class UpdateChangelogDocument(
    val schemaVersion: Int,
    val releases: List<UpdateChangelogRelease>,
)

@Serializable
internal data class UpdateChangelogRelease(
    val versionCode: Int,
    val versionName: String,
    val date: String,
    val headline: String,
    val highlights: List<String> = emptyList(),
    val releaseUrl: String? = null,
)

internal fun normalizeUpdateChangelog(
    raw: UpdateChangelogDocument,
): UpdateChangelogDocument? {
    if (raw.schemaVersion != UPDATE01_CHANGELOG_SCHEMA_VERSION) return null

    val normalized = raw.releases
        .asSequence()
        .mapNotNull { release ->
            if (release.versionCode <= 0) return@mapNotNull null

            val versionName = release.versionName.trim()
            if (!versionName.matches(Regex("[0-9A-Za-z][0-9A-Za-z._+-]{0,63}"))) {
                return@mapNotNull null
            }

            val date = release.date.trim()
            if (
                date.isEmpty() ||
                date.length > 32 ||
                '\u0000' in date
            ) {
                return@mapNotNull null
            }

            val headline = release.headline.trim()
            if (
                headline.isEmpty() ||
                headline.length > UPDATE01_MAX_CHANGELOG_TEXT ||
                '\u0000' in headline
            ) {
                return@mapNotNull null
            }

            val releaseUrl = release.releaseUrl
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { url ->
                    val parsed = url.toHttpUrlOrNull() ?: return@mapNotNull null
                    if (
                        !parsed.isHttps ||
                        parsed.host != "github.com" ||
                        !parsed.encodedPath.startsWith(
                            "/brealorg/subshell/releases/tag/v",
                        )
                    ) {
                        return@mapNotNull null
                    }
                    url
                }

            val highlights = release.highlights
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filter { value ->
                    value.length <= UPDATE01_MAX_CHANGELOG_TEXT &&
                        '\u0000' !in value
                }
                .take(UPDATE01_MAX_CHANGELOG_HIGHLIGHTS)
                .toList()

            release.copy(
                versionName = versionName,
                date = date,
                headline = headline,
                highlights = highlights,
                releaseUrl = releaseUrl,
            )
        }
        .distinctBy(UpdateChangelogRelease::versionCode)
        .sortedByDescending(UpdateChangelogRelease::versionCode)
        .take(UPDATE01_MAX_CHANGELOG_RELEASES)
        .toList()

    return UpdateChangelogDocument(
        schemaVersion = UPDATE01_CHANGELOG_SCHEMA_VERSION,
        releases = normalized,
    )
}

internal class UpdateChangelogRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    /**
     * UPDATE05_CHANGELOG_READ_ONCE_OFF_MAIN_V1: the bundled asset never
     * changes while the process runs, so it is read and parsed once and kept.
     * Composables go through [rememberBundledChangelog], which loads it on an
     * IO thread instead of during composition.
     */
    fun load(): UpdateChangelogDocument =
        bundledChangelogCache ?: loadUncached().also { bundledChangelogCache = it }

    private fun loadUncached(): UpdateChangelogDocument =
        runCatching {
            val raw = appContext.assets
                .open(UPDATE01_CHANGELOG_ASSET_PATH)
                .use { input -> input.readUpdateUtf8Bounded(UPDATE01_MAX_CHANGELOG_BYTES) }

            val decoded =
                SubshellNetworkJson.providerJson
                    .decodeFromString<UpdateChangelogDocument>(raw)

            normalizeUpdateChangelog(decoded)
                ?: error("Unsupported bundled changelog")
        }.getOrElse {
            UpdateChangelogDocument(
                schemaVersion = UPDATE01_CHANGELOG_SCHEMA_VERSION,
                releases = emptyList(),
            )
        }
}

internal fun UpdateChangelogDocument.releaseForVersionCode(
    versionCode: Int,
): UpdateChangelogRelease? =
    releases.firstOrNull { release -> release.versionCode == versionCode }

internal fun UpdateChangelogDocument.latestRelease(): UpdateChangelogRelease? =
    releases.maxByOrNull(UpdateChangelogRelease::versionCode)

@Volatile
private var bundledChangelogCache: UpdateChangelogDocument? = null

internal val EMPTY_UPDATE_CHANGELOG = UpdateChangelogDocument(
    schemaVersion = UPDATE01_CHANGELOG_SCHEMA_VERSION,
    releases = emptyList(),
)

/** The bundled changelog, read once on an IO thread; empty until it is loaded. */
@Composable
internal fun rememberBundledChangelog(appContext: Context): UpdateChangelogDocument =
    rememberBundledChangelogLoad(appContext).document

internal data class BundledChangelogLoad(
    val document: UpdateChangelogDocument,
    val loaded: Boolean,
)

/**
 * LOW_UPDATE_NO_AVAILABLE_DIALOG_BEFORE_WHATS_NEW_V1 (OCB-574): the changelog
 * and whether it has been read yet, so a caller can wait for it instead of
 * treating "not read yet" as "no release notes".
 */
@Composable
internal fun rememberBundledChangelogLoad(appContext: Context): BundledChangelogLoad {
    val load by produceState(
        initialValue = bundledChangelogCache
            ?.let { cached -> BundledChangelogLoad(cached, loaded = true) }
            ?: BundledChangelogLoad(EMPTY_UPDATE_CHANGELOG, loaded = false),
        appContext,
    ) {
        if (!value.loaded) {
            value = BundledChangelogLoad(
                document = withContext(Dispatchers.IO) { UpdateChangelogRepository(appContext).load() },
                loaded = true,
            )
        }
    }
    return load
}
