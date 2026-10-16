package app.subshell.prototype.ui

import app.subshell.prototype.model.Community
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMPOSER19_PICKER_OWN_COMMUNITIES_V1: the post composer's community picker
 * starts with the account's own communities, not everything Subshell cached.
 */
class Composer19PickerOwnCommunitiesTest {
    private val seenOnPopular = ComposerCommunityOption(name = "AskReddit")
    private val joined = ComposerCommunityOption(name = "norge", joined = true)
    private val favorite = ComposerCommunityOption(name = "subshell", joined = true, favorite = true)
    private val joinedToo = ComposerCommunityOption(name = "Android", joined = true)
    private val known = listOf(seenOnPopular, joined, favorite, joinedToo)

    private fun names(options: List<ComposerCommunityOption>) = options.map(ComposerCommunityOption::name)

    @Test
    fun anEmptySearchShowsFavouritesThenJoinedOnly() {
        assertEquals(
            listOf("subshell", "Android", "norge"),
            names(composerCommunityPickerOptions(known, emptyList(), query = "", authenticated = true)),
        )
    }

    @Test
    fun typingFindsAnyCachedCommunityAndRedditResultsFirst() {
        val remote = ComposerCommunityOption(name = "AskScience")
        assertEquals(
            listOf("AskScience", "AskReddit"),
            names(composerCommunityPickerOptions(known, listOf(remote), query = "ask", authenticated = true)),
        )
    }

    @Test
    fun signedOutTheLocalListIsShownWhole() {
        assertEquals(
            listOf("subshell", "Android", "norge", "AskReddit"),
            names(composerCommunityPickerOptions(known, emptyList(), query = "", authenticated = false)),
        )
    }

    @Test
    fun aCommunityInBothListsIsShownOnce() {
        val remoteNorge = ComposerCommunityOption(name = "Norge", title = "From Reddit")
        assertEquals(
            listOf("Norge"),
            names(composerCommunityPickerOptions(known, listOf(remoteNorge), query = "norge", authenticated = true)),
        )
    }

    private fun community(name: String) =
        Community(id = "t5_$name", name = name, description = "", members = "", accent = 0L)

    @Test
    fun theAccountsRedditSubscriptionsAreJoinedEvenWhenNotCached() {
        val options = composerKnownCommunityOptions(
            cached = listOf(community("AskReddit"), community("norge")),
            subscribed = listOf(community("norge"), community("subshell")),
            joinedIds = emptyList(),
            isFavorite = { name -> name == "subshell" },
            identity = ::community,
        )

        assertEquals(listOf("norge", "subshell", "AskReddit"), names(options))
        assertEquals(listOf(true, true, false), options.map(ComposerCommunityOption::joined))
        assertEquals(
            listOf("subshell", "norge"),
            names(composerCommunityPickerOptions(options, emptyList(), query = "", authenticated = true)),
        )
    }
}
