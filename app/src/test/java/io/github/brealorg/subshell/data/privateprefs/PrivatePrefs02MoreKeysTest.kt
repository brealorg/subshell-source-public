package io.github.brealorg.subshell.data.privateprefs

import io.github.brealorg.subshell.communityposts.communityPostWatchPreferenceKey
import io.github.brealorg.subshell.data.account.REDDIT_FAVORITES_PREFERENCES
import io.github.brealorg.subshell.data.account.REDDIT_FAVORITE_SYNC_ENABLED_KEY
import io.github.brealorg.subshell.data.drafts.TextPostSubmitAttemptStore
import io.github.brealorg.subshell.ui.RedditLinkHandlingPromptStore
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.accountPostActionsPreferenceKey
import io.github.brealorg.subshell.ui.favoriteCommunitiesPreferenceKey
import io.github.brealorg.subshell.ui.favoriteCustomFeedPathsPreferenceKey
import io.github.brealorg.subshell.ui.joinedCommunitiesPreferenceKey
import io.github.brealorg.subshell.ui.recentCommunitiesPreferenceKey
import io.github.brealorg.subshell.ui.searchHistoryPreferenceKey
import io.github.brealorg.subshell.ui.searchIndexPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRIVATEPREFS02_WHAT_THE_READER_DID_AND_VISITED_IS_SEALED_V1: the keys that say where the reader has
 * been and what they did (post actions, visits, joined and favourite communities, watch list,
 * search history, content filters) are sealed; the settings that only configure the app are not.
 * Each key is taken from the code that writes it, so a renamed key cannot stay plain unnoticed.
 */
class PrivatePrefs02MoreKeysTest {
    private val account = "reddit:me"

    @Test
    fun whatTheReaderDidToPostsAndWhereTheyHaveBeenIsSealedInTheSettingsFile() {
        val sealed = listOf(
            accountPostActionsPreferenceKey("me", "votes"),
            accountPostActionsPreferenceKey("me", "saved"),
            accountPostActionsPreferenceKey("me", "hidden"),
            accountPostActionsPreferenceKey("me", "read"),
            accountPostActionsPreferenceKey("me", "followed_users"),
            accountPostActionsPreferenceKey("me", "poll_votes"),
            joinedCommunitiesPreferenceKey("me"),
            favoriteCommunitiesPreferenceKey(account),
            favoriteCustomFeedPathsPreferenceKey(account),
            recentCommunitiesPreferenceKey(account),
            communityPostWatchPreferenceKey(account),
            SubshellSettings.COMMENT_VISIT_HISTORY,
            SubshellSettings.COMMUNITY_SORT_PREFERENCES,
            TextPostSubmitAttemptStore.KEY,
            TextPostSubmitAttemptStore.UNREADABLE_KEY,
        )
        sealed.forEach {
            assertTrue("$it is sealed", SealedKeysPreferences.isSealedKey(SealedKeysPreferences.SETTINGS_PREFERENCES_NAME, it))
        }
    }

    @Test
    fun theContentFiltersAreSealedAndNoOtherSettingIsSealedByMistake() {
        val keys = SubshellSettings().toStorageMap().keys
        val filters = keys.filter { it.startsWith("content_filter_") }
        assertTrue("there are content filter keys", filters.isNotEmpty())
        filters.forEach { assertTrue("$it is sealed", isPrivatePreferenceKey(it)) }

        // The settings that are about the app, not the reader, stay as they are.
        val sealedSettings = keys.filter(::isPrivatePreferenceKey).toSet()
        val expected = filters.toSet() + setOf(SubshellSettings.COMMUNITY_SORT_PREFERENCES)
        assertEquals(expected, sealedSettings)
    }

    @Test
    fun theBooleanUnderThePostActionsPrefixIsNotASealedValue() {
        // "hide read" is stored as a boolean under the same prefix; the layer passes booleans through.
        assertTrue(isPrivatePreferenceKey(accountPostActionsPreferenceKey("me", "hide_read")))
    }

    @Test
    fun theSearchHistoryAndIndexAreSealedInTheDeviceFileAndTheLinkPromptIsNot() {
        val file = SealedKeysPreferences.DEVICE_PREFERENCES_NAME
        assertEquals(RedditLinkHandlingPromptStore.DEVICE_PREFERENCES_NAME, file)
        assertTrue(SealedKeysPreferences.isSealedKey(file, searchHistoryPreferenceKey(account)))
        assertTrue(SealedKeysPreferences.isSealedKey(file, searchIndexPreferenceKey(account)))
        assertFalse(SealedKeysPreferences.isSealedKey(file, RedditLinkHandlingPromptStore.REDDIT_LINK_PROMPT_SEEN_V1))
        assertFalse(SealedKeysPreferences.isSealedKey(file, "update01.last_check_epoch_ms"))
    }

    @Test
    fun theFavouriteNamesAreSealedAndTheSyncSwitchIsNot() {
        val file = SealedKeysPreferences.REDDIT_FAVORITES_PREFERENCES_NAME
        assertEquals(REDDIT_FAVORITES_PREFERENCES, file)
        assertTrue(SealedKeysPreferences.isSealedKey(file, "names.$account"))
        assertFalse(SealedKeysPreferences.isSealedKey(file, REDDIT_FAVORITE_SYNC_ENABLED_KEY))
    }

    @Test
    fun theReplyTargetsOfConversationNotificationsAreSealedAndTheirBookkeepingIsNot() {
        val file = SealedKeysPreferences.CONVERSATION_REPLIES_PREFERENCES_NAME
        assertTrue(SealedKeysPreferences.isSealedKey(file, "target:event-1"))
        assertFalse(SealedKeysPreferences.isSealedKey(file, "order"))
        assertFalse(SealedKeysPreferences.isSealedKey(file, "claimed"))
    }

    @Test
    fun aFileWithNothingAboutTheReaderHasNoSealedKeys() {
        assertFalse(SealedKeysPreferences.isSealedKey("subshell_crash_reports", "exit_reasons_seen_until_epoch_ms"))
    }
}
