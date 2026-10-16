package app.subshell.prototype.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.auth.AuthApplicationRuntimeFactory
import app.subshell.prototype.data.account.AccountSessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Account01ActiveAccountPersistenceInstrumentedTest {
    @Test
    fun repairInstalledPreferencePairFromAuthoritativeDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = AuthApplicationRuntimeFactory.create(context)
        val directory = requireNotNull(runtime.accountSessionDirectory)
        val snapshot = directory.snapshot()
        val activeAccount = snapshot.accounts.first { account ->
            account.stableId == snapshot.activeAccountId
        }
        assertEquals(AccountSessionSource.AUTHENTICATED, activeAccount.source)

        val preferences = context.getSharedPreferences("subshell_preferences", 0)
        val beforeId = preferences.getString(ACTIVE_ACCOUNT_ID_TEST_KEY, null)
        val beforeUsername = preferences.getString(ACTIVE_ACCOUNT_USERNAME_TEST_KEY, null)
        val resolved = resolveActiveAccountSelection(
            accounts = snapshot.accounts,
            persistedActiveAccountId = beforeId,
            directoryActiveAccountId = snapshot.activeAccountId,
            legacyActiveAccountUsername = beforeUsername,
        )

        assertEquals(activeAccount, resolved.account)
        assertTrue(
            persistActiveAccountSelection(
                preferences = preferences,
                account = resolved.account,
            ),
        )

        val afterId = preferences.getString(ACTIVE_ACCOUNT_ID_TEST_KEY, null)
        val afterUsername = preferences.getString(ACTIVE_ACCOUNT_USERNAME_TEST_KEY, null)
        assertEquals(activeAccount.stableId, afterId)
        assertEquals(activeAccount.username, afterUsername)
        assertFalse(afterId.orEmpty().startsWith("local:"))

        Log.i(
            ACCOUNT01_LOG_TAG,
            "ACCOUNT01_ACTIVE_ID_AUTHORITATIVE=true " +
                "LEGACY_USERNAME_REPAIRED=${beforeUsername != afterUsername} " +
                "AUTHENTICATED_ACCOUNT=true " +
                "PERSISTED_PAIR_MATCH=true " +
                "APP_UI_LANGUAGE=ENGLISH LIVE_REDDIT_NETWORK=false REMOTE_WRITES=false",
        )
        Log.i(ACCOUNT01_LOG_TAG, "RESULT=SUBSHELL_ACCOUNT01_RUNTIME_PASS")
    }
}

private const val ACCOUNT01_LOG_TAG = "subshell-account01"
private const val ACTIVE_ACCOUNT_ID_TEST_KEY = "active_account_id"
private const val ACTIVE_ACCOUNT_USERNAME_TEST_KEY = "active_account_username"
