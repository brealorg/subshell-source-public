package app.subshell.prototype.ui

import android.content.SharedPreferences
import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.ui.state.BackgroundPostPagingExecutor
import app.subshell.prototype.ui.state.PostPagingExecutor
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.createPersistentSubshellRepository
import app.subshell.prototype.data.createRedditIdentityMetadataRepository
import app.subshell.prototype.data.itemsOrEmpty
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.drafts.ComposerDraftStore
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.data.moderation.ModeratorSavedResponseStore
import app.subshell.prototype.data.search.SEARCH_DISCOVERY_TREND_CACHE_PREFERENCE_KEY
import app.subshell.prototype.communityposts.communityPostWatchPreferenceKey
import app.subshell.prototype.data.search.decodeSavedSearches
import app.subshell.prototype.data.search.encodeSavedSearches
import app.subshell.prototype.data.usertags.decodeUserTags
import app.subshell.prototype.data.usertags.encodeUserTags
import app.subshell.prototype.data.usertags.keepUnreadableUserTags
import app.subshell.prototype.ui.theme.ThemeMode

/**
 * Builds the process's SubshellState: opens the preferences and stores,
 * wires every load/persist lambda, restores the saveable state and installs
 * the Saver. Moved out of SubshellState.kt verbatim; the class is unchanged.
 */
