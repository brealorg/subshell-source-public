package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPostActionsPersistenceTest {
    @Test
    fun postActionsAreIsolatedAndRestoredAcrossAccounts() {
        val stored = mutableMapOf(
            "root" to AccountPostActions(),
            "packetmoss" to AccountPostActions(
                savedPostIds = setOf("packet-saved"),
                readPostIds = setOf("packet-read"),
                votes = mapOf("packet-vote" to -1),
            ),
        )
        val state = stateWithActions(stored)

        state.toggleSaved("root-saved")
        state.hidePost("root-hidden")
        state.markPostRead("root-read")
        state.vote("root-vote", 1)
        state.votePoll("subshell-poll", "media")
        state.toggleFollowUser("packetmoss")

        state.switchAccount("packetmoss")

        assertEquals(setOf("packet-saved"), state.savedPostIds.toSet())
        assertTrue(state.hiddenPostIds.isEmpty())
        assertEquals(setOf("packet-read"), state.readPostIds.toSet())
        assertEquals(mapOf("packet-vote" to -1), state.votes.toMap())
        assertTrue(state.pollVotes.isEmpty())
        assertTrue(state.followedUsernames.isEmpty())

        state.toggleSaved("packet-own-save")
        state.vote("packet-own-vote", 1)
        state.toggleFollowUser("root")
        state.switchAccount("root")

        assertEquals(setOf("root-saved"), state.savedPostIds.toSet())
        assertEquals(setOf("root-hidden"), state.hiddenPostIds.toSet())
        assertEquals(setOf("root-read"), state.readPostIds.toSet())
        assertEquals(mapOf("root-vote" to 1), state.votes.toMap())
        assertEquals(mapOf("subshell-poll" to "media"), state.pollVotes.toMap())
        assertEquals(setOf("packetmoss"), state.followedUsernames.toSet())

        assertEquals(
            setOf("packet-saved", "packet-own-save"),
            stored.getValue("packetmoss").savedPostIds,
        )
        assertEquals(setOf("root"), stored.getValue("packetmoss").followedUsernames)
    }

    @Test
    fun readMarkersSurviveStateRecreationAndCanBeReset() {
        val stored = mutableMapOf("root" to AccountPostActions())
        stateWithActions(stored).markPostRead(" durable-read ")

        val restored = stateWithActions(stored)
        assertEquals(setOf("durable-read"), restored.readPostIds.toSet())

        restored.updateHideReadPosts(true)
        restored.resetReadPostHistory()

        assertTrue(restored.readPostIds.isEmpty())
        assertFalse(restored.hideReadPosts)
        assertTrue(stored.getValue("root").readPostIds.isEmpty())
    }

    @Test
    fun repeatingTheSameVoteReturnsToNeutralAndPersistsRemoval() {
        val stored = mutableMapOf("root" to AccountPostActions())
        val state = stateWithActions(stored)

        state.vote("post", 1)
        assertEquals(1, state.votes["post"])

        state.vote("post", 1)

        assertNull(state.votes["post"])
        assertTrue(stored.getValue("root").votes.isEmpty())
    }

    @Test
    fun selfFollowUsesTheActiveAccountRatherThanTheFixtureRoot() {
        val stored = mutableMapOf(
            "root" to AccountPostActions(),
            "packetmoss" to AccountPostActions(),
        )
        val state = stateWithActions(stored)

        state.toggleFollowUser("ROOT")
        assertTrue(state.followedUsernames.isEmpty())

        state.toggleFollowUser("packetmoss")
        assertTrue(state.isFollowingUser("PACKETMOSS"))

        state.switchAccount("packetmoss")
        state.toggleFollowUser("packetmoss")
        assertFalse(state.isFollowingUser("packetmoss"))

        state.toggleFollowUser("root")
        assertTrue(state.isFollowingUser("ROOT"))
    }

    @Test
    fun emptyLoadedActionsRemainEmptyInsteadOfRestoringFixtureDefaults() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadAccountPostActions = { AccountPostActions() },
        )

        assertTrue(state.savedPostIds.isEmpty())
        assertTrue(state.hiddenPostIds.isEmpty())
        assertTrue(state.votes.isEmpty())
        assertTrue(state.pollVotes.isEmpty())
        assertTrue(state.followedUsernames.isEmpty())
    }

    @Test
    fun normalizationDropsInvalidVotesAndTheActiveAccountsSelfFollow() {
        val normalized = normalizeAccountPostActions(
            actions = AccountPostActions(
                savedPostIds = setOf(" post ", ""),
                readPostIds = setOf(" read ", ""),
                votes = mapOf("up" to 1, "down" to -1, "neutral" to 0, "bad" to 2),
                pollVotes = mapOf("poll" to " option ", "blank" to ""),
                followedUsernames = setOf(" ROOT ", "PacketMoss", ""),
            ),
            username = "root",
        )

        assertEquals(setOf("post"), normalized.savedPostIds)
        assertEquals(setOf("read"), normalized.readPostIds)
        assertEquals(mapOf("up" to 1, "down" to -1), normalized.votes)
        assertEquals(mapOf("poll" to "option"), normalized.pollVotes)
        assertEquals(setOf("packetmoss"), normalized.followedUsernames)
    }

    @Test
    fun mapEncodingRoundTripsWithoutDependingOnPunctuation() {
        val original = linkedMapOf(
            "post:one" to "option=alpha",
            "post/two" to "option beta",
        )

        assertEquals(original, decodeAccountActionMap(encodeAccountActionMap(original)))
        assertEquals(
            "account_post_actions.root.poll_votes",
            accountPostActionsPreferenceKey(" ROOT ", "POLL_VOTES"),
        )
    }

    @Test
    fun switchingAccountUsesDedicatedDurablePersistenceCallback() {
        val persistedUsernames = mutableListOf<String>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { emptySet() },
            loadAccountPostActions = { AccountPostActions() },
            persistActiveAccountUsername = persistedUsernames::add,
        )

        state.switchAccount("packetmoss")

        assertEquals("packetmoss", state.activeAccountUsername)
        assertEquals(listOf("packetmoss"), persistedUsernames)
    }

    // STEP3 (safety01a3): a signed-in account's hidden posts are Reddit's.
    // Hiding one asks Reddit and shows the post hidden at once, but the hide
    // is never written into the account's local post actions; local hides
    // stored earlier are not shown for a signed-in account either.
    @Test
    fun aSignedInHideAsksRedditAndIsNeverStoredLocally() {
        val stored = mutableMapOf<String, AccountPostActions>()
        val requests = mutableListOf<RedditPostVisibilityRequest>()
        val state = signedInStateWithActions(stored)
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = { request -> requests += request },
            reportRequester = {},
        )
        val post = Post(
            id = "t3_safe1",
            community = "homelab",
            author = "someone",
            age = "1h",
            title = "A post to hide",
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )

        state.togglePostHidden(post)
        // Any other post action writes the account's actions out.
        state.markPostRead("t3_other")

        assertEquals(listOf(RedditPostVisibilityRequest("reddit:me", "t3_safe1", true)), requests)
        assertTrue(state.effectivePostHidden(post))
        assertEquals(setOf("t3_other"), stored.getValue("me_user").readPostIds)
        assertTrue(stored.getValue("me_user").hiddenPostIds.isEmpty())
    }

    @Test
    fun locallyStoredHidesAreNotShownForASignedInAccount() {
        val stored = mutableMapOf(
            "me_user" to AccountPostActions(hiddenPostIds = setOf("stale-local-hide")),
        )

        val state = signedInStateWithActions(stored)

        assertTrue(state.hiddenPostIds.isEmpty())
    }

    private fun signedInStateWithActions(
        stored: MutableMap<String, AccountPostActions>,
    ): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            loadAccountPostActions = { username -> stored[username] ?: AccountPostActions() },
            persistAccountPostActions = { username, actions -> stored[username] = actions },
        )
    }

    private fun stateWithActions(
        stored: MutableMap<String, AccountPostActions>,
    ): SubshellState = SubshellState(
        initialActiveAccountUsername = "root",
        loadAccountPostActions = { username -> stored[username] ?: AccountPostActions() },
        persistAccountPostActions = { username, actions -> stored[username] = actions },
    )
}
