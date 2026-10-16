package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.components.redditFlairFallbackText
import java.util.Locale

enum class ContentFilterType(
    val label: String,
    val singularLabel: String,
    val storageKey: String,
    val placeholder: String,
    val description: String,
) {
    WORD(
        label = "Words",
        singularLabel = "word or phrase",
        storageKey = "content_filter_words",
        placeholder = "e.g. giveaway",
        description = "Matches text in post titles and bodies",
    ),
    COMMUNITY(
        label = "Communities",
        singularLabel = "community",
        storageKey = "content_filter_communities",
        placeholder = "e.g. r/example",
        description = "Matches a complete community name",
    ),
    USER(
        label = "Users",
        singularLabel = "user",
        storageKey = "content_filter_users",
        placeholder = "e.g. u/example",
        description = "Matches a complete author name",
    ),
    DOMAIN(
        label = "Domains",
        singularLabel = "domain",
        storageKey = "content_filter_domains",
        placeholder = "e.g. example.com",
        description = "Matches a domain and its subdomains",
    ),
    FLAIR(
        label = "Flairs",
        singularLabel = "flair",
        storageKey = "content_filter_flairs",
        placeholder = "e.g. Meme",
        description = "Matches complete post flair text",
    ),
    ;

    // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1: rules that apply only in
    // one community live under their own key, so the global key and older
    // readers of it are untouched.
    val scopedStorageKey: String get() = "${storageKey}_scoped"

    /** A community rule scoped to a community would hide that whole page. */
    val scopable: Boolean get() = this != COMMUNITY
}

/** A filter rule that applies only to posts in [community]. */
data class ScopedContentFilter(
    val type: ContentFilterType,
    val value: String,
    val community: String,
)

data class ContentFilters(
    val words: Set<String> = emptySet(),
    val communities: Set<String> = emptySet(),
    val users: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val flairs: Set<String> = emptySet(),
    val scoped: Set<ScopedContentFilter> = emptySet(),
) {
    val size: Int
        get() = words.size + communities.size + users.size + domains.size + flairs.size + scoped.size

    fun scopedEntries(type: ContentFilterType): List<ScopedContentFilter> =
        scoped.filter { rule -> rule.type == type }.sortedWith(compareBy({ it.community }, { it.value }))

    fun addingScoped(type: ContentFilterType, rawValue: String, rawCommunity: String): ContentFilters {
        if (!type.scopable) return adding(type, rawValue)
        val value = normalizeContentFilterValue(type, rawValue)
        val community = normalizeContentFilterValue(ContentFilterType.COMMUNITY, rawCommunity)
        // CONTENTFILTER06_SCOPED_RULE_NEEDS_VALID_COMMUNITY_V1: a scoped rule
        // with a community that cannot match is refused, never widened to a
        // global rule.
        if (value.isEmpty() || !isValidContentFilterCommunity(community)) return this
        return copy(scoped = scoped + ScopedContentFilter(type, value, community))
    }

    fun removingScoped(type: ContentFilterType, rawValue: String, rawCommunity: String): ContentFilters {
        val rule = ScopedContentFilter(
            type = type,
            value = normalizeContentFilterValue(type, rawValue),
            community = normalizeContentFilterValue(ContentFilterType.COMMUNITY, rawCommunity),
        )
        return copy(scoped = scoped - rule)
    }

    fun entries(type: ContentFilterType): Set<String> = when (type) {
        ContentFilterType.WORD -> words
        ContentFilterType.COMMUNITY -> communities
        ContentFilterType.USER -> users
        ContentFilterType.DOMAIN -> domains
        ContentFilterType.FLAIR -> flairs
    }

    fun adding(type: ContentFilterType, rawValue: String): ContentFilters {
        val value = normalizeContentFilterValue(type, rawValue)
        if (value.isEmpty()) return this
        if (type == ContentFilterType.COMMUNITY && !isValidContentFilterCommunity(value)) return this
        val updated = entries(type) + value
        return withEntries(type, updated)
    }

    fun removing(type: ContentFilterType, rawValue: String): ContentFilters {
        val value = normalizeContentFilterValue(type, rawValue)
        return withEntries(type, entries(type) - value)
    }

    fun hides(post: Post): Boolean {
        val match = ContentFilterMatch(post)
        return hidesCrosspostSource(post) ||
            match.community in communities ||
            users.any { filter -> match.matches(ContentFilterType.USER, filter) } ||
            flairs.any { filter -> match.matches(ContentFilterType.FLAIR, filter) } ||
            domains.any { filter -> match.matches(ContentFilterType.DOMAIN, filter) } ||
            words.any { filter -> match.matches(ContentFilterType.WORD, filter) } ||
            scoped.any { rule -> rule.community == match.community && match.matches(rule.type, rule.value) }
    }

    // LOWUI_FILTERS_SEE_THE_CROSSPOST_SOURCE_V1 (OCB-080): a crosspost shows
    // the source post; a filtered community or user of the source hides it
    // wherever it was crossposted to.
    private fun hidesCrosspostSource(post: Post): Boolean {
        val source = post.crosspost ?: return false
        val sourceCommunity = normalizeContentFilterValue(ContentFilterType.COMMUNITY, source.community)
        val sourceUser = normalizeContentFilterValue(ContentFilterType.USER, source.author)
        return (sourceCommunity.isNotEmpty() && sourceCommunity in communities) ||
            (sourceUser.isNotEmpty() && sourceUser in users)
    }

    // CONTENTFILTER02_EVERY_FEED_SURFACE_V1: a community filter keeps that
    // community out of feeds; on the community's own page it does not apply,
    // or the page would be empty. Every other rule applies there too.
    fun hidesOnCommunityPage(post: Post, communityName: String): Boolean =
        copy(
            communities = communities - normalizeContentFilterValue(ContentFilterType.COMMUNITY, communityName),
        ).hides(post)

    fun toStorageMap(): Map<String, String> = ContentFilterType.entries.flatMap { type ->
        listOf(
            type.storageKey to encodeContentFilterEntries(entries(type)),
            type.scopedStorageKey to encodeScopedContentFilterEntries(scopedEntries(type)),
        )
    }.toMap()

    private fun withEntries(type: ContentFilterType, entries: Set<String>): ContentFilters = when (type) {
        ContentFilterType.WORD -> copy(words = entries)
        ContentFilterType.COMMUNITY -> copy(communities = entries)
        ContentFilterType.USER -> copy(users = entries)
        ContentFilterType.DOMAIN -> copy(domains = entries)
        ContentFilterType.FLAIR -> copy(flairs = entries)
    }

    companion object {
        fun fromStorageMap(values: Map<String, String>): ContentFilters {
            fun entries(type: ContentFilterType): Set<String> = decodeContentFilterEntries(
                type = type,
                encoded = values[type.storageKey].orEmpty(),
            )
            return ContentFilters(
                words = entries(ContentFilterType.WORD),
                communities = entries(ContentFilterType.COMMUNITY),
                users = entries(ContentFilterType.USER),
                domains = entries(ContentFilterType.DOMAIN),
                flairs = entries(ContentFilterType.FLAIR),
                scoped = ContentFilterType.entries
                    .filter(ContentFilterType::scopable)
                    .flatMap { type ->
                        decodeScopedContentFilterEntries(type, values[type.scopedStorageKey].orEmpty())
                    }
                    .toSet(),
            )
        }
    }
}

