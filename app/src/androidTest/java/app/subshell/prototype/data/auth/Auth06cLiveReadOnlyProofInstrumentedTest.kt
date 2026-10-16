package app.subshell.prototype.data.auth

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Auth06cLiveReadOnlyProofInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun inspectPersistedLiveReadOnlyProofWithoutNetwork() {
        val store = EncryptedAuthSessionStore(context)
        val selection = requireNotNull(store.oauthClientSelection())
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, selection.mode)
        val registry = requireNotNull(store.accountRegistry())
        val activeId = requireNotNull(registry.activeAccountId)
        val active = requireNotNull(registry.accounts.firstOrNull { it.accountId == activeId })
        assertTrue(active.username.isNotBlank())
        val session = requireNotNull(store.session(activeId))
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, session.clientMode)
        assertEquals(
            RedditOAuth.coreReadScopes,
            session.tokens.scope.split(' ').filter(String::isNotBlank).toSet(),
        )

        val cacheStore = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context),
        )
        val snapshot = requireNotNull(
            cacheStore.readPostFeedSnapshot(auth06cHomeBestQueryKey(activeId)),
        )
        assertTrue(snapshot.posts.isNotEmpty())
        val itemCount = snapshot.posts.size
        cacheStore.close()

        Log.i(
            AUTH06C_LIVE_LOG_TAG,
            "CLIENT_MODE_REDREADER=true ACCOUNT_PRESENT=true TOKEN_PRESENT=true " +
                "SCOPES_IDENTITY_READ=true ROOM_FEED_PRESENT=true " +
                "ROOM_ITEM_COUNT=$itemCount PROCESS_REOPEN=true",
        )
        Log.i(AUTH06C_LIVE_LOG_TAG, "RESULT=SUBSHELL_AUTH06C_LIVE_REDDIT_READONLY_PROOF_PASS")
    }
}

private const val AUTH06C_LIVE_LOG_TAG = "subshell-auth06c-live"
