package io.github.brealorg.subshell.export

import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkResult
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1 (owner 2026-10-31, before Reddit
 * closes API access for unregistered apps on 2027-01-12): what Reddit holds for
 * the account, read page by page and written as one ZIP: each list as Reddit's
 * own answer (`<list>.json`, nothing left out) and as a spreadsheet
 * (`<list>.csv`), plus `summary.json` and `README.txt`. A list that fails keeps
 * what it read and the rest goes on; the summary says which stopped and why.
 */
internal enum class RedditExportList(
    val fileStem: String,
    val title: String,
    val scope: String,
    val paged: Boolean = true,
    private val path: (username: String) -> List<String>,
    private val userListing: Boolean = false,
) {
    SAVED("saved", "Saved", "history", path = { user -> listOf("user", user, "saved") }, userListing = true),
    UPVOTED("upvoted", "Upvoted", "history", path = { user -> listOf("user", user, "upvoted") }, userListing = true),
    DOWNVOTED("downvoted", "Downvoted", "history", path = { user -> listOf("user", user, "downvoted") }, userListing = true),
    HIDDEN("hidden", "Hidden", "history", path = { user -> listOf("user", user, "hidden") }, userListing = true),
    POSTS("posts", "Your posts", "history", path = { user -> listOf("user", user, "submitted") }, userListing = true),
    COMMENTS("comments", "Your comments", "history", path = { user -> listOf("user", user, "comments") }, userListing = true),
    SUBSCRIPTIONS("subscriptions", "Joined communities", "mysubreddits", path = { listOf("subreddits", "mine", "subscriber") }),
    CUSTOM_FEEDS("custom_feeds", "Custom feeds", "read", paged = false, path = { listOf("api", "multi", "mine") }),
    INBOX("messages_inbox", "Inbox", "privatemessages", path = { listOf("message", "inbox") }),
    SENT("messages_sent", "Sent messages", "privatemessages", path = { listOf("message", "sent") }),
    BLOCKED("blocked_users", "Blocked users", "read", paged = false, path = { listOf("prefs", "blocked") }),
    ;

    fun request(username: String, after: String?): JsonGetRequest =
        JsonGetRequest(
            pathSegments = path(username),
            queryParameters = buildList {
                add("raw_json" to "1")
                if (paged) add("limit" to REDDIT_EXPORT_PAGE_SIZE.toString())
                if (userListing) add("sort" to "new")
                if (paged && after != null) add("after" to after)
            },
        )
}

internal data class RedditExportListResult(
    val list: RedditExportList,
    val items: Int,
    val pages: Int,
    /** `end` (Reddit gave no next page), `cap` (the safety cap, Reddit had more), or why it stopped. */
    val stop: String,
) {
    val complete: Boolean get() = stop == STOP_END
}

internal data class RedditExportRun(
    val lists: List<RedditExportListResult>,
) {
    val items: Int get() = lists.sumOf(RedditExportListResult::items)
    val incomplete: List<RedditExportListResult> get() = lists.filterNot(RedditExportListResult::complete)
}

/**
 * Reads every [RedditExportList] for [username] with [fetch] and writes the ZIP
 * to [output]. Lists whose scope is not in [grantedScopes] are skipped as
 * `not_granted`; [pause] runs before each request after the first (Reddit
 * allows about 100 a minute).
 */