fun contentFilterSummary(filters: ContentFilters): String = when (filters.size) {
    0 -> "No active filters"
    1 -> "1 active filter · hidden locally"
    else -> "${filters.size} active filters · hidden locally"
}

private fun encodeContentFilterEntries(entries: Set<String>): String =
    entries.sorted().joinToString("\n")

/** One rule per line: community, a tab, the value. */
private fun encodeScopedContentFilterEntries(entries: List<ScopedContentFilter>): String =
    entries.joinToString("\n") { rule -> "${rule.community}\t${rule.value}" }

private fun decodeScopedContentFilterEntries(
    type: ContentFilterType,
    encoded: String,
): List<ScopedContentFilter> = encoded
    .lineSequence()
    .mapNotNull { line ->
        val community = normalizeContentFilterValue(ContentFilterType.COMMUNITY, line.substringBefore('\t', ""))
        val value = normalizeContentFilterValue(type, line.substringAfter('\t', ""))
        ScopedContentFilter(type, value, community).takeIf { community.isNotEmpty() && value.isNotEmpty() }
    }
    .toList()

/** A post's values in the form filters compare them. */
private class ContentFilterMatch(post: Post) {
    val community = normalizeContentFilterValue(ContentFilterType.COMMUNITY, post.community)
    private val user = normalizeContentFilterValue(ContentFilterType.USER, post.author)
    private val domain = post.domain
        ?.let { normalizeContentFilterValue(ContentFilterType.DOMAIN, it) }
        .orEmpty()
    private val flair = post.flair
        ?.let { normalizeContentFilterValue(ContentFilterType.FLAIR, it) }
        .orEmpty()

    // ISSUE44_POST_FLAIR_RICHTEXT_V1: ":upvote:Outfit Check" is also "Outfit Check".
    private val visibleFlair = post.flair
        ?.let(::redditFlairFallbackText)
        ?.let { normalizeContentFilterValue(ContentFilterType.FLAIR, it) }
        .orEmpty()
    private val searchableText = "${post.title}\n${post.body}".lowercase(Locale.ROOT)

