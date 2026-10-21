package app.subshell.prototype.backup

import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.UserTagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1: private notes and tags (issue #34) in backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup04A5UserTagsTest {
    private val first = authenticated("reddit:First", "first_user")
    private val second = authenticated("reddit:second", "second_user")
    private val stored = mutableMapOf<String, Map<String, UserTag>>()

    @Test
    fun codecRoundTripsPerAccountAndHonoursMissingEmptyAndOlderSchemas() {
        val tags = mapOf(
            "reddit:First" to mapOf(
                "Alice" to PortableUserTag("friend", "GREEN", "met in r/android"),
                "bob" to PortableUserTag("", "MAUVE", "note only"),
            ),
            "reddit:second" to mapOf("carol" to PortableUserTag("mod", "BLUE", "")),
        )
        val encoded = SettingsSnapshotCodec.encode(snapshot(tags))
        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"userTags\""))
        assertEquals(
            mapOf(
                // Stable ids stay opaque (not case-folded); usernames are lowercased;
                // TAGCOLOR01: an unknown colour keeps its name (shown grey).
                "reddit:First" to mapOf(
                    "alice" to PortableUserTag("friend", "GREEN", "met in r/android"),
                    "bob" to PortableUserTag("", "MAUVE", "note only"),
                ),
                "reddit:second" to mapOf("carol" to PortableUserTag("mod", "BLUE", "")),
            ),
            SettingsSnapshotCodec.decode(encoded).userTagsByAccount,
        )

        val schemaEight = encoded.replace(
            "\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION",
            "\"schemaVersion\": 8",
        )
        assertNull(SettingsSnapshotCodec.decode(schemaEight).userTagsByAccount)

        val empty = SettingsSnapshotCodec.encode(snapshot(emptyMap()))
        assertEquals(emptyMap<String, Any>(), SettingsSnapshotCodec.decode(empty).userTagsByAccount)
        val absent = SettingsSnapshotCodec.encode(snapshot(null))
        assertFalse(absent.contains("userTags"))
        assertNull(SettingsSnapshotCodec.decode(absent).userTagsByAccount)

        val malformed = JsonObject(
            Json.parseToJsonElement(encoded).jsonObject + ("userTags" to JsonArray(emptyList())),
        ).toString()
        // BACKUP06: a module of the wrong type is corrupt, not empty, and
        // leaves the current tags unchanged (it used to wipe them).
        assertNull(SettingsSnapshotCodec.decode(malformed).userTagsByAccount)
    }

    @Test
    fun normalizationDropsInvalidRowsAndBoundsEachAccount() {
        val normalized = normalizePortableUserTagsByAccount(
            mapOf(
                "  " to mapOf("x" to PortableUserTag("x", "RED", "")),
                "acct" to (
                    mapOf(
                        "r/android" to PortableUserTag("community", "RED", ""),
                        "blank" to PortableUserTag("  ", "RED", "  "),
                    ) + (1..BACKUP04A5_MAX_TAGS_PER_ACCOUNT + 10).associate { index ->
                        "user$index" to PortableUserTag("t$index", "RED", "")
                    }
                ),
            ),
        )
        assertEquals(setOf("acct"), normalized.keys)
        val tags = normalized.getValue("acct")
        assertEquals(BACKUP04A5_MAX_TAGS_PER_ACCOUNT, tags.size)
        assertFalse(tags.containsKey("r/android"))
        assertFalse(tags.containsKey("blank"))
    }

    @Test
    fun captureCoversEverySignedInAccountButNotTheLocalSession() {
        val local = localFixtureAccountSummaries(listOf("local")).single()
        stored[first.stableId] = mapOf("alice" to UserTag("friend", UserTagColor.GREEN))
        stored[second.stableId] = mapOf("bob" to UserTag(note = "from storage"))
        stored[local.stableId] = mapOf("stale" to UserTag("x"))
        val state = state(listOf(first, second, local), first.stableId)
        state.setUserTag("carol", UserTag("live", UserTagColor.RED))

        assertEquals(
            mapOf(
                first.stableId to mapOf(
                    "alice" to PortableUserTag("friend", "GREEN", ""),
                    "carol" to PortableUserTag("live", "RED", ""),
                ),
                second.stableId to mapOf("bob" to PortableUserTag("", "GREY", "from storage")),
            ),
            state.captureUserTagsByAccount(),
        )
        assertEquals(state.captureUserTagsByAccount(), state.capturePortableBackupState().userTagsByAccount)
    }

    @Test
    fun restoreLeavesAMissingModuleAloneReplacesListedAccountsAndClearsOnEmpty() {
        stored[first.stableId] = mapOf("alice" to UserTag("keep"))
        var staged: Map<String, Map<String, UserTag>>? = null
        var calls = 0
        val state = state(listOf(first, second), first.stableId) { tags ->
            calls += 1
            staged = tags
        }

        state.restoreBackupState(settings = state.captureSettings(), joinedCommunityIdsByAccount = null)
        assertNull("missing module: nothing staged", staged)
        assertEquals("keep", state.userTagFor("alice")?.label)

        // Only the second account is listed: the active (first) account keeps its tags.
        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            userTagsByAccount = mapOf(second.stableId to mapOf("bob" to PortableUserTag("b", "TEAL", ""))),
        )
        assertEquals(mapOf(second.stableId to mapOf("bob" to UserTag("b", UserTagColor.TEAL))), staged)
        assertEquals("keep", state.userTagFor("alice")?.label)

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            userTagsByAccount = mapOf(first.stableId to mapOf("dave" to PortableUserTag("new", "PURPLE", "n"))),
        )
        assertNull(state.userTagFor("alice"))
        assertEquals(UserTag("new", UserTagColor.PURPLE, "n"), state.userTagFor("dave"))

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            userTagsByAccount = emptyMap(),
        )
        assertEquals(emptyMap<String, Any>(), staged)
        assertNull(state.userTagFor("dave"))
        assertEquals(4, calls)
    }

    @Test
    fun liveTagsOnlyChangeAfterTheDurableCommit() {
        stored[first.stableId] = mapOf("alice" to UserTag("keep"))
        val state = state(listOf(first), first.stableId) { error("simulated durable commit failure") }
        val failed = runCatching {
            state.restoreBackupState(
                settings = state.captureSettings(),
                joinedCommunityIdsByAccount = null,
                userTagsByAccount = emptyMap(),
            )
        }
        assertTrue(failed.isFailure)
        assertEquals("keep", state.userTagFor("alice")?.label)
    }

    @Test
    fun theRepositoryIncludesTagsOnlyWhenOptedIn() {
        val repository = SettingsBackupRepository(
            context = ApplicationProvider.getApplicationContext(),
            appVersion = "test",
        )
        val tags = mapOf(first.stableId to mapOf("alice" to PortableUserTag("friend", "GREEN", "")))
        assertFalse(repository.userTagsBackupEnabled())

        val off = repository.saveCurrent(
            name = "off",
            settings = SubshellSettings(),
            userTagsByAccount = tags,
        )
        assertNull(off.userTagsByAccount)

        repository.setUserTagsBackupEnabled(true)
        assertTrue(repository.userTagsBackupEnabled())
        val on = repository.saveCurrent(
            name = "on",
            settings = SubshellSettings(),
            userTagsByAccount = tags,
        )
        assertEquals(tags, on.userTagsByAccount)
    }

    private fun state(
        accounts: List<AccountSessionSummary>,
        activeId: String,
        stageUserTags: (Map<String, Map<String, UserTag>>?) -> Unit = {},
    ): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = activeId,
        loadUserTags = { id -> stored[id].orEmpty() },
        persistUserTags = { id, tags -> stored[id] = tags },
        persistBackupRestoreState = { _, _, _, _, _, _, _, _, userTags, _, _ ->
            stageUserTags(userTags)
            userTags?.forEach { (id, tags) -> stored[id] = tags }
        },
    )

    private fun authenticated(id: String, username: String) = AccountSessionSummary(
        stableId = id,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun snapshot(tags: Map<String, Map<String, PortableUserTag>>?) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-0000000000a5",
        name = "BACKUP04A5",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        userTagsByAccount = tags,
    )
}