internal suspend fun runRedditDataExport(
    username: String,
    grantedScopes: Set<String>,
    fetch: suspend (JsonGetRequest) -> NetworkResult<JsonElement>,
    pause: suspend () -> Unit,
    output: OutputStream,
    exportedAtEpochSeconds: Long,
    appVersion: String,
    progress: (done: Int, total: Int) -> Unit = { _, _ -> },
): RedditExportRun {
    val lists = RedditExportList.entries
    val results = mutableListOf<RedditExportListResult>()
    var requests = 0
    ZipOutputStream(output).use { zip ->
        lists.forEachIndexed { index, list ->
            progress(index, lists.size)
            val rows = mutableListOf<List<String>>()
            var items = 0
            var pages = 0
            var stop: String
            zip.putNextEntry(ZipEntry("${list.fileStem}.json"))
            zip.writeText("{\"list\":${JsonPrimitive(list.fileStem)},\"items\":[")
            if (!grantedScopes.grants(list.scope)) {
                stop = STOP_NOT_GRANTED
            } else {
                var after: String? = null
                while (true) {
                    if (requests > 0) pause()
                    requests += 1
                    val page = when (val result = fetch(list.request(username, after))) {
                        is NetworkResult.Success -> result.value
                        is NetworkResult.HttpFailure -> {
                            stop = buildString {
                                append("http_").append(result.statusCode)
                                result.providerReason?.let { append('_').append(it) }
                            }
                            break
                        }
                        is NetworkResult.TransportFailure -> {
                            stop = "transport"
                            break
                        }
                        is NetworkResult.ProtocolFailure -> {
                            stop = "protocol"
                            break
                        }
                    }
                    pages += 1
                    val (children, next) = page.childrenAndAfter()
                    children.forEach { child ->
                        if (items > 0) zip.writeText(",")
                        zip.writeText(EXPORT_JSON.encodeToString(JsonElement.serializer(), child))
                        rows += child.csvRow()
                        items += 1
                    }
                    // A page that points back to itself ends the list too.
                    if (!list.paged || next == null || next == after || children.isEmpty()) {
                        stop = STOP_END
                        break
                    }
                    if (pages >= REDDIT_EXPORT_MAX_PAGES) {
                        stop = STOP_CAP
                        break
                    }
                    after = next
                }
            }
            zip.writeText("]}")
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${list.fileStem}.csv"))
            zip.writeText(csvLine(CSV_HEADER))
            rows.forEach { row -> zip.writeText(csvLine(row)) }
            zip.closeEntry()
            results += RedditExportListResult(list, items, pages, stop)
        }
        progress(lists.size, lists.size)
        val run = RedditExportRun(results)
        zip.putNextEntry(ZipEntry("summary.json"))
        zip.writeText(EXPORT_JSON.encodeToString(JsonElement.serializer(), run.summary(username, exportedAtEpochSeconds, appVersion)))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("README.txt"))
        zip.writeText(exportReadme(username, run))
        zip.closeEntry()
    }
    return RedditExportRun(results)
}

private fun Set<String>.grants(scope: String): Boolean = "*" in this || scope in this

/** A Listing's children and `after`; a JSON array (custom feeds) is its own children. */
private fun JsonElement.childrenAndAfter(): Pair<List<JsonElement>, String?> = when (this) {
    is JsonArray -> this to null
    is JsonObject -> {
        val data = this["data"] as? JsonObject
        val children = (data?.get("children") as? JsonArray).orEmpty()
        children to data?.string("after")?.takeIf(String::isNotBlank)
    }
    else -> emptyList<JsonElement>() to null
}

internal val CSV_HEADER = listOf("type", "id", "created_utc", "community", "author", "to", "title", "text", "link", "url")

