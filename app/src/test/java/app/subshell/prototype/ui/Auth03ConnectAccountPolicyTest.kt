package app.subshell.prototype.ui

import app.subshell.prototype.data.account.ConnectAccountFailureKind
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth03ConnectAccountPolicyTest {
    @Test
    fun connectAccountStateMachineContainsEverySanitizedPhase() {
        assertEquals(
            setOf(
                ConnectAccountPhase.UNAVAILABLE,
                ConnectAccountPhase.IDLE,
                ConnectAccountPhase.LAUNCHING,
                ConnectAccountPhase.AWAITING_CALLBACK,
                ConnectAccountPhase.EXCHANGING,
                ConnectAccountPhase.RESOLVING_IDENTITY,
                ConnectAccountPhase.SYNCING_REDDIT_FEED,
                ConnectAccountPhase.CONNECTED,
                ConnectAccountPhase.CANCELLED,
                ConnectAccountPhase.ERROR,
            ),
            ConnectAccountPhase.entries.toSet(),
        )
        assertTrue(
            ConnectAccountUiState(
                phase = ConnectAccountPhase.AWAITING_CALLBACK,
                providerLabel = "Reddit",
            ).inProgress,
        )
        assertFalse(
            ConnectAccountUiState(
                phase = ConnectAccountPhase.CONNECTED,
                providerLabel = "Reddit",
                username = "alice",
                directoryRevision = 1L,
            ).inProgress,
        )
    }

    @Test
    fun connectAccountUiModelHasNoCredentialOrCallbackPayloadFields() {
        val fieldNames = ConnectAccountUiState::class.java.declaredFields
            .map { field -> field.name.lowercase() }
        listOf(
            "accesstoken",
            "refreshtoken",
            "authorizationcode",
            "authorizationurl",
            "oauthstate",
            "redirecturi",
        ).forEach { forbidden ->
            assertTrue(
                "Sanitized connect-account UI exposed $forbidden",
                fieldNames.none { name -> forbidden in name },
            )
        }

        val unavailable = ConnectAccountUiState.unavailable()
        assertEquals(ConnectAccountPhase.UNAVAILABLE, unavailable.phase)
        assertEquals(
            ConnectAccountFailureKind.CONFIGURATION_REQUIRED,
            unavailable.failureKind,
        )
        assertNull(unavailable.username)
    }

    @Test
    fun connectedUiStateCarriesOnlyIdentitySummaryAndRevision() {
        val connected = ConnectAccountUiState(
            phase = ConnectAccountPhase.CONNECTED,
            providerLabel = "Reddit",
            username = "alice",
            directoryRevision = 3L,
        )
        assertEquals("alice", connected.username)
        assertEquals(3L, connected.directoryRevision)
        assertNull(connected.failureKind)
        assertFalse(connected.retryable)
    }
}
