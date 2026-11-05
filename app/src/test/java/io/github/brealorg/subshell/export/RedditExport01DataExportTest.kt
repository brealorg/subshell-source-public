package io.github.brealorg.subshell.export

import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.NetworkResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1 (owner 2026-10-31): Settings ›
 * Accounts › "Export your Reddit data" reads each list page by page and writes
 * one ZIP: Reddit's own answer per list, a CSV per list, summary.json and
 * README.txt. A failing list keeps what it read and the others go on.
 */
class RedditExport01DataExportTest {
    private val all = setOf("history", "mysubreddits", "read", "privatemessages")

    @Test
    fun everyListIsReadPageByPageAndWrittenAsRedditAnsweredIt() = runBlocking {
        val fake = FakeReddit().apply {
            // Saved: two pages, the second ends the list.
            pages["user/me/saved"] = listOf(
                listing(after = "t3_b", post("a", extra = "\"future_field\":{\"kept\":true}"), post("b")),
                listing(after = null, comment("c")),
            )
            pages["api/multi/mine"] = listOf("[{\"kind\":\"LabeledMulti\",\"data\":{\"name\":\"mine\",\"display_name\":\"Mine\",\"path\":\"/user/me/m/mine\"}}]")
            pages["prefs/blocked"] = listOf("{\"kind\":\"UserList\",\"data\":{\"children\":[{\"date\":1700000000.0,\"name\":\"troll\",\"id\":\"t2_x\",\"rel_id\":\"r9_1\"}]}}")
        }
        val zip = export(fake, all)

        assertEquals(listOf(null, "t3_b"), fake.afters("user/me/saved"))
        val saved = Json.parseToJsonElement(zip.getValue("saved.json")).jsonObject
        val items = saved.getValue("items").jsonArray
        assertEquals(3, items.size)
        // Nothing of Reddit's answer is left out, not even a field the app doesn't know.
        assertEquals("true", items[0].jsonObject["data"]!!.jsonObject["future_field"]!!.jsonObject["kept"]!!.jsonPrimitive.content)

        val csv = zip.getValue("saved.csv").trimEnd().lines()
        assertEquals(CSV_HEADER.joinToString(","), csv[0].trimEnd('\r'))
        assertEquals(4, csv.size)
        assertTrue(csv[3].startsWith("comment,t1_c,2023-11-14T22:13:20Z,r/test,someone,,Post title,Reply c,https://www.reddit.com/r/test/comments/p/_/c/,"))
        assertTrue(zip.getValue("custom_feeds.csv").contains("custom_feed,mine,,,,,Mine,,https://www.reddit.com/user/me/m/mine,"))
        assertTrue(zip.getValue("blocked_users.csv").contains("user,t2_x,2023-11-14T22:13:20Z,,troll,"))

        val summary = Json.parseToJsonElement(zip.getValue("summary.json")).jsonObject
        assertEquals("u/me", summary["account"]!!.jsonPrimitive.content)
        val lists = summary.getValue("lists").jsonArray.associate { entry ->
            entry.jsonObject["list"]!!.jsonPrimitive.content to entry.jsonObject
        }
        assertEquals(RedditExportList.entries.map { it.fileStem }.toSet(), lists.keys)
        assertEquals("3", lists.getValue("saved")["items"]!!.jsonPrimitive.content)
        assertTrue(lists.values.all { it["complete"]!!.jsonPrimitive.content == "true" })
        assertTrue(zip.getValue("README.txt").contains("Saved: 3"))
    }

    @Test
    fun aRefusedListKeepsWhatItReadAndTheOthersGoOn() = runBlocking {
        val fake = FakeReddit().apply {
            pages["user/me/upvoted"] = listOf(listing(after = "t3_a", post("a")))
            refusedAfterFirstPage += "user/me/upvoted"
            pages["user/me/comments"] = listOf(listing(after = null, comment("c")))
        }
        val zip = export(fake, all)
        val lists = summaryLists(zip)

        assertEquals("1", lists.getValue("upvoted")["items"]!!.jsonPrimitive.content)
        assertEquals("false", lists.getValue("upvoted")["complete"]!!.jsonPrimitive.content)
        assertEquals("http_403_network_security_block", lists.getValue("upvoted")["stop"]!!.jsonPrimitive.content)
        assertEquals("1", lists.getValue("comments")["items"]!!.jsonPrimitive.content)
        assertEquals("true", lists.getValue("comments")["complete"]!!.jsonPrimitive.content)
        assertTrue(zip.getValue("README.txt").contains("Upvoted: 1 (incomplete: http_403_network_security_block)"))
    }

    @Test
    fun aListWithoutItsPermissionIsNotAskedForAndAStarGrantsEverything() = runBlocking {
        val withoutMessages = FakeReddit()
        val lists = summaryLists(export(withoutMessages, all - "privatemessages"))
        assertEquals(STOP_NOT_GRANTED, lists.getValue("messages_inbox")["stop"]!!.jsonPrimitive.content)
        assertEquals(STOP_NOT_GRANTED, lists.getValue("messages_sent")["stop"]!!.jsonPrimitive.content)
        assertFalse(withoutMessages.asked.any { it.startsWith("message/") })

        val star = FakeReddit()
        export(star, setOf("*"))
        assertEquals(RedditExportList.entries.size, star.asked.distinct().size)
    }

