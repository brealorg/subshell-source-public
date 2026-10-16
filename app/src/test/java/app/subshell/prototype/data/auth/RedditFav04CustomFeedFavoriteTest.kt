package app.subshell.prototype.data.auth

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: the feed list keeps Reddit's
 * is_favorited (true only when Reddit says so).
 */
class RedditFav04CustomFeedFavoriteTest {
    @Test
    fun feedListReadsRedditsFavoriteMark() {
        val feeds = SubshellNetworkJson.providerJson.decodeFromString(
            ListSerializer(RedditCustomFeedEnvelope.serializer()),
            """
            [
              {"kind":"LabeledMulti","data":{"path":"/user/brealorg/m/baesj/","display_name":"baesj","is_favorited":true}},
              {"kind":"LabeledMulti","data":{"path":"/user/brealorg/m/music/","display_name":"music","is_favorited":false}},
              {"kind":"LabeledMulti","data":{"path":"/user/brealorg/m/news/","display_name":"news"}}
            ]
            """.trimIndent(),
        ).mapNotNull(RedditCustomFeedEnvelope::toSummaryOrNull)

        assertEquals(listOf(true, false, false), feeds.map { feed -> feed.redditFavorite })
    }
}
