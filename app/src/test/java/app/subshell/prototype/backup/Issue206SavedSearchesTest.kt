package app.subshell.prototype.backup

import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.search.decodeSavedSearches
import app.subshell.prototype.data.search.encodeSavedSearches
import app.subshell.prototype.ui.SAVED_SEARCHES_PER_ACCOUNT
import app.subshell.prototype.ui.SavedSearch
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.normalizeSavedSearches
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE206_SAVED_SEARCHES_V1 / ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1 (#206):
 * saved searches per signed-in account, behind a setting that is off by
 * default, and their backup module (schema v10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue206SavedSearchesTest {
    private val first = authenticated("reddit:First", "first_user")
    private val second = authenticated("reddit:second", "second_user")
    private val stored = mutableMapOf<String, List<SavedSearch>>()

    private val author = SavedSearch("author:spez  cats", SearchScope.POSTS, SearchArea.ALL_SUBSHELL)
    private val inNorge = SavedSearch("fjell", SearchScope.POSTS, SearchArea.CURRENT_COMMUNITY, "r/norge")

    @Test
    fun searchesAreNormalizedDistinctNewestFirstAndBounded() {
        val normalized = normalizeSavedSearches(
            listOf(
                author,
                author.copy(query = "AUTHOR:spez cats"),
                SavedSearch("   ", SearchScope.ALL, SearchArea.ALL_SUBSHELL),
                SavedSearch("x", SearchScope.ALL, SearchArea.CURRENT_COMMUNITY, community = null),
                inNorge.copy(scope = SearchScope.USERS),
            ) + (1..SAVED_SEARCHES_PER_ACCOUNT + 5).map { SavedSearch("q$it", SearchScope.ALL, SearchArea.ALL_SUBSHELL) },
        )

        assertEquals(SAVED_SEARCHES_PER_ACCOUNT, normalized.size)
        assertEquals(SavedSearch("author:spez cats", SearchScope.POSTS, SearchArea.ALL_SUBSHELL), normalized[0])
        // A community search is a post search in that community.
        assertEquals(SavedSearch("fjell", SearchScope.POSTS, SearchArea.CURRENT_COMMUNITY, "norge"), normalized[1])
        assertEquals("q1", normalized[2].query)
    }

    @Test
    fun storedFormRoundTripsAndDropsWhatItCannotRead() {
        val searches = normalizeSavedSearches(listOf(author, inNorge))
        assertEquals(searches, decodeSavedSearches(encodeSavedSearches(searches)))
        assertEquals(emptyList<SavedSearch>(), decodeSavedSearches("not json"))
        assertEquals(
            listOf(SavedSearch("ok", SearchScope.ALL, SearchArea.ALL_SUBSHELL)),
            decodeSavedSearches(
                """[{"query":"ok","scope":"ALL","area":"ALL_SUBSHELL"},""" +
                    """{"query":"later","scope":"VIDEOS","area":"ALL_SUBSHELL"},"x"]""",
            ),
        )
    }

    @Test
    fun savingNeedsTheSettingAndFollowsTheActiveAccount() {
        val state = state(listOf(first, second), first.stableId)
        state.saveSearch(author)
        assertTrue("off by default: nothing saved", stored.isEmpty())
        assertFalse(state.savedSearchesAvailable)

        state.updateSavedSearchesEnabled(true)
        state.saveSearch(author)
        state.saveSearch(inNorge)
        state.saveSearch(author)
        assertEquals(listOf("author:spez cats", "fjell"), state.savedSearches.map { it.query })
        assertTrue(state.isSearchSaved(author))

        state.switchAccount(second.stableId)
        assertEquals(emptyList<SavedSearch>(), state.savedSearches)
        state.switchAccount(first.stableId)
        state.removeSavedSearch(inNorge)
        assertEquals(listOf("author:spez cats"), state.savedSearches.map { it.query })
        assertEquals(listOf("author:spez cats"), stored.getValue(first.stableId).map { it.query })
    }

    @Test
    fun aSavedSearchPutsItsQueryScopeAndCommunityOnTheSearchScreen() {
        val state = state(listOf(first), first.stableId)
        state.openSearch(refocus = false)
        state.applySavedSearch(inNorge)

        assertEquals("fjell", state.searchQuery)
        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals(SearchScope.POSTS, state.searchScope)
        assertEquals("norge", state.searchOriginCommunity)
        state.submitSearch()
        assertEquals(normalizeSavedSearches(listOf(inNorge)).single(), state.currentSavedSearch)
    }

    @Test
    fun codecRoundTripsPerAccountAndHonoursMissingEmptyCorruptAndOlderSchemas() {
        val searches = mapOf(
            first.stableId to listOf(author, inNorge),
            second.stableId to listOf(SavedSearch("cats", SearchScope.ALL, SearchArea.ALL_SUBSHELL)),
        )
        val encoded = SettingsSnapshotCodec.encode(snapshot(searches))
        assertTrue(encoded.contains("\"schemaVersion\": 10"))
        assertTrue(encoded.contains("\"savedSearches\""))
        assertEquals(
            normalizeSavedSearchesByAccount(searches),
            SettingsSnapshotCodec.decode(encoded).savedSearchesByAccount,
        )

        val schemaNine = encoded.replace("\"schemaVersion\": 10", "\"schemaVersion\": 9")
        assertNull(SettingsSnapshotCodec.decode(schemaNine).savedSearchesByAccount)

        val empty = SettingsSnapshotCodec.encode(snapshot(emptyMap()))
        assertEquals(emptyMap<String, Any>(), SettingsSnapshotCodec.decode(empty).savedSearchesByAccount)
        val absent = SettingsSnapshotCodec.encode(snapshot(null))
        assertFalse(absent.contains("savedSearches"))
        assertNull(SettingsSnapshotCodec.decode(absent).savedSearchesByAccount)

        val corrupt = JsonObject(
            Json.parseToJsonElement(encoded).jsonObject + ("savedSearches" to JsonArray(emptyList())),
        ).toString()
        assertNull(SettingsSnapshotCodec.decode(corrupt).savedSearchesByAccount)
    }

    @Test
    fun restoreLeavesAMissingModuleAloneReplacesListedAccountsAndClearsOnEmpty() {
        stored[first.stableId] = listOf(author)
        var staged: Map<String, List<SavedSearch>>? = null
        val state = state(listOf(first, second), first.stableId, enabled = true) { staged = it }

        state.restoreBackupState(settings = state.captureSettings(), joinedCommunityIdsByAccount = null)
        assertNull("missing module: nothing staged", staged)
        assertEquals(listOf("author:spez cats"), state.savedSearches.map { it.query })

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            savedSearchesByAccount = mapOf(second.stableId to listOf(inNorge)),
        )
        assertEquals(listOf("author:spez cats"), state.savedSearches.map { it.query })

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            savedSearchesByAccount = mapOf(first.stableId to listOf(inNorge)),
        )
        assertEquals(listOf("fjell"), state.savedSearches.map { it.query })

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            savedSearchesByAccount = emptyMap(),
        )
        assertEquals(emptyMap<String, Any>(), staged)
        assertEquals(emptyList<SavedSearch>(), state.savedSearches)
    }

    @Test
    fun captureCoversEverySignedInAccountAndEveryBackup() {
        stored[second.stableId] = listOf(inNorge)
        val state = state(listOf(first, second), first.stableId, enabled = true)
        state.saveSearch(author)
        val captured = state.captureSavedSearchesByAccount()
        assertEquals(normalizeSavedSearches(listOf(author)), captured[first.stableId])
        assertEquals(normalizeSavedSearches(listOf(inNorge)), captured[second.stableId])
        assertEquals(captured, state.capturePortableBackupState().savedSearchesByAccount)

        // Owner decision: no opt-in switch; every backup carries them.
        val repository = SettingsBackupRepository(
            context = ApplicationProvider.getApplicationContext(),
            appVersion = "test",
        )
        val saved = repository.saveCurrent(
            name = "saved searches",
            settings = SubshellSettings(),
            savedSearchesByAccount = captured,
        )
        assertEquals(captured, saved.savedSearchesByAccount)
    }

    private fun state(
        accounts: List<AccountSessionSummary>,
        activeId: String,
        enabled: Boolean = false,
        stage: (Map<String, List<SavedSearch>>?) -> Unit = {},
    ): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = activeId,
        initialSavedSearchesEnabled = enabled,
        loadSavedSearches = { id -> stored[id].orEmpty() },
        persistSavedSearches = { id, searches -> stored[id] = searches },
        persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, searches ->
            stage(searches)
            searches?.forEach { (id, list) -> stored[id] = list }
        },
    )

    private fun authenticated(id: String, username: String) = AccountSessionSummary(
        stableId = id,
        username = username,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun snapshot(searches: Map<String, List<SavedSearch>>?) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-000000000206",
        name = "ISSUE206",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        savedSearchesByAccount = searches,
    )
}