    @Test
    fun aListGoesOnUntilRedditEndsItAndOnlyANeverEndingOneIsCapped() = runBlocking {
        val fake = FakeReddit().apply {
            pages["user/me/hidden"] = List(REDDIT_EXPORT_MAX_PAGES + 3) { index -> listing(after = "t3_$index", post("$index")) }
            // 12 full pages of 100 comments, then Reddit gives no next page.
            pages["user/me/comments"] = List(12) { page ->
                listing(after = if (page < 11) "t1_$page" else null, *Array(100) { comment("${page}_$it") })
            }
            // A next page that is the page just read.
            pages["user/me/saved"] = List(5) { listing(after = "t3_same", post("same")) }
        }
        var pauses = 0
        val zip = export(fake, all) { pauses += 1 }
        val hidden = summaryLists(zip).getValue("hidden")

        assertEquals(STOP_CAP, hidden["stop"]!!.jsonPrimitive.content)
        assertEquals(REDDIT_EXPORT_MAX_PAGES.toString(), hidden["pages"]!!.jsonPrimitive.content)
        assertEquals("false", hidden["complete"]!!.jsonPrimitive.content)
        assertTrue(zip.getValue("README.txt").contains("Hidden: $REDDIT_EXPORT_MAX_PAGES (stopped at"))

        val comments = summaryLists(zip).getValue("comments")
        assertEquals("1200", comments["items"]!!.jsonPrimitive.content)
        assertEquals(STOP_END, comments["stop"]!!.jsonPrimitive.content)
        assertEquals("true", comments["complete"]!!.jsonPrimitive.content)

        val saved = summaryLists(zip).getValue("saved")
        assertEquals("2", saved["pages"]!!.jsonPrimitive.content)
        assertEquals(STOP_END, saved["stop"]!!.jsonPrimitive.content)
        assertEquals(fake.asked.size - 1, pauses)
    }

    @Test
    fun csvCellsAreQuotedAndFormulasStayText() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\",\"two\nlines\"\r\n", csvLine(listOf("a", "b,c", "say \"hi\"", "two\nlines")))
        assertEquals("'=HYPERLINK(1),'+1,'-2,'@x,ok\r\n", csvLine(listOf("=HYPERLINK(1)", "+1", "-2", "@x", "ok")))
    }

    private suspend fun export(
        fake: FakeReddit,
        scopes: Set<String>,
        pause: suspend () -> Unit = {},
    ): Map<String, String> {
        val output = ByteArrayOutputStream()
        runRedditDataExport(
            username = "me",
            grantedScopes = scopes,
            fetch = fake::fetch,
            pause = pause,
            output = output,
            exportedAtEpochSeconds = 1_700_000_000L,
            appVersion = "test",
        )
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(RedditExportList.entries.size * 2 + 2, files.size)
        return files
    }

    private fun summaryLists(zip: Map<String, String>): Map<String, JsonObject> =
        Json.parseToJsonElement(zip.getValue("summary.json")).jsonObject.getValue("lists").jsonArray
            .associate { entry -> entry.jsonObject["list"]!!.jsonPrimitive.content to entry.jsonObject }

    private class FakeReddit {
        val pages = mutableMapOf<String, List<String>>()
        val refusedAfterFirstPage = mutableSetOf<String>()
        val asked = mutableListOf<String>()
        private val requests = mutableListOf<JsonGetRequest>()

        fun afters(path: String): List<String?> = requests
            .filter { it.pathSegments.joinToString("/") == path }
            .map { request -> request.queryParameters.firstOrNull { it.first == "after" }?.second }

        suspend fun fetch(request: JsonGetRequest): NetworkResult<JsonElement> {
            val path = request.pathSegments.joinToString("/")
            val index = asked.count { it == path }
            asked += path
            requests += request
            if (path in refusedAfterFirstPage && index >= 1) {
                return NetworkResult.HttpFailure(403, false, META, "network_security_block")
            }
            val body = pages[path]?.getOrNull(index) ?: listing(after = null)
            return NetworkResult.Success(Json.parseToJsonElement(body), META)
        }
    }
}

private fun listing(after: String?, vararg children: String): String =
    "{\"kind\":\"Listing\",\"data\":{\"after\":${after?.let { "\"$it\"" } ?: "null"},\"children\":[${children.joinToString(",")}]}}"

private fun post(id: String, extra: String? = null): String =
    "{\"kind\":\"t3\",\"data\":{\"name\":\"t3_$id\",\"created_utc\":1700000000.0,\"subreddit_name_prefixed\":\"r/test\"," +
        "\"author\":\"someone\",\"title\":\"Post $id\",\"selftext\":\"\",\"permalink\":\"/r/test/comments/$id/\"," +
        "\"url\":\"https://example.com/$id\"${extra?.let { ",$it" }.orEmpty()}}}"

private fun comment(id: String): String =
    "{\"kind\":\"t1\",\"data\":{\"name\":\"t1_$id\",\"created_utc\":1700000000.0,\"subreddit_name_prefixed\":\"r/test\"," +
        "\"author\":\"someone\",\"link_title\":\"Post title\",\"body\":\"Reply $id\",\"permalink\":\"/r/test/comments/p/_/$id/\"}}"

private val META = NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null)