    fun matches(type: ContentFilterType, filter: String): Boolean = when (type) {
        ContentFilterType.WORD -> containsWholePhrase(searchableText, filter)
        ContentFilterType.COMMUNITY -> filter == community
        ContentFilterType.USER -> filter == user
        ContentFilterType.DOMAIN -> domain == filter || domain.endsWith(".$filter")
        ContentFilterType.FLAIR -> filter == flair || (visibleFlair.isNotEmpty() && filter == visibleFlair)
    }
}

/**
 * LOWUI_WORD_FILTERS_MATCH_WHOLE_WORDS_V1 (OCB-081, owner: whole words): the
 * editor says "word or phrase"; "cat" no longer hides "category" or
 * "vacation". The phrase must start and end at a word boundary.
 */
internal fun containsWholePhrase(text: String, phrase: String): Boolean {
    if (phrase.isEmpty()) return false
    var from = 0
    while (true) {
        val index = text.indexOf(phrase, from)
        if (index < 0) return false
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + phrase.length)
        val startsAtBoundary = before == null || !before.isLetterOrDigit() || !phrase.first().isLetterOrDigit()
        val endsAtBoundary = after == null || !after.isLetterOrDigit() || !phrase.last().isLetterOrDigit()
        if (startsAtBoundary && endsAtBoundary) return true
        from = index + 1
    }
}

private fun decodeContentFilterEntries(
    type: ContentFilterType,
    encoded: String,
): Set<String> = encoded
    .lineSequence()
    .map { normalizeContentFilterValue(type, it) }
    .filter(String::isNotEmpty)
    .toCollection(linkedSetOf())

/**
 * Why a draft rule cannot be added, or null when it can. [draftCommunity] is
 * the optional "Only in community" field.
 */
fun contentFilterDraftError(
    type: ContentFilterType,
    draft: String,
    draftCommunity: String,
): ContentFilterDraftError? = when {
    type == ContentFilterType.COMMUNITY && draft.isNotBlank() &&
        !isValidContentFilterCommunity(normalizeContentFilterValue(type, draft)) ->
        ContentFilterDraftError.VALUE
    type == ContentFilterType.USER && draft.isNotBlank() &&
        !CONTENT_FILTER_USER_NAME.matches(normalizeContentFilterValue(type, draft)) ->
        ContentFilterDraftError.VALUE
    // LOWUX1_EMPTY_FILTER_VALUE_IS_REFUSED_V1 (OCB-192): a value that
    // normalises to nothing ("https://" for a domain) was cleared without a
    // rule or a word.
    draft.isNotBlank() && normalizeContentFilterValue(type, draft).isBlank() ->
        ContentFilterDraftError.VALUE
    type.scopable && draftCommunity.isNotBlank() &&
        !isValidContentFilterCommunity(
            normalizeContentFilterValue(ContentFilterType.COMMUNITY, draftCommunity),
        ) ->
        ContentFilterDraftError.COMMUNITY
    else -> null
}

enum class ContentFilterDraftError { VALUE, COMMUNITY }

private val CONTENT_FILTER_COMMUNITY_NAME = Regex("[a-z0-9_]{2,32}")
private val CONTENT_FILTER_USER_NAME = Regex("[a-z0-9_-]{3,20}")

private fun isValidContentFilterCommunity(normalized: String): Boolean =
    CONTENT_FILTER_COMMUNITY_NAME.matches(normalized)

// LOWUI6_FILTER_VALUES_HAVE_NO_CONTROL_CHARACTERS_V1 (OCB-193): a pasted '\r'
// or '\t' split one phrase into two broader filters on the next load (the
// store is line-based); every control character becomes a space.
internal fun contentFilterInputWithoutControlCharacters(value: String): String =
    value.map { char -> if (char.isISOControl()) ' ' else char }.joinToString("")

private fun normalizeContentFilterValue(type: ContentFilterType, rawValue: String): String {
    val normalized = contentFilterInputWithoutControlCharacters(rawValue).trim().lowercase(Locale.ROOT)
    return when (type) {
        ContentFilterType.WORD, ContentFilterType.FLAIR -> normalized
        ContentFilterType.COMMUNITY -> normalized
            .substringAfter("://")
            .let { value -> if ("/r/" in value) value.substringAfter("/r/") else value.removePrefix("r/") }
            .substringBefore('/')
        // LOWUI_USER_FILTER_TAKES_ANY_PROFILE_FORM_V1 (OCB-082): a profile URL,
        // /user/x, user/x, u/x or @x is the user x.
        ContentFilterType.USER -> normalized
            .substringAfter("://")
            .let { value -> if ('/' in value && '.' in value.substringBefore('/')) value.substringAfter('/') else value }
            .removePrefix("/")
            .removePrefix("user/")
            .removePrefix("u/")
            .removePrefix("@")
            .substringBefore('/')
        ContentFilterType.DOMAIN -> normalized
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .removePrefix("www.")
            .trimEnd('.')
    }
}
