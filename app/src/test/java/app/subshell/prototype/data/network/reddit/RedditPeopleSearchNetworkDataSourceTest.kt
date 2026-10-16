package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditPeopleSearchNetworkDataSourceTest {
    @Test
    fun requestUsesProfileAwareAutocompleteWithPlainSubmittedQuery() {
        val request = redditPeopleSearchJsonRequest(
            searchQuery = "brealorg",
            cursor = null,
            limit = 10,
        )

        assertEquals(listOf("api", "subreddit_autocomplete_v2"), request.pathSegments)
        assertEquals(
            mapOf(
                "raw_json" to "1",
                "limit" to "10",
                "query" to "brealorg",
                "include_over_18" to "true",
                "include_profiles" to "true",
                "typeahead_active" to "true",
            ),
            request.queryParameters.toMap(),
        )
    }

    @Test
    fun underscoreUsernameRemainsPlainAutocompleteQuery() {
        val request = redditPeopleSearchJsonRequest(
            searchQuery = "subshell_dev",
            cursor = null,
            limit = 10,
        )

        assertEquals(listOf("api", "subreddit_autocomplete_v2"), request.pathSegments)
        assertEquals("subshell_dev", request.queryParameters.toMap()["query"])
    }

    @Test
    fun longProfileTextQueryFallsBackToUsersSearchListing() {
        val query = "android developer profile biography"
        val request = redditPeopleSearchJsonRequest(
            searchQuery = query,
            cursor = "t5_after",
            limit = 10,
        )

        assertTrue(query.length > REDDIT_PEOPLE_AUTOCOMPLETE_MAX_QUERY_LENGTH)
        assertEquals(listOf("users", "search"), request.pathSegments)
        assertEquals(
            mapOf(
                "raw_json" to "1",
                "limit" to "10",
                "q" to query,
                "sort" to "relevance",
                "sr_detail" to "true",
                "typeahead_active" to "false",
                "after" to "t5_after",
            ),
            request.queryParameters.toMap(),
        )
    }

    @Test
    fun accountAutocompleteThingMapsNestedProfileMetadata() {
        val page = RedditPeopleSearchListingDto(
            data = RedditPeopleSearchListingDataDto(
                children = listOf(
                    RedditPeopleSearchThingDto(
                        kind = "t2",
                        data = RedditPeopleSearchDataDto(
                            id = "account-id",
                            name = "AndroidDev",
                            subreddit = RedditPeopleSearchProfileSubredditDto(
                                displayName = "u_AndroidDev",
                                displayNamePrefixed = "u/AndroidDev",
                                title = "Android developer",
                                publicDescription = "Kotlin and Android",
                                url = "/user/AndroidDev/",
                                subredditType = "user",
                                communityIcon =
                                    "https://styles.redditmedia.com/icon.png?x=1&amp;y=2",
                                over18 = true,
                            ),
                        ),
                    ),
                ),
            ),
        ).toNetworkPage()

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("AndroidDev", item.username)
        assertEquals("Android developer", item.title)
        assertEquals("Kotlin and Android", item.description)
        assertEquals(
            "https://styles.redditmedia.com/icon.png?x=1&y=2",
            item.avatarImageUri,
        )
        assertTrue(item.nsfw)
    }

    @Test
    fun accountAutocompleteWithoutProfileMetadataStillMapsUsername() {
        val page = RedditPeopleSearchListingDto(
            data = RedditPeopleSearchListingDataDto(
                children = listOf(
                    RedditPeopleSearchThingDto(
                        kind = "t2",
                        data = RedditPeopleSearchDataDto(name = "brealorg"),
                    ),
                ),
            ),
        ).toNetworkPage()

        assertEquals(listOf("brealorg"), page.items.map { it.username })
        assertEquals("", page.items.single().title)
        assertEquals("", page.items.single().description)
        assertFalse(page.items.single().nsfw)
    }

    @Test
    fun regularCommunityT5IsRejectedButLegacyUserProfileT5StillMaps() {
        val page = RedditPeopleSearchListingDto(
            data = RedditPeopleSearchListingDataDto(
                after = "t5_next",
                children = listOf(
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            displayName = "Android",
                            displayNamePrefixed = "r/Android",
                            subredditType = "public",
                            url = "/r/Android/",
                        ),
                    ),
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            displayNamePrefixed = "u/legacy-user",
                            subredditType = "user",
                            url = "/user/legacy-user/",
                        ),
                    ),
                ),
            ),
        ).toNetworkPage()

        assertEquals("t5_next", page.nextCursor)
        assertEquals(listOf("legacy-user"), page.items.map { it.username })
    }

    @Test
    fun userProfilePathAndLegacyDisplayNameAreSafeUsernameFallbacks() {
        val fromPath = RedditPeopleSearchListingDto(
            data = RedditPeopleSearchListingDataDto(
                children = listOf(
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            url = "/user/path_user/",
                            subredditType = "user",
                        ),
                    ),
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            displayName = "u_legacy-user",
                            subredditType = "user",
                        ),
                    ),
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            displayName = "plain_user",
                            subredditType = "user",
                        ),
                    ),
                    RedditPeopleSearchThingDto(
                        kind = "t5",
                        data = RedditPeopleSearchDataDto(
                            url = "https://www.reddit.com/user/absolute_user/",
                            subredditType = "user",
                        ),
                    ),
                ),
            ),
        ).toNetworkPage()

        assertEquals(
            listOf("path_user", "legacy-user", "plain_user", "absolute_user"),
            fromPath.items.map { item -> item.username },
        )
        assertFalse(fromPath.items.any { item -> item.username.startsWith("u_") })
    }

    @Test
    fun requestNormalizesWhitespaceAndRejectsInvalidBounds() {
        val request = redditPeopleSearchJsonRequest(
            searchQuery = "  android   developer  ",
            cursor = null,
            limit = 10,
        )

        assertEquals("android developer", request.queryParameters.toMap()["query"])
        assertTrue(
            runCatching {
                redditPeopleSearchJsonRequest(
                    searchQuery = "a",
                    cursor = null,
                    limit = 10,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                redditPeopleSearchJsonRequest(
                    searchQuery = "android",
                    cursor = null,
                    limit = 11,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                redditPeopleSearchJsonRequest(
                    searchQuery = "android",
                    cursor = "t5_after",
                    limit = 10,
                )
            }.isFailure,
        )
    }

    @Test
    fun missingListingDataMapsToEmptyPage() {
        val page = RedditPeopleSearchListingDto(data = null).toNetworkPage()

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }
}
