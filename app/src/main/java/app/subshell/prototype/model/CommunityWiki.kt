package app.subshell.prototype.model

import app.subshell.prototype.data.normalizeRedditCommunityName

// WIKI01A1_TYPED_COMMUNITY_WIKI_DOMAIN_V1
data class CommunityWikiIndex(
    val communityName: String,
    val pages: List<CommunityWikiPageRef>,
)

data class CommunityWikiPageRef(
    val path: String,
)

data class CommunityWikiPage(
    val communityName: String,
    val path: String,
    val markdown: String,
    val revisionId: String? = null,
    val revisionDateEpochSeconds: Long? = null,
    val revisionAuthor: String? = null,
    val revisionReason: String? = null,
)

internal fun normalizeCommunityWikiPagePath(rawPath: String): String {
    val normalized = rawPath
        .trim()
        .trim('/')

    require(normalized.isNotEmpty()) {
        "Community wiki page path cannot be blank"
    }
    require(normalized.length <= MAX_COMMUNITY_WIKI_PAGE_PATH_CHARS) {
        "Community wiki page path is too long"
    }
    require(normalized.none { it == '?' || it == '#' || it == '\\' || it.isISOControl() }) {
        "Community wiki page path contains unsupported characters"
    }

    val segments = normalized.split('/')
    require(
        segments.all { segment ->
            segment.isNotBlank() &&
                segment != "." &&
                segment != ".." &&
                segment.length <= MAX_COMMUNITY_WIKI_PAGE_SEGMENT_CHARS
        },
    ) {
        "Community wiki page path contains an invalid segment"
    }

    return segments.joinToString("/")
}

private const val MAX_COMMUNITY_WIKI_PAGE_PATH_CHARS = 512
private const val MAX_COMMUNITY_WIKI_PAGE_SEGMENT_CHARS = 128
