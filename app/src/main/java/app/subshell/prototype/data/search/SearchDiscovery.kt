package app.subshell.prototype.data.search

import app.subshell.prototype.data.network.GoogleTrendsRssNetworkDataSource
import androidx.compose.runtime.Immutable
import java.nio.charset.StandardCharsets
import java.util.Base64

internal const val SEARCH_DISCOVERY_TREND_LIMIT = 8
internal const val SEARCH_DISCOVERY_TREND_CACHE_TTL_MS = 30L * 60L * 1_000L
internal const val SEARCH_DISCOVERY_TREND_CACHE_PREFERENCE_KEY = "search_discovery.google_trends.v1"
private const val SEARCH_DISCOVERY_TREND_TITLE_MAX_LENGTH = 120
private const val SEARCH_DISCOVERY_TREND_TRAFFIC_MAX_LENGTH = 32

@Immutable
internal data class TrendingSearchSeed(
    val title: String,
    val approximateTraffic: String = "",
)

@Immutable
internal data class TrendingSearchCache(
    val fetchedAtEpochMs: Long = 0L,
    val seeds: List<TrendingSearchSeed> = emptyList(),
)

internal object GoogleTrendsSearchDiscoveryLoader {
    suspend fun load(): List<TrendingSearchSeed> = normalizeTrendingSearchSeeds(
        GoogleTrendsRssNetworkDataSource.load().map { item ->
            TrendingSearchSeed(
                title = item.title,
                approximateTraffic = item.approximateTraffic,
            )
        },
    )
}

internal fun normalizeTrendingSearchSeeds(
    values: Iterable<TrendingSearchSeed>,
    limit: Int = SEARCH_DISCOVERY_TREND_LIMIT,
): List<TrendingSearchSeed> {
    if (limit <= 0) return emptyList()
    val seen = linkedSetOf<String>()
    return values.mapNotNull { seed ->
        val title = seed.title.trim().replace(WHITESPACE_RUN, " ")
            .takeWholeCharacters(SEARCH_DISCOVERY_TREND_TITLE_MAX_LENGTH)
        if (title.isBlank()) return@mapNotNull null
        val key = title.lowercase()
        if (!seen.add(key)) return@mapNotNull null
        TrendingSearchSeed(
            title = title,
            approximateTraffic = seed.approximateTraffic
                .trim()
                .replace(WHITESPACE_RUN, " ")
                .takeWholeCharacters(SEARCH_DISCOVERY_TREND_TRAFFIC_MAX_LENGTH),
        )
    }.take(limit)
}

// LOW_SEARCH_TRUNCATION_KEEPS_WHOLE_CHARACTERS_V1 (OCB-641): a cut between
// the halves of a surrogate pair dropped the half; it was stored as '?'.
private fun String.takeWholeCharacters(maxLength: Int): String {
    val cut = take(maxLength)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate() && cut.length < length) cut.dropLast(1) else cut
}

internal fun freshTrendingSearchSeeds(
    cache: TrendingSearchCache,
    nowEpochMs: Long,
): List<TrendingSearchSeed> {
    if (nowEpochMs < 0L || cache.fetchedAtEpochMs <= 0L) return emptyList()
    // LOW_SEARCH_FUTURE_TREND_CACHE_IS_STALE_V1 (OCB-556): a fetch time in the
    // future (the clock was ahead) is stale, not fresh until the clock catches up.
    val ageMs = nowEpochMs - cache.fetchedAtEpochMs
    if (ageMs < 0L || ageMs >= SEARCH_DISCOVERY_TREND_CACHE_TTL_MS) return emptyList()
    return normalizeTrendingSearchSeeds(cache.seeds)
}

internal fun encodeTrendingSearchCache(cache: TrendingSearchCache): String {
    val seeds = normalizeTrendingSearchSeeds(cache.seeds)
    if (cache.fetchedAtEpochMs <= 0L || seeds.isEmpty()) return ""
    return buildString {
        append(cache.fetchedAtEpochMs)
        seeds.forEach { seed ->
            append('\n')
            append(encodeField(seed.title))
            append('|')
            append(encodeField(seed.approximateTraffic))
        }
    }
}

internal fun decodeTrendingSearchCache(encoded: String): TrendingSearchCache {
    val lines = encoded.lineSequence().toList()
    val fetchedAt = lines.firstOrNull()?.toLongOrNull() ?: return TrendingSearchCache()
    if (fetchedAt <= 0L) return TrendingSearchCache()
    val seeds = lines.drop(1).mapNotNull { line ->
        val fields = line.split('|', limit = 2)
        if (fields.size != 2) return@mapNotNull null
        runCatching {
            TrendingSearchSeed(
                title = decodeField(fields[0]),
                approximateTraffic = decodeField(fields[1]),
            )
        }.getOrNull()
    }
    val normalized = normalizeTrendingSearchSeeds(seeds)
    return if (normalized.isEmpty()) {
        TrendingSearchCache()
    } else {
        TrendingSearchCache(fetchedAtEpochMs = fetchedAt, seeds = normalized)
    }
}

private fun encodeField(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeField(value: String): String = String(
    Base64.getUrlDecoder().decode(value),
    StandardCharsets.UTF_8,
)

private val WHITESPACE_RUN = Regex("\\s+")
