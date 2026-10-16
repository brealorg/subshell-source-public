package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.normalizeCommentVoteFullname
import app.subshell.prototype.data.account.normalizePostVoteFullname
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentListingDto
import app.subshell.prototype.data.network.reddit.RedditCommentThingDto
import app.subshell.prototype.data.network.reddit.redditPostVoteFormRequest
import app.subshell.prototype.data.network.reddit.toComments
import app.subshell.prototype.ui.nextPostVoteDirection
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.postScoreForEffectiveVote
import app.subshell.prototype.ui.voteTargetControlEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class Vote01PostVotePolicyTest {
    @Test
    fun postVoteScopeIsExplicitAndPartOfTheDailyDriverGrant() {
        assertEquals(
            RedditOAuth.coreReadScopes + "vote",
            RedditOAuth.livePostVoteScopes,
        )
        assertEquals(
            RedditOAuth.livePostVoteScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.POST_VOTE),
        )
        assertTrue(
            RedditOAuth.dailyDriverScopes.containsAll(
                RedditOAuth.livePostVoteScopes,
            ),
        )
        assertTrue(RedditOAuth.dailyDriverScopes.contains("save"))
    }

    @Test
    fun postVoteRequestUsesCanonicalFullnameAndAbsoluteDirection() {
        val up = redditPostVoteFormRequest("AbC123", 1)
        assertEquals(listOf("api", "vote"), up.pathSegments)
        assertEquals(
            listOf("id" to "t3_abc123", "dir" to "1"),
            up.fields,
        )
        assertEquals(
            "t3_abc123",
            normalizePostVoteFullname("t3_AbC123"),
        )
        assertEquals(
            listOf("id" to "t3_abc123", "dir" to "0"),
            redditPostVoteFormRequest("t3_abc123", 0).fields,
        )
        assertEquals(
            listOf("id" to "t3_abc123", "dir" to "-1"),
            redditPostVoteFormRequest("t3_abc123", -1).fields,
        )
        assertThrows(IllegalArgumentException::class.java) {
            redditPostVoteFormRequest("t3_abc123", 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PostVoteRequest(
                accountStableId = "reddit:fixture",
                postFullname = "abc123",
                direction = 1,
            )
        }
    }


    @Test
    fun voteButtonUsesAbsoluteToggleSemantics() {
        assertEquals(1, nextPostVoteDirection(0, 1))
        assertEquals(0, nextPostVoteDirection(1, 1))
        assertEquals(-1, nextPostVoteDirection(1, -1))
        assertEquals(0, nextPostVoteDirection(-1, -1))
        assertEquals(1, nextPostVoteDirection(-1, 1))
        assertThrows(IllegalArgumentException::class.java) {
            nextPostVoteDirection(0, 0)
        }
    }

    @Test
    fun voteColorPaletteDefaultsNeutralAndRoundTripsRedBlue() {
        assertEquals(VoteColorPalette.NEUTRAL, SubshellSettings().voteColorPalette)
        val configured = SubshellSettings(voteColorPalette = VoteColorPalette.RED_BLUE)
        assertEquals(
            VoteColorPalette.RED_BLUE.name,
            configured.toStorageMap().getValue(SubshellSettings.VOTE_COLOR_PALETTE),
        )
        assertEquals(
            VoteColorPalette.RED_BLUE,
            SubshellSettings.fromStorageMap(configured.toStorageMap()).voteColorPalette,
        )
    }

    @Test
    fun inFlightVoteStylingIsIsolatedToExactTarget() {
        assertFalse(voteTargetControlEnabled(setOf("t1_same"), "t1_same"))
        assertTrue(voteTargetControlEnabled(setOf("t1_other"), "t1_same"))
        assertTrue(voteTargetControlEnabled(setOf("t3_other"), "t1_same"))
        assertTrue(voteTargetControlEnabled(emptySet(), "t1_same"))
    }

    @Test
    fun voteControlModeDefaultsComfortableAndRoundTripsCompact() {
        assertEquals(VoteControlMode.COMFORTABLE, SubshellSettings().voteControlMode)
        val configured = SubshellSettings(voteControlMode = VoteControlMode.COMPACT)
        assertEquals(
            VoteControlMode.COMPACT.name,
            configured.toStorageMap().getValue(SubshellSettings.VOTE_CONTROL_MODE),
        )
        assertEquals(
            VoteControlMode.COMPACT,
            SubshellSettings.fromStorageMap(configured.toStorageMap()).voteControlMode,
        )
    }

    @Test
    fun optimisticScoreUsesDeltaFromAuthoritativeVote() {
        assertEquals(101, postScoreForEffectiveVote(100, 0, 1))
        assertEquals(99, postScoreForEffectiveVote(100, 0, -1))
        assertEquals(99, postScoreForEffectiveVote(100, 1, 0))
        assertEquals(98, postScoreForEffectiveVote(100, 1, -1))
        assertEquals(102, postScoreForEffectiveVote(100, -1, 1))
    }

    @Test
    fun commentVoteUsesCanonicalT1FullnameAndSameAbsoluteEndpoint() {
        assertEquals("t1_abc123", normalizeCommentVoteFullname("ABC123"))
        assertEquals("t1_abc123", normalizePostVoteFullname("t1_ABC123"))
        val request = PostVoteRequest(
            accountStableId = "reddit:one",
            postFullname = "t1_abc123",
            direction = -1,
        )
        assertEquals("t1_abc123", request.postFullname)
        assertEquals(
            listOf("id" to "t1_abc123", "dir" to "-1"),
            redditPostVoteFormRequest("t1_abc123", -1).fields,
        )
    }

    @Test
    fun redditCommentLikesMapToAuthoritativeVoteState() {
        fun mapped(likes: Boolean?): Int {
            val listing = RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(
                                id = "abc123",
                                name = "t1_abc123",
                                author = "someone",
                                body = "body",
                                score = 10L,
                                likes = likes,
                            ),
                        ),
                    ),
                ),
            )
            return listOf(RedditCommentListingDto(), listing).toComments(
                json = Json { ignoreUnknownKeys = true },
                nowEpochSeconds = 1_800_000_000L,
                limit = 25,
                maxDepth = 8,
            ).single().vote
        }

        assertEquals(1, mapped(true))
        assertEquals(-1, mapped(false))
        assertEquals(0, mapped(null))
        assertEquals(8, postScoreForEffectiveVote(10, 1, -1))
    }

}