@Composable
fun rememberSubshellState(
    repository: SubshellRepository? = null,
    accountSessionDirectory: AccountSessionDirectory? = null,
    redditUserAgent: String? = null,
): SubshellState {
    val context = LocalContext.current
    val defaultRepository = if (repository == null) {
        remember(context) { createPersistentSubshellRepository(context) }
    } else {
        null
    }
    val resolvedRepository = repository ?: requireNotNull(defaultRepository)
    // PERF02_PAGER_LOADS_OFF_MAIN_V1 (#69): the app's post paging reads Room off Main.
    val postPagingExecutor: PostPagingExecutor = BackgroundPostPagingExecutor.shared
    val identityMetadataRepository = remember(resolvedRepository, accountSessionDirectory, redditUserAgent) {
        createRedditIdentityMetadataRepository(
            repository = resolvedRepository,
            accountSessionDirectory = accountSessionDirectory,
            userAgent = redditUserAgent,
        )
    }
    val localAccountSessions = remember(resolvedRepository) {
        localFixtureAccountSummaries(resolvedRepository.accounts().itemsOrEmpty())
    }
    val accountSessionSnapshot = accountSessionDirectory?.snapshot()
    val availableAccountSessions = accountSessionSnapshot?.accounts ?: localAccountSessions
    val directoryActiveAccountId = accountSessionSnapshot?.activeAccountId
    var restoredDestinationName by rememberSaveable { mutableStateOf(MainDestination.HOME.name) }
    var restoredSelectedSubreddit by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSelectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredPostOriginToken by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredFocusedCommentId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSelectedMediaPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredSelectedMediaIndex by rememberSaveable { mutableIntStateOf(0) }
    var restoredPostVisitId by rememberSaveable { mutableIntStateOf(0) }
    var restoredCommunitySortName by rememberSaveable { mutableStateOf(CommunitySort.HOT.name) }
    var restoredFeedVideoMuted by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var restoredMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredMediaWasManual by rememberSaveable { mutableStateOf(false) }

    fun createState(processSnapshotToken: String?): SubshellState {
        // STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243): the
        // application's files, read into memory by StartupPreferences; an
        // activity context would look up the folder on disk again per activity.
        val preferences = context.applicationContext.getSharedPreferences("subshell_preferences", 0)
        val devicePreferences = context.applicationContext.getSharedPreferences(
            RedditLinkHandlingPromptStore.DEVICE_PREFERENCES_NAME,
            0,
        )
        val composerDraftStore = ComposerDraftStore(preferences)
        val moderatorSavedResponseStore = ModeratorSavedResponseStore(preferences)
        val activeAccountSelection = resolveActiveAccountSelection(
            accounts = availableAccountSessions,
            persistedActiveAccountId = preferences.getString(ACTIVE_ACCOUNT_ID, null),
            directoryActiveAccountId = directoryActiveAccountId,
            legacyActiveAccountUsername = preferences.getString(ACTIVE_ACCOUNT_USERNAME, null),
        )
        // LOWUI1_FAILED_PREFERENCE_WRITE_IS_NOT_A_CRASH_V1 (OCB-090): a failed
        // write keeps the selection in memory; the next start migrates again.
        if (activeAccountSelection.migrationRequired) {
            persistActiveAccountSelection(
                preferences = preferences,
                account = activeAccountSelection.account,
            )
        }

        fun loadPersistedJoinedCommunityIds(username: String): Set<String> {
            val key = joinedCommunitiesPreferenceKey(username)
            return if (preferences.contains(key)) {
                normalizeJoinedCommunityIds(
                    communityIds = preferences.getStringSet(key, emptySet()).orEmpty(),
                    repository = resolvedRepository,
                )
            } else {
                defaultJoinedCommunityIds(resolvedRepository)
            }
        }

        // LOWUI1_FAILED_PREFERENCE_WRITE_IS_NOT_A_CRASH_V1 (OCB-090): the
        // in-memory list stays; the next change writes it again.
        // COMMIT01_UI_PREFERENCE_WRITES_APPLY_V1 (#243): apply() writes on a
        // background thread (the in-memory file updates at once), as Google
        // advises for UI-thread writes; a failed write is no longer logged.
        fun persistJoinedCommunityIds(username: String, communityIds: Set<String>) {
            preferences.edit()
                .putStringSet(
                    joinedCommunitiesPreferenceKey(username),
                    normalizeJoinedCommunityIds(
                        communityIds = communityIds,
                        repository = resolvedRepository,
                    ),
                )
                .apply()
        }

        fun persistBackupRestoreState(
            values: Map<String, String>,
            joinedCommunities: Map<String, Set<String>>?,
            favoriteCommunities: Map<String, Set<String>>?,
            favoriteCustomFeeds: Map<String, Set<String>>?,
            hiddenCommunities: Map<String, Set<String>>?,
            communityMuteEntries: Map<String, Set<String>>?,
            restoredPostComposerDrafts: List<PostComposerDraft>?,
            restoredModeratorSavedResponses: List<ModeratorSavedResponse>?,
            restoredUserTags: Map<String, Map<String, UserTag>>?,
            restoredSavedSearches: Map<String, List<SavedSearch>>?,
            restoredWatchedCommunities: Map<String, Set<String>>?,
        ) {
            val editor = preferences.edit()
            values.forEach { (key, value) -> editor.putSubshellPreference(key, value) }
            joinedCommunities?.forEach { (username, communityIds) ->
                editor.putStringSet(
                    joinedCommunitiesPreferenceKey(username),
                    normalizeJoinedCommunityIds(
                        communityIds = communityIds,
                        repository = resolvedRepository,
                    ),
                )
            }
            favoriteCommunities?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = favoriteCommunitiesPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, communityNames) ->
                        editor.putStringSet(
                            favoriteCommunitiesPreferenceKey(accountStableId),
                            normalizeFavoriteCommunityNames(communityNames),
                        )
                    }
                }
            }
            favoriteCustomFeeds?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = favoriteCustomFeedPathsPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, feedPaths) ->
                        editor.putStringSet(
                            favoriteCustomFeedPathsPreferenceKey(accountStableId),
                            normalizeFavoriteCustomFeedPaths(feedPaths).toSet(),
                        )
                    }
                }
            }
            hiddenCommunities?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = hiddenCommunityNamesPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, communityNames) ->
                        editor.putStringSet(
                            hiddenCommunityNamesPreferenceKey(accountStableId),
                            normalizeFavoriteCommunityNames(communityNames).toSet(),
                        )
                    }
                }
            }
            communityMuteEntries?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = communityMutesPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, entries) ->
                        editor.putStringSet(
                            communityMutesPreferenceKey(accountStableId),
                            entries.toSet(),
                        )
                    }
                }
            }
            restoredPostComposerDrafts?.let { drafts ->
                composerDraftStore.stageRestore(editor, drafts)
            }
            restoredModeratorSavedResponses?.let { responses ->
                moderatorSavedResponseStore.stageRestore(editor, responses)
            }
            // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
            restoredUserTags?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = userTagsPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, tags) ->
                        if (tags.isEmpty()) {
                            editor.remove(userTagsPreferenceKey(accountStableId))
                        } else {
                            editor.putString(
                                userTagsPreferenceKey(accountStableId),
                                encodeUserTags(tags),
                            )
                        }
                    }
                }
            }
            // ISSUE206_SAVED_SEARCHES_V1: in the same commit as the rest.
            restoredSavedSearches?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = savedSearchesPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, searches) ->
                        if (searches.isEmpty()) {
                            editor.remove(savedSearchesPreferenceKey(accountStableId))
                        } else {
                            editor.putString(
                                savedSearchesPreferenceKey(accountStableId),
                                encodeSavedSearches(searches),
                            )
                        }
                    }
                }
            }
            // ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1: in the same commit as the rest.
            restoredWatchedCommunities?.let { restored ->
                if (restored.isEmpty()) {
                    val prefix = communityPostWatchPreferenceKey("")
                    preferences.all.keys
                        .filter { key -> key.startsWith(prefix) }
                        .forEach { key -> editor.remove(key) }
                } else {
                    restored.forEach { (accountStableId, names) ->
                        if (names.isEmpty()) {
                            editor.remove(communityPostWatchPreferenceKey(accountStableId))
                        } else {
                            editor.putStringSet(communityPostWatchPreferenceKey(accountStableId), names.toSet())
                        }
                    }
                }
            }
            check(editor.commit()) {
                "Could not atomically persist restored backup state"
            }
        }

        fun loadPersistedFavoriteCommunityNames(accountStableId: String): Set<String> =
            normalizeFavoriteCommunityNames(
                preferences.getStringSet(
                    favoriteCommunitiesPreferenceKey(accountStableId),
                    emptySet(),
                ).orEmpty(),
            )

        fun persistFavoriteCommunityNames(
            accountStableId: String,
            communityNames: Set<String>,
        ) {
            preferences.edit {
                putStringSet(
                    favoriteCommunitiesPreferenceKey(accountStableId),
                    normalizeFavoriteCommunityNames(communityNames),
                )
            }
        }

        fun loadPersistedRecentCommunityVisits(
            accountStableId: String,
        ): List<RecentCommunityVisit> = decodeRecentCommunityVisits(
            preferences.getStringSet(
                recentCommunitiesPreferenceKey(accountStableId),
                emptySet(),
            ).orEmpty(),
        )

        fun persistRecentCommunityVisits(
            accountStableId: String,
            visits: List<RecentCommunityVisit>,
        ) {
            preferences.edit {
                putStringSet(
                    recentCommunitiesPreferenceKey(accountStableId),
                    encodeRecentCommunityVisits(visits),
                )
            }
        }

        fun loadPersistedSearchHistory(accountStableId: String): List<String> =
            decodeSearchHistory(
                devicePreferences.getString(
                    searchHistoryPreferenceKey(accountStableId),
                    "",
                ).orEmpty(),
            )

        fun persistSearchHistory(accountStableId: String, history: List<String>) {
            devicePreferences.edit {
                putString(
                    searchHistoryPreferenceKey(accountStableId),
                    encodeSearchHistory(history),
                )
            }
        }

        fun loadPersistedSearchIndex(accountStableId: String): List<SearchIndexEntry> =
            decodeSearchIndex(
                devicePreferences.getString(
                    searchIndexPreferenceKey(accountStableId),
                    "",
                ).orEmpty(),
            )

        fun persistSearchIndex(
            accountStableId: String,
            entries: List<SearchIndexEntry>,
        ) {
            devicePreferences.edit {
                putString(
                    searchIndexPreferenceKey(accountStableId),
                    encodeSearchIndex(entries),
                )
            }
        }

        fun loadPersistedTrendingSearchCache(): String =
            devicePreferences.getString(
                SEARCH_DISCOVERY_TREND_CACHE_PREFERENCE_KEY,
                "",
            ).orEmpty()

        fun persistTrendingSearchCache(encoded: String) {
            devicePreferences.edit {
                putString(SEARCH_DISCOVERY_TREND_CACHE_PREFERENCE_KEY, encoded)
            }
        }

        fun accountActionSet(
            username: String,
            field: String,
            fallback: Set<String>,
        ): Set<String> {
            val key = accountPostActionsPreferenceKey(username, field)
            return if (preferences.contains(key)) {
                preferences.getStringSet(key, emptySet()).orEmpty().toSet()
            } else {
                fallback
            }
        }

        fun loadPersistedAccountPostActions(username: String): AccountPostActions {
            val defaults = defaultAccountPostActions(resolvedRepository)
            val encodedDefaultVotes = encodeAccountActionMap(
                defaults.votes.mapValues { (_, direction) -> direction.toString() },
            )
            val decodedVotes = linkedMapOf<String, Int>()
            decodeAccountActionMap(
                accountActionSet(username, ACCOUNT_ACTION_FIELD_VOTES, encodedDefaultVotes),
            ).forEach { (postId, direction) ->
                direction.toIntOrNull()?.let { decodedVotes[postId] = it }
            }

            return normalizeAccountPostActions(
                actions = AccountPostActions(
                    savedPostIds = accountActionSet(
                        username,
                        ACCOUNT_ACTION_FIELD_SAVED,
                        defaults.savedPostIds,
                    ),
                    hiddenPostIds = accountActionSet(
                        username,
                        ACCOUNT_ACTION_FIELD_HIDDEN,
                        defaults.hiddenPostIds,
                    ),
                    readPostIds = accountActionSet(
                        username,
                        ACCOUNT_ACTION_FIELD_READ,
                        defaults.readPostIds,
                    ),
                    votes = decodedVotes,
                    pollVotes = decodeAccountActionMap(
                        accountActionSet(
                            username,
                            ACCOUNT_ACTION_FIELD_POLL_VOTES,
                            encodeAccountActionMap(defaults.pollVotes),
                        ),
                    ),
                    followedUsernames = accountActionSet(
                        username,
                        ACCOUNT_ACTION_FIELD_FOLLOWED_USERS,
                        defaults.followedUsernames,
                    ),
                ),
                username = username,
            )
        }

        fun persistAccountPostActions(username: String, actions: AccountPostActions) {
            val normalized = normalizeAccountPostActions(actions, username)
            val encodedVotes = encodeAccountActionMap(
                normalized.votes.mapValues { (_, direction) -> direction.toString() },
            )
            // ISSUE65_POST_ACTIONS_WRITTEN_OFF_MAIN_V1: written in the background
            // (apply), not committed on the main thread on every post open.
            preferences.edit {
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_SAVED),
                    normalized.savedPostIds,
                )
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_HIDDEN),
                    normalized.hiddenPostIds,
                )
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_READ),
                    normalized.readPostIds,
                )
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_VOTES),
                    encodedVotes,
                )
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_POLL_VOTES),
                    encodeAccountActionMap(normalized.pollVotes),
                )
                putStringSet(
                    accountPostActionsPreferenceKey(username, ACCOUNT_ACTION_FIELD_FOLLOWED_USERS),
                    normalized.followedUsernames,
                )
            }
        }

        fun <T : Enum<T>> enumValue(key: String, entries: List<T>, fallback: T): T =
            preferences.getString(key, null)
                ?.let { stored -> entries.firstOrNull { it.name == stored } }
                ?: fallback

        val legacyCommentActionPlacement = preferences.getString(
            SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT,
            null,
        )?.let { stored ->
            CommentActionPlacement.entries.firstOrNull { it.name == stored }
        }
        val interactionSideConfigured =
            preferences.contains(SubshellSettings.INTERACTION_SIDE) ||
                legacyCommentActionPlacement != null
        val interactionSide = enumValue(
            SubshellSettings.INTERACTION_SIDE,
            InteractionSide.entries,
            legacyInteractionSide(legacyCommentActionPlacement),
        )
        val drawerSidePreference = enumValue(
            SubshellSettings.DRAWER_SIDE_PREFERENCE,
            DrawerSidePreference.entries,
            DefaultSubshellSettings.drawerSidePreference,
        )
        val commentActionPreference = enumValue(
            SubshellSettings.COMMENT_ACTION_PREFERENCE,
            CommentActionPreference.entries,
            legacyCommentActionPreference(legacyCommentActionPlacement),
        )
        val labelStyle = enumValue("subreddit_label_style", SubredditLabelStyle.entries, DefaultSubshellSettings.subredditLabelStyle)
        val oldBottomBehavior = preferences.getString("bottom_navigation_behavior", null)
        val bottomFallback = if (oldBottomBehavior == "STICKY") ChromeBehavior.ALWAYS_SHOW else ChromeBehavior.HIDE_ON_SCROLL
        val topFallback = if (preferences.getBoolean("home_header_visible", true)) {
            ChromeBehavior.HIDE_ON_SCROLL
        } else {
            ChromeBehavior.ALWAYS_SHOW
        }

        val legacyVideoQuality = enumValue(
            SubshellSettings.LEGACY_VIDEO_QUALITY,
            VideoQuality.entries,
            DefaultSubshellSettings.wifiVideoQuality,
        )

        val legacyNavigationLabelMode = if (
            preferences.getBoolean(SubshellSettings.LEGACY_NAVIGATION_LABELS_VISIBLE, true)
        ) {
            NavigationLabelMode.ON
        } else {
            NavigationLabelMode.PARTIAL
        }

        return SubshellState(
            initialThemeMode = enumValue("theme_mode", ThemeMode.entries, DefaultSubshellSettings.themeMode),
            initialDestination = MainDestination.entries.firstOrNull {
                it.name == restoredDestinationName
            } ?: MainDestination.HOME,
            initialSelectedSubreddit = restoredSelectedSubreddit,
            initialSelectedPostId = restoredSelectedPostId,
            initialPostOriginToken = restoredPostOriginToken,
            initialFocusedCommentId = restoredFocusedCommentId,
            initialSelectedMediaPostId = restoredSelectedMediaPostId,
            initialSelectedMediaIndex = restoredSelectedMediaIndex,
            initialPostVisitId = restoredPostVisitId,
            initialCommunitySort = decodeCommunitySortName(restoredCommunitySortName)
                ?: CommunitySort.HOT,
            initialProcessSnapshotToken = processSnapshotToken,
            initialFeedVideoMuted = restoredFeedVideoMuted,
            initialBackgroundResumeVideoId = restoredMediaId,
            initialBackgroundResumeWasManual = restoredMediaWasManual,
            initialPostComposerDrafts = composerDraftStore.load(),
            persistPostComposerDrafts = composerDraftStore::save,
            initialModeratorSavedResponses = moderatorSavedResponseStore.load(),
            persistModeratorSavedResponses = moderatorSavedResponseStore::save,
            initialModeratorRemovalMessageTypes = loadModeratorRemovalMessageTypes(preferences),
            initialDynamicColor = preferences.getBoolean("dynamic_color", DefaultSubshellSettings.dynamicColor),
            initialPureBlackDarkTheme = preferences.getBoolean(
                SubshellSettings.PURE_BLACK_DARK_THEME,
                DefaultSubshellSettings.pureBlackDarkTheme,
            ),
            initialAppTextScale = enumValue(
                SubshellSettings.APP_TEXT_SCALE,
                AppTextScale.entries,
                DefaultSubshellSettings.appTextScale,
            ),
            initialVoteColorPalette = enumValue(
                SubshellSettings.VOTE_COLOR_PALETTE,
                VoteColorPalette.entries,
                DefaultSubshellSettings.voteColorPalette,
            ),
            initialVoteControlMode = enumValue(
                SubshellSettings.VOTE_CONTROL_MODE,
                VoteControlMode.entries,
                DefaultSubshellSettings.voteControlMode,
            ),
            initialCodeFont = enumValue(
                SubshellSettings.CODE_FONT,
                CodeFont.entries,
                DefaultSubshellSettings.codeFont,
            ),
            initialCommentActionPlacement = legacyCommentActionPlacement,
            initialInteractionSide = interactionSide,
            initialDrawerSidePreference = drawerSidePreference,
            initialCommentActionPreference = commentActionPreference,
            initialInteractionSideConfigured = interactionSideConfigured,
            initialCommentTimeFormat = enumValue(
                SubshellSettings.COMMENT_TIME_FORMAT,
                CommentTimeFormat.entries,
                DefaultSubshellSettings.commentTimeFormat,
            ),
            initialCommentCollapseMode = enumValue(
                SubshellSettings.COMMENT_COLLAPSE_MODE,
                CommentCollapseMode.entries,
                DefaultSubshellSettings.commentCollapseMode,
            ),
            initialCommentHierarchyStyle = enumValue(
                SubshellSettings.COMMENT_HIERARCHY_STYLE,
                CommentHierarchyStyle.entries,
                DefaultSubshellSettings.commentHierarchyStyle,
            ),
            initialHighlightNewComments = preferences.getBoolean(
                SubshellSettings.HIGHLIGHT_NEW_COMMENTS,
                DefaultSubshellSettings.highlightNewComments,
            ),
            initialShowCommentAvatars = preferences.getBoolean(
                SubshellSettings.SHOW_COMMENT_AVATARS,
                DefaultSubshellSettings.showCommentAvatars,
            ),
            initialTopLevelCommentJumpButton = preferences.getBoolean(
                SubshellSettings.TOP_LEVEL_COMMENT_JUMP_BUTTON,
                DefaultSubshellSettings.topLevelCommentJumpButton,
            ),
            initialUseSuggestedCommentSort = preferences.getBoolean(
                SubshellSettings.USE_SUGGESTED_COMMENT_SORT,
                DefaultSubshellSettings.useSuggestedCommentSort,
            ),
            initialCommentVisitHistoryToken = preferences.getString(
                SubshellSettings.COMMENT_VISIT_HISTORY,
                "",
            ).orEmpty(),
            initialShowUnreadMarker = preferences.getBoolean(
                SubshellSettings.SHOW_UNREAD_MARKER,
                DefaultSubshellSettings.showUnreadMarker,
            ),
            initialInboxNotificationsEnabled = preferences.getBoolean(
                SubshellSettings.INBOX_NOTIFICATIONS_ENABLED,
                INBOX_NOTIFICATIONS_ON_BY_DEFAULT,
            ),
            initialNotifyReplies = preferences.getBoolean(
                SubshellSettings.NOTIFY_REPLIES,
                DefaultSubshellSettings.notifyReplies,
            ),
            initialNotifyMentions = preferences.getBoolean(
                SubshellSettings.NOTIFY_MENTIONS,
                DefaultSubshellSettings.notifyMentions,
            ),
            initialNotifyPrivateMessages = preferences.getBoolean(
                SubshellSettings.NOTIFY_PRIVATE_MESSAGES,
                DefaultSubshellSettings.notifyPrivateMessages,
            ),
            initialNotifyRedditNotices = preferences.getBoolean(
                SubshellSettings.NOTIFY_REDDIT_NOTICES,
                DefaultSubshellSettings.notifyRedditNotices,
            ),
            initialNotifyModmail = preferences.getBoolean(
                SubshellSettings.NOTIFY_MODMAIL,
                DefaultSubshellSettings.notifyModmail,
            ),
            initialShowNsfwPosts = preferences.getBoolean(
                SubshellSettings.SHOW_NSFW_POSTS,
                DefaultSubshellSettings.showNsfwPosts,
            ),
            initialSensitiveMediaPreviewMode = enumValue(
                SubshellSettings.SENSITIVE_MEDIA_PREVIEW_MODE,
                SensitiveMediaPreviewMode.entries,
                DefaultSubshellSettings.sensitiveMediaPreviewMode,
            ),
            initialContentFilters = ContentFilters.fromStorageMap(
                ContentFilterType.entries.flatMap { type ->
                    // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1
                    listOf(type.storageKey, type.scopedStorageKey)
                }.associateWith { key -> preferences.freeTextPreference(key) },
            ),
            initialCommunitySortPreferences = decodeCommunitySortPreferences(
                preferences.getString(
                    SubshellSettings.COMMUNITY_SORT_PREFERENCES,
                    "",
                ).orEmpty(),
            ),
            initialListingTimePeriod = enumValue(
                SubshellSettings.LISTING_TIME_PERIOD,
                RedditListingTimePeriod.entries,
                DefaultSubshellSettings.listingTimePeriod,
            ),
            initialDrawerCommunitiesMode = enumValue(
                SubshellSettings.DRAWER_COMMUNITIES_MODE,
                DrawerCommunitiesMode.entries,
                DefaultSubshellSettings.drawerCommunitiesMode,
            ),
            initialDrawerRecentCommunities = preferences.getBoolean(
                SubshellSettings.DRAWER_RECENT_COMMUNITIES,
                DefaultSubshellSettings.drawerRecentCommunities,
            ),
            initialBackTwiceToExit = preferences.getBoolean(
                SubshellSettings.BACK_TWICE_TO_EXIT,
                DefaultSubshellSettings.backTwiceToExit,
            ),
            initialFilteredPostsSummary = preferences.getBoolean(
                SubshellSettings.FILTERED_POSTS_SUMMARY,
                DefaultSubshellSettings.filteredPostsSummary,
            ),
            initialSavedSearchesEnabled = preferences.getBoolean(
                SubshellSettings.SAVED_SEARCHES_ENABLED,
                DefaultSubshellSettings.savedSearchesEnabled,
            ),
            initialScrollPositionIndicator = preferences.getBoolean(
                SubshellSettings.SCROLL_POSITION_INDICATOR,
                DefaultSubshellSettings.scrollPositionIndicator,
            ),
            initialRandomNsfwCommunityPlacement = enumValue(
                SubshellSettings.RANDOM_NSFW_COMMUNITY_PLACEMENT,
                RandomNsfwCommunityPlacement.entries,
                DefaultSubshellSettings.randomNsfwCommunityPlacement,
            ),
            initialPinnedPostsPresentation = enumValue(
                SubshellSettings.PINNED_POSTS_PRESENTATION,
                PinnedPostsPresentation.entries,
                DefaultSubshellSettings.pinnedPostsPresentation,
            ),
            initialPostLayout = enumValue(
                SubshellSettings.POST_LAYOUT,
                PostLayout.entries,
                DefaultSubshellSettings.postLayout,
            ),
            initialListThumbnailSide = enumValue(
                SubshellSettings.LIST_THUMBNAIL_SIDE,
                ListThumbnailSide.entries,
                DefaultSubshellSettings.listThumbnailSide,
            ),
            initialCommunityDiscoveryLayout = enumValue(
                SubshellSettings.COMMUNITY_DISCOVERY_LAYOUT,
                CommunityDiscoveryLayout.entries,
                DefaultSubshellSettings.communityDiscoveryLayout,
            ),
            initialRequireDeviceUnlockForHiddenSubscriptions = preferences.getBoolean(
                REQUIRE_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS,
                false,
            ),
            initialNavigationLabelMode = enumValue(
                SubshellSettings.NAVIGATION_LABEL_MODE,
                NavigationLabelMode.entries,
                legacyNavigationLabelMode,
            ),
            initialTabletLayoutMode = enumValue(
                SubshellSettings.TABLET_LAYOUT_MODE,
                TabletLayoutMode.entries,
                DefaultSubshellSettings.tabletLayoutMode,
            ),
            initialTopBarBehavior = enumValue("top_bar_behavior", ChromeBehavior.entries, topFallback),
            initialBottomBarBehavior = enumValue("bottom_bar_behavior", ChromeBehavior.entries, bottomFallback),
            initialMediaLabelsVisible = preferences.getBoolean("media_labels_visible", DefaultSubshellSettings.mediaLabelsVisible),
            initialMediaCaptionMode = enumValue(
                SubshellSettings.MEDIA_CAPTION_MODE,
                MediaCaptionMode.entries,
                DefaultSubshellSettings.mediaCaptionMode,
            ),
            initialFullscreenCaptionMode = enumValue(
                SubshellSettings.FULLSCREEN_CAPTION_MODE,
                FullscreenCaptionMode.entries,
                DefaultSubshellSettings.fullscreenCaptionMode,
            ),
            initialSubredditLabelStyle = labelStyle,
            initialOpenKeyboardOnSearch = preferences.getBoolean("open_keyboard_on_search", DefaultSubshellSettings.openKeyboardOnSearch),
            initialVideoAutoplay = enumValue("video_autoplay", VideoAutoplay.entries, DefaultSubshellSettings.videoAutoplay),
            initialStartVideosMuted = preferences.getBoolean("start_videos_muted", DefaultSubshellSettings.startVideosMuted),
            initialLoopVideos = preferences.getBoolean(SubshellSettings.LOOP_VIDEOS, DefaultSubshellSettings.loopVideos),
            initialWifiVideoQuality = enumValue(
                SubshellSettings.VIDEO_QUALITY_WIFI,
                VideoQuality.entries,
                legacyVideoQuality,
            ),
            initialMobileVideoQuality = enumValue(
                SubshellSettings.VIDEO_QUALITY_MOBILE,
                VideoQuality.entries,
                if (preferences.contains(SubshellSettings.LEGACY_VIDEO_QUALITY)) {
                    legacyVideoQuality
                } else {
                    DefaultSubshellSettings.mobileVideoQuality
                },
            ),
            initialVideoTimelineSize = enumValue(
                SubshellSettings.VIDEO_TIMELINE_SIZE,
                VideoTimelineSize.entries,
                DefaultSubshellSettings.videoTimelineSize,
            ),
            initialPlaybackOptionsPlacement = enumValue(
                SubshellSettings.PLAYBACK_OPTIONS_PLACEMENT,
                PlaybackOptionsPlacement.entries,
                DefaultSubshellSettings.playbackOptionsPlacement,
            ),
            initialVideoSubtitleDefault = enumValue(
                SubshellSettings.VIDEO_SUBTITLE_DEFAULT,
                VideoSubtitleDefault.entries,
                DefaultSubshellSettings.videoSubtitleDefault,
            ),
            initialVideoAudioDefault = enumValue(
                SubshellSettings.VIDEO_AUDIO_DEFAULT,
                VideoAudioDefault.entries,
                DefaultSubshellSettings.videoAudioDefault,
            ),
            initialVideoSpeedControls = preferences.getBoolean(SubshellSettings.VIDEO_SPEED_CONTROLS, DefaultSubshellSettings.videoSpeedControls),
            initialVideoDoubleTapSeek = preferences.getBoolean(SubshellSettings.VIDEO_DOUBLE_TAP_SEEK, DefaultSubshellSettings.videoDoubleTapSeek),
            initialVideoHoldForDoubleSpeed = preferences.getBoolean(SubshellSettings.VIDEO_HOLD_FOR_DOUBLE_SPEED, DefaultSubshellSettings.videoHoldForDoubleSpeed),
            initialVideoPinchToZoom = preferences.getBoolean(SubshellSettings.VIDEO_PINCH_TO_ZOOM, DefaultSubshellSettings.videoPinchToZoom),
            initialVideoPictureInPicture = preferences.getBoolean(SubshellSettings.VIDEO_PICTURE_IN_PICTURE, DefaultSubshellSettings.videoPictureInPicture),
            initialVideoRotateFullscreen = preferences.getBoolean(SubshellSettings.VIDEO_ROTATE_FULLSCREEN, DefaultSubshellSettings.videoRotateFullscreen),
            initialVideoTechnicalDetails = preferences.getBoolean(SubshellSettings.VIDEO_TECHNICAL_DETAILS, DefaultSubshellSettings.videoTechnicalDetails),
            initialLandscapeVideoGesturesEnabled = preferences.getBoolean(
                SubshellSettings.LANDSCAPE_VIDEO_GESTURES_ENABLED,
                DefaultSubshellSettings.landscapeVideoGesturesEnabled,
            ),
            initialLandscapeBrightnessGestureEnabled = preferences.getBoolean(
                SubshellSettings.LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED,
                DefaultSubshellSettings.landscapeBrightnessGestureEnabled,
            ),
            initialLandscapeVolumeGestureEnabled = preferences.getBoolean(
                SubshellSettings.LANDSCAPE_VOLUME_GESTURE_ENABLED,
                DefaultSubshellSettings.landscapeVolumeGestureEnabled,
            ),
            initialSwipeActionsEnabled = preferences.getBoolean(
                SubshellSettings.SWIPE_ACTIONS_ENABLED,
                DefaultSubshellSettings.swipeActionsEnabled,
            ),
            initialPostSwipeLeftAction = enumValue(
                SubshellSettings.POST_SWIPE_LEFT_ACTION,
                app.subshell.prototype.ui.PostSwipeAction.entries,
                DefaultSubshellSettings.postSwipeLeftAction,
            ),
            initialPostSwipeRightAction = enumValue(
                SubshellSettings.POST_SWIPE_RIGHT_ACTION,
                app.subshell.prototype.ui.PostSwipeAction.entries,
                DefaultSubshellSettings.postSwipeRightAction,
            ),
            initialCommentSwipeLeftAction = enumValue(
                SubshellSettings.COMMENT_SWIPE_LEFT_ACTION,
                app.subshell.prototype.ui.CommentSwipeAction.entries,
                DefaultSubshellSettings.commentSwipeLeftAction,
            ),
            initialCommentSwipeRightAction = enumValue(
                SubshellSettings.COMMENT_SWIPE_RIGHT_ACTION,
                app.subshell.prototype.ui.CommentSwipeAction.entries,
                DefaultSubshellSettings.commentSwipeRightAction,
            ),
            initialReduceMotion = preferences.getBoolean("reduce_motion", DefaultSubshellSettings.reduceMotion),
            initialFeedPreviewState = enumValue(
                "feed_preview_state",
                FeedPreviewState.entries,
                FeedPreviewState.CONTENT,
            ),
            initialAccountSessions = availableAccountSessions,
            initialActiveAccountId = activeAccountSelection.account.stableId,
            repository = resolvedRepository,
            postPagingExecutor = postPagingExecutor,
            identityMetadataRepository = identityMetadataRepository,
            loadJoinedCommunityIds = ::loadPersistedJoinedCommunityIds,
            persistJoinedCommunityIds = ::persistJoinedCommunityIds,
            loadFavoriteCommunityNames = ::loadPersistedFavoriteCommunityNames,
            persistFavoriteCommunityNames = ::persistFavoriteCommunityNames,
            loadFavoriteCustomFeedPaths = { accountStableId ->
                preferences.getStringSet(
                    favoriteCustomFeedPathsPreferenceKey(accountStableId),
                    emptySet(),
                ).orEmpty().toSet()
            },
            persistFavoriteCustomFeedPaths = { accountStableId, paths ->
                preferences.edit {
                    putStringSet(
                        favoriteCustomFeedPathsPreferenceKey(accountStableId),
                        paths.toSet(),
                    )
                }
            },
            loadHiddenCommunityNames = { accountStableId ->
                preferences.getStringSet(
                    hiddenCommunityNamesPreferenceKey(accountStableId),
                    emptySet(),
                ).orEmpty().toSet()
            },
            persistHiddenCommunityNames = { accountStableId, names ->
                preferences.edit {
                    putStringSet(
                        hiddenCommunityNamesPreferenceKey(accountStableId),
                        names.toSet(),
                    )
                }
            },
            loadCommunityMuteEntries = { accountStableId ->
                preferences.getStringSet(
                    communityMutesPreferenceKey(accountStableId),
                    emptySet(),
                ).orEmpty().toSet()
            },
            persistCommunityMuteEntries = { accountStableId, entries ->
                preferences.edit {
                    putStringSet(
                        communityMutesPreferenceKey(accountStableId),
                        entries.toSet(),
                    )
                }
            },
            loadMutedCommunityDirectViewMode = {
                preferences.getString(
                    MUTED_COMMUNITY_DIRECT_VIEW_MODE_KEY,
                    null,
                )
            },
            persistMutedCommunityDirectViewMode = { modeName ->
                preferences.edit {
                    putString(
                        MUTED_COMMUNITY_DIRECT_VIEW_MODE_KEY,
                        modeName,
                    )
                }
            },
            loadRecentCommunityVisits = ::loadPersistedRecentCommunityVisits,
            persistRecentCommunityVisits = ::persistRecentCommunityVisits,
            loadSearchHistory = ::loadPersistedSearchHistory,
            persistSearchHistory = ::persistSearchHistory,
            loadSearchIndex = ::loadPersistedSearchIndex,
            persistSearchIndex = ::persistSearchIndex,
            loadTrendingSearchCache = ::loadPersistedTrendingSearchCache,
            persistTrendingSearchCache = ::persistTrendingSearchCache,
            loadUserTags = { accountStableId ->
                decodeUserTags(
                    preferences.getString(
                        userTagsPreferenceKey(accountStableId),
                        "",
                    ).orEmpty(),
                )
            },
            persistUserTags = { accountStableId, tags ->
                preferences.edit {
                    keepUnreadableUserTags(preferences, userTagsPreferenceKey(accountStableId))
                    if (tags.isEmpty()) {
                        remove(userTagsPreferenceKey(accountStableId))
                    } else {
                        putString(
                            userTagsPreferenceKey(accountStableId),
                            encodeUserTags(tags),
                        )
                    }
                }
            },
            // ISSUE206_SAVED_SEARCHES_V1: one JSON value per signed-in account.
            loadSavedSearches = { accountStableId ->
                decodeSavedSearches(
                    preferences.getString(savedSearchesPreferenceKey(accountStableId), "").orEmpty(),
                )
            },
            persistSavedSearches = { accountStableId, searches ->
                preferences.edit {
                    if (searches.isEmpty()) {
                        remove(savedSearchesPreferenceKey(accountStableId))
                    } else {
                        putString(savedSearchesPreferenceKey(accountStableId), encodeSavedSearches(searches))
                    }
                }
            },
            // ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1: one string set per
            // signed-in account; the background check reads the same key.
            loadWatchedCommunityNames = { accountStableId ->
                preferences.getStringSet(communityPostWatchPreferenceKey(accountStableId), emptySet())
                    .orEmpty()
                    .toSet()
            },
            persistWatchedCommunityNames = { accountStableId, names ->
                preferences.edit {
                    if (names.isEmpty()) {
                        remove(communityPostWatchPreferenceKey(accountStableId))
                    } else {
                        putStringSet(communityPostWatchPreferenceKey(accountStableId), names.toSet())
                    }
                }
            },
            loadAccountPostActions = ::loadPersistedAccountPostActions,
            persistAccountPostActions = ::persistAccountPostActions,
            selectAccountSession = { accountId ->
                accountSessionDirectory?.selectAccount(accountId)
            },
            persistActiveAccountId = { accountId ->
                val selectedAccount = accountSessionForPersistence(
                    accountId = accountId,
                    accountSessionDirectory = accountSessionDirectory,
                    fallbackAccounts = availableAccountSessions,
                ) ?: error("Unknown account session: $accountId")
                // LOWUI1_FAILED_PREFERENCE_WRITE_IS_NOT_A_CRASH_V1 (OCB-090): the
                // switch stands in memory; the next start opens the account stored before.
                persistActiveAccountSelection(
                    preferences = preferences,
                    account = selectedAccount,
                )
            },
            persistPreference = { key, value ->
                preferences.edit { putSubshellPreference(key, value) }
            },
            loadInboxActivityDismissedIds = { accountStableId ->
                preferences.getStringSet(
                    inboxActivityDismissedPreferenceKey(accountStableId),
                    emptySet(),
                ).orEmpty().toSet()
            },
            persistInboxActivityDismissedIds = { accountStableId, ids ->
                preferences.edit {
                    putStringSet(
                        inboxActivityDismissedPreferenceKey(accountStableId),
                        ids.toSet(),
                    )
                }
            },
            loadInboxActivityClearBoundary = { accountStableId ->
                preferences.getLong(
                    inboxActivityClearBoundaryPreferenceKey(accountStableId),
                    0L,
                )
            },
            persistInboxActivityClearBoundary = { accountStableId, boundary ->
                preferences.edit {
                    putLong(inboxActivityClearBoundaryPreferenceKey(accountStableId), boundary)
                }
            },
            loadInboxActivityPreviousClearBoundary = { accountStableId ->
                preferences.getLong(
                    inboxActivityPreviousClearBoundaryPreferenceKey(accountStableId),
                    0L,
                )
            },
            persistInboxActivityPreviousClearBoundary = { accountStableId, boundary ->
                preferences.edit {
                    putLong(
                        inboxActivityPreviousClearBoundaryPreferenceKey(accountStableId),
                        boundary,
                    )
                }
            },
            persistSettings = { values ->
                val persisted = preferences.edit().apply {
                    values.forEach { (key, value) -> putSubshellPreference(key, value) }
                }.commit()
                check(persisted) { "Could not persist restored settings" }
            },
            persistBackupRestoreState = ::persistBackupRestoreState,
        )
    }

    // ISSUE245_STATE_SURVIVES_CONFIGURATION_CHANGES_V1: a recreated activity
    // finds the state its ViewModelStore still holds; only a new process (no
    // held state) builds it from the snapshot.
    val holder = subshellStateHolder()
    val stateSaver = remember(context, resolvedRepository, accountSessionDirectory) {
        Saver<SubshellState, String>(
            save = { currentState ->
                subshellProcessSnapshotWithinBudget(
                    encodeSubshellProcessSnapshot(currentState.processStateSnapshot()),
                )
            },
            restore = { processSnapshotToken ->
                holder?.stateFor(resolvedRepository, accountSessionDirectory)
                    ?: createState(processSnapshotToken)
            },
        )
    }
    val state = rememberSaveable(saver = stateSaver) {
        holder?.stateFor(resolvedRepository, accountSessionDirectory)
            ?: createState(processSnapshotToken = null)
    }
    holder?.hold(resolvedRepository, accountSessionDirectory, state)

    val routeRestoreSnapshot = navigationRouteRestoreSnapshot(state)
    SideEffect {
        restoredSelectedPostId = routeRestoreSnapshot.selectedPostId
        restoredPostOriginToken = routeRestoreSnapshot.postOriginToken
        restoredFocusedCommentId = routeRestoreSnapshot.focusedCommentId
        restoredSelectedMediaPostId = routeRestoreSnapshot.selectedMediaPostId
        restoredSelectedMediaIndex = routeRestoreSnapshot.selectedMediaIndex
        restoredPostVisitId = routeRestoreSnapshot.postVisitId
    }

    LaunchedEffect(
        state.destination,
        state.selectedSubreddit,
        state.communitySort,
        state.feedVideoMuted,
        state.activeAnimatedMediaId,
        state.navigationLifecycleMediaId,
        state.navigationLifecycleMediaWasManual,
    ) {
        restoredDestinationName = state.destination.name
        restoredSelectedSubreddit = state.selectedSubreddit
        restoredCommunitySortName = state.communitySort.name
        restoredFeedVideoMuted = state.feedVideoMuted
        restoredMediaId = state.navigationLifecycleMediaId
        restoredMediaWasManual = state.navigationLifecycleMediaWasManual
    }

    return state
}

// ISSUE47_FREE_TEXT_PREFERENCES_STAY_TEXT_V1: Boolean settings are written
// as "true"/"false" and stored as Booleans, but a free-text value (a content
// filter list holding just the word "true" or "false") must stay text: read
// back with getString, a stored Boolean crashed every launch.
internal fun isFreeTextPreferenceKey(key: String): Boolean =
    ContentFilterType.entries.any { type -> key == type.storageKey || key == type.scopedStorageKey }

internal fun preferenceStoredAsBoolean(key: String, value: String): Boolean =
    (value == "true" || value == "false") && !isFreeTextPreferenceKey(key)

internal fun SharedPreferences.Editor.putSubshellPreference(key: String, value: String) {
    if (preferenceStoredAsBoolean(key, value)) putBoolean(key, value.toBoolean()) else putString(key, value)
}

/** Reads a free-text key; one an older build stored as a Boolean is read as its text. */
internal fun SharedPreferences.freeTextPreference(key: String): String =
    when (val stored = all[key]) {
        is String -> stored
        is Boolean -> stored.toString()
        else -> ""
    }