/** One spreadsheet row for a Reddit thing, whatever its kind. */
internal fun JsonElement.csvRow(): List<String> {
    val thing = this as? JsonObject ?: return CSV_HEADER.map { "" }
    val kind = thing.string("kind")
    // prefs/blocked children are plain {name, id, date}.
    val data = thing["data"] as? JsonObject ?: thing
    val type = when (kind) {
        "t1" -> "comment"
        "t3" -> "post"
        "t4" -> "message"
        "t5" -> "community"
        "LabeledMulti" -> "custom_feed"
        null -> if (data.string("rel_id") != null || data.string("date") != null) "user" else ""
        else -> kind
    }
    val created = data.number("created_utc") ?: data.number("date") ?: data.number("created")
    return listOf(
        type,
        if (type == "user") data.string("id").orEmpty() else data.string("name") ?: data.string("id").orEmpty(),
        created?.let { seconds -> ISO_UTC.format(Instant.ofEpochSecond(seconds.toLong())) }.orEmpty(),
        data.string("subreddit_name_prefixed") ?: data.string("display_name_prefixed").orEmpty(),
        if (type == "user") data.string("name").orEmpty() else data.string("author").orEmpty(),
        data.string("dest").orEmpty(),
        data.string("title") ?: data.string("subject") ?: data.string("link_title")
            ?: data.string("display_name").orEmpty(),
        data.string("selftext") ?: data.string("body") ?: data.string("public_description")
            ?: data.string("description_md").orEmpty(),
        (data.string("permalink") ?: data.string("context") ?: data.string("path")
            ?: if (type == "community") data.string("url") else null)
            ?.let { path -> if (path.startsWith("/")) "https://www.reddit.com$path" else path }
            .orEmpty(),
        if (type == "post") data.string("url").orEmpty() else "",
    )
}

/** RFC 4180, and a cell a spreadsheet would run as a formula starts with '. */
internal fun csvLine(cells: List<String>): String =
    cells.joinToString(separator = ",", postfix = "\r\n") { raw ->
        val cell = if (raw.isNotEmpty() && raw[0] in FORMULA_STARTS) "'$raw" else raw
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

private fun RedditExportRun.summary(username: String, exportedAt: Long, appVersion: String): JsonObject =
    buildJsonObject {
        put("format", 1)
        put("account", "u/$username")
        put("exported_at", ISO_UTC.format(Instant.ofEpochSecond(exportedAt)))
        put("app_version", appVersion)
        put("items", items)
        put(
            "lists",
            buildJsonArray {
                lists.forEach { result ->
                    add(
                        buildJsonObject {
                            put("list", result.list.fileStem)
                            put("items", result.items)
                            put("pages", result.pages)
                            put("complete", result.complete)
                            put("stop", result.stop)
                        },
                    )
                }
            },
        )
    }

private fun exportReadme(username: String, run: RedditExportRun): String = buildString {
    appendLine("Your Reddit data for u/$username, exported by subshell.")
    appendLine()
    appendLine("Each list is here twice:")
    appendLine("- <list>.json is Reddit's own answer, item for item, nothing left out.")
    appendLine("- <list>.csv has one row per item for a spreadsheet: type, id, date (UTC),")
    appendLine("  community, author, recipient, title, text, a reddit.com link and a post's link.")
    appendLine("  A cell that starts with = + - or @ gets a ' in front, so a spreadsheet")
    appendLine("  shows it as text instead of running it.")
    appendLine("summary.json says how many items each list has and whether it is complete.")
    appendLine()
    appendLine("Each list is as long as Reddit gives it out, newest first (often about 1 000).")
    appendLine("The inbox and sent messages are private: keep this file to yourself.")
    appendLine()
    run.lists.forEach { result ->
        append("${result.list.title}: ${result.items}")
        when (result.stop) {
            STOP_END -> Unit
            STOP_CAP -> append(" (stopped at $REDDIT_EXPORT_MAX_PAGES pages; Reddit has more)")
            STOP_NOT_GRANTED -> append(" (not exported: subshell has no permission for it)")
            else -> append(" (incomplete: ${result.stop})")
        }
        appendLine()
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.doubleOrNull

private fun OutputStream.writeText(text: String) = write(text.toByteArray(Charsets.UTF_8))

private val EXPORT_JSON = Json { encodeDefaults = true }
private val ISO_UTC: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

internal const val STOP_END = "end"
internal const val STOP_CAP = "cap"
internal const val STOP_NOT_GRANTED = "not_granted"
internal const val REDDIT_EXPORT_PAGE_SIZE = 100

// Reddit ends a listing by giving no next page (the owner's comments went past
// 1 000); this only stops a listing that never ends. 100 pages take ~70 s.
internal const val REDDIT_EXPORT_MAX_PAGES = 100
