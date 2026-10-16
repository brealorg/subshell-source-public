package app.subshell.prototype.ui.screens

import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class Subs04bIntegratedDiscoveryPolicyTest {
    @Test
    fun inlineDiscoveryFiltersJoinedNsfwAndDuplicatesInStableOrder() {
        val items = listOf(
            item("joined", nsfw = false),
            item("first", nsfw = false),
            item("nsfw", nsfw = true),
            item("FIRST", nsfw = false),
            item("second", nsfw = false),
            item("third", nsfw = false),
        )

        val visible = inlineCommunityDiscoveryItems(
            items = items,
            joinedCommunityNames = setOf("joined"),
            showNsfw = false,
            limit = 3,
        )

        assertEquals(
            listOf("first", "second", "third"),
            visible.map { candidate -> candidate.community.name },
        )
    }

    @Test
    fun inlineDiscoveryCanIncludeNsfwWhenEnabled() {
        val visible = inlineCommunityDiscoveryItems(
            items = listOf(
                item("safe", nsfw = false),
                item("nsfw", nsfw = true),
            ),
            joinedCommunityNames = emptySet(),
            showNsfw = true,
        )

        assertEquals(listOf("safe", "nsfw"), visible.map { it.community.name })
    }

    @Test
    fun inlineDiscoveryHonoursZeroLimit() {
        assertEquals(
            emptyList<CommunityDiscoveryItem>(),
            inlineCommunityDiscoveryItems(
                items = listOf(item("safe", nsfw = false)),
                joinedCommunityNames = emptySet(),
                showNsfw = true,
                limit = 0,
            ),
        )
    }

    private fun item(name: String, nsfw: Boolean): CommunityDiscoveryItem =
        CommunityDiscoveryItem(
            community = Community(
                id = "t5_$name",
                name = name,
                description = "$name community",
                members = "1k",
                accent = 0xFF884422,
            ),
            nsfw = nsfw,
        )
}
