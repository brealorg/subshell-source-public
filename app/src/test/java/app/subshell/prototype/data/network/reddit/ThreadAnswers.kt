package app.subshell.prototype.data.network.reddit

// ISSUE281_READS_AVOID_API_INFO_V1: a post or comment is now read from its
// thread; these wrap a listing the way Reddit's /comments answer does.
private const val EMPTY_LISTING = """{"kind":"Listing","data":{"children":[]}}"""

/** /comments/<post>?limit=1: the post listing, then its comments. */
internal fun postThreadAnswer(postListing: String): String = "[$postListing,$EMPTY_LISTING]"

/** /comments/<post>/_/<comment>: the post, then the comment listing. */
internal fun commentThreadAnswer(commentListing: String): String = "[$EMPTY_LISTING,$commentListing]"
