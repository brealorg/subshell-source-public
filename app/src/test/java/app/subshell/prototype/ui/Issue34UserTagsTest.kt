package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.usertags.decodeUserTags
import app.subshell.prototype.data.usertags.encodeUserTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue34UserTagsTest {
    private val first = authenticated("reddit:first", "first_user")
    private val second = authenticated("reddit:second", "second_user")
    private val local = localFixtureAccountSummaries(listOf("local")).single()
    private val stored = mutableMapOf<String, Map<String, UserTag>>()
    private val writes = mutableListOf<String>()

    @Test
    fun usernamesNormaliseCaseAndPrefixesAndRejectNonUsernames() {
        assertEquals("spez", normalizeUserTagUsername("Spez"))
        assertEquals("spez", normalizeUserTagUsername(" u/Spez "))
        assertEquals("spez", normalizeUserTagUsername("/u/spez"))
        assertEquals("some-user_1", normalizeUserTagUsername("/user/Some-User_1"))
        assertNull(normalizeUserTagUsername(""))
        assertNull(normalizeUserTagUsername("r/android"))
        assertNull(normalizeUserTagUsername("two words"))
    }

    @Test
    fun tagsAreTrimmedCappedAndDroppedWhenEmpty() {
        val tag = normalizedUserTagOrNull(
            UserTag(label = "  helpful \n  person ", color = UserTagColor.GREEN, note = "  note  "),
        )
        assertEquals(UserTag("helpful person", UserTagColor.GREEN, "note"), tag)
        assertEquals(
            USER_TAG_LABEL_MAX_LENGTH,
            normalizedUserTagOrNull(UserTag(label = "x".repeat(100)))!!.label.length,
        )
        assertEquals(
            USER_TAG_NOTE_MAX_LENGTH,
            normalizedUserTagOrNull(UserTag(note = "y".repeat(900)))!!.note.length,
        )
        // A cap never splits a surrogate pair.
        val emoji = "😀"
        val capped = normalizedUserTagOrNull(
            UserTag(label = "a".repeat(USER_TAG_LABEL_MAX_LENGTH - 1) + emoji),
        )!!.label
        assertEquals("a".repeat(USER_TAG_LABEL_MAX_LENGTH - 1), capped)
        assertNull(normalizedUserTagOrNull(UserTag(label = "  ", note = "\n")))
        // Note-only and label-only tags are both kept.
        assertEquals("n", normalizedUserTagOrNull(UserTag(note = "n"))!!.note)
        assertEquals("l", normalizedUserTagOrNull(UserTag(label = "l"))!!.label)
    }

    @Test
    fun codecRoundTripsAndToleratesMalformedPayloads() {
        val tags = mapOf(
            "Alice" to UserTag("friend", UserTagColor.BLUE, "met in r/android\nline two"),
            "bob" to UserTag(note = "only a note \"quoted\""),
        )
        assertEquals(
            mapOf(
                "alice" to UserTag("friend", UserTagColor.BLUE, "met in r/android\nline two"),
                "bob" to UserTag(note = "only a note \"quoted\""),
            ),
            decodeUserTags(encodeUserTags(tags)),
        )
        assertEquals(emptyMap<String, UserTag>(), decodeUserTags(""))
        assertEquals(emptyMap<String, UserTag>(), decodeUserTags("not json"))
        assertEquals(emptyMap<String, UserTag>(), decodeUserTags("[1,2]"))
        assertEquals(
            // TAGCOLOR01: the unknown colour shows grey and keeps its name.
            mapOf("carol" to UserTag("x", UserTagColor.GREY, "", unknownColorName = "MAUVE")),
            decodeUserTags(
                """{"carol":{"label":"x","color":"MAUVE"},"dave":"bad","r/x":{"label":"y"},"erin":{"label":{}}}""",
            ),
        )
    }

    @Test
    fun setTagPersistsForTheActiveAccountAndEmptyTagsRemoveIt() {
        val state = state(listOf(first, second), first.stableId)
        assertTrue(state.userTagsAvailable)
        assertNull(state.userTagFor("alice"))

        state.setUserTag("u/Alice", UserTag("friend", UserTagColor.GREEN))
        assertEquals(UserTag("friend", UserTagColor.GREEN), state.userTagFor("ALICE"))
        assertEquals(mapOf("alice" to UserTag("friend", UserTagColor.GREEN)), stored[first.stableId])
        assertEquals(listOf(first.stableId), writes)

        // Same tag again: no write.
        state.setUserTag("alice", UserTag(" friend ", UserTagColor.GREEN))
        assertEquals(1, writes.size)

        // Blank tag removes it.
        state.setUserTag("alice", UserTag(label = " ", note = ""))
        assertNull(state.userTagFor("alice"))
        assertEquals(emptyMap<String, UserTag>(), stored[first.stableId])
        assertEquals(2, writes.size)

        // Removing an absent tag and tagging a non-username persist nothing.
        state.setUserTag("alice", null)
        state.setUserTag("r/android", UserTag("x"))
        assertEquals(2, writes.size)
    }

    @Test
    fun tagsFollowTheActiveAccountAcrossSwitchAndSessionSync() {
        stored[first.stableId] = mapOf("alice" to UserTag("from first"))
        stored[second.stableId] = mapOf("alice" to UserTag("from second"))
        val state = state(listOf(first, second), first.stableId)
        assertEquals("from first", state.userTagFor("alice")?.label)

        state.switchAccount(second.stableId)
        assertEquals("from second", state.userTagFor("alice")?.label)
        state.setUserTag("bob", UserTag("only on second"))
        assertEquals(listOf(second.stableId), writes)

        state.synchronizeAccountSessions(AccountSessionDirectorySnapshot(listOf(first, second), first.stableId))
        assertEquals("from first", state.userTagFor("alice")?.label)
        assertNull(state.userTagFor("bob"))
        assertEquals(setOf("alice"), state.userTags.keys)
    }

    @Test
    fun signedOutSessionNeitherShowsNorStoresTags() {
        stored[local.stableId] = mapOf("alice" to UserTag("stale"))
        val state = state(listOf(local), local.stableId)
        assertFalse(state.userTagsAvailable)
        assertNull(state.userTagFor("alice"))
        assertTrue(state.userTags.isEmpty())
        state.setUserTag("bob", UserTag("x"))
        assertTrue(writes.isEmpty())
    }

    private fun state(
        accounts: List<AccountSessionSummary>,
        activeId: String,
    ): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = activeId,
        loadUserTags = { account -> stored[account].orEmpty() },
        persistUserTags = { account, tags ->
            writes += account
            stored[account] = tags
        },
    )

    private fun authenticated(id: String, username: String) = AccountSessionSummary(
        stableId = id,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
}
