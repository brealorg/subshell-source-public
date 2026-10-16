package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.screens.commentRowAvatarUri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (issue22): comment avatars are on by default, also for settings saved
 * before the setting existed; the thread row shows a known avatar only while
 * the setting is on; avatars and author ids are never part of the cached
 * model.
 */
class Step3CommentAvatarsTest {
    @Test
    fun commentAvatarsAreOnByDefaultAlsoForOlderSavedSettings() {
        assertTrue(SubshellSettings().showCommentAvatars)
        val saved = SubshellSettings().toStorageMap()
        assertTrue(SubshellSettings.fromStorageMap(saved - "show_comment_avatars").showCommentAvatars)
        assertFalse(SubshellSettings.fromStorageMap(saved + ("show_comment_avatars" to "false")).showCommentAvatars)
    }

    @Test
    fun theThreadRowShowsAKnownAvatarOnlyWhileTheSettingIsOn() = runBlocking {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = OneAvatarRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        state.ensureAuthorAvatars(mapOf("alice" to "t2_a"))

        assertEquals("https://i.redd.it/t2_a.png", commentRowAvatarUri(state, "alice"))
        state.updateShowCommentAvatars(false)
        assertNull(commentRowAvatarUri(state, "alice"))
    }

    @Test
    fun avatarsAndAuthorIdsStayOutOfTheCachedModel() {
        listOf(Comment::class.java, Post::class.java).forEach { type ->
            val fields = type.declaredFields.map { field -> field.name.lowercase() }
            assertTrue("${type.simpleName}: $fields", fields.none { it.contains("authoravatar") || it.contains("authorfullname") })
        }
    }
}

private class OneAvatarRepository : IdentityMetadataRepository {
    override suspend fun refreshAuthorAvatars(
        fullnamesByUsername: Map<String, String>,
    ): RepositoryResult<Map<String, String?>> =
        RepositoryResult.Content(fullnamesByUsername.mapValues { (_, fullname) -> "https://i.redd.it/$fullname.png" })

    override fun cachedCommunityIdentity(name: String): Community? = null
    override suspend fun refreshCommunityIdentity(name: String, fallback: Community): RepositoryResult<Community> =
        RepositoryResult.Content(fallback)
    override fun cachedUserIdentity(username: String): UserFixture? = null
    override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> =
        RepositoryResult.Content(fallback)
}
