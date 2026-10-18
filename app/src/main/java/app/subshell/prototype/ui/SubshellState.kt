package app.subshell.prototype.ui

import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.backup.PortableCommunityMute
import app.subshell.prototype.backup.PortableComposerFlair
import app.subshell.prototype.backup.PortableComposerFlairRichText
import app.subshell.prototype.backup.PortableModeratorSavedResponse
import app.subshell.prototype.backup.PortablePostComposerDraft
import app.subshell.prototype.backup.toUserTag
import app.subshell.prototype.backup.toPortableUserTag
import app.subshell.prototype.backup.normalizePortableUserTagsByAccount
import app.subshell.prototype.backup.normalizeSavedSearchesByAccount
import app.subshell.prototype.backup.PortableUserTag
import app.subshell.prototype.backup.normalizePortableModeratorSavedResponses
import app.subshell.prototype.backup.normalizePortablePostComposerDraftsByAccount

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.CommunityRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.ListCacheWriteCounter
import app.subshell.prototype.data.RepositoryPagingSnapshot
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.createFakeSubshellRepository
import app.subshell.prototype.data.itemsOrEmpty
import app.subshell.prototype.data.valueOrNull
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWriteGalleryItem
import app.subshell.prototype.data.account.textPostWriteRequestKey
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.ui.state.ModeratorResponseState
import app.subshell.prototype.data.search.TrendingSearchSeed
import app.subshell.prototype.model.reannotateRedditMedia
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.media.RedGifsPlayback
import app.subshell.prototype.media.FeedScrollDirection
import app.subshell.prototype.media.FeedViewportMotion
import app.subshell.prototype.media.FeedViewportState
import app.subshell.prototype.media.FeedViewportMotionMetrics
import app.subshell.prototype.media.MediaPerformanceTrace
import app.subshell.prototype.media.VideoVisibilityPolicy
import app.subshell.prototype.media.VideoSurfaceMetrics
import app.subshell.prototype.ui.state.CommentDraftState
import app.subshell.prototype.ui.state.CommentThreadState
import app.subshell.prototype.ui.state.CommunityPreferencesState
import app.subshell.prototype.ui.state.ContentSafetyState
import app.subshell.prototype.ui.state.IdentityMetadataState
import app.subshell.prototype.ui.state.InboxActivityState
import app.subshell.prototype.ui.state.PostFollowState
import app.subshell.prototype.ui.state.PostPagingExecutor
import app.subshell.prototype.ui.state.PostPagingState
import app.subshell.prototype.ui.state.PostSaveState
import app.subshell.prototype.ui.state.PostVoteState
import app.subshell.prototype.ui.state.ReportReasonPolicyState
import app.subshell.prototype.ui.state.SearchHistoryState
import app.subshell.prototype.ui.state.SavedSearchState
import app.subshell.prototype.ui.state.UserTagState
import app.subshell.prototype.ui.state.SettingsState
import app.subshell.prototype.ui.state.SurfaceTabState
import app.subshell.prototype.ui.state.UiRequestState
import app.subshell.prototype.ui.state.UserFlairState
import app.subshell.prototype.ui.theme.ThemeMode
import kotlin.math.abs
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditReportReasonPolicy
import app.subshell.prototype.data.account.RedditReportTargetKind
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState


enum class ProfileActivityEvidence {
    POST,
    COMMENT,
}

internal fun isUnavailablePublicProfileHttpStatus(statusCode: Int?): Boolean =
    statusCode == 404

private const val MAX_POST_COMPOSER_DRAFTS = 100

internal fun normalizedPostIdentity(value: String): String =
    value.trim().removePrefix("t3_").lowercase()

// HIDDENIDS01_HIDDEN_POSTS_MATCH_BY_IDENTITY_V1: hidden ids are stored
// normalized (no t3_, lowercase) while live posts carry the fullname, so a
// feed's hidden filter compares identities. PostCard already did (via
// effectivePostHidden); the lists kept hidden live posts as empty rows.
internal fun hiddenPostIdentities(hiddenPostIds: Collection<String>): Set<String> =
    hiddenPostIds.mapTo(HashSet(), ::normalizedPostIdentity)

internal fun Post.isHiddenBy(hiddenIdentities: Set<String>): Boolean =
    normalizedPostIdentity(id) in hiddenIdentities

internal fun postScoreForEffectiveVote(
    serverScore: Int,
    serverVote: Int,
    effectiveVote: Int,
): Int = serverScore + (effectiveVote - serverVote)

internal fun voteTargetControlEnabled(
    inFlightTargets: Set<String>,
    target: String,
): Boolean = target !in inFlightTargets

internal fun nextPostVoteDirection(
    currentVote: Int,
    pressedDirection: Int,
): Int {
    require(currentVote in -1..1) {
        "Current vote must be -1, 0, or 1"
    }
    require(pressedDirection == -1 || pressedDirection == 1) {
        "Pressed vote direction must be -1 or 1"
    }
    return if (currentVote == pressedDirection) 0 else pressedDirection
}

private fun validatedAccountSessions(
    accounts: List<AccountSessionSummary>,
): List<AccountSessionSummary> = accounts
    .distinctBy(AccountSessionSummary::stableId)
    .also { distinctAccounts ->
        require(distinctAccounts.isNotEmpty()) {
            "Subshell account session directory must expose at least one account"
        }
        require(
            distinctAccounts.map { account -> account.username.lowercase() }
                .distinct()
                .size == distinctAccounts.size,
        ) {
            "Account usernames must be unique across local and authenticated sessions"
        }
    }

internal data class ResolvedActiveAccountSelection(
    val account: AccountSessionSummary,
    val migrationRequired: Boolean,
)

internal fun resolveActiveAccountSelection(
    accounts: List<AccountSessionSummary>,
    persistedActiveAccountId: String?,
    directoryActiveAccountId: String?,
    legacyActiveAccountUsername: String?,
): ResolvedActiveAccountSelection {
    val validatedAccounts = validatedAccountSessions(accounts)
    val storedId = persistedActiveAccountId?.takeIf(String::isNotBlank)
    val directoryId = directoryActiveAccountId?.takeIf(String::isNotBlank)
    val legacyUsername = legacyActiveAccountUsername?.takeIf(String::isNotBlank)
    val selected = storedId
        ?.let { candidate ->
            validatedAccounts.firstOrNull { account -> account.stableId == candidate }
        }
        ?: directoryId
            ?.let { candidate ->
                validatedAccounts.firstOrNull { account -> account.stableId == candidate }
            }
        ?: legacyUsername
            ?.let { candidate ->
                validatedAccounts.firstOrNull { account ->
                    account.username.equals(candidate, ignoreCase = true)
                }
            }
        ?: validatedAccounts.first()

    return ResolvedActiveAccountSelection(
        account = selected,
        migrationRequired = storedId != selected.stableId ||
            legacyActiveAccountUsername != selected.username,
    )
}

internal fun persistActiveAccountSelection(
    preferences: SharedPreferences,
    account: AccountSessionSummary,
): Boolean = preferences.edit()
    .putString(ACTIVE_ACCOUNT_ID, account.stableId)
    .putString(ACTIVE_ACCOUNT_USERNAME, account.username)
    .commit()

internal fun accountSessionForPersistence(
    accountId: String,
    accountSessionDirectory: AccountSessionDirectory?,
    fallbackAccounts: List<AccountSessionSummary>,
): AccountSessionSummary? {
    val accounts =
        accountSessionDirectory?.snapshot()?.accounts
            ?: fallbackAccounts

    return accounts.firstOrNull { account ->
        account.stableId == accountId
    }
}

enum class MainDestination(val label: String) {
    HOME("Home"),
    SUBS("Communities"),
    INBOX("Inbox"),
    YOU("You"),
}

// LOWUI4_LIBRARY_RETIRED_V1 (OCB-225): nothing opened LIBRARY since the You
// profile took over Saved/Upvoted/Hidden/Drafts; the screen and destination
// are gone (a restored LIBRARY route is skipped, see SubshellProcessState).
enum class SecondaryDestination {
    SETTINGS,
    HELP,
    COMMUNITY_EXPLORE,
    NEW_MESSAGE,
}

enum class FeedSort(val label: String) {
    BEST("Best"),
    HOT("Hot"),
    NEW("New"),
    RISING("Rising"),
    TOP("Top"),
    CONTROVERSIAL("Controversial"),
}

enum class CommunitySort(val label: String) {
    HOT("Hot"),
    NEW("New"),
    RISING("Rising"),
    TOP("Top"),
    CONTROVERSIAL("Controversial"),
}

enum class FeedSource(val label: String) {
    HOME("Home"),
    POPULAR("Popular"),
    ALL("All"),
    SAVED("Saved"),
}

enum class SearchScope(val label: String) {
    ALL("All"),
    POSTS("Posts"),
    COMMUNITIES("Communities"),
    USERS("People"),
}

enum class SearchArea {
    ALL_SUBSHELL,
    MY_COMMUNITIES,
    CURRENT_COMMUNITY,
}

enum class CommentActionPlacement(val label: String) {
    LEFT("Left"),
    BALANCED("Balanced"),
    RIGHT("Right"),
}

enum class CommentTimeFormat(val label: String, val example: String) {
    RELATIVE("Relative", "2h"),
    LOCAL_24_HOUR("24-hour", "20:20"),
    LOCAL_12_HOUR("12-hour", "8:20 PM"),
}

enum class SubredditLabelStyle(val label: String, val example: String) {
    MODERN("Modern", "r/linux"),
    CLASSIC("Classic", "/r/linux"),
    PLAIN("Plain", "linux"),
}

enum class ChromeBehavior(val label: String) {
    ALWAYS_SHOW("Always show"),
    HIDE_ON_SCROLL("Hide on scroll"),
}

internal fun videoAutoplayAllows(setting: VideoAutoplay, unmetered: Boolean): Boolean = when (setting) {
    VideoAutoplay.ALWAYS -> true
    VideoAutoplay.WIFI_ONLY -> unmetered
    VideoAutoplay.NEVER -> false
}

enum class VideoAutoplay(val label: String) {
    ALWAYS("Always"),
    WIFI_ONLY("Wi-Fi only"),
    NEVER("Never"),
}

enum class VideoQuality(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
}

// VIDEO04_PLAYBACK_OPTIONS_PLACEMENT_V1 (#227, owner): where the playback options
// button shows; post and full screen by default, a reader may widen or narrow it.
// VIDEO05_SETTING_ROWS_SHOW_SHORT_VALUES_V1: the row shows a short value so its
// description keeps its width (device: one word per line); the dialog says it all.
enum class PlaybackOptionsPlacement(val label: String, val rowLabel: String) {
    POST_AND_FULLSCREEN("Post and full screen", "Post + full screen"),
    FULLSCREEN_ONLY("Full screen only", "Full screen"),
    EVERYWHERE("Feed, post and full screen", "Everywhere"),
}

internal fun videoPictureInPictureAllowed(
    enabled: Boolean,
    viewerOpen: Boolean,
    isVideo: Boolean,
    playing: Boolean,
): Boolean = enabled && viewerOpen && isVideo && playing

/** Android accepts 1:2.39 to 2.39:1; unknown sizes use 16:9. */
internal fun pictureInPictureAspect(width: Int?, height: Int?): Pair<Int, Int> {
    if (width == null || height == null || width <= 0 || height <= 0) return 16 to 9
    val ratio = width.toFloat() / height.toFloat()
    return when {
        ratio > 2.39f -> 239 to 100
        ratio < 1f / 2.39f -> 100 to 239
        else -> width to height
    }
}

internal fun playbackOptionsButtonShown(
    placement: PlaybackOptionsPlacement,
    fullscreen: Boolean,
    inPostDetail: Boolean,
): Boolean = when (placement) {
    PlaybackOptionsPlacement.EVERYWHERE -> true
    PlaybackOptionsPlacement.POST_AND_FULLSCREEN -> fullscreen || inPostDetail
    PlaybackOptionsPlacement.FULLSCREEN_ONLY -> fullscreen
}

// VIDEO05_PLAYBACK_DEFAULTS_IN_SETTINGS_V1 (#227, owner): Settings holds the
// defaults; the playback options panel overrides them for one video only.
enum class VideoSubtitleDefault(val label: String, val rowLabel: String) {
    OFF("Off", "Off"),
    WHEN_MARKED("Auto · when the video marks them", "Auto"),
    SYSTEM_CAPTIONS("Follow Android caption settings", "System"),
}

enum class VideoAudioDefault(val label: String, val rowLabel: String) {
    VIDEO_DEFAULT("The video's own", "Video's own"),
    DEVICE_LANGUAGE("Your device language", "Device"),
}

enum class VideoTimelineSize(
    val label: String,
    val trackHeightDp: Int,
    val restingThumbDiameterDp: Int,
    val scrubbingThumbDiameterDp: Int,
    val touchTargetHeightDp: Int,
) {
    COMPACT("Compact", 3, 10, 14, 48),
    STANDARD("Standard", 4, 12, 16, 48),
    LARGE("Large", 6, 16, 20, 48),
}

enum class FeedPreviewState(val label: String) {
    CONTENT("Content"),
    LOADING("Loading"),
    OFFLINE("Offline"),
    ERROR("Error"),
    EMPTY("Empty"),
}

private data class VideoSurfaceVisibility(
    val mediaId: String,
    val metrics: VideoSurfaceMetrics,
)

private data class GoogleFeedVideoVisibility(
    val viewportOwner: Any,
    val mediaId: String,
    val present: Boolean = false,
    val qualified: Boolean = false,
)

private const val JOINED_COMMUNITIES_PREFERENCE_PREFIX = "joined_community_ids."
private const val FAVORITE_COMMUNITIES_PREFERENCE_PREFIX = "favorite_community_names."
private const val RECENT_COMMUNITIES_PREFERENCE_PREFIX = "recent_community_visits."
internal const val RECENT_COMMUNITY_HISTORY_LIMIT = 20
internal const val RECENT_COMMUNITY_DRAWER_LIMIT = 5

data class RecentCommunityVisit(
    val communityName: String,
    val visitedAtEpochMillis: Long,
)

internal fun joinedCommunitiesPreferenceKey(username: String): String =
    JOINED_COMMUNITIES_PREFERENCE_PREFIX + username.trim().lowercase()

internal fun favoriteCommunitiesPreferenceKey(accountStableId: String): String =
    FAVORITE_COMMUNITIES_PREFERENCE_PREFIX + accountStableId.trim().lowercase()

internal fun recentCommunitiesPreferenceKey(accountStableId: String): String =
    RECENT_COMMUNITIES_PREFERENCE_PREFIX + accountStableId.trim().lowercase()

internal fun normalizeRecentCommunityVisits(
    visits: Iterable<RecentCommunityVisit>,
): List<RecentCommunityVisit> = visits
    .mapNotNull { visit ->
        val normalizedName = runCatching {
            normalizeRedditCommunityName(visit.communityName)
        }.getOrNull() ?: return@mapNotNull null
        visit.copy(
            communityName = normalizedName,
            visitedAtEpochMillis = visit.visitedAtEpochMillis.coerceAtLeast(0L),
        )
    }
    .groupBy { visit -> visit.communityName.lowercase() }
    .values
    .mapNotNull { sameCommunity ->
        sameCommunity.maxWithOrNull(
            compareBy<RecentCommunityVisit> { visit -> visit.visitedAtEpochMillis }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { visit -> visit.communityName },
        )
    }
    .sortedWith(
        compareByDescending<RecentCommunityVisit> { visit -> visit.visitedAtEpochMillis }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { visit -> visit.communityName },
    )
    .take(RECENT_COMMUNITY_HISTORY_LIMIT)

internal fun encodeRecentCommunityVisits(
    visits: Iterable<RecentCommunityVisit>,
): Set<String> = normalizeRecentCommunityVisits(visits)
    .mapTo(linkedSetOf()) { visit ->
        "${visit.visitedAtEpochMillis}|${visit.communityName}"
    }

internal fun decodeRecentCommunityVisits(
    encoded: Iterable<String>,
): List<RecentCommunityVisit> = normalizeRecentCommunityVisits(
    encoded.mapNotNull { entry ->
        val separator = entry.indexOf('|')
        if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
        val timestamp = entry.substring(0, separator).toLongOrNull()
            ?: return@mapNotNull null
        RecentCommunityVisit(
            communityName = entry.substring(separator + 1),
            visitedAtEpochMillis = timestamp,
        )
    },
)

internal fun normalizeFavoriteCommunityNames(
    communityNames: Iterable<String>,
): Set<String> = communityNames
    .mapNotNull { communityName ->
        runCatching { normalizeRedditCommunityName(communityName) }.getOrNull()
    }
    .distinctBy { communityName -> communityName.lowercase() }
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .toCollection(linkedSetOf())

internal fun normalizeJoinedCommunityIds(
    communityIds: Iterable<String>,
    repository: CommunityRepository = createFakeSubshellRepository(),
): Set<String> {
    val canonicalIdsByLowercase = repository.communities()
        .itemsOrEmpty()
        .associate { community ->
            community.id.lowercase() to community.id
        }
    return communityIds
        .mapNotNull { communityId -> canonicalIdsByLowercase[communityId.trim().lowercase()] }
        .toCollection(linkedSetOf())
}

internal fun defaultJoinedCommunityIds(
    repository: CommunityRepository = createFakeSubshellRepository(),
): Set<String> =
    repository.communities()
        .itemsOrEmpty()
        .filter { community -> community.joined }
        .mapTo(linkedSetOf()) { community -> community.id }

private const val ACCOUNT_POST_ACTIONS_PREFERENCE_PREFIX = "account_post_actions."
private const val ACCOUNT_ACTION_KEY_LENGTH_SEPARATOR = ':'

// ISSUE65_READ_MARKERS_CAPPED_V1: read markers are kept for the newest posts
// only (Reddit post ids grow with time), so the set no longer grows for as
// long as the app is installed.
internal const val READ_POST_IDS_LIMIT = 5_000

internal fun newestReadPostIds(postIds: Set<String>): Set<String> {
    if (postIds.size <= READ_POST_IDS_LIMIT) return postIds
    fun age(postId: String) = postId.lowercase().removePrefix("t3_")
    val kept = postIds
        .sortedWith(compareByDescending<String> { age(it).length }.thenByDescending { age(it) })
        .take(READ_POST_IDS_LIMIT)
        .toSet()
    return postIds.filterTo(linkedSetOf()) { it in kept }
}

internal const val ACCOUNT_ACTION_FIELD_SAVED = "saved"
internal const val ACCOUNT_ACTION_FIELD_HIDDEN = "hidden"
internal const val ACCOUNT_ACTION_FIELD_READ = "read"
internal const val ACCOUNT_ACTION_FIELD_VOTES = "votes"
internal const val ACCOUNT_ACTION_FIELD_POLL_VOTES = "poll_votes"
internal const val ACCOUNT_ACTION_FIELD_FOLLOWED_USERS = "followed_users"

data class AccountPostActions(
    val savedPostIds: Set<String> = emptySet(),
    val hiddenPostIds: Set<String> = emptySet(),
    val readPostIds: Set<String> = emptySet(),
    val votes: Map<String, Int> = emptyMap(),
    val pollVotes: Map<String, String> = emptyMap(),
    val followedUsernames: Set<String> = emptySet(),
)

internal fun accountPostActionsPreferenceKey(username: String, field: String): String =
    ACCOUNT_POST_ACTIONS_PREFERENCE_PREFIX +
        username.trim().lowercase() +
        "." +
        field.trim().lowercase()

internal fun encodeAccountActionMap(values: Map<String, String>): Set<String> = values.entries
    .mapNotNull { (key, value) ->
        val normalizedKey = key.trim()
        val normalizedValue = value.trim()
        if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
            null
        } else {
            normalizedKey.length.toString() +
                ACCOUNT_ACTION_KEY_LENGTH_SEPARATOR +
                normalizedKey +
                normalizedValue
        }
    }
    .toCollection(linkedSetOf())

internal fun decodeAccountActionMap(values: Iterable<String>): Map<String, String> {
    val decoded = linkedMapOf<String, String>()
    values.forEach { encoded ->
        val separatorIndex = encoded.indexOf(ACCOUNT_ACTION_KEY_LENGTH_SEPARATOR)
        if (separatorIndex <= 0) return@forEach

        val keyLength = encoded.substring(0, separatorIndex).toIntOrNull() ?: return@forEach
        val keyStart = separatorIndex + 1
        if (keyLength <= 0 || keyLength > encoded.length - keyStart) return@forEach

        val keyEnd = keyStart + keyLength
        if (keyEnd >= encoded.length) return@forEach

        val key = encoded.substring(keyStart, keyEnd).trim()
        val value = encoded.substring(keyEnd).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) decoded[key] = value
    }
    return decoded
}

internal fun normalizeAccountPostActions(
    actions: AccountPostActions,
    username: String,
): AccountPostActions {
    val normalizedUsername = username.trim().lowercase()

    fun normalizePostIds(postIds: Iterable<String>): Set<String> = postIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toCollection(linkedSetOf())

    val normalizedVotes = linkedMapOf<String, Int>()
    actions.votes.forEach { (postId, direction) ->
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isNotEmpty() && (direction == -1 || direction == 1)) {
            normalizedVotes[normalizedPostId] = direction
        }
    }

    val normalizedPollVotes = linkedMapOf<String, String>()
    actions.pollVotes.forEach { (postId, optionId) ->
        val normalizedPostId = postId.trim()
        val normalizedOptionId = optionId.trim()
        if (normalizedPostId.isNotEmpty() && normalizedOptionId.isNotEmpty()) {
            normalizedPollVotes[normalizedPostId] = normalizedOptionId
        }
    }

    val normalizedFollowedUsernames = actions.followedUsernames
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it != normalizedUsername }
        .toCollection(linkedSetOf())

    return AccountPostActions(
        savedPostIds = normalizePostIds(actions.savedPostIds),
        hiddenPostIds = normalizePostIds(actions.hiddenPostIds),
        readPostIds = newestReadPostIds(normalizePostIds(actions.readPostIds)),
        votes = normalizedVotes,
        pollVotes = normalizedPollVotes,
        followedUsernames = normalizedFollowedUsernames,
    )
}

internal fun defaultAccountPostActions(
    repository: SubshellRepository = createFakeSubshellRepository(),
): AccountPostActions = AccountPostActions(
    savedPostIds = repository.defaultSavedPostIds,
    votes = repository.defaultUpvotedPostIds.associateWith { 1 },
)

private fun initialFeedPostOrigin(
    destination: MainDestination,
    selectedSubreddit: String?,
    communitySort: CommunitySort,
): FeedPostOrigin = selectedSubreddit?.let { communityName ->
    FeedPostOrigin.Community(
        destination = destination,
        communityName = communityName,
        sort = communitySort,
    )
} ?: FeedPostOrigin.MainFeed(
    destination = destination,
    source = FeedSource.HOME,
    sort = FeedSort.BEST,
)

private const val FAVORITE_CUSTOM_FEED_PATHS_KEY_PREFIX =
    "favorite_custom_feed_paths:"

internal fun normalizeFavoriteCustomFeedPath(path: String): String? {
    val normalized = path.trim().trimEnd('/')
    val segments = normalized.split('/').filter(String::isNotBlank)
    if (
        segments.size != 4 ||
        !segments[0].equals("user", ignoreCase = true) ||
        !segments[2].equals("m", ignoreCase = true) ||
        segments[1].isBlank() ||
        segments[3].isBlank()
    ) {
        return null
    }
    return "/user/${segments[1]}/m/${segments[3]}"
}

internal fun normalizeFavoriteCustomFeedPaths(paths: Iterable<String>): List<String> =
    paths
        .mapNotNull(::normalizeFavoriteCustomFeedPath)
        .distinctBy { path -> path.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

internal fun favoriteCustomFeedPathsPreferenceKey(accountStableId: String): String =
    FAVORITE_CUSTOM_FEED_PATHS_KEY_PREFIX + accountStableId

private const val HIDDEN_COMMUNITY_NAMES_KEY_PREFIX =
    "hidden_community_names:"
private const val COMMUNITY_MUTES_KEY_PREFIX =
    "community_mutes:"

internal const val REQUIRE_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS =
    "require_device_unlock_hidden_subscriptions"

internal fun hiddenCommunityNamesPreferenceKey(accountStableId: String): String =
    HIDDEN_COMMUNITY_NAMES_KEY_PREFIX + accountStableId

internal fun communityMutesPreferenceKey(accountStableId: String): String =
    COMMUNITY_MUTES_KEY_PREFIX + accountStableId

private const val INBOX_ACTIVITY_DISMISSED_KEY_PREFIX = "inbox_activity_dismissed:"
private const val INBOX_ACTIVITY_CLEAR_BOUNDARY_KEY_PREFIX = "inbox_activity_clear_boundary:"
private const val INBOX_ACTIVITY_PREVIOUS_CLEAR_BOUNDARY_KEY_PREFIX =
    "inbox_activity_previous_clear_boundary:"

internal fun inboxActivityDismissedPreferenceKey(accountStableId: String): String =
    INBOX_ACTIVITY_DISMISSED_KEY_PREFIX + accountStableId

internal fun inboxActivityClearBoundaryPreferenceKey(accountStableId: String): String =
    INBOX_ACTIVITY_CLEAR_BOUNDARY_KEY_PREFIX + accountStableId

internal fun inboxActivityPreviousClearBoundaryPreferenceKey(accountStableId: String): String =
    INBOX_ACTIVITY_PREVIOUS_CLEAR_BOUNDARY_KEY_PREFIX + accountStableId

internal data class PendingInboxReadReceipt(
    val accountStableId: String,
    val inboxItemId: String,
    val postId: String,
    val commentFullname: String,
)


// BACKUP03C_FAVORITES_SCHEMA_V4_COMPLETE_V1
internal data class PortableBackupState(
    val settings: SubshellSettings,
    val joinedCommunityIdsByAccount: Map<String, Set<String>>,
    val favoriteCommunityNamesByAccount: Map<String, Set<String>>,
    val favoriteCustomFeedPathsByAccount: Map<String, Set<String>>,
    val hiddenCommunityNamesByAccount: Map<String, Set<String>>,
    val communityMutesByAccount: Map<String, List<PortableCommunityMute>>,
    val postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>,
    val moderatorSavedResponses: List<PortableModeratorSavedResponse>,
    val userTagsByAccount: Map<String, Map<String, PortableUserTag>>,
    // ISSUE206_SAVED_SEARCHES_V1: backup module "savedSearches" (schema v10).
    val savedSearchesByAccount: Map<String, List<SavedSearch>> = emptyMap(),
)

@Stable
class SubshellState(
    initialThemeMode: ThemeMode = DefaultSubshellSettings.themeMode,
    initialDynamicColor: Boolean = false,
    initialPureBlackDarkTheme: Boolean = false,
    initialAppTextScale: AppTextScale = AppTextScale.DEFAULT,
    initialVoteColorPalette: VoteColorPalette = VoteColorPalette.NEUTRAL,
    initialVoteControlMode: VoteControlMode = VoteControlMode.COMFORTABLE,
    initialCodeFont: CodeFont = CodeFont.JETBRAINS_MONO,
    initialCommentActionPlacement: CommentActionPlacement? = null,
    initialInteractionSide: InteractionSide = legacyInteractionSide(initialCommentActionPlacement),
    initialDrawerSidePreference: DrawerSidePreference = DrawerSidePreference.FOLLOW,
    initialCommentActionPreference: CommentActionPreference =
        legacyCommentActionPreference(initialCommentActionPlacement),
    initialInteractionSideConfigured: Boolean = initialCommentActionPlacement != null,
    initialCommentTimeFormat: CommentTimeFormat = CommentTimeFormat.RELATIVE,
    initialCommentCollapseMode: CommentCollapseMode = CommentCollapseMode.HEADER,
    initialCommentHierarchyStyle: CommentHierarchyStyle = CommentHierarchyStyle.GUIDE_LINES,
    initialSwipeActionsEnabled: Boolean = false,
    initialPostSwipeLeftAction: PostSwipeAction = DefaultSubshellSettings.postSwipeLeftAction,
    initialPostSwipeRightAction: PostSwipeAction = DefaultSubshellSettings.postSwipeRightAction,
    initialCommentSwipeLeftAction: CommentSwipeAction = DefaultSubshellSettings.commentSwipeLeftAction,
    initialCommentSwipeRightAction: CommentSwipeAction = DefaultSubshellSettings.commentSwipeRightAction,
    initialHighlightNewComments: Boolean = true,
    initialShowCommentAvatars: Boolean = true,
    initialTopLevelCommentJumpButton: Boolean = false,
    initialUseSuggestedCommentSort: Boolean = true,
    initialCommentVisitHistoryToken: String = "",
    initialShowUnreadMarker: Boolean = DefaultSubshellSettings.showUnreadMarker,
    initialInboxNotificationsEnabled: Boolean = false,
    initialNotifyReplies: Boolean = true,
    initialNotifyMentions: Boolean = true,
    initialNotifyPrivateMessages: Boolean = true,
    initialNotifyRedditNotices: Boolean = true,
    initialNotifyModmail: Boolean = true,
    initialShowNsfwPosts: Boolean = false,
    initialSensitiveMediaPreviewMode: SensitiveMediaPreviewMode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD,
    initialContentFilters: ContentFilters = ContentFilters(),
    initialCommunitySortPreferences: Map<String, CommunitySort> = emptyMap(),
    initialListingTimePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    initialNavigationLabelMode: NavigationLabelMode = NavigationLabelMode.ON,
    initialTabletLayoutMode: TabletLayoutMode = TabletLayoutMode.SPLIT,
    initialTopBarBehavior: ChromeBehavior = ChromeBehavior.HIDE_ON_SCROLL,
    initialBottomBarBehavior: ChromeBehavior = ChromeBehavior.HIDE_ON_SCROLL,
    initialMediaLabelsVisible: Boolean = false,
    initialMediaCaptionMode: MediaCaptionMode = MediaCaptionMode.OVERLAY,
    initialFullscreenCaptionMode: FullscreenCaptionMode = FullscreenCaptionMode.WITH_CONTROLS,
    initialSubredditLabelStyle: SubredditLabelStyle = SubredditLabelStyle.MODERN,
    initialOpenKeyboardOnSearch: Boolean = true,
    initialVideoAutoplay: VideoAutoplay = VideoAutoplay.ALWAYS,
    initialStartVideosMuted: Boolean = true,
    initialLoopVideos: Boolean = false,
    initialWifiVideoQuality: VideoQuality = VideoQuality.HIGH,
    initialMobileVideoQuality: VideoQuality = VideoQuality.MEDIUM,
    initialVideoTimelineSize: VideoTimelineSize = VideoTimelineSize.STANDARD,
    initialPlaybackOptionsPlacement: PlaybackOptionsPlacement = PlaybackOptionsPlacement.POST_AND_FULLSCREEN,
    initialVideoSubtitleDefault: VideoSubtitleDefault = VideoSubtitleDefault.WHEN_MARKED,
    initialVideoAudioDefault: VideoAudioDefault = VideoAudioDefault.VIDEO_DEFAULT,
    initialVideoSpeedControls: Boolean = false,
    initialVideoDoubleTapSeek: Boolean = false,
    initialVideoHoldForDoubleSpeed: Boolean = false,
    initialVideoPinchToZoom: Boolean = false,
    initialVideoPictureInPicture: Boolean = false,
    initialVideoRotateFullscreen: Boolean = false,
    initialVideoTechnicalDetails: Boolean = false,
    initialLandscapeVideoGesturesEnabled: Boolean = true,
    initialLandscapeBrightnessGestureEnabled: Boolean = true,
    initialLandscapeVolumeGestureEnabled: Boolean = true,
    initialReduceMotion: Boolean = false,
    initialFeedPreviewState: FeedPreviewState = FeedPreviewState.CONTENT,
    initialDestination: MainDestination = MainDestination.HOME,
    initialSelectedSubreddit: String? = null,
    initialSelectedPostId: String? = null,
    initialPostOriginToken: String? = null,
    initialFocusedCommentId: String? = null,
    initialSelectedMediaPostId: String? = null,
    initialSelectedMediaIndex: Int = 0,
    initialPostVisitId: Int = 0,
    initialCommunitySort: CommunitySort = CommunitySort.HOT,
    initialProcessSnapshotToken: String? = null,
    initialFeedVideoMuted: Boolean? = null,
    initialBackgroundResumeVideoId: String? = null,
    initialBackgroundResumeWasManual: Boolean = false,
    internal val repository: SubshellRepository = createFakeSubshellRepository(),
    // PERF02_PAGER_LOADS_OFF_MAIN_V1 (#69): Inline here; the app passes a background executor.
    postPagingExecutor: PostPagingExecutor = PostPagingExecutor.Inline,
    private val identityMetadataRepository: IdentityMetadataRepository? = null,
    initialAccountSessions: List<AccountSessionSummary> = localFixtureAccountSummaries(
        repository.accounts().itemsOrEmpty(),
    ),
    initialActiveAccountUsername: String? = null,
    initialActiveAccountId: String = initialActiveAccountUsername
        ?.let { username ->
            initialAccountSessions.firstOrNull { account ->
                account.username.equals(username, ignoreCase = true)
            }?.stableId
        }
        ?: initialAccountSessions.firstOrNull()?.stableId
        ?: error("Subshell account session directory must expose at least one account"),
    private val loadJoinedCommunityIds: (String) -> Set<String> = {
        defaultJoinedCommunityIds(repository)
    },
    private val persistJoinedCommunityIds: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadFavoriteCommunityNames: (String) -> Set<String> = { emptySet() },
    private val persistFavoriteCommunityNames: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadFavoriteCustomFeedPaths: (String) -> Set<String> = { emptySet() },
    private val persistFavoriteCustomFeedPaths: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadHiddenCommunityNames: (String) -> Set<String> = { emptySet() },
    private val persistHiddenCommunityNames: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadCommunityMuteEntries: (String) -> Set<String> = { emptySet() },
    private val persistCommunityMuteEntries: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadMutedCommunityDirectViewMode: () -> String? = { null },
    private val persistMutedCommunityDirectViewMode: (String) -> Unit = {},
    private val loadRecentCommunityVisits: (String) -> List<RecentCommunityVisit> = { emptyList() },
    private val persistRecentCommunityVisits: (String, List<RecentCommunityVisit>) -> Unit = { _, _ -> },
    private val loadSearchHistory: (String) -> List<String> = { emptyList() },
    private val persistSearchHistory: (String, List<String>) -> Unit = { _, _ -> },
    private val loadSearchIndex: (String) -> List<SearchIndexEntry> = { emptyList() },
    private val persistSearchIndex: (String, List<SearchIndexEntry>) -> Unit = { _, _ -> },
    private val loadTrendingSearchCache: () -> String = { "" },
    private val persistTrendingSearchCache: (String) -> Unit = {},
    private val loadUserTags: (String) -> Map<String, UserTag> = { emptyMap() },
    private val persistUserTags: (String, Map<String, UserTag>) -> Unit = { _, _ -> },
    private val loadSavedSearches: (String) -> List<SavedSearch> = { emptyList() },
    private val persistSavedSearches: (String, List<SavedSearch>) -> Unit = { _, _ -> },
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
    private val loadAccountPostActions: (String) -> AccountPostActions = {
        defaultAccountPostActions(repository)
    },
    private val persistAccountPostActions: (String, AccountPostActions) -> Unit = { _, _ -> },
    private val selectAccountSession: (String) -> Unit = {},
    private val persistActiveAccountId: (String) -> Unit = {},
    private val persistActiveAccountUsername: (String) -> Unit = {},
    private val persistPreference: (String, String) -> Unit = { _, _ -> },
    private val loadInboxActivityDismissedIds: (String) -> Set<String> = { emptySet() },
    private val persistInboxActivityDismissedIds: (String, Set<String>) -> Unit = { _, _ -> },
    private val loadInboxActivityClearBoundary: (String) -> Long = { 0L },
    private val persistInboxActivityClearBoundary: (String, Long) -> Unit = { _, _ -> },
    private val loadInboxActivityPreviousClearBoundary: (String) -> Long = { 0L },
    private val persistInboxActivityPreviousClearBoundary: (String, Long) -> Unit = { _, _ -> },
    private val persistSettings: (Map<String, String>) -> Unit = {},
    private val persistBackupRestoreState: (
        Map<String, String>,
        Map<String, Set<String>>?,
        Map<String, Set<String>>?,
        Map<String, Set<String>>?,
        Map<String, Set<String>>?,
        Map<String, Set<String>>?,
        List<PostComposerDraft>?,
        List<ModeratorSavedResponse>?,
        Map<String, Map<String, UserTag>>?,
        Map<String, List<SavedSearch>>?,
    ) -> Unit = {
        settings,
        joinedCommunities,
        favoriteCommunities,
        favoriteCustomFeeds,
        hiddenCommunities,
        communityMuteEntries,
        _,
        _,
        userTags,
        savedSearches ->
        persistSettings(settings)
        joinedCommunities?.forEach { (username, communityIds) ->
            persistJoinedCommunityIds(username, communityIds)
        }
        favoriteCommunities?.forEach { (accountStableId, communityNames) ->
            persistFavoriteCommunityNames(accountStableId, communityNames)
        }
        favoriteCustomFeeds?.forEach { (accountStableId, feedPaths) ->
            persistFavoriteCustomFeedPaths(accountStableId, feedPaths)
        }
        hiddenCommunities?.forEach { (accountStableId, communityNames) ->
            persistHiddenCommunityNames(accountStableId, communityNames)
        }
        communityMuteEntries?.forEach { (accountStableId, entries) ->
            persistCommunityMuteEntries(accountStableId, entries)
        }
        userTags?.forEach { (accountStableId, tags) ->
            persistUserTags(accountStableId, tags)
        }
        savedSearches?.forEach { (accountStableId, searches) ->
            persistSavedSearches(accountStableId, searches)
        }
    },
    initialPostComposerDrafts: List<PostComposerDraft> = emptyList(),
    private val persistPostComposerDrafts: (List<PostComposerDraft>) -> Unit = {},
    // MODRESPONSE01A3_SAVED_RESPONSES_STATE_V1
    initialModeratorSavedResponses: List<ModeratorSavedResponse> = emptyList(),
    persistModeratorSavedResponses: (List<ModeratorSavedResponse>) -> Unit = {},
    initialModeratorRemovalMessageTypes: Map<String, RedditRemovalMessageType> = emptyMap(),
    initialDrawerCommunitiesMode: DrawerCommunitiesMode = DrawerCommunitiesMode.COLLAPSIBLE,
    initialDrawerRecentCommunities: Boolean = false,
    initialBackTwiceToExit: Boolean = false,
    initialFilteredPostsSummary: Boolean = false,
    initialSavedSearchesEnabled: Boolean = false,
    initialScrollPositionIndicator: Boolean = false,
    initialRandomNsfwCommunityPlacement: RandomNsfwCommunityPlacement = RandomNsfwCommunityPlacement.OFF,
    initialPinnedPostsPresentation: PinnedPostsPresentation = PinnedPostsPresentation.CARDS,
    initialPostLayout: PostLayout = PostLayout.LARGE,
    initialListThumbnailSide: ListThumbnailSide = ListThumbnailSide.RIGHT,
    initialRequireDeviceUnlockForHiddenSubscriptions: Boolean = false,
    initialCommunityDiscoveryLayout: CommunityDiscoveryLayout = CommunityDiscoveryLayout.CAROUSEL,
) {
    private val restoredProcessSnapshot = decodeSubshellProcessSnapshot(
        encoded = initialProcessSnapshotToken,
        catalog = subshellProcessRestoreCatalog(repository),
    )
    private val processSnapshotRejected =
        initialProcessSnapshotToken != null && restoredProcessSnapshot == null

    var destination by mutableStateOf(initialDestination)
        private set
    var secondaryDestination by mutableStateOf<SecondaryDestination?>(null)
        private set
    var selectedPost by mutableStateOf(
        initialSelectedPostId?.let { postId ->
            repository.postForAccount(postId, initialActiveAccountId).valueOrNull()
        },
    )
        private set
    var selectedConversation by mutableStateOf<InboxItem?>(null)
        private set
    private val inboxActivity = InboxActivityState(
        loadInboxActivityDismissedIds = loadInboxActivityDismissedIds,
        persistInboxActivityDismissedIds = persistInboxActivityDismissedIds,
        loadInboxActivityClearBoundary = loadInboxActivityClearBoundary,
        persistInboxActivityClearBoundary = persistInboxActivityClearBoundary,
        loadInboxActivityPreviousClearBoundary = loadInboxActivityPreviousClearBoundary,
        persistInboxActivityPreviousClearBoundary = persistInboxActivityPreviousClearBoundary,
        currentTimeMillis = currentTimeMillis,
        currentInboxItems = { inboxItems },
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        resolvePost = { id ->
            posts.firstOrNull { post -> post.id == id }
                ?: repository.postForAccount(id, activeAccountId).valueOrNull()
        },
        currentRoute = { routeStack.current },
        currentConversation = { selectedConversation },
        selectConversation = { item -> selectedConversation = item },
        openPostRoute = { post, commentId, origin -> openPostRoute(post = post, commentId = commentId, origin = origin) },
        replaceCurrentPostRoute = { post, commentId, origin -> replaceCurrentPostRoute(post = post, commentId = commentId, origin = origin) },
        pushRoute = ::pushRoute,
        replaceCurrentRoute = ::replaceCurrentRoute,
        showChrome = ::showChrome,
    )
    val inboxActivityLifecycleRevision: Int
        get() = inboxActivity.inboxActivityLifecycleRevision
    private var pendingInboxReadReceipt: PendingInboxReadReceipt? by inboxActivity::pendingInboxReadReceipt
    var selectedMediaPost by mutableStateOf(
        initialSelectedMediaPostId
            ?.let { postId -> repository.postForAccount(postId, initialActiveAccountId).valueOrNull() }
            ?.takeIf { post ->
                post.kind in setOf(PostKind.VIDEO, PostKind.IMAGE, PostKind.GIF, PostKind.GALLERY)
            },
    )
        private set
    var selectedMediaIndex by mutableIntStateOf(initialSelectedMediaIndex.coerceAtLeast(0))
        private set
    var selectedSubreddit by mutableStateOf(
        initialSelectedSubreddit
            ?.let { selected -> repository.community(selected).valueOrNull()?.name },
    )
        private set
    var selectedUser by mutableStateOf<String?>(null)
        private set
    var communitySort by mutableStateOf(initialCommunitySort)
    var focusedCommentId by mutableStateOf(initialFocusedCommentId)
    var postVisitId by mutableIntStateOf(initialPostVisitId.coerceAtLeast(0))
        private set
    var feedSort by mutableStateOf(FeedSort.BEST)
    var feedSource by mutableStateOf(FeedSource.HOME)
    var hideReadPosts by mutableStateOf(false)
        private set
    var searchVisible by mutableStateOf(false)
        private set
    var searchScope by mutableStateOf(SearchScope.ALL)
        private set
    var searchArea by mutableStateOf(SearchArea.ALL_SUBSHELL)
        private set
    var searchOriginDestination by mutableStateOf(MainDestination.HOME)
        private set
    var searchOriginCommunity by mutableStateOf<String?>(null)
        private set
    private var searchSession by mutableStateOf(SearchSessionSnapshot())
    val searchQuery: String
        get() = searchSession.draftQuery
    val searchSubmittedQuery: String
        get() = searchSession.submittedQuery
    val searchSubmissionRevision: Long
        get() = searchSession.submissionRevision
    val searchHasUnsubmittedChanges: Boolean
        get() = searchSession.hasUnsubmittedChanges
    private val settings = SettingsState(
        initialThemeMode = initialThemeMode,
        initialDynamicColor = initialDynamicColor,
        initialPureBlackDarkTheme = initialPureBlackDarkTheme,
        initialAppTextScale = initialAppTextScale,
        initialVoteColorPalette = initialVoteColorPalette,
        initialVoteControlMode = initialVoteControlMode,
        initialCodeFont = initialCodeFont,
        initialInteractionSide = initialInteractionSide,
        initialDrawerSidePreference = initialDrawerSidePreference,
        initialCommentActionPreference = initialCommentActionPreference,
        initialInteractionSideConfigured = initialInteractionSideConfigured,
        initialCommentTimeFormat = initialCommentTimeFormat,
        initialCommentCollapseMode = initialCommentCollapseMode,
        initialCommentHierarchyStyle = initialCommentHierarchyStyle,
        initialSwipeActionsEnabled = initialSwipeActionsEnabled,
        initialPostSwipeLeftAction = initialPostSwipeLeftAction,
        initialPostSwipeRightAction = initialPostSwipeRightAction,
        initialCommentSwipeLeftAction = initialCommentSwipeLeftAction,
        initialCommentSwipeRightAction = initialCommentSwipeRightAction,
        initialHighlightNewComments = initialHighlightNewComments,
        initialShowCommentAvatars = initialShowCommentAvatars,
        initialTopLevelCommentJumpButton = initialTopLevelCommentJumpButton,
        initialUseSuggestedCommentSort = initialUseSuggestedCommentSort,
        initialShowUnreadMarker = initialShowUnreadMarker,
        initialInboxNotificationsEnabled = initialInboxNotificationsEnabled,
        initialNotifyReplies = initialNotifyReplies,
        initialNotifyMentions = initialNotifyMentions,
        initialNotifyPrivateMessages = initialNotifyPrivateMessages,
        initialNotifyRedditNotices = initialNotifyRedditNotices,
        initialNotifyModmail = initialNotifyModmail,
        initialDrawerCommunitiesMode = initialDrawerCommunitiesMode,
        initialDrawerRecentCommunities = initialDrawerRecentCommunities,
        initialBackTwiceToExit = initialBackTwiceToExit,
        initialFilteredPostsSummary = initialFilteredPostsSummary,
        initialSavedSearchesEnabled = initialSavedSearchesEnabled,
        initialScrollPositionIndicator = initialScrollPositionIndicator,
        initialRandomNsfwCommunityPlacement = initialRandomNsfwCommunityPlacement,
        initialPinnedPostsPresentation = initialPinnedPostsPresentation,
        initialPostLayout = initialPostLayout,
        initialListThumbnailSide = initialListThumbnailSide,
        initialCommunityDiscoveryLayout = initialCommunityDiscoveryLayout,
        initialRequireDeviceUnlockForHiddenSubscriptions = initialRequireDeviceUnlockForHiddenSubscriptions,
        initialNavigationLabelMode = initialNavigationLabelMode,
        initialTabletLayoutMode = initialTabletLayoutMode,
        initialTopBarBehavior = initialTopBarBehavior,
        initialBottomBarBehavior = initialBottomBarBehavior,
        initialMediaLabelsVisible = initialMediaLabelsVisible,
        initialMediaCaptionMode = initialMediaCaptionMode,
        initialFullscreenCaptionMode = initialFullscreenCaptionMode,
        initialSubredditLabelStyle = initialSubredditLabelStyle,
        initialOpenKeyboardOnSearch = initialOpenKeyboardOnSearch,
        initialVideoAutoplay = initialVideoAutoplay,
        initialStartVideosMuted = initialStartVideosMuted,
        initialLoopVideos = initialLoopVideos,
        initialWifiVideoQuality = initialWifiVideoQuality,
        initialMobileVideoQuality = initialMobileVideoQuality,
        initialVideoTimelineSize = initialVideoTimelineSize,
        initialPlaybackOptionsPlacement = initialPlaybackOptionsPlacement,
        initialVideoSubtitleDefault = initialVideoSubtitleDefault,
        initialVideoAudioDefault = initialVideoAudioDefault,
        initialVideoSpeedControls = initialVideoSpeedControls,
        initialVideoDoubleTapSeek = initialVideoDoubleTapSeek,
        initialVideoHoldForDoubleSpeed = initialVideoHoldForDoubleSpeed,
        initialVideoPinchToZoom = initialVideoPinchToZoom,
        initialVideoPictureInPicture = initialVideoPictureInPicture,
        initialVideoRotateFullscreen = initialVideoRotateFullscreen,
        initialVideoTechnicalDetails = initialVideoTechnicalDetails,
        initialLandscapeVideoGesturesEnabled = initialLandscapeVideoGesturesEnabled,
        initialLandscapeBrightnessGestureEnabled = initialLandscapeBrightnessGestureEnabled,
        initialLandscapeVolumeGestureEnabled = initialLandscapeVolumeGestureEnabled,
        initialReduceMotion = initialReduceMotion,
        initialFeedPreviewState = initialFeedPreviewState,
        persistPreference = persistPreference,
    )
    var settingsQuery by settings::settingsQuery
    private val uiRequests = UiRequestState()
    var searchFocusRequest by uiRequests::searchFocusRequest
    var themeMode by settings::themeMode
    var dynamicColor by settings::dynamicColor
    var pureBlackDarkTheme by settings::pureBlackDarkTheme
        private set
    var appTextScale by settings::appTextScale
        private set
    var voteColorPalette by settings::voteColorPalette
        private set
    var voteControlMode by settings::voteControlMode
        private set
    var codeFont by settings::codeFont
    var postComposerVisible by mutableStateOf(false)
        private set
    var postComposerDraft by mutableStateOf(
        restoredProcessSnapshot?.postComposerDraft ?: PostComposerDraft(),
    )
        private set
    val postComposerDrafts = mutableStateListOf<PostComposerDraft>().apply {
        addAll(
            initialPostComposerDrafts
                .filter(PostComposerDraft::hasContent)
                .distinctBy(PostComposerDraft::id)
                .sortedByDescending(PostComposerDraft::updatedAtEpochMs),
        )
    }

    // COMPOSER06_DRAFTS_LIST_PER_ACCOUNT_V1: the Drafts lists show the active
    // account's drafts; a draft from before drafts carried an account has no
    // owner yet and is shown to every account (it takes the account that
    // edits it).
    val activeAccountPostComposerDrafts: List<PostComposerDraft>
        get() = postComposerDrafts.filter { draft ->
            draft.accountStableId == null || draft.accountStableId == activeAccountId
        }
    private val commentDraft = CommentDraftState(
        initialCommentComposerVisible = restoredProcessSnapshot?.commentComposerVisible == true,
        initialCommentComposerDraft = restoredProcessSnapshot?.commentComposerDraft,
        addLocalComment = { post, body, media -> addLocalComment(post, body, media) },
        activeAccountId = { activeAccountId },
    )
    val commentComposerVisible
        get() = commentDraft.commentComposerVisible
    val commentComposerDraft
        get() = commentDraft.commentComposerDraft
    var interactionSide by settings::interactionSide
        private set
    var drawerSidePreference by settings::drawerSidePreference
        private set
    var commentActionPreference by settings::commentActionPreference
        private set
    val resolvedDrawerSide: InteractionSide
        get() = settings.resolvedDrawerSide
    val commentActionPlacement: CommentActionPlacement
        get() = settings.commentActionPlacement
    var commentActionOnboardingVisible by settings::commentActionOnboardingVisible
        private set
    var commentTimeFormat by settings::commentTimeFormat
    var commentCollapseMode by settings::commentCollapseMode
        private set
    var commentHierarchyStyle by settings::commentHierarchyStyle
        private set
    var swipeActionsEnabled by settings::swipeActionsEnabled
        private set
    var postSwipeLeftAction by settings::postSwipeLeftAction
        private set
    var postSwipeRightAction by settings::postSwipeRightAction
        private set
    var commentSwipeLeftAction by settings::commentSwipeLeftAction
        private set
    var commentSwipeRightAction by settings::commentSwipeRightAction
        private set
    var highlightNewComments by settings::highlightNewComments
    var showCommentAvatars by settings::showCommentAvatars
        private set
    var topLevelCommentJumpButton by settings::topLevelCommentJumpButton
        private set
    var useSuggestedCommentSort by settings::useSuggestedCommentSort
        private set
    private val commentThread = CommentThreadState(
        initialCommentVisitHistoryToken = initialCommentVisitHistoryToken,
        persistPreference = persistPreference,
    )
    var showUnreadMarker by settings::showUnreadMarker
    var inboxNotificationsEnabled by settings::inboxNotificationsEnabled
        private set
    // NOTIFY01A1_LIVE_GRANULAR_POLICY_V1
    var notifyReplies by settings::notifyReplies
        private set
    var notifyMentions by settings::notifyMentions
        private set
    var notifyPrivateMessages by settings::notifyPrivateMessages
        private set
    var notifyRedditNotices by settings::notifyRedditNotices
        private set
    var notifyModmail by settings::notifyModmail
        private set
    private var showNsfwPostsState by mutableStateOf(initialShowNsfwPosts)

    // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: RedGIFs is asked only while this is on.
    var showNsfwPosts: Boolean
        get() = showNsfwPostsState
        private set(value) {
            showNsfwPostsState = value
            RedGifsPlayback.allowed = value
        }
    var sensitiveMediaPreviewMode by mutableStateOf(initialSensitiveMediaPreviewMode)
        private set
    var contentFilters by mutableStateOf(initialContentFilters)
        private set
    var communitySortPreferences by mutableStateOf(
        decodeCommunitySortPreferences(
            encodeCommunitySortPreferences(initialCommunitySortPreferences),
        ),
    )
        private set
    var listingTimePeriod by mutableStateOf(initialListingTimePeriod)
        private set
    var drawerCommunitiesMode by settings::drawerCommunitiesMode
        private set
    var drawerRecentCommunities by settings::drawerRecentCommunities
        private set
    var backTwiceToExit by settings::backTwiceToExit
        private set
    var filteredPostsSummary by settings::filteredPostsSummary
        private set
    var savedSearchesEnabled by settings::savedSearchesEnabled
        private set
    var scrollPositionIndicator by settings::scrollPositionIndicator
        private set
    var randomNsfwCommunityPlacement by settings::randomNsfwCommunityPlacement
        private set
    var pinnedPostsPresentation by settings::pinnedPostsPresentation
        private set
    var postLayout by settings::postLayout
        private set
    var listThumbnailSide by settings::listThumbnailSide
        private set
    var communityDiscoveryLayout by settings::communityDiscoveryLayout
        private set
    var requireDeviceUnlockForHiddenSubscriptions by settings::requireDeviceUnlockForHiddenSubscriptions
        private set
    var communityExploreSearchFocusRequest by uiRequests::communityExploreSearchFocusRequest
        private set
    var navigationLabelMode by settings::navigationLabelMode
    var tabletLayoutMode by settings::tabletLayoutMode
        private set
    var topBarBehavior by settings::topBarBehavior
    var bottomBarBehavior by settings::bottomBarBehavior
    var chromeVisible by mutableStateOf(true)
    var drawerOpenRequest by uiRequests::drawerOpenRequest
    var accountSwitcherOpenRequest by uiRequests::accountSwitcherOpenRequest
    var homeScrollToTopRequest by uiRequests::homeScrollToTopRequest
    var mediaLabelsVisible by settings::mediaLabelsVisible
    var mediaCaptionMode by settings::mediaCaptionMode
        private set
    var fullscreenCaptionMode by settings::fullscreenCaptionMode
        private set
    var subredditLabelStyle by settings::subredditLabelStyle
    var openKeyboardOnSearch by settings::openKeyboardOnSearch
    var videoAutoplay by settings::videoAutoplay

    // VIDEO02_AUTOPLAY_FOLLOWS_WIFI_ONLY_V1 (#227): "Wi-Fi only" was never read;
    // every check asked only for NEVER, so videos autoplayed on mobile data.
    var videoNetworkUnmetered by mutableStateOf(true)
        private set

    val videoAutoplayAllowed: Boolean
        get() = videoAutoplayAllows(videoAutoplay, videoNetworkUnmetered)

    fun updateVideoNetworkUnmetered(unmetered: Boolean) {
        if (videoNetworkUnmetered == unmetered) return
        videoNetworkUnmetered = unmetered
        // Like switching autoplay off: a video the reader started keeps playing.
        if (!videoAutoplayAllowed) feedAutoplayMediaId = null
        reconcileVideoPlayback()
    }
    var startVideosMuted by settings::startVideosMuted
    var loopVideos by settings::loopVideos
    var feedVideoMuted by mutableStateOf(
        when {
            restoredProcessSnapshot != null -> restoredProcessSnapshot.feedVideoMuted
            processSnapshotRejected -> initialStartVideosMuted
            else -> initialFeedVideoMuted ?: initialStartVideosMuted
        },
    )
        private set
    var wifiVideoQuality by settings::wifiVideoQuality
    var mobileVideoQuality by settings::mobileVideoQuality
    var videoTimelineSize by settings::videoTimelineSize
    var playbackOptionsPlacement by settings::playbackOptionsPlacement
    var videoSubtitleDefault by settings::videoSubtitleDefault
    var videoAudioDefault by settings::videoAudioDefault
    var videoSpeedControls by settings::videoSpeedControls
    var videoDoubleTapSeek by settings::videoDoubleTapSeek
    var videoHoldForDoubleSpeed by settings::videoHoldForDoubleSpeed
    var videoPinchToZoom by settings::videoPinchToZoom
    var videoPictureInPicture by settings::videoPictureInPicture
    var videoRotateFullscreen by settings::videoRotateFullscreen
    var videoTechnicalDetails by settings::videoTechnicalDetails
    var landscapeVideoGesturesEnabled by settings::landscapeVideoGesturesEnabled
        private set
    var landscapeBrightnessGestureEnabled by settings::landscapeBrightnessGestureEnabled
        private set
    var landscapeVolumeGestureEnabled by settings::landscapeVolumeGestureEnabled
        private set
    var reduceMotion by settings::reduceMotion
    var systemRemovesAnimations by settings::systemRemovesAnimations
    val motionReduced: Boolean
        get() = settings.motionReduced
    var activeAnimatedMediaId by mutableStateOf<String?>(null)
    var feedPreviewState by settings::feedPreviewState
    private val accountSessionCatalog = mutableStateListOf<AccountSessionSummary>().apply {
        val accounts = validatedAccountSessions(initialAccountSessions)
        addAll(accounts)
    }

    var activeAccountId by mutableStateOf(
        initialActiveAccountId.takeIf { candidate ->
            accountSessionCatalog.any { it.stableId == candidate }
        } ?: accountSessionCatalog.first().stableId,
    )
        private set

    var activeAccountUsername by mutableStateOf(
        accountSessionCatalog.first { it.stableId == activeAccountId }.username,
    )
        private set

    private var accumulatedScrollPx = 0f
    private var scrollDirection = 0
    private var nextPostVisitId = maxOf(
        initialPostVisitId.coerceAtLeast(0),
        restoredProcessSnapshot
            ?.routeStack
            ?.entries
            ?.filterIsInstance<SubshellRoute.Post>()
            ?.maxOfOrNull { route -> route.postRoute.visitId }
            ?: 0,
    )
    private val routedPostsByVisitId = mutableMapOf<Int, Post>()
    private val consumedPostEntryVisitIds = mutableSetOf<Int>()
    private val feedScrollAnchorStore = FeedScrollAnchorStore(
        restoredProcessSnapshot?.scrollAnchors.orEmpty(),
        activeAccount = { activeAccountId },
    )
    private val surfaceTabs = SurfaceTabState(
        initialUserProfileTabs = restoredProcessSnapshot?.userProfileTabs.orEmpty(),
    )
    private var routeStack by mutableStateOf(
        restoredProcessSnapshot?.routeStack
            ?: if (processSnapshotRejected) {
                SubshellRouteStack.forDestination(
                    destination = MainDestination.HOME,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                )
            } else {
                SubshellRouteStack.restored(
                    destination = initialDestination,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                    selectedCommunity = selectedSubreddit,
                    communitySort = initialCommunitySort,
                    postRoute = selectedPost?.let { post ->
                        FeedPostRoute(
                            postId = post.id,
                            focusedCommentId = initialFocusedCommentId,
                            visitId = initialPostVisitId.coerceAtLeast(0),
                            origin = decodeFeedPostOrigin(initialPostOriginToken)
                                ?: initialFeedPostOrigin(
                                    destination = initialDestination,
                                    selectedSubreddit = initialSelectedSubreddit,
                                    communitySort = initialCommunitySort,
                                ),
                        )
                    },
                    mediaPostId = selectedMediaPost?.id,
                    mediaIndex = initialSelectedMediaIndex,
                )
            },
    )
    private val videoSurfaceVisibility = mutableStateMapOf<Any, VideoSurfaceVisibility>()
    private val googleFeedVideoViewports = mutableMapOf<Any, SubshellRoute>()
    private val googleFeedVideoVisibility = linkedMapOf<Any, GoogleFeedVideoVisibility>()
    private var googleFeedVideoRoute: SubshellRoute? = null
    internal var googleFeedVisibilityEpoch by mutableIntStateOf(0)
        private set
    internal val googleFeedVideoForeground: Boolean
        get() = videoPlaybackForeground
    internal val googleFeedOwnsPlayback: Boolean
        get() = googleFeedVideoRoute == routeStack.current &&
            selectedPost == null && selectedMediaPost == null

    private var activeVideoMediaId: String? = null
    private var manuallyStartedVideoId: String? = null
    private var manuallyPausedVideoId: String? = null
    private var scrubbingVideoId: String? = null
    private var mediaPresentationHandoffId: String? = null
    private var feedAutoplayMediaId: String? = null
    private var backgroundResumeVideoId: String? = when {
        restoredProcessSnapshot != null -> restoredProcessSnapshot.backgroundResumeMediaId
        processSnapshotRejected -> null
        else -> initialBackgroundResumeVideoId
    }
    private var backgroundResumeWasManual = when {
        restoredProcessSnapshot != null -> restoredProcessSnapshot.backgroundResumeWasManual
        processSnapshotRejected -> false
        else -> initialBackgroundResumeWasManual && initialBackgroundResumeVideoId != null
    }
    private var backgroundResumePending = backgroundResumeVideoId != null
    var feedViewportState by mutableStateOf(FeedViewportState(motion = FeedViewportMotion.FLINGING))
        private set
    private var feedViewportMotionMetrics = FeedViewportMotionMetrics()
    var foregroundResumeRequest by uiRequests::foregroundResumeRequest
        private set
    var activeVideoTransitionId by mutableLongStateOf(0L)
        private set
    var preloadVideoMediaId by mutableStateOf<String?>(null)
        private set
    var videoFeedGeneration by mutableLongStateOf(0L)
        private set
    private var orderedFeedVideoIds: List<String> = emptyList()
    private var terminalFeedVideoId: String? = null
    private var videoPlaybackForeground = true

    internal val navigationLifecycleMediaId: String?
        get() = backgroundResumeVideoId ?: activeVideoMediaId

    internal val navigationLifecycleMediaWasManual: Boolean
        get() = when {
            backgroundResumeVideoId != null -> backgroundResumeWasManual
            activeVideoMediaId != null -> manuallyStartedVideoId == activeVideoMediaId
            else -> false
        }

    internal val activeFeedPostRoute: FeedPostRoute?
        get() = (routeStack.surface as? SubshellRoute.Post)?.postRoute

    internal val currentNavigationRoute: SubshellRoute
        get() = routeStack.current

    internal val navigationRouteDepth: Int
        get() = routeStack.depth

    internal val navigationRouteKinds: List<String>
        get() = routeStack.entries.map { route -> route.kindName }

    // ISSUE74_POST_VISIT_STATE_IS_PRUNED_V1: the post visits still on the stack.
    internal val livePostVisitIds: Set<Int>
        get() = routeStack.entries
            .mapNotNull { route -> (route as? SubshellRoute.Post)?.postRoute?.visitId }
            .toSet()

    internal fun feedScrollAnchor(key: FeedScrollKey): FeedScrollAnchor =
        postPaging.feedScrollAnchor(key)

    internal fun updateFeedScrollAnchor(
        key: FeedScrollKey,
        itemKey: String?,
        fallbackIndex: Int,
        pixelOffset: Int,
    ) {
        postPaging.updateFeedScrollAnchor(key, itemKey, fallbackIndex, pixelOffset)
    }

    internal fun captureFeedScrollAnchor(
        key: FeedScrollKey,
        itemKey: String?,
        fallbackIndex: Int,
        pixelOffset: Int,
    ) {
        postPaging.captureFeedScrollAnchor(key, itemKey, fallbackIndex, pixelOffset)
    }

    internal fun releaseFeedScrollAnchor(key: FeedScrollKey) {
        postPaging.releaseFeedScrollAnchor(key)
    }

    internal fun captureFeedReturnAnchor(
        key: FeedScrollKey,
        itemKey: String,
        itemOffset: Int,
    ) {
        postPaging.captureFeedReturnAnchor(key, itemKey, itemOffset)
    }

    internal fun feedReturnAnchor(key: FeedScrollKey): FeedReturnAnchor? =
        postPaging.feedReturnAnchor(key)

    internal fun releaseFeedReturnAnchor(key: FeedScrollKey) {
        postPaging.releaseFeedReturnAnchor(key)
    }

    internal fun feedScrollAnchorSnapshot(): Map<FeedScrollKey, FeedScrollAnchor> =
        postPaging.feedScrollAnchorSnapshot()

    internal fun processStateSnapshot(): SubshellProcessSnapshot {
        refreshCurrentRouteSnapshot()
        return SubshellProcessSnapshot(
            routeStack = routeStack,
            scrollAnchors = feedScrollAnchorStore.snapshot(),
            userProfileTabs = surfaceTabs.userProfileTabs.toMap(),
            postComposerDraft = postComposerDraft,
            commentComposerVisible = commentComposerVisible,
            commentComposerDraft = commentComposerDraft,
            feedVideoMuted = feedVideoMuted,
            backgroundResumeMediaId = navigationLifecycleMediaId,
            backgroundResumeWasManual = navigationLifecycleMediaWasManual,
        )
    }

    internal fun userProfileTab(username: String): Int =
        surfaceTabs.userProfileTab(username)

    internal fun selectUserProfileTab(username: String, tab: Int) {
        surfaceTabs.selectUserProfileTab(username, tab)
    }

    val savedPostIds
        get() = postSaves.savedPostIds
    val hiddenPostIds
        get() = contentSafety.hiddenPostIds
    val readPostIds = mutableStateListOf<String>()
    val revealedMediaIds = mutableStateListOf<String>()
    private val revealedNsfwMediaIds = mutableSetOf<String>()
    private val postVotes = PostVoteState(
        repository = repository,
        persistActiveAccountPostActions = ::persistActiveAccountPostActions,
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        localMutationsAllowedProvider = { localMutationsAllowed },
    )
    val votes
        get() = postVotes.votes
    private val postSaves = PostSaveState(
        repository = repository,
        persistActiveAccountPostActions = ::persistActiveAccountPostActions,
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        localMutationsAllowedProvider = { localMutationsAllowed },
    )
    private val postFollows = PostFollowState(
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
    )
    private val contentSafety = ContentSafetyState(
        persistActiveAccountPostActions = ::persistActiveAccountPostActions,
        activeAccountIdProvider = { activeAccountId },
        activeAccountUsernameProvider = { activeAccountUsername },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        localMutationsAllowedProvider = { localMutationsAllowed },
    )
    private val reportReasonPolicyState = ReportReasonPolicyState(
        isAuthenticated = { activeAccountIsAuthenticated },
        activeAccountIdProvider = { activeAccountId },
    )
    private val userFlair = UserFlairState(
        isAuthenticated = { activeAccountIsAuthenticated },
        activeAccountIdProvider = { activeAccountId },
    )
    val userFlairUiState: UserFlairUiState
        get() = userFlair.userFlairUiState
    val reportReasonCommunityInfoState: RedditCommunityInfoUiState
        get() = reportReasonPolicyState.reportReasonCommunityInfoState
    val pollVotes = mutableStateMapOf<String, String>()
    val galleryIndices = mutableStateMapOf<String, Int>()
    private var repositoryRevision by mutableIntStateOf(0)
    private val identityMetadata = IdentityMetadataState(
        repository = repository,
        identityMetadataRepository = identityMetadataRepository,
        isAuthenticated = { activeAccountIsAuthenticated },
    )
    private val moderatorResponses = ModeratorResponseState(
        initialResponses = initialModeratorSavedResponses,
        persist = persistModeratorSavedResponses,
        currentTimeMillis = currentTimeMillis,
        initialMessageTypesByAccount = initialModeratorRemovalMessageTypes,
        persistMessageType = { accountStableId, type ->
            persistPreference(moderatorRemovalMessageTypeKey(accountStableId), type.name)
        },
    )

    fun moderatorRemovalMessageType(accountStableId: String): RedditRemovalMessageType =
        moderatorResponses.messageTypeFor(accountStableId)

    fun rememberModeratorRemovalMessageType(accountStableId: String, type: RedditRemovalMessageType) {
        moderatorResponses.rememberMessageType(accountStableId, type)
    }
    val moderatorSavedResponses: List<ModeratorSavedResponse>
        get() = moderatorResponses.responses

    fun saveModeratorResponse(title: String, body: String): ModeratorSavedResponse? =
        moderatorResponses.save(title, body)

    fun deleteModeratorResponse(id: String) {
        moderatorResponses.delete(id)
    }

    private val postPaging = PostPagingState(
        repository = repository,
        feedScrollAnchorStore = feedScrollAnchorStore,
        isPostDeleted = ::isPostDeleted,
        executor = postPagingExecutor,
    )
    internal val postPagingInvalidationRevision: Int
        get() = postPaging.postPagingInvalidationRevision
    private val postPagingSnapshots
        get() = postPaging.postPagingSnapshots

    private val communityPreferences = CommunityPreferencesState(
        repository = repository,
        communityFor = ::communityFor,
        communityIdentityFor = ::communityIdentityFor,
        loadJoinedCommunityIds = loadJoinedCommunityIds,
        persistJoinedCommunityIds = persistJoinedCommunityIds,
        loadFavoriteCommunityNames = loadFavoriteCommunityNames,
        persistFavoriteCommunityNames = persistFavoriteCommunityNames,
        loadFavoriteCustomFeedPaths = loadFavoriteCustomFeedPaths,
        persistFavoriteCustomFeedPaths = persistFavoriteCustomFeedPaths,
        loadHiddenCommunityNames = loadHiddenCommunityNames,
        persistHiddenCommunityNames = persistHiddenCommunityNames,
        loadCommunityMuteEntries = loadCommunityMuteEntries,
        persistCommunityMuteEntries = persistCommunityMuteEntries,
        loadMutedCommunityDirectViewMode = loadMutedCommunityDirectViewMode,
        persistMutedCommunityDirectViewMode = persistMutedCommunityDirectViewMode,
        loadRecentCommunityVisits = loadRecentCommunityVisits,
        persistRecentCommunityVisits = persistRecentCommunityVisits,
        currentTimeMillis = currentTimeMillis,
        activeAccountIdProvider = { activeAccountId },
        activeAccountUsernameProvider = { activeAccountUsername },
        localMutationsAllowedProvider = { localMutationsAllowed },
        accountSessionCatalogProvider = { accountSessionCatalog },
    )
    val joinedCommunityIds
        get() = communityPreferences.joinedCommunityIds
    val favoriteCommunityNames
        get() = communityPreferences.favoriteCommunityNames
    val effectiveFavoriteCommunityNames: List<String>
        get() = communityPreferences.effectiveFavoriteCommunityNames
    internal var redditFavoriteSync by communityPreferences::redditFavoriteSync
    internal var redditFavoriteWriter by communityPreferences::redditFavoriteWriter
    internal var redditCustomFeedFavoriteWriter by communityPreferences::redditCustomFeedFavoriteWriter
    val favoriteCustomFeedPaths
        get() = communityPreferences.favoriteCustomFeedPaths
    val hiddenCommunityNames
        get() = communityPreferences.hiddenCommunityNames
    var communityMuteClockEpochMillis by communityPreferences::communityMuteClockEpochMillis
        private set
    val communityMutes
        get() = communityPreferences.communityMutes
    var mutedCommunityDirectViewMode by communityPreferences::mutedCommunityDirectViewMode
        private set

    val requestedCommunityMuteEditorName: String?
        get() = communityPreferences.requestedCommunityMuteEditorName

    val recentCommunityVisits
        get() = communityPreferences.recentCommunityVisits
    val followedUsernames = mutableStateListOf<String>()
    val readInboxIds
        get() = inboxActivity.readInboxIds
    val collapsedCommentIds
        get() = commentThread.collapsedCommentIds
    private val searchHistory = SearchHistoryState(
        loadSearchHistory = loadSearchHistory,
        persistSearchHistory = persistSearchHistory,
        loadSearchIndex = loadSearchIndex,
        persistSearchIndex = persistSearchIndex,
        loadTrendingSearchCache = loadTrendingSearchCache,
        persistTrendingSearchCache = persistTrendingSearchCache,
        currentTimeMillis = currentTimeMillis,
        activeAccountIdProvider = { activeAccountId },
    )
    val recentSearches
        get() = searchHistory.recentSearches
    val searchIndexEntries
        get() = searchHistory.searchIndexEntries
    val trendingSearchRefreshInProgress
        get() = searchHistory.trendingSearchRefreshInProgress
    internal val trendingSearchSeeds: List<TrendingSearchSeed>
        get() = searchHistory.trendingSearchSeeds
    private val userTagState = UserTagState(
        loadUserTags = loadUserTags,
        persistUserTags = persistUserTags,
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        authenticatedAccountIdsProvider = {
            accountSessionCatalog
                .filter { account -> account.source == AccountSessionSource.AUTHENTICATED }
                .map { account -> account.stableId }
        },
    )
    private val savedSearchState = SavedSearchState(
        loadSavedSearches = loadSavedSearches,
        persistSavedSearches = persistSavedSearches,
        activeAccountIdProvider = { activeAccountId },
        activeAccountIsAuthenticatedProvider = { activeAccountIsAuthenticated },
        authenticatedAccountIdsProvider = {
            accountSessionCatalog
                .filter { account -> account.source == AccountSessionSource.AUTHENTICATED }
                .map { account -> account.stableId }
        },
    )

    // ISSUE206_SAVED_SEARCHES_V1 (#206): shown and saved only with the
    // setting on and a signed-in account.
    val savedSearchesAvailable: Boolean
        get() = savedSearchesEnabled && savedSearchState.available
    val savedSearches: List<SavedSearch>
        get() = if (savedSearchesAvailable) savedSearchState.saved else emptyList()

    /** The submitted search on screen, as it would be saved; null before a submit. */
    val currentSavedSearch: SavedSearch?
        get() = searchSubmittedQuery.takeIf(String::isNotBlank)?.let { query ->
            normalizedSavedSearchOrNull(
                SavedSearch(
                    query = query,
                    scope = searchScope,
                    area = searchArea,
                    community = searchOriginCommunity.takeIf { searchArea == SearchArea.CURRENT_COMMUNITY },
                ),
            )
        }

    fun isSearchSaved(search: SavedSearch): Boolean =
        savedSearchesAvailable && savedSearchState.isSaved(search)

    fun saveSearch(search: SavedSearch) {
        if (savedSearchesAvailable) savedSearchState.save(search)
    }

    fun removeSavedSearch(search: SavedSearch) {
        if (savedSearchesAvailable) savedSearchState.remove(search)
    }

    /** Puts [search]'s query, scope and area on the search screen, ready to submit. */
    fun applySavedSearch(search: SavedSearch) {
        val normalized = normalizedSavedSearchOrNull(search) ?: return
        if (normalized.area == SearchArea.CURRENT_COMMUNITY) {
            searchOriginCommunity = normalized.community
        }
        searchArea = if (normalized.area in availableSearchAreas) normalized.area else SearchArea.ALL_SUBSHELL
        searchScope = normalized.scope.takeIf { it in availableSearchScopes }
            ?: availableSearchScopes.firstOrNull()
            ?: SearchScope.POSTS
        searchSession = searchSession.updateDraft(normalized.query)
        refreshCurrentRouteSnapshot()
    }

    fun captureSavedSearchesByAccount(): Map<String, List<SavedSearch>> =
        normalizeSavedSearchesByAccount(savedSearchState.captureByAccount())

    val userTagsAvailable: Boolean
        get() = userTagState.available
    val userTags: Map<String, UserTag>
        get() = userTagState.tags

    fun userTagFor(username: String): UserTag? = userTagState.tagFor(username)

    fun canTagUser(username: String): Boolean =
        userTagState.canTag(username, activeAccountUsername)

    fun setUserTag(username: String, tag: UserTag?) {
        userTagState.setTag(username, tag)
    }
    private var pendingPostEditId by uiRequests::pendingPostEditId
    private var pendingPostDeleteId by uiRequests::pendingPostDeleteId
    private var pendingPostManageId by uiRequests::pendingPostManageId
    private val remotelyDeletedPostIds = mutableStateListOf<String>()

    // LOWUI4 (OCB-100): private, like postsResult/communitiesResult/
    // usersResult/inboxResult; nothing outside SubshellState reads them.
    private val createdPosts: List<Post>
        get() = repository.createdPosts.filterNot { post -> isPostDeleted(post.id) }

    val localContentRevision: Int
        get() = repositoryRevision

    init {
        RedGifsPlayback.allowed = initialShowNsfwPosts
    }

    init {
        syncNavigationProjection()
        replaceAccountPostActions(
            actions = loadAccountPostActions(activeAccountUsername),
            username = activeAccountUsername,
        )
    }

    private fun replaceAccountPostActions(
        actions: AccountPostActions,
        username: String,
    ) {
        val normalized = normalizeAccountPostActions(actions, username)
        savedPostIds.clear()
        savedPostIds.addAll(normalized.savedPostIds)
        // SAFETY01A3_AUTHENTICATED_HIDDEN_IS_REMOTE_PROJECTION_V1
        hiddenPostIds.clear()
        if (!activeAccountIsAuthenticated) {
            hiddenPostIds.addAll(normalized.hiddenPostIds)
        }
        readPostIds.clear()
        readPostIds.addAll(normalized.readPostIds)
        hideReadPosts = false
        votes.clear()
        votes.putAll(normalized.votes)
        pollVotes.clear()
        pollVotes.putAll(normalized.pollVotes)
        followedUsernames.clear()
        followedUsernames.addAll(normalized.followedUsernames)
    }

    private fun captureAccountPostActions(): AccountPostActions = normalizeAccountPostActions(
        actions = AccountPostActions(
            savedPostIds = savedPostIds.toSet(),
            hiddenPostIds = if (activeAccountIsAuthenticated) emptySet() else hiddenPostIds.toSet(),
            readPostIds = readPostIds.toSet(),
            votes = votes.toMap(),
            pollVotes = pollVotes.toMap(),
            followedUsernames = followedUsernames.toSet(),
        ),
        username = activeAccountUsername,
    )

    private fun persistActiveAccountPostActions() {
        persistAccountPostActions(
            activeAccountUsername,
            captureAccountPostActions(),
        )
    }

    internal fun postPagingSnapshot(
        queryKey: RepositoryQueryKey,
    ): RepositoryPagingSnapshot<Post> = postPaging.postPagingSnapshot(queryKey)

    internal fun ensurePostPaging(queryKey: RepositoryQueryKey) {
        postPaging.ensurePostPaging(queryKey)
    }

    internal fun refreshPostPaging(queryKey: RepositoryQueryKey) {
        postPaging.refreshPostPaging(queryKey)
    }

    internal fun synchronizePostPagingAfterRemoteWrite(
        queryKey: RepositoryQueryKey,
    ) {
        postPaging.synchronizePostPagingAfterRemoteWrite(queryKey)
    }

    internal fun claimProfileHistoryAutoRefresh(
        queryKey: RepositoryQueryKey,
    ): Boolean = postPaging.claimProfileHistoryAutoRefresh(queryKey)

    internal fun claimFeedEntryRefresh(
        queryKey: RepositoryQueryKey,
        deepInList: Boolean = false,
    ): FeedEntryRefreshDecision = postPaging.claimFeedEntryRefresh(queryKey, deepInList)

    internal fun appendPostPaging(queryKey: RepositoryQueryKey) {
        postPaging.appendPostPaging(queryKey)
    }

    internal fun retryPostPaging(queryKey: RepositoryQueryKey) {
        postPaging.retryPostPaging(queryKey)
    }

    internal fun invalidatePostPaging() {
        postPaging.invalidatePostPaging()
    }

    // PERF03_REPOSITORY_LISTS_READ_ONCE_PER_REVISION_V1 (#69): these lists come
    // from the Room cache (a blocking read and decode) and were read during
    // composition: on every keystroke in Create post, once per item in a
    // "My communities" search, twice per Communities frame. A list changes
    // when repositoryRevision does or when a single item is written to its
    // cache (identity lookups write communities and users without a revision,
    // PERF03_LIST_CACHE_WRITES_INVALIDATE_V1), so each is read once per pair;
    // reading the revision keeps the composition observing it, as before.
    // Posts and comments stay direct: local vote/save/poll writes change them
    // without a revision.
    private class RevisionMemo<T>(private val load: () -> T) {
        private var revision = -1
        private var cacheWrites = -1
        private var value: T? = null

        @Suppress("UNCHECKED_CAST")
        fun get(currentRevision: Int, currentCacheWrites: Int): T {
            if (currentRevision != revision || currentCacheWrites != cacheWrites) {
                cacheWrites = currentCacheWrites
                value = load()
                revision = currentRevision
            }
            return value as T
        }
    }

    private val listCacheWrites: Int
        get() = (repository as? ListCacheWriteCounter)?.listCacheWrites ?: 0

    private val communitiesMemo = RevisionMemo { repository.communities() }
    private val usersMemo = RevisionMemo { repository.users() }
    private val inboxMemo = RevisionMemo { repository.inbox() }

    private val postsResult: RepositoryResult<List<Post>>
        get() {
            check(repositoryRevision >= 0)
            return repository.posts()
        }

    val posts: List<Post>
        get() = postsResult.itemsOrEmpty().filterNot { post -> isPostDeleted(post.id) }

    private val communitiesResult: RepositoryResult<List<Community>>
        get() = communitiesMemo.get(repositoryRevision, listCacheWrites)

    val communities: List<Community>
        get() = communitiesResult.itemsOrEmpty()

    private val usersResult: RepositoryResult<List<UserFixture>>
        get() = usersMemo.get(repositoryRevision, listCacheWrites)

    val users: List<UserFixture>
        get() = usersResult.itemsOrEmpty()

    private val inboxResult: RepositoryResult<List<InboxItem>>
        get() = inboxMemo.get(repositoryRevision, listCacheWrites)

    val inboxItems: List<InboxItem>
        get() = if (activeAccountIsAuthenticated) emptyList() else inboxResult.itemsOrEmpty()

    val comments: List<Comment>
        get() = repository.comments().itemsOrEmpty()

    val accountSessions: List<AccountSessionSummary>
        get() = accountSessionCatalog

    val activeAccountSession: AccountSessionSummary
        get() = accountSessionCatalog.first { it.stableId == activeAccountId }

    val activeAccountIsAuthenticated: Boolean
        get() = activeAccountSession.source == AccountSessionSource.AUTHENTICATED

    val localMutationsAllowed: Boolean
        get() = !activeAccountIsAuthenticated

    fun postVoteControlEnabled(post: Post): Boolean =
        postVotes.postVoteControlEnabled(post)

    fun commentVoteControlEnabled(comment: Comment): Boolean =
        postVotes.commentVoteControlEnabled(comment)

    fun setAuthenticatedPostVoteRequester(
        requester: (PostVoteRequest) -> Unit,
    ) {
        postVotes.setAuthenticatedPostVoteRequester(requester)
    }

    val authenticatedPostSavingEnabled: Boolean
        get() = postSaves.authenticatedPostSavingEnabled

    fun postSaveControlEnabled(post: Post): Boolean =
        postSaves.postSaveControlEnabled(post)

    fun setAuthenticatedPostSaveRequester(
        requester: (PostSaveRequest) -> Unit,
    ) {
        postSaves.setAuthenticatedPostSaveRequester(requester)
    }

    fun postFollowControlEnabled(post: Post): Boolean =
        postFollows.postFollowControlEnabled(post)

    fun setAuthenticatedPostFollowRequester(
        requester: (PostFollowRequest) -> Unit,
    ) {
        postFollows.setAuthenticatedPostFollowRequester(requester)
    }

    fun setAuthenticatedContentSafetyRequesters(
        postVisibilityRequester: (RedditPostVisibilityRequest) -> Unit,
        reportRequester: (RedditContentReportRequest) -> Unit,
        userBlockRequester: (RedditUserBlockRequest) -> Unit = {},
    ) {
        contentSafety.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester,
            reportRequester,
            userBlockRequester,
        )
    }

    fun setAuthenticatedReportReasonInfoRequester(
        requester: (String, String, Boolean) -> Unit,
    ) {
        reportReasonPolicyState.setAuthenticatedReportReasonInfoRequester(requester)
    }

    fun synchronizeReportReasonCommunityInfo(
        uiState: RedditCommunityInfoUiState,
    ) {
        reportReasonPolicyState.synchronizeReportReasonCommunityInfo(uiState)
    }

    fun setAuthenticatedUserFlairRequesters(
        loader: (String, String, Boolean) -> Unit,
        mutator: (UserFlairMutationRequest) -> Unit,
        authorizer: (String) -> Unit,
    ) {
        userFlair.setAuthenticatedUserFlairRequesters(loader, mutator, authorizer)
    }

    fun synchronizeUserFlairUiState(uiState: UserFlairUiState) {
        userFlair.synchronizeUserFlairUiState(uiState)
    }

    fun loadUserFlair(communityName: String, force: Boolean = false) {
        userFlair.loadUserFlair(communityName, force)
    }

    fun setUserFlair(communityName: String, templateId: String, text: String?) {
        userFlair.setUserFlair(communityName, templateId, text)
    }

    fun authorizeUserFlair() {
        userFlair.authorizeUserFlair()
    }

    fun ensureReportReasonPolicy(
        communityName: String,
        force: Boolean = false,
    ) {
        reportReasonPolicyState.ensureReportReasonPolicy(communityName, force)
    }

    fun reportReasonPolicy(
        communityName: String,
        targetKind: RedditReportTargetKind,
    ): RedditReportReasonPolicy? = reportReasonPolicyState.reportReasonPolicy(communityName, targetKind)

    fun reportReasonPolicyLoading(communityName: String): Boolean =
        reportReasonPolicyState.reportReasonPolicyLoading(communityName)

    fun reportReasonPolicyFailed(communityName: String): Boolean =
        reportReasonPolicyState.reportReasonPolicyFailed(communityName)

    fun effectivePostHidden(
        post: Post,
        authoritativeHiddenHint: Boolean = false,
    ): Boolean = contentSafety.effectivePostHidden(post, authoritativeHiddenHint)

    fun postVisibilityControlEnabled(post: Post): Boolean =
        contentSafety.postVisibilityControlEnabled(post)

    fun contentReportControlEnabled(): Boolean =
        contentSafety.contentReportControlEnabled()

    fun isUserBlocked(username: String): Boolean =
        contentSafety.isUserBlocked(username)

    fun showsAuthoritativeBlockedAccount(username: String): Boolean =
        contentSafety.showsAuthoritativeBlockedAccount(username)

    fun userBlockControlEnabled(username: String): Boolean =
        contentSafety.userBlockControlEnabled(username)

    fun userBlockInProgress(username: String): Boolean =
        contentSafety.userBlockInProgress(username)

    fun noteAuthoritativeHiddenPost(postId: String) {
        contentSafety.noteAuthoritativeHiddenPost(postId)
    }

    fun effectivePostSaved(post: Post): Boolean = postSaves.effectivePostSaved(post)

    fun effectivePostFollowed(post: Post): Boolean? = postFollows.effectivePostFollowed(post)

    fun effectiveCommentSaved(comment: Comment): Boolean =
        postSaves.effectiveCommentSaved(comment)

    fun commentSaveControlEnabled(comment: Comment): Boolean =
        postSaves.commentSaveControlEnabled(comment)

    fun effectivePostVote(post: Post): Int = postVotes.effectivePostVote(post)

    fun effectivePostScore(post: Post): Int = postVotes.effectivePostScore(post)

    fun effectiveCommentVote(comment: Comment): Int = postVotes.effectiveCommentVote(comment)

    fun effectiveCommentScore(comment: Comment): Int = postVotes.effectiveCommentScore(comment)

    val accounts: List<String>
        get() = accountSessionCatalog.map(AccountSessionSummary::username)

    fun communityFor(name: String): Community? = identityMetadata.communityFor(name)

    fun communityIdentityFor(name: String): Community = identityMetadata.communityIdentityFor(name)

    fun isCommunityFavorite(name: String): Boolean =
        communityPreferences.isCommunityFavorite(name)

    fun favoriteCommunities(
        candidates: Iterable<Community> = emptyList(),
    ): List<Community> = communityPreferences.favoriteCommunities(candidates)

    fun communityPickerCommunities(): List<Community> = communities
        .sortedWith(
            compareByDescending<Community> { community ->
                isCommunityFavorite(community.name)
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { community ->
                community.name
            },
        )

    fun toggleFavoriteCommunity(name: String) {
        communityPreferences.toggleFavoriteCommunity(name)
    }

    fun isCustomFeedFavorite(path: String): Boolean =
        communityPreferences.isCustomFeedFavorite(path)

    fun favoriteCustomFeeds(
        candidates: Iterable<CustomFeedSummary>,
    ): List<CustomFeedSummary> = communityPreferences.favoriteCustomFeeds(candidates)

    fun toggleFavoriteCustomFeed(path: String) {
        communityPreferences.toggleFavoriteCustomFeed(path)
    }

    fun isCommunityHidden(name: String): Boolean =
        communityPreferences.isCommunityHidden(name)

    fun toggleHiddenCommunity(name: String) {
        communityPreferences.toggleHiddenCommunity(name)
    }

    fun updateMutedCommunityDirectViewMode(
        mode: MutedCommunityDirectViewMode,
    ) {
        communityPreferences.updateMutedCommunityDirectViewMode(mode)
    }

    fun requestCommunityMuteEditor(name: String) {
        communityPreferences.requestCommunityMuteEditor(name)
    }

    fun clearRequestedCommunityMuteEditor() {
        communityPreferences.clearRequestedCommunityMuteEditor()
    }

    fun shouldHidePostsForMutedCommunity(name: String): Boolean =
        communityPreferences.shouldHidePostsForMutedCommunity(name)

    fun activeMutedCommunityNames(
        nowEpochMillis: Long = communityMuteClockEpochMillis,
    ): Set<String> = communityPreferences.activeMutedCommunityNames(nowEpochMillis)

    fun activeCommunityMuteEntries(
        nowEpochMillis: Long = communityMuteClockEpochMillis,
    ): List<CommunityMute> = communityPreferences.activeCommunityMuteEntries(nowEpochMillis)

    fun communityMuteFor(
        name: String,
        nowEpochMillis: Long = currentTimeMillis(),
    ): CommunityMute? = communityPreferences.communityMuteFor(name, nowEpochMillis)

    fun isCommunityMuted(
        name: String,
        nowEpochMillis: Long = currentTimeMillis(),
    ): Boolean = communityPreferences.isCommunityMuted(name, nowEpochMillis)

    val nextCommunityMuteExpiryEpochMillis: Long?
        get() = communityPreferences.nextCommunityMuteExpiryEpochMillis

    fun millisUntilNextCommunityMuteExpiry(): Long? =
        communityPreferences.millisUntilNextCommunityMuteExpiry()

    fun refreshCommunityMuteClock() {
        communityPreferences.refreshCommunityMuteClock()
    }

    fun muteCommunity(
        name: String,
        expiresAtEpochMillis: Long?,
    ): Boolean = communityPreferences.muteCommunity(name, expiresAtEpochMillis)

    fun unmuteCommunity(name: String): Boolean =
        communityPreferences.unmuteCommunity(name)

    fun recentCommunities(
        candidates: Iterable<Community> = emptyList(),
        limit: Int = RECENT_COMMUNITY_HISTORY_LIMIT,
    ): List<Community> = communityPreferences.recentCommunities(candidates, limit)

    fun clearRecentCommunities() {
        communityPreferences.clearRecentCommunities()
    }

    private fun recordRecentCommunityVisit(name: String) {
        communityPreferences.recordRecentCommunityVisit(name)
    }

    fun userFor(username: String): UserFixture = identityMetadata.userFor(username)

    suspend fun ensureCommunityIdentity(name: String) {
        identityMetadata.ensureCommunityIdentity(name)
    }

    suspend fun ensureCommunityIdentities(names: Iterable<String>) {
        identityMetadata.ensureCommunityIdentities(names)
    }

    /** FEEDS04_CACHED_COMMUNITY_IDENTITY_OFF_MAIN_V1: favorites and recent visits. */
    suspend fun loadCachedCommunityListIdentities() {
        identityMetadata.loadCachedCommunityIdentities(
            effectiveFavoriteCommunityNames + recentCommunityVisits.map { visit -> visit.communityName },
        )
    }

    suspend fun ensureUserIdentity(username: String) {
        identityMetadata.ensureUserIdentity(username)
    }

    // ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
    fun avatarUriForAuthor(username: String): String? = identityMetadata.avatarUriForAuthor(username)

    suspend fun ensureAuthorAvatars(fullnamesByUsername: Map<String, String>) {
        identityMetadata.ensureAuthorAvatars(fullnamesByUsername)
    }

    fun publicUserProfileUnavailable(username: String): Boolean =
        identityMetadata.publicUserProfileUnavailable(username)

    fun profileCommentsFor(username: String): List<ProfileCommentFixture> =
        repository.profileComments(username).itemsOrEmpty()

    fun commentsForPost(post: Post): List<Comment> =
        repository.commentsForPost(post.id).itemsOrEmpty()

    val postComposerValidationMessage: String?
        get() = if (activeAccountIsAuthenticated) {
            validateRemoteTextPostComposerDraft(postComposerDraft)
        } else {
            validatePostComposerDraft(
                draft = postComposerDraft,
                communityNames = communities.map { community -> community.name },
            )
        }

    val unreadInboxCount: Int
        get() = inboxItems.count { item -> item.unread && item.id !in readInboxIds }

    val canNavigateBack: Boolean
        get() = routeStack.canPop

    val currentSearchHint: String
        get() = when (searchArea) {
            SearchArea.ALL_SUBSHELL -> if (activeAccountIsAuthenticated) {
                "Search Reddit"
            } else {
                "Search subshell"
            }
            SearchArea.MY_COMMUNITIES -> "Search my communities"
            SearchArea.CURRENT_COMMUNITY -> searchOriginCommunity?.let { "Search r/$it" } ?: "Search subshell"
        }

    val currentSearchAreaLabel: String
        get() = searchAreaLabel(searchArea)

    val availableSearchAreas: List<SearchArea>
        get() = buildList {
            if (searchOriginCommunity != null) add(SearchArea.CURRENT_COMMUNITY)
            add(SearchArea.ALL_SUBSHELL)
            if (!activeAccountIsAuthenticated) add(SearchArea.MY_COMMUNITIES)
        }

    val availableSearchScopes: List<SearchScope>
        get() = when {
            searchArea == SearchArea.CURRENT_COMMUNITY ->
                listOf(SearchScope.POSTS)
            activeAccountIsAuthenticated ->
                listOf(
                    SearchScope.ALL,
                    SearchScope.POSTS,
                    SearchScope.COMMUNITIES,
                    SearchScope.USERS,
                )
            searchArea == SearchArea.MY_COMMUNITIES ->
                listOf(SearchScope.ALL, SearchScope.POSTS, SearchScope.COMMUNITIES)
            else -> SearchScope.entries
        }

    fun navigate(destination: MainDestination) {
        val homeReselected = destination == MainDestination.HOME &&
            routeStack.depth == 1 &&
            (routeStack.current as? SubshellRoute.Main)?.destination == MainDestination.HOME
        routeStack = SubshellRouteStack.forDestination(
            destination = destination,
            source = feedSource,
            sort = feedSort,
        )
        syncNavigationProjection()
        showChrome()
        if (homeReselected) homeScrollToTopRequest += 1
    }

    internal fun consumeHomeScrollToTopRequest(): Boolean =
        uiRequests.consumeHomeScrollToTopRequest()

    fun applyAuthenticatedMainFeedLanding(source: FeedSource) {
        require(
            source == FeedSource.HOME ||
                source == FeedSource.POPULAR,
        ) {
            "Authenticated account landing must use Home or Popular"
        }
        feedSource = source
        feedSort = if (source == FeedSource.HOME) {
            FeedSort.BEST
        } else {
            FeedSort.HOT
        }
        navigate(MainDestination.HOME)
    }

    fun selectFeedSource(source: FeedSource) {
        feedSource = source
        feedSort = normalizedFeedSort(source, feedSort)
        navigate(MainDestination.HOME)
    }

    fun updateFeedSort(sort: FeedSort) {
        feedSort = normalizedFeedSort(feedSource, sort)
    }

    fun navigateBack(): Boolean = if (routeStack.current is SubshellRoute.Search) {
        handleSearchBack()
    } else {
        popCurrentRoute()
    }

    fun requestDrawer() {
        uiRequests.requestDrawer()
    }

    fun requestAccountSwitcher() {
        uiRequests.requestAccountSwitcher()
    }

    fun switchAccount(accountIdOrUsername: String) {
        if (currentNavigationRoute is SubshellRoute.PostComposer) {
            archiveCurrentPostComposerDraft()
        }
        val selected = accountSessionCatalog.firstOrNull { account ->
            account.stableId == accountIdOrUsername
        } ?: accountSessionCatalog.singleOrNull { account ->
            account.username.equals(accountIdOrUsername, ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown account session: $accountIdOrUsername")

        selectAccountSession(selected.stableId)
        activeAccountId = selected.stableId
        activeAccountUsername = selected.username
        joinedCommunityIds.clear()
        joinedCommunityIds.addAll(
            normalizeJoinedCommunityIds(
                communityIds = loadJoinedCommunityIds(selected.username),
                repository = repository,
            ),
        )
        favoriteCommunityNames.clear()
        favoriteCommunityNames.addAll(
            normalizeFavoriteCommunityNames(
                loadFavoriteCommunityNames(selected.stableId),
            ),
        )
        favoriteCustomFeedPaths.clear()
        favoriteCustomFeedPaths.addAll(
            normalizeFavoriteCustomFeedPaths(
                loadFavoriteCustomFeedPaths(selected.stableId),
            ),
        )
        hiddenCommunityNames.clear()
        hiddenCommunityNames.addAll(
            normalizeFavoriteCommunityNames(
                loadHiddenCommunityNames(selected.stableId),
            ),
        )
        communityMuteClockEpochMillis = currentTimeMillis()
        communityMutes.clear()
        communityMutes.addAll(
            activeCommunityMutesAt(
                decodeCommunityMutes(loadCommunityMuteEntries(selected.stableId)),
                communityMuteClockEpochMillis,
            ),
        )
        recentCommunityVisits.clear()
        recentCommunityVisits.addAll(
            normalizeRecentCommunityVisits(
                loadRecentCommunityVisits(selected.stableId),
            ),
        )
        searchHistory.reloadForAccount(selected.stableId)
        userTagState.reloadForAccount(selected.stableId)
        savedSearchState.reloadForAccount(selected.stableId)
        searchSession = SearchSessionSnapshot()
        refreshCurrentRouteSnapshot()
        clearAuthenticatedPostVoteState()
        clearAuthenticatedPostSaveState()
        clearAuthenticatedPostFollowState()
        clearAuthenticatedContentSafetyState()
        replaceAccountPostActions(
            actions = loadAccountPostActions(selected.username),
            username = selected.username,
        )
        persistActiveAccountId(selected.stableId)
        persistActiveAccountUsername(selected.username)
        sanitizeSearchFiltersForActiveAccount()
        invalidatePostPaging()
        navigate(MainDestination.HOME)
    }

    fun synchronizeAccountSessions(snapshot: AccountSessionDirectorySnapshot) {
        val accounts = validatedAccountSessions(snapshot.accounts)
        accountSessionCatalog.clear()
        accountSessionCatalog.addAll(accounts)

        val selected = accounts.first { account ->
            account.stableId == snapshot.activeAccountId
        }
        if (
            activeAccountId == selected.stableId &&
            activeAccountUsername == selected.username
        ) {
            return
        }

        if (currentNavigationRoute is SubshellRoute.PostComposer) {
            archiveCurrentPostComposerDraft()
        }

        activeAccountId = selected.stableId
        activeAccountUsername = selected.username
        joinedCommunityIds.clear()
        joinedCommunityIds.addAll(
            normalizeJoinedCommunityIds(
                communityIds = loadJoinedCommunityIds(selected.username),
                repository = repository,
            ),
        )
        favoriteCommunityNames.clear()
        favoriteCommunityNames.addAll(
            normalizeFavoriteCommunityNames(
                loadFavoriteCommunityNames(selected.stableId),
            ),
        )
        favoriteCustomFeedPaths.clear()
        favoriteCustomFeedPaths.addAll(
            normalizeFavoriteCustomFeedPaths(
                loadFavoriteCustomFeedPaths(selected.stableId),
            ),
        )
        hiddenCommunityNames.clear()
        hiddenCommunityNames.addAll(
            normalizeFavoriteCommunityNames(
                loadHiddenCommunityNames(selected.stableId),
            ),
        )
        communityMuteClockEpochMillis = currentTimeMillis()
        communityMutes.clear()
        communityMutes.addAll(
            activeCommunityMutesAt(
                decodeCommunityMutes(loadCommunityMuteEntries(selected.stableId)),
                communityMuteClockEpochMillis,
            ),
        )
        recentCommunityVisits.clear()
        recentCommunityVisits.addAll(
            normalizeRecentCommunityVisits(
                loadRecentCommunityVisits(selected.stableId),
            ),
        )
        searchHistory.reloadForAccount(selected.stableId)
        userTagState.reloadForAccount(selected.stableId)
        savedSearchState.reloadForAccount(selected.stableId)
        searchSession = SearchSessionSnapshot()
        refreshCurrentRouteSnapshot()
        clearAuthenticatedPostVoteState()
        clearAuthenticatedPostSaveState()
        clearAuthenticatedPostFollowState()
        clearAuthenticatedContentSafetyState()
        replaceAccountPostActions(
            actions = loadAccountPostActions(selected.username),
            username = selected.username,
        )
        persistActiveAccountId(selected.stableId)
        persistActiveAccountUsername(selected.username)
        sanitizeSearchFiltersForActiveAccount()
        invalidatePostPaging()
    }

    // CROSSPOST01A1E_SOURCE_BOUND_ENTRY_AND_DRAFT_REOPEN_V1
    fun canOpenCrosspostComposer(post: Post): Boolean =
        activeAccountIsAuthenticated && post.toComposerCrosspostSource() != null &&
            !post.author.startsWith("[") && routeStack.current !is SubshellRoute.PostComposer

    fun openCrosspostComposer(post: Post): Boolean {
        if (!canOpenCrosspostComposer(post)) return false
        val source = post.toComposerCrosspostSource() ?: return false
        postComposerDraft = newCrosspostComposerDraft(source, activeAccountId)
        archiveCurrentPostComposerDraft()
        pushRoute(SubshellRoute.PostComposer)
        showChrome()
        return true
    }

    private fun canReopenSourceBoundDraft(draft: PostComposerDraft): Boolean =
        draft.isValidSourceBoundCrosspostDraft() && activeAccountIsAuthenticated &&
            draft.accountStableId == activeAccountId

    fun openPostComposer() {
        if (routeStack.current is SubshellRoute.PostComposer) return
        val preferredCommunity = routeStack.surface
            .composerCommunityOrNull()
            ?.trim()
            .orEmpty()
        postComposerDraft = PostComposerDraft(
            accountStableId = activeAccountId,
            community = preferredCommunity,
        )
        pushRoute(SubshellRoute.PostComposer)
        showChrome()
    }

    // ISSUE33_SHARE_INTO_SUBSHELL_V1: a share starts a new draft for the
    // active account. A draft in progress goes to Drafts first; the community
    // stays empty and the composer opens its picker.
    fun openSharedPostComposer(shared: SharedPostContent) {
        archiveCurrentPostComposerDraft(persist = true)
        postComposerDraft = PostComposerDraft(
            accountStableId = activeAccountId,
            community = "",
            type = shared.type,
            title = shared.title,
            body = shared.body,
            linkUrl = shared.linkUrl,
            media = shared.media,
        )
        uiRequests.requestComposerCommunityPicker(postComposerDraft.id)
        if (routeStack.current !is SubshellRoute.PostComposer) pushRoute(SubshellRoute.PostComposer)
        showChrome()
    }

    internal fun consumeComposerCommunityPicker(draftId: String): Boolean =
        uiRequests.consumeComposerCommunityPicker(draftId)

    fun closePostComposer() {
        archiveCurrentPostComposerDraft()
        popExpectedRoute<SubshellRoute.PostComposer>()
        postComposerDraft = PostComposerDraft(
            accountStableId = activeAccountId,
            community = postComposerDraft.community,
        )
    }

    fun updatePostComposerDraft(draft: PostComposerDraft) {
        // CROSSPOST01A1C_SOURCE_AND_OWNER_IMMUTABLE_DURING_EDIT_V1
        if (!canUpdateSourceBoundComposerDraft(postComposerDraft, draft)) return
        postComposerDraft = draft.copy(accountStableId = draft.accountStableId ?: activeAccountId).touched()
        archiveCurrentPostComposerDraft(persist = false)
    }

    fun savePostComposerDraft() {
        archiveCurrentPostComposerDraft(persist = true)
    }

    fun openPostComposerDraft(draftId: String): Boolean {
        val draft = postComposerDrafts.firstOrNull { it.id == draftId } ?: return false
        // CROSSPOST01A1E_REOPEN_REQUIRES_SOURCE_AND_OWNER_V1
        if ((draft.type == ComposerPostType.CROSSPOST || draft.crosspostSource != null) &&
            !canReopenSourceBoundDraft(draft)
        ) return false
        postComposerDraft = draft
        pushRoute(SubshellRoute.PostComposer)
        showChrome()
        return true
    }

    // NEXT01A4C_DRAFT_SELECTION_REBIND_V1
    internal fun currentPostComposerSupportsExpandedYouDraftsListDetail(): Boolean {
        if (routeStack.current !is SubshellRoute.PostComposer) return false
        val surface = routeStack.surface as? SubshellRoute.Main ?: return false
        return surface.destination == MainDestination.YOU
    }

    internal fun replaceCurrentPostComposerDraftSelection(
        draftId: String,
    ): Boolean {
        if (routeStack.current !is SubshellRoute.PostComposer) return false

        val draft =
            postComposerDrafts.firstOrNull { candidate ->
                candidate.id == draftId
            } ?: return true

        if ((draft.type == ComposerPostType.CROSSPOST || draft.crosspostSource != null) &&
            !canReopenSourceBoundDraft(draft)
        ) return true
        if (draft.id == postComposerDraft.id) return true

        archiveCurrentPostComposerDraft()
        postComposerDraft = draft
        showChrome()
        return true
    }

    fun deletePostComposerDraft(draftId: String) {
        postComposerDrafts.removeAll { it.id == draftId }
        persistPostComposerDrafts(postComposerDrafts.toList())
    }

    internal fun deleteCurrentPostComposerDraftSelection(
        draftId: String,
    ): Boolean {
        if (routeStack.current !is SubshellRoute.PostComposer) return false
        if (postComposerDraft.id != draftId) return false

        discardPostComposerDraft()
        return true
    }

    private fun archiveCurrentPostComposerDraft(persist: Boolean = true) {
        val draft = postComposerDraft
        postComposerDrafts.removeAll { it.id == draft.id }
        if (draft.hasContent) postComposerDrafts.add(0, draft)
        while (postComposerDrafts.size > MAX_POST_COMPOSER_DRAFTS) {
            postComposerDrafts.removeAt(postComposerDrafts.lastIndex)
        }
        if (persist) persistPostComposerDrafts(postComposerDrafts.toList())
    }

    fun discardPostComposerDraft() {
        deletePostComposerDraft(postComposerDraft.id)
        postComposerDraft = PostComposerDraft(
            accountStableId = activeAccountId,
            community = postComposerDraft.community,
        )
        popExpectedRoute<SubshellRoute.PostComposer>()
    }

    fun publishPostComposerDraft(): Post? {
        if (!localMutationsAllowed) return null
        if (postComposerValidationMessage != null) return null
        val post = buildLocalPost(
            draft = postComposerDraft,
            author = activeAccountUsername,
            id = "local-post-${createdPosts.size + 1}",
        )
        val created = repository.createPost(post).valueOrNull() ?: return null
        repositoryRevision += 1
        invalidatePostPaging()
        deletePostComposerDraft(postComposerDraft.id)
        postComposerDraft = PostComposerDraft(
            accountStableId = activeAccountId,
            community = created.community,
        )
        popExpectedRoute<SubshellRoute.PostComposer>()
        feedPreviewState = FeedPreviewState.CONTENT
        open(created)
        return created
    }

    internal fun completeRemoteCrosspostComposer(
        accountStableId: String,
        requestKey: String,
        submittedPost: SubmittedTextPost,
    ): Post? {
        val account = accountSessionCatalog.firstOrNull {
            it.stableId == accountStableId && it.source == AccountSessionSource.AUTHENTICATED
        } ?: return null
        val matches = (listOf(postComposerDraft) + postComposerDrafts.toList())
            .distinctBy { it.id }
            .mapNotNull { draft ->
                completedCrosspostForDraft(
                    draft, accountStableId, true, account.username, requestKey, submittedPost,
                )?.let { draft.id to it }
            }
        val created = matches.firstOrNull()?.second ?: return null
        val matchingIds = matches.map { it.first }.toSet()
        val navigate = activeAccountId == accountStableId &&
            routeStack.current is SubshellRoute.PostComposer && postComposerDraft.id in matchingIds
        // A success arriving after Close still clears the exact saved attempt, not another draft.
        postComposerDrafts.removeAll { it.id in matchingIds }
        persistPostComposerDrafts(postComposerDrafts.toList())
        if (postComposerDraft.id in matchingIds) {
            postComposerDraft = PostComposerDraft(accountStableId = activeAccountId, community = created.community)
        }
        if (navigate) {
            popExpectedRoute<SubshellRoute.PostComposer>()
            feedPreviewState = FeedPreviewState.CONTENT
            open(created)
        }
        return created
    }

    // CROSSPOST01A2_ROUTE_SAFE_CANONICAL_REBIND_V1
    // A delayed readback may update only the still-open wrapper created by this submit.
    // Back/navigation during readback wins; canonical completion never steals navigation.
    internal fun rebindCompletedCrosspostToCanonical(
        submittedPost: SubmittedTextPost,
        canonicalPost: Post,
    ): Boolean {
        val route = routeStack.current as? SubshellRoute.Post ?: return false
        val current = selectedPost ?: return false
        if (
            !redditTargetMatchesPost(submittedPost.id, route.postRoute.postId) ||
            !redditTargetMatchesPost(submittedPost.id, current.id)
        ) {
            return false
        }

        val rebound = canonicalCrosspostCompletionOrFallback(
            submitted = submittedPost,
            fallback = current,
            candidate = canonicalPost,
        )
        if (rebound === current) return false

        return replaceCurrentPostSelection(rebound)
    }

    // COMPOSER05_POST_PUBLISH_CANONICAL_REBIND_V1: same route guard as the
    // crosspost rebind; navigation during the read-back wins.
    internal fun rebindCompletedTextPostToCanonical(
        submittedPost: SubmittedTextPost,
        canonicalPost: Post,
    ): Boolean {
        val route = routeStack.current as? SubshellRoute.Post ?: return false
        val current = selectedPost ?: return false
        if (
            !redditTargetMatchesPost(submittedPost.id, route.postRoute.postId) ||
            !redditTargetMatchesPost(submittedPost.id, current.id)
        ) {
            return false
        }
        val rebound = canonicalTextPostCompletionOrFallback(
            submitted = submittedPost,
            fallback = current,
            candidate = canonicalPost,
        )
        if (rebound === current) return false
        return replaceCurrentPostSelection(rebound)
    }

    fun completeRemoteTextPostComposer(
        accountStableId: String,
        requestKey: String,
        submittedPost: SubmittedTextPost,
    ): Post {
        val account = accountSessionCatalog.firstOrNull { candidate ->
            candidate.stableId == accountStableId &&
                candidate.source == AccountSessionSource.AUTHENTICATED
        }
        // COMPOSER03_POST_SUCCESS_FOLLOWS_CROSSPOST_RULE_V1: a success removes every
        // saved draft of the sending account that is exactly what was sent, even
        // after Close or an account switch; a draft changed while sending no longer
        // matches and stays. Only an open composer showing that draft, under the
        // same account, moves on to the new post.
        val matchingDraftIds = (listOf(postComposerDraft) + postComposerDrafts.toList())
            .distinctBy { draft -> draft.id }
            .filter { draft ->
                (
                    draft.accountStableId == accountStableId ||
                        (
                            draft.accountStableId == null &&
                                draft.id == postComposerDraft.id &&
                                activeAccountId == accountStableId
                            )
                    ) &&
                    draft.type in setOf(
                        ComposerPostType.TEXT,
                        ComposerPostType.LINK,
                        ComposerPostType.MEDIA,
                    ) &&
                    draft.textPostWriteRequestKeyOrNull() == requestKey
            }
            .map { draft -> draft.id }
            .toSet()
        val navigate = activeAccountId == accountStableId &&
            routeStack.current is SubshellRoute.PostComposer &&
            postComposerDraft.id in matchingDraftIds
        val linkUri = submittedPost
            .linkUrl
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { value -> runCatching { java.net.URI(value) }.getOrNull() }
        val submittedGallery = submittedPost.galleryItems.mapIndexed { index, item ->
            GalleryItem(
                id = "${submittedPost.id}-gallery-$index",
                label = item.caption.ifBlank { "Image ${index + 1}" },
                format = if (item.mediaMimeType.equals("image/gif", ignoreCase = true)) {
                    MediaFormat.GIF
                } else {
                    MediaFormat.IMAGE
                },
                remoteImageUri = item.mediaRemoteImageUrl,
                localMediaUri = item.mediaLocalUri,
                localMediaMimeType = item.mediaMimeType,
                caption = item.caption,
                outboundUrl = item.outboundUrl,
            )
        }
        val created = Post(
            id = submittedPost.id,
            community = submittedPost.community,
            author = account?.username ?: "unknown",
            age = "0m",
            title = submittedPost.title,
            body = submittedPost.text,
            score = 1,
            comments = 0,
            kind = when {
                submittedGallery.isNotEmpty() -> PostKind.GALLERY
                submittedPost.mediaLocalUri != null &&
                    submittedPost.mediaMimeType?.startsWith(
                        "video/",
                        ignoreCase = true,
                    ) == true -> PostKind.VIDEO
                submittedPost.mediaLocalUri != null &&
                    submittedPost.mediaMimeType?.equals(
                        "image/gif",
                        ignoreCase = true,
                    ) == true -> PostKind.GIF
                submittedPost.mediaLocalUri != null -> PostKind.IMAGE
                submittedPost.linkUrl != null -> PostKind.LINK
                else -> PostKind.TEXT
            },
            domain = linkUri?.host?.removePrefix("www."),
            mediaLabel = when {
                submittedGallery.isNotEmpty() -> "${submittedGallery.size} items"
                submittedPost.mediaAsGif -> "GIF"
                submittedPost.mediaLocalUri != null &&
                    submittedPost.mediaMimeType?.startsWith(
                        "video/",
                        ignoreCase = true,
                    ) == true -> "Video"
                submittedPost.mediaLocalUri != null &&
                    submittedPost.mediaMimeType?.equals(
                        "image/gif",
                        ignoreCase = true,
                    ) == true -> "GIF"
                submittedPost.mediaLocalUri != null -> "Photo"
                submittedPost.linkUrl != null -> submittedPost.linkUrl
                else -> ""
            },
            localMediaUri = submittedPost.mediaLocalUri,
            localMediaMimeType = submittedPost.mediaMimeType,
            gallery = submittedGallery,
            remoteImageUri = submittedPost.mediaRemoteImageUrl
                ?: submittedPost.linkPreviewImageUrl,
            videoWidth = submittedPost.videoWidth,
            videoHeight = submittedPost.videoHeight,
            flair = submittedPost.flairText,
            nsfw = submittedPost.nsfw,
            spoiler = submittedPost.spoiler,
        )

        if (matchingDraftIds.isNotEmpty()) {
            postComposerDrafts.removeAll { draft -> draft.id in matchingDraftIds }
            persistPostComposerDrafts(postComposerDrafts.toList())
        }
        if (postComposerDraft.id in matchingDraftIds) {
            postComposerDraft = PostComposerDraft(
                accountStableId = activeAccountId,
                community = submittedPost.community,
            )
        }
        if (navigate) {
            popExpectedRoute<SubshellRoute.PostComposer>()
            feedPreviewState = FeedPreviewState.CONTENT
            open(created)
        }
        return created
    }

    fun openCommentComposer(
        post: Post,
        comment: Comment? = null,
    ): Boolean = commentDraft.openCommentComposer(post, comment)

    fun closeCommentComposer() {
        commentDraft.closeCommentComposer()
    }

    fun updateCommentComposerDraft(draft: CommentComposerDraft) {
        commentDraft.updateCommentComposerDraft(draft)
    }

    fun discardCommentComposerDraft() {
        commentDraft.discardCommentComposerDraft()
    }

    fun publishLocalCommentComposer(post: Post): Comment? =
        commentDraft.publishLocalCommentComposer(post)

    fun completeRemoteCommentComposer(targetStableKey: String): Boolean =
        commentDraft.completeRemoteCommentComposer(targetStableKey)

    fun addLocalComment(
        post: Post,
        body: String,
        media: ComposerMediaAttachment? = null,
    ): Comment? {
        if (!localMutationsAllowed) return null
        val cleanBody = body.trim()
        if ((cleanBody.isEmpty() && media == null) || cleanBody.length > 10_000) return null
        val existingCommentCount = commentsForPost(post).sumOf { comment -> comment.threadSize() }
        val comment = Comment(
            id = "local-comment-${post.id}-${existingCommentCount + 1}",
            author = activeAccountUsername,
            age = "0m",
            body = cleanBody,
            score = 1,
            depth = 0,
            accent = communityFor(post.community)?.accent ?: 0xFF6750A4L,
            isOp = post.author.equals(activeAccountUsername, ignoreCase = true),
            localMediaUri = media?.uri,
            localMediaMimeType = media?.mimeType,
        )
        val created = repository.addComment(post.id, comment).valueOrNull() ?: return null
        repositoryRevision += 1
        return created
    }

    fun onMainContentScroll(deltaPx: Float, atTop: Boolean, hideThresholdPx: Float, showThresholdPx: Float) {
        if (atTop) {
            showChrome()
            return
        }
        if (abs(deltaPx) < 0.5f) return
        val direction = if (deltaPx > 0f) 1 else -1
        if (direction != scrollDirection) {
            scrollDirection = direction
            accumulatedScrollPx = 0f
        }
        accumulatedScrollPx += abs(deltaPx)
        when {
            direction > 0 && accumulatedScrollPx >= hideThresholdPx -> {
                chromeVisible = false
                accumulatedScrollPx = 0f
            }
            direction < 0 && accumulatedScrollPx >= showThresholdPx -> {
                chromeVisible = true
                accumulatedScrollPx = 0f
            }
        }
    }

    fun showChrome() {
        chromeVisible = true
        accumulatedScrollPx = 0f
        scrollDirection = 0
    }

    private fun refreshCurrentRouteSnapshot() {
        routeStack = when (val route = routeStack.current) {
            is SubshellRoute.Main -> routeStack.replaceCurrent(
                route.copy(
                    destination = destination,
                    source = feedSource,
                    sort = feedSort,
                ),
            )
            is SubshellRoute.Community -> routeStack.replaceCurrent(
                route.copy(
                    destination = destination,
                    sort = communitySort,
                ),
            )
            is SubshellRoute.Search -> routeStack.replaceCurrent(
                route.copy(
                    destination = searchOriginDestination,
                    originCommunity = searchOriginCommunity,
                    query = searchQuery,
                    submittedQuery = searchSubmittedQuery,
                    scope = searchScope,
                    area = searchArea,
                ),
            )
            is SubshellRoute.Media -> routeStack.replaceCurrent(
                route.copy(index = selectedMediaIndex),
            )
            else -> routeStack
        }
    }

    private fun pushRoute(route: SubshellRoute) {
        refreshCurrentRouteSnapshot()
        routeStack = routeStack.push(route)
        syncNavigationProjection()
    }

    private inline fun <reified T : SubshellRoute> popExpectedRoute(): Boolean {
        if (routeStack.current !is T) return false
        return popCurrentRoute()
    }

    private fun popCurrentRoute(): Boolean {
        if (!routeStack.canPop) return false
        val currentRoute = routeStack.current
        when (currentRoute) {
            is SubshellRoute.Media -> selectedMediaPost?.let(::beginVideoPresentationHandoff)
            is SubshellRoute.Post -> selectedPost?.let(::beginVideoPresentationHandoff)
            else -> Unit
        }
        if (currentRoute is SubshellRoute.Post) {
            routedPostsByVisitId.remove(currentRoute.postRoute.visitId)
        }
        routeStack = routeStack.pop()
        syncNavigationProjection()
        showChrome()
        return true
    }

    /**
     * Replaces the current route in place and re-projects. A replaced post
     * route gives up its routed post; the caller registers the new one.
     */
    private fun replaceCurrentRoute(route: SubshellRoute) {
        val currentPostRoute = routeStack.current as? SubshellRoute.Post
        if (currentPostRoute != null) {
            routedPostsByVisitId.remove(currentPostRoute.postRoute.visitId)
        }
        routeStack = routeStack.replaceCurrent(route)
        syncNavigationProjection()
    }

    private fun syncNavigationProjection() {
        // LOWUI6_ROUTED_POSTS_PRUNED_WITH_THE_STACK_V1 (OCB-101): a whole-stack
        // replacement (tab, account switch, secondary, notification root) left
        // every earlier post visit in these maps for the life of the process.
        val livePostVisits = livePostVisitIds
        routedPostsByVisitId.keys.retainAll(livePostVisits)
        consumedPostEntryVisitIds.retainAll(livePostVisits)
        val retainedPost = selectedPost
        val retainedMediaPost = selectedMediaPost
        val retainedConversation = selectedConversation
        val surface = routeStack.surface
        destination = surface.destinationOrNull() ?: MainDestination.HOME

        routeStack.entries
            .filterIsInstance<SubshellRoute.Main>()
            .lastOrNull()
            ?.let { main ->
                feedSource = main.source
                feedSort = normalizedFeedSort(main.source, main.sort)
            }
        routeStack.entries
            .filterIsInstance<SubshellRoute.Community>()
            .lastOrNull()
            ?.let { community -> communitySort = community.sort }

        selectedPost = null
        selectedConversation = null
        selectedUser = null
        secondaryDestination = null
        searchVisible = false
        selectedSubreddit = null
        focusedCommentId = null

        when (surface) {
            is SubshellRoute.Main -> Unit
            is SubshellRoute.Community -> {
                selectedSubreddit = surface.communityName
                communitySort = surface.sort
            }
            is SubshellRoute.Search -> {
                selectedSubreddit = surface.originCommunity
                searchOriginDestination = surface.destination
                searchOriginCommunity = surface.originCommunity
                searchSession = searchSession.restore(
                    draftQuery = surface.query,
                    submittedQuery = surface.submittedQuery,
                )
                searchScope = surface.scope
                searchArea = surface.area
                searchVisible = true
            }
            is SubshellRoute.Post -> {
                val postId = surface.postRoute.postId
                selectedPost = routedPostsByVisitId[surface.postRoute.visitId]
                    ?: retainedPost?.takeIf { post -> post.id == postId }
                    ?: postPagingSnapshots.values
                        .asSequence()
                        .flatMap { snapshot -> snapshot.items.asSequence() }
                        .firstOrNull { post -> post.id == postId }
                    ?: repository.postForAccount(postId, activeAccountId).valueOrNull()
                    ?: posts.firstOrNull { post -> post.id == postId }
                focusedCommentId = surface.postRoute.focusedCommentId
                postVisitId = surface.postRoute.visitId
                nextPostVisitId = maxOf(nextPostVisitId, surface.postRoute.visitId)
            }
            is SubshellRoute.User -> selectedUser = surface.username
            is SubshellRoute.Conversation -> {
                selectedConversation = retainedConversation
                    ?.takeIf { item -> item.id == surface.inboxItemId }
                    ?: repository.inboxItem(surface.inboxItemId).valueOrNull()
            }
            is SubshellRoute.Secondary -> secondaryDestination = surface.secondaryDestination
            is SubshellRoute.Media,
            is SubshellRoute.CommentMedia,
            SubshellRoute.PostComposer,
            -> error("Overlay routes cannot be the canonical surface")
        }

        val overlay = routeStack.current
        selectedMediaPost = (overlay as? SubshellRoute.Media)
            ?.postId
            ?.let { postId ->
                retainedMediaPost?.takeIf { post -> post.id == postId }
                    ?: postPagingSnapshots.values
                        .asSequence()
                        .flatMap { snapshot -> snapshot.items.asSequence() }
                        .firstOrNull { post -> post.id == postId }
                    ?: repository.postForAccount(postId, activeAccountId).valueOrNull()
                    ?: posts.firstOrNull { post -> post.id == postId }
            }
        if (overlay is SubshellRoute.Media) selectedMediaIndex = overlay.index
        postComposerVisible = overlay is SubshellRoute.PostComposer
    }

    // ISSUE19_INLINE_COMMENT_IMAGE_FULLSCREEN_V1
    internal fun openCommentMedia(
        commentId: String,
        media: app.subshell.prototype.model.RemoteCommentMedia,
    ): Boolean {
        if (media.kind != app.subshell.prototype.model.RemoteCommentMediaKind.IMAGE) {
            return false
        }
        pushRoute(
            SubshellRoute.CommentMedia(
                commentId = commentId,
                uri = media.uri,
            ),
        )
        return true
    }

    internal fun closeCommentMedia(): Boolean =
        popExpectedRoute<SubshellRoute.CommentMedia>()

    fun open(post: Post, commentId: String? = null) {
        openPostRoute(
            post = post,
            commentId = commentId,
            origin = captureCurrentPostOrigin(),
        )
    }

    internal fun openRedditTarget(
        target: RedditTarget,
        resolvedPost: Post? = null,
        readCache: Boolean = true,
    ): Boolean {
        return when (target) {
            is RedditTarget.Community -> {
                openSubreddit(target.name, target.sort)
                target.wikiPage?.let { page -> uiRequests.requestCommunityWikiPage(target.name, page) }
                true
            }
            is RedditTarget.Feed -> {
                selectFeedSource(target.source)
                true
            }
            is RedditTarget.User -> {
                // ISSUE46_REDDIT_USERNAME_LENGTH_V1: user/me is the active
                // account's own profile; signed out it cannot be opened.
                val username = if (target.username.equals(REDDIT_OWN_PROFILE_ALIAS, ignoreCase = true)) {
                    if (!activeAccountIsAuthenticated) return false
                    activeAccountUsername
                } else {
                    target.username
                }
                openUser(username)
                target.tab
                    ?.takeUnless { tab ->
                        tab.ownProfileOnly && !(
                            activeAccountIsAuthenticated &&
                                username.equals(activeAccountUsername, ignoreCase = true)
                            )
                    }
                    ?.let { tab -> selectUserProfileTab(username, tab.profileTabIndex) }
                true
            }
            is RedditTarget.Search -> {
                // REDDITURL04_SEARCH_LINK_V1: a community search opens from
                // that community so the search is scoped to it.
                target.community?.let(::openSubreddit)
                openSearch(refocus = false)
                if (target.community == null) updateSearchArea(SearchArea.ALL_SUBSHELL)
                updateSearchQuery(target.query)
                submitSearch()
                true
            }
            is RedditTarget.Post -> {
                val post = resolvedPost?.takeIf { candidate ->
                    redditTargetMatchesPost(target.postId, candidate.id)
                } ?: localRedditTargetPost(target.postId, readCache) ?: return false
                open(post)
                true
            }
            is RedditTarget.Comment -> {
                val post = resolvedPost?.takeIf { candidate ->
                    redditTargetMatchesPost(target.postId, candidate.id)
                } ?: localRedditTargetPost(target.postId, readCache) ?: return false
                open(post, commentId = target.commentId)
                true
            }
            is RedditTarget.InlineVideo -> {
                val sourcePost = target.postId?.let { postId ->
                    resolvedPost?.takeIf { candidate -> redditTargetMatchesPost(postId, candidate.id) }
                        ?: localRedditTargetPost(postId, readCache)
                }
                openMedia(inlineRedditVideoPost(target, sourcePost))
                true
            }
            is RedditTarget.Image -> {
                openMedia(redditImageLinkPost(target))
                true
            }
            // Needs Reddit's redirect first; the deep-link effect resolves it.
            is RedditTarget.ShareLink -> false
            is RedditTarget.CustomFeed -> {
                val owner = target.owner
                    ?: activeAccountUsername.takeIf { activeAccountIsAuthenticated }
                    ?: return false
                uiRequests.requestCustomFeedLink("/user/$owner/m/${target.name}/")
                true
            }
            is RedditTarget.Compose -> {
                uiRequests.requestNewMessagePrefill(newMessagePrefillFor(target))
                openSecondary(SecondaryDestination.NEW_MESSAGE)
                true
            }
        }
    }

    private fun localRedditTargetPost(postId: String, readCache: Boolean = true): Post? =
        inMemoryRedditTargetPost(postId)
            ?: cachedRedditTargetPost(postId, activeAccountId).takeIf { readCache }
            ?: posts.firstOrNull { post -> redditTargetMatchesPost(postId, post.id) }

    private fun inMemoryRedditTargetPost(postId: String): Post? =
        postPagingSnapshots.values
            .asSequence()
            .flatMap { snapshot -> snapshot.items.asSequence() }
            .firstOrNull { post -> redditTargetMatchesPost(postId, post.id) }

    // LOWUI2_DEEP_LINK_CACHE_READ_OFF_MAIN_V1 (OCB-117): the deep-link effect
    // reads the post cache (a blocking Room call) on IO before it opens the route.
    internal fun redditTargetPostNeedsCacheRead(target: RedditTarget): String? =
        (target.postIdOrNull() ?: (target as? RedditTarget.InlineVideo)?.postId)
            ?.takeIf { postId -> inMemoryRedditTargetPost(postId) == null }

    internal fun cachedRedditTargetPost(postId: String, accountStableId: String): Post? =
        repository.postForAccount(postId.trim().removePrefix("t3_"), accountStableId).valueOrNull()

    fun openFromCustomFeed(post: Post) {
        openPostRoute(
            post = post,
            origin = captureCurrentPostOrigin(),
        )
    }

    fun openFromMainFeed(post: Post) {
        openFromMainFeed(post, FeedPostEntryTarget.TOP)
    }

    internal fun openFromMainFeed(
        post: Post,
        entryTarget: FeedPostEntryTarget,
    ) {
        openPostRoute(
            post = post,
            entryTarget = entryTarget,
            origin = FeedPostOrigin.MainFeed(
                destination = MainDestination.HOME,
                source = feedSource,
                sort = feedSort,
            ),
        )
    }

    fun openFromCommunity(post: Post, communityName: String) {
        openPostRoute(
            post = post,
            origin = FeedPostOrigin.Community(
                destination = destination,
                communityName = communityName,
                sort = communitySort,
            ),
        )
    }

    fun openFromSearch(post: Post) {
        openPostRoute(
            post = post,
            origin = FeedPostOrigin.Search(
                destination = searchOriginDestination,
                originCommunity = searchOriginCommunity,
                query = searchSubmittedQuery.ifBlank { searchQuery },
                scope = searchScope,
                area = searchArea,
            ),
        )
    }

    fun openFromUser(post: Post, username: String, commentId: String? = null) {
        openPostRoute(
            post = post,
            commentId = commentId,
            origin = FeedPostOrigin.User(
                destination = destination,
                username = username,
            ),
        )
    }

    fun openFromYou(post: Post) {
        openPostRoute(
            post = post,
            origin = FeedPostOrigin.MainFeed(
                destination = MainDestination.YOU,
                source = feedSource,
                sort = feedSort,
            ),
        )
    }

    internal fun currentPostSupportsExpandedHomeListDetail(): Boolean {
        val origin = activeFeedPostRoute?.origin as? FeedPostOrigin.MainFeed
            ?: return false
        return origin.destination == MainDestination.HOME
    }

    internal fun currentPostSupportsExpandedInboxListDetail(): Boolean =
        activeFeedPostRoute?.origin == FeedPostOrigin.Inbox

    // A11Y01A4_REPLACE_CURRENT_POST_DETAIL_V1
    internal fun replaceCurrentPostSelection(
        post: Post,
        entryTarget: FeedPostEntryTarget = FeedPostEntryTarget.TOP,
    ): Boolean {
        val current = routeStack.current as? SubshellRoute.Post ?: return false
        return replaceCurrentPostRoute(
            post = post,
            commentId = null,
            origin = current.postRoute.origin,
            entryTarget = entryTarget,
        )
    }

    // NEXT01A4A_REPLACE_CURRENT_USER_CONTENT_DETAIL_V1
    internal fun replaceCurrentUserContentSelection(
        post: Post,
        username: String,
        commentId: String? = null,
        entryTarget: FeedPostEntryTarget = FeedPostEntryTarget.TOP,
    ): Boolean {
        val current =
            routeStack.current as? SubshellRoute.Post ?: return false
        val origin =
            current.postRoute.origin as? FeedPostOrigin.User ?: return false
        if (!origin.username.equals(username, ignoreCase = true)) {
            return false
        }
        return replaceCurrentPostRoute(
            post = post,
            commentId = commentId,
            origin = origin,
            entryTarget = entryTarget,
        )
    }

    private fun replaceCurrentPostRoute(
        post: Post,
        commentId: String? = null,
        origin: FeedPostOrigin,
        entryTarget: FeedPostEntryTarget = FeedPostEntryTarget.TOP,
    ): Boolean {
        selectedPost = post
        beginVideoPresentationHandoff(post)
        nextPostVisitId += 1
        val replacement = FeedPostRoute(
            postId = post.id,
            focusedCommentId = commentId,
            visitId = nextPostVisitId,
            origin = origin,
            entryTarget = entryTarget,
        )
        routedPostsByVisitId[replacement.visitId] = post
        replaceCurrentRoute(SubshellRoute.Post(replacement))
        markPostRead(post.id)
        return true
    }

    private fun openPostRoute(
        post: Post,
        commentId: String? = null,
        origin: FeedPostOrigin,
        entryTarget: FeedPostEntryTarget = FeedPostEntryTarget.TOP,
    ) {
        selectedPost = post
        beginVideoPresentationHandoff(post)
        nextPostVisitId += 1
        val route = FeedPostRoute(
            postId = post.id,
            focusedCommentId = commentId,
            visitId = nextPostVisitId,
            origin = origin,
            entryTarget = entryTarget,
        )
        routedPostsByVisitId[route.visitId] = post
        pushRoute(SubshellRoute.Post(route))
        markPostRead(post.id)
    }

    internal fun isPostEntryTargetPending(
        visitId: Int,
        target: FeedPostEntryTarget,
    ): Boolean {
        val route = activeFeedPostRoute ?: return false
        return route.visitId == visitId &&
            route.entryTarget == target &&
            visitId !in consumedPostEntryVisitIds
    }

    internal fun consumePostEntryTarget(
        visitId: Int,
        target: FeedPostEntryTarget,
    ): Boolean {
        if (!isPostEntryTargetPending(visitId, target)) return false
        return consumedPostEntryVisitIds.add(visitId)
    }

    fun requestPostManage(postId: String) {
        uiRequests.requestPostManage(postId)
    }

    fun consumePostManageRequest(postId: String): Boolean =
        uiRequests.consumePostManageRequest(postId)

    fun requestPostDelete(postId: String) {
        uiRequests.requestPostDelete(postId)
    }

    fun consumePostDeleteRequest(postId: String): Boolean =
        uiRequests.consumePostDeleteRequest(postId)

    fun requestPostEdit(postId: String) {
        uiRequests.requestPostEdit(postId)
    }

    fun consumePostEditRequest(postId: String): Boolean =
        uiRequests.consumePostEditRequest(postId)

    internal fun consumeNewMessagePrefill(): NewMessagePrefill? =
        uiRequests.consumeNewMessagePrefill()

    /** A compose link waiting for New message (read as state, OCB-229). */
    internal val hasPendingNewMessagePrefill: Boolean
        get() = uiRequests.pendingNewMessagePrefill != null

    internal val pendingCommunityWikiPage: Pair<String, String>?
        get() = uiRequests.pendingCommunityWikiPage

    internal fun consumeCommunityWikiPage(communityName: String): String? =
        uiRequests.consumeCommunityWikiPage(communityName)

    internal val pendingCustomFeedLinkPath: String?
        get() = uiRequests.pendingCustomFeedLinkPath

    internal fun consumeCustomFeedLinkPath(): String? =
        uiRequests.consumeCustomFeedLinkPath()

    fun applyManagedPostMetadata(
        postId: String,
        flairText: String?,
        nsfw: Boolean,
        spoiler: Boolean,
    ) {
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isEmpty()) return

        fun updated(post: Post): Post =
            if (normalizedPostIdentity(post.id) == normalizedPostId) {
                val flair = flairText?.trim()?.takeIf(String::isNotEmpty)
                post.copy(
                    flair = flair,
                    // ISSUE44_POST_FLAIR_RICHTEXT_V1: the readback carries only the text;
                    // icons for a changed flair return with the next fetch.
                    flairRichText = if (flair == post.flair) post.flairRichText else emptyList(),
                    nsfw = nsfw,
                    spoiler = spoiler,
                )
            } else {
                post
            }

        postPagingSnapshots.keys.toList().forEach { queryKey ->
            val snapshot = postPagingSnapshots[queryKey] ?: return@forEach
            postPagingSnapshots[queryKey] = snapshot.copy(
                items = snapshot.items.map(::updated),
            )
        }
        routedPostsByVisitId.keys.toList().forEach { visitId ->
            routedPostsByVisitId[visitId]?.let { post ->
                routedPostsByVisitId[visitId] = updated(post)
            }
        }
        selectedPost = selectedPost?.let(::updated)
        selectedMediaPost = selectedMediaPost?.let(::updated)
    }

    fun applyDeletedPost(postId: String) {
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isEmpty()) return
        if (normalizedPostId !in remotelyDeletedPostIds) remotelyDeletedPostIds.add(normalizedPostId)
        if (pendingPostEditId == normalizedPostId) pendingPostEditId = null
        pendingPostDeleteId = null

        postPagingSnapshots.keys.toList().forEach { queryKey ->
            val snapshot = postPagingSnapshots[queryKey] ?: return@forEach
            postPagingSnapshots[queryKey] = snapshot.copy(
                items = snapshot.items.filterNot { post ->
                    normalizedPostIdentity(post.id) == normalizedPostId
                },
            )
        }

        savedPostIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        hiddenPostIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        readPostIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        revealedMediaIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        revealedNsfwMediaIds.removeAll { id -> normalizedPostIdentity(id) == normalizedPostId }
        votes.keys.filter { id -> normalizedPostIdentity(id) == normalizedPostId }.toList().forEach(votes::remove)
        pollVotes.keys.filter { id -> normalizedPostIdentity(id) == normalizedPostId }.toList().forEach(pollVotes::remove)
        galleryIndices.keys.filter { id -> normalizedPostIdentity(id) == normalizedPostId }.toList().forEach(galleryIndices::remove)
        persistActiveAccountPostActions()

        videoSurfaceVisibility.entries.removeAll { entry ->
            normalizedPostIdentity(entry.value.mediaId) == normalizedPostId
        }
        if (normalizedPostIdentity(activeVideoMediaId.orEmpty()) == normalizedPostId) activeVideoMediaId = null
        if (normalizedPostIdentity(manuallyStartedVideoId.orEmpty()) == normalizedPostId) manuallyStartedVideoId = null
        if (normalizedPostIdentity(manuallyPausedVideoId.orEmpty()) == normalizedPostId) manuallyPausedVideoId = null
        if (normalizedPostIdentity(scrubbingVideoId.orEmpty()) == normalizedPostId) scrubbingVideoId = null
        if (normalizedPostIdentity(activeAnimatedMediaId.orEmpty()) == normalizedPostId) activeAnimatedMediaId = null
        if (normalizedPostIdentity(feedAutoplayMediaId.orEmpty()) == normalizedPostId) feedAutoplayMediaId = null
        if (normalizedPostIdentity(preloadVideoMediaId.orEmpty()) == normalizedPostId) preloadVideoMediaId = null
        if (normalizedPostIdentity(mediaPresentationHandoffId.orEmpty()) == normalizedPostId) mediaPresentationHandoffId = null
        if (normalizedPostIdentity(backgroundResumeVideoId.orEmpty()) == normalizedPostId) {
            backgroundResumeVideoId = null
            backgroundResumePending = false
            backgroundResumeWasManual = false
        }

        routedPostsByVisitId.entries.removeAll { entry ->
            normalizedPostIdentity(entry.value.id) == normalizedPostId
        }
        if (normalizedPostIdentity(selectedMediaPost?.id.orEmpty()) == normalizedPostId) selectedMediaPost = null

        val currentRoute = routeStack.current as? SubshellRoute.Post
        val shouldClosePost = currentRoute != null &&
            normalizedPostIdentity(currentRoute.postRoute.postId) == normalizedPostId
        if (shouldClosePost) {
            selectedPost = null
            popCurrentRoute()
        } else if (normalizedPostIdentity(selectedPost?.id.orEmpty()) == normalizedPostId) {
            selectedPost = null
        }
        reconcileVideoPlayback()
    }

    fun applyEditedPostBody(postId: String, text: String) {
        // LOWUI8_SAVED_EDIT_REACHES_EVERY_COPY_V1 (OCB-103): the open post was
        // the only copy updated, so Home kept the old body and opened it again
        // from its page; every copy of the post takes the saved text, as
        // applyManagedPostMetadata does. Only the end is trimmed (OCB-319).
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isEmpty()) return

        fun updated(post: Post): Post =
            if (normalizedPostIdentity(post.id) == normalizedPostId) {
                // ISSUE8_INLINE_REDDIT_MEDIA_V1: the saved text is Reddit's own; keep
                // the media addresses the post already had.
                post.copy(body = reannotateRedditMedia(text.trimEnd(), previous = post.body))
            } else {
                post
            }

        postPagingSnapshots.keys.toList().forEach { queryKey ->
            val snapshot = postPagingSnapshots[queryKey] ?: return@forEach
            postPagingSnapshots[queryKey] = snapshot.copy(
                items = snapshot.items.map(::updated),
            )
        }
        routedPostsByVisitId.keys.toList().forEach { visitId ->
            routedPostsByVisitId[visitId]?.let { post ->
                routedPostsByVisitId[visitId] = updated(post)
            }
        }
        selectedPost = selectedPost?.let(::updated)
        selectedMediaPost = selectedMediaPost?.let(::updated)
    }

    fun closePost() {
        pendingInboxReadReceipt = null
        popExpectedRoute<SubshellRoute.Post>()
    }

    fun openUser(
        username: String,
        activityEvidence: ProfileActivityEvidence? = null,
    ) {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty()) return
        activityEvidence?.let { evidence ->
            notePublicProfileActivity(normalizedUsername, evidence)
        }
        val current = routeStack.current
        if (current is SubshellRoute.User &&
            current.username.equals(normalizedUsername, ignoreCase = true)
        ) {
            return
        }
        pushRoute(
            SubshellRoute.User(
                destination = destination,
                username = normalizedUsername,
            ),
        )
        showChrome()
    }

    // NEXT01A4B_REPLACE_CURRENT_USER_DETAIL_V1
    internal fun replaceCurrentUserSelection(username: String): Boolean {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty()) return false

        val current =
            routeStack.current as? SubshellRoute.User ?: return false

        if (
            current.username.equals(
                normalizedUsername,
                ignoreCase = true,
            )
        ) {
            return true
        }

        replaceCurrentRoute(
            SubshellRoute.User(
                destination = current.destination,
                username = normalizedUsername,
            ),
        )
        showChrome()
        return true
    }

    fun hasPublicProfileActivityEvidence(
        username: String,
        evidence: ProfileActivityEvidence,
    ): Boolean = identityMetadata.hasPublicProfileActivityEvidence(username, evidence)

    private fun notePublicProfileActivity(
        username: String,
        evidence: ProfileActivityEvidence,
    ) {
        identityMetadata.notePublicProfileActivity(username, evidence)
    }

    fun closeUser() {
        popExpectedRoute<SubshellRoute.User>()
    }

    fun openMedia(post: Post, index: Int = galleryIndices[post.id] ?: 0) {
        val preservesExistingVideoIntent = post.kind == PostKind.VIDEO &&
            (activeVideoMediaId == post.id || manuallyPausedVideoId == post.id)
        if (preservesExistingVideoIntent) mediaPresentationHandoffId = post.id
        selectedMediaPost = post
        val route = SubshellRoute.Media(
            postId = post.id,
            index = index.coerceAtLeast(0),
        )
        routeStack = if (routeStack.current is SubshellRoute.Media) {
            routeStack.replaceCurrent(route)
        } else {
            refreshCurrentRouteSnapshot()
            routeStack.push(route)
        }
        syncNavigationProjection()
        if (post.kind == PostKind.VIDEO) {
            if (!preservesExistingVideoIntent) playVideo(post.id)
        } else {
            activeAnimatedMediaId = post.id
        }
    }

    fun closeMedia() {
        popExpectedRoute<SubshellRoute.Media>()
    }

    internal fun isVideoPresentationHandoff(postId: String): Boolean =
        mediaPresentationHandoffId == postId

    internal fun completeVideoPresentationHandoff(postId: String) {
        if (mediaPresentationHandoffId != postId) return
        mediaPresentationHandoffId = null
        reconcileVideoPlayback()
    }

    private fun beginVideoPresentationHandoff(post: Post) {
        if (post.kind == PostKind.VIDEO &&
            (activeVideoMediaId == post.id || manuallyPausedVideoId == post.id)
        ) {
            mediaPresentationHandoffId = post.id
        }
    }

    fun updateMediaIndex(postId: String, index: Int) {
        galleryIndices[postId] = index
        selectedMediaIndex = index.coerceAtLeast(0)
        val current = routeStack.current
        if (current is SubshellRoute.Media && current.postId == postId) {
            routeStack = routeStack.replaceCurrent(
                current.copy(index = selectedMediaIndex),
            )
        }
    }

    fun openSearch(refocus: Boolean = true) {
        if (routeStack.current is SubshellRoute.Search) {
            if (refocus) searchFocusRequest += 1
            return
        }
        val originCommunity = selectedSubreddit
        val area = when {
            originCommunity != null -> SearchArea.CURRENT_COMMUNITY
            activeAccountIsAuthenticated -> SearchArea.ALL_SUBSHELL
            destination == MainDestination.SUBS -> SearchArea.MY_COMMUNITIES
            else -> SearchArea.ALL_SUBSHELL
        }
        val scope = when {
            area == SearchArea.CURRENT_COMMUNITY -> SearchScope.POSTS
            activeAccountIsAuthenticated -> SearchScope.ALL
            else -> searchScope.takeIf { candidate ->
                when (area) {
                    SearchArea.ALL_SUBSHELL -> true
                    SearchArea.MY_COMMUNITIES ->
                        candidate in listOf(
                            SearchScope.ALL,
                            SearchScope.POSTS,
                            SearchScope.COMMUNITIES,
                        )
                    SearchArea.CURRENT_COMMUNITY -> candidate == SearchScope.POSTS
                }
            } ?: SearchScope.ALL
        }
        pushRoute(
            SubshellRoute.Search(
                destination = destination,
                originCommunity = originCommunity,
                query = "",
                submittedQuery = "",
                scope = scope,
                area = area,
            ),
        )
        if (refocus) searchFocusRequest += 1
    }

    fun closeSearch() {
        popExpectedRoute<SubshellRoute.Search>()
    }

    fun handleSearchBack(): Boolean {
        if (routeStack.current !is SubshellRoute.Search) return false
        return when (
            resolveSearchBackAction(
                draftQuery = searchQuery,
                submittedQuery = searchSubmittedQuery,
            )
        ) {
            SearchBackAction.RESTORE_SUBMITTED_QUERY -> {
                searchSession = searchSession.updateDraft(searchSubmittedQuery)
                refreshCurrentRouteSnapshot()
                showChrome()
                true
            }
            SearchBackAction.CLEAR_TO_SEARCH_HUB -> {
                clearSearchToHub()
                true
            }
            SearchBackAction.CLOSE_SEARCH -> popExpectedRoute<SubshellRoute.Search>()
        }
    }

    fun clearSearchToHub() {
        searchSession = SearchSessionSnapshot()
        refreshCurrentRouteSnapshot()
        showChrome()
    }

    fun updateSearchQuery(query: String) {
        searchSession = searchSession.updateDraft(query)
        refreshCurrentRouteSnapshot()
    }

    fun updateSearchScope(scope: SearchScope) {
        searchScope = scope.takeIf { it in availableSearchScopes }
            ?: availableSearchScopes.firstOrNull()
            ?: SearchScope.POSTS
        refreshCurrentRouteSnapshot()
    }

    fun updateSearchArea(area: SearchArea) {
        searchArea = if (area in availableSearchAreas) area else SearchArea.ALL_SUBSHELL
        if (searchScope !in availableSearchScopes) {
            searchScope = availableSearchScopes.firstOrNull() ?: SearchScope.POSTS
        }
        refreshCurrentRouteSnapshot()
    }

    fun searchAreaLabel(area: SearchArea): String = when (area) {
        SearchArea.ALL_SUBSHELL -> if (activeAccountIsAuthenticated) "All Reddit" else "All subshell"
        SearchArea.MY_COMMUNITIES -> "My communities"
        SearchArea.CURRENT_COMMUNITY -> searchOriginCommunity?.let { "r/$it" } ?: "This community"
    }

    fun searchAreaDescription(area: SearchArea): String = when (area) {
        SearchArea.ALL_SUBSHELL -> if (activeAccountIsAuthenticated) {
            "Reddit posts, communities and people"
        } else {
            "Posts, communities and people"
        }
        SearchArea.MY_COMMUNITIES -> "Communities you joined"
        SearchArea.CURRENT_COMMUNITY -> "This community"
    }

    fun matchesSearchArea(communityName: String): Boolean = when (searchArea) {
        SearchArea.ALL_SUBSHELL -> true
        SearchArea.MY_COMMUNITIES -> communities.any { community ->
            community.name.equals(communityName, ignoreCase = true) &&
                community.id in joinedCommunityIds
        }
        SearchArea.CURRENT_COMMUNITY -> searchOriginCommunity?.equals(communityName, ignoreCase = true) == true
    }

    fun submitSearch(): String? {
        val submitted = searchSession.submit() ?: return null
        searchSession = submitted
        refreshCurrentRouteSnapshot()
        searchHistory.recordSubmittedSearch(submitted.submittedQuery)
        return submitted.submittedQuery
    }

    fun clearRecentSearches() {
        searchHistory.clearRecentSearches()
    }

    internal fun searchIndexBoostFor(
        kind: SearchIndexKind,
        key: String,
    ): Int = searchHistory.searchIndexBoostFor(kind, key)

    internal fun recentSearchIndexEntries(
        kind: SearchIndexKind,
        limit: Int,
    ): List<SearchIndexEntry> = searchHistory.recentSearchIndexEntries(kind, limit)

    internal fun recordSearchSelection(
        kind: SearchIndexKind,
        key: String,
        primaryText: String,
        secondaryText: String = "",
        imageUri: String? = null,
    ) {
        searchHistory.recordSearchSelection(kind, key, primaryText, secondaryText, imageUri)
    }

    internal fun clearSearchIndex(kind: SearchIndexKind? = null) {
        searchHistory.clearSearchIndex(kind)
    }

    internal suspend fun refreshTrendingSearchSeedsIfStale() {
        searchHistory.refreshTrendingSearchSeedsIfStale()
    }

    internal fun openSearchCommunity(
        name: String,
        description: String = "",
        iconImageUri: String? = null,
    ) {
        recordSearchSelection(
            kind = SearchIndexKind.COMMUNITY,
            key = name,
            primaryText = name,
            secondaryText = description,
            imageUri = iconImageUri,
        )
        openSubreddit(name)
    }

    internal fun openSearchUser(
        username: String,
        description: String = "",
        avatarImageUri: String? = null,
    ) {
        recordSearchSelection(
            kind = SearchIndexKind.USER,
            key = username,
            primaryText = username,
            secondaryText = description,
            imageUri = avatarImageUri,
        )
        openUser(username)
    }

    private fun sanitizeSearchFiltersForActiveAccount() {
        if (searchArea !in availableSearchAreas) {
            searchArea = SearchArea.ALL_SUBSHELL
        }
        if (searchScope !in availableSearchScopes) {
            searchScope = availableSearchScopes.firstOrNull() ?: SearchScope.POSTS
        }
    }

    fun openInboxNotificationRoot() {
        routeStack = SubshellRouteStack.forDestination(
            destination = MainDestination.INBOX,
            source = feedSource,
            sort = feedSort,
        )
        syncNavigationProjection()
    }

    fun openInboxNotificationItem(
        item: InboxItem,
        resolvedPost: Post? = null,
        allowFallbackDetail: Boolean = false,
        verifiedReadAccountStableId: String? = null,
    ) {
        openInboxNotificationRoot()
        openInboxItem(
            item = item,
            resolvedPost = resolvedPost,
            allowFallbackDetail = allowFallbackDetail,
            verifiedReadAccountStableId = verifiedReadAccountStableId,
        )
    }

    fun openModeratorQueueItem(
        item: RedditModeratorQueueItem,
        resolvedPost: Post,
    ) {
        inboxActivity.openModeratorQueueItem(item, resolvedPost)
    }

    internal fun replaceCurrentModeratorQueueDetail(
        item: RedditModeratorQueueItem,
        resolvedPost: Post,
    ): Boolean = inboxActivity.replaceCurrentModeratorQueueDetail(item, resolvedPost)

    fun openInboxItem(
        item: InboxItem,
        resolvedPost: Post? = null,
        allowFallbackDetail: Boolean = false,
        verifiedReadAccountStableId: String? = null,
    ) {
        inboxActivity.openInboxItem(item, resolvedPost, allowFallbackDetail, verifiedReadAccountStableId)
    }

    internal fun consumeInboxReadReceipt(
        postId: String,
        commentId: String,
    ): PendingInboxReadReceipt? = inboxActivity.consumeInboxReadReceipt(postId, commentId)

    internal fun replaceCurrentInboxDetail(
        item: InboxItem,
        resolvedPost: Post? = null,
        allowFallbackDetail: Boolean = false,
    ): Boolean = inboxActivity.replaceCurrentInboxDetail(item, resolvedPost, allowFallbackDetail)

    fun messageConversationDraft(
        accountStableId: String,
        inboxItemId: String,
    ): String = inboxActivity.messageConversationDraft(accountStableId, inboxItemId)

    fun updateMessageConversationDraft(
        accountStableId: String,
        inboxItemId: String,
        text: String,
    ) {
        inboxActivity.updateMessageConversationDraft(accountStableId, inboxItemId, text)
    }

    fun closeConversation() {
        popExpectedRoute<SubshellRoute.Conversation>()
    }

    fun openSecondary(destination: SecondaryDestination) {
        routeStack = SubshellRouteStack.forDestination(
            destination = this.destination,
            source = feedSource,
            sort = feedSort,
        ).push(
            SubshellRoute.Secondary(
                destination = this.destination,
                secondaryDestination = destination,
            ),
        )
        syncNavigationProjection()
        showChrome()
    }

    fun openCommunityExplore(focusSearch: Boolean = false) {
        val current = routeStack.current
        if (
            current is SubshellRoute.Secondary &&
            current.secondaryDestination == SecondaryDestination.COMMUNITY_EXPLORE
        ) {
            if (focusSearch) communityExploreSearchFocusRequest += 1
            return
        }
        if (focusSearch) communityExploreSearchFocusRequest += 1
        openSecondary(SecondaryDestination.COMMUNITY_EXPLORE)
    }

    internal fun replaceCurrentNewMessageWithConversation(
        item: InboxItem,
    ): Boolean = inboxActivity.replaceCurrentNewMessageWithConversation(item)

    fun closeSecondary() {
        popExpectedRoute<SubshellRoute.Secondary>()
    }

    // NEXT01A5_SUBSCRIPTIONS_COMMUNITY_SELECTION_V1
    internal fun openSubscriptionsCommunitySelection(name: String) {
        if (!replaceCurrentCommunitySelection(name)) {
            openSubreddit(name)
        }
    }

    internal fun currentSubscriptionsCommunitySelectionMatches(
        name: String,
    ): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false
        val current =
            routeStack.current as? SubshellRoute.Community ?: return false
        return current.destination == MainDestination.SUBS &&
            current.communityName.equals(normalizedName, ignoreCase = true)
    }

    internal fun replaceCurrentCommunitySelection(name: String): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false

        val current =
            routeStack.current as? SubshellRoute.Community ?: return false

        if (current.destination != MainDestination.SUBS) return false

        if (
            current.communityName.equals(
                normalizedName,
                ignoreCase = true,
            )
        ) {
            return true
        }

        replaceCurrentRoute(
            current.copy(
                communityName = normalizedName,
                sort = preferredCommunitySort(
                    communitySortPreferences,
                    normalizedName,
                ),
            ),
        )
        recordRecentCommunityVisit(normalizedName)
        showChrome()
        return true
    }

    fun openSubreddit(name: String, sort: CommunitySort? = null) {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return
        val current = routeStack.current
        if (current is SubshellRoute.Community &&
            current.communityName.equals(normalizedName, ignoreCase = true)
        ) {
            return
        }
        val switchingCommunity = !selectedSubreddit.equals(normalizedName, ignoreCase = true)
        pushRoute(
            SubshellRoute.Community(
                destination = destination,
                communityName = normalizedName,
                // A link's sort (REDDITURL01_COMMUNITY_SORT_LINK_V1) applies
                // to this visit only; it is not stored as the preference.
                sort = sort ?: if (switchingCommunity) {
                    preferredCommunitySort(communitySortPreferences, normalizedName)
                } else {
                    communitySort
                },
            ),
        )
        recordRecentCommunityVisit(normalizedName)
        showChrome()
    }

    fun closeSubreddit() {
        popExpectedRoute<SubshellRoute.Community>()
    }

    private fun captureCurrentPostOrigin(): FeedPostOrigin {
        refreshCurrentRouteSnapshot()
        return when (val route = routeStack.surface) {
            is SubshellRoute.Main -> if (route.destination == MainDestination.INBOX) {
                FeedPostOrigin.Inbox
            } else {
                FeedPostOrigin.MainFeed(
                    destination = route.destination,
                    source = route.source,
                    sort = route.sort,
                )
            }
            is SubshellRoute.Community -> FeedPostOrigin.Community(
                destination = route.destination,
                communityName = route.communityName,
                sort = route.sort,
            )
            is SubshellRoute.Search -> FeedPostOrigin.Search(
                destination = route.destination,
                originCommunity = route.originCommunity,
                query = route.query,
                scope = route.scope,
                area = route.area,
            )
            is SubshellRoute.Post -> route.postRoute.origin
            is SubshellRoute.User -> FeedPostOrigin.User(
                destination = route.destination,
                username = route.username,
            )
            is SubshellRoute.Conversation -> FeedPostOrigin.Inbox
            is SubshellRoute.Secondary -> FeedPostOrigin.Secondary(
                destination = route.destination,
                secondaryDestination = route.secondaryDestination,
            )
            is SubshellRoute.Media,
            is SubshellRoute.CommentMedia,
            SubshellRoute.PostComposer,
            -> error("Overlay routes cannot provide a post origin")
        }
    }

    fun subredditLabel(name: String): String {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrDefault(name.trim())
        return when (subredditLabelStyle) {
            SubredditLabelStyle.MODERN -> "r/$normalizedName"
            SubredditLabelStyle.CLASSIC -> "/r/$normalizedName"
            SubredditLabelStyle.PLAIN -> normalizedName
        }
    }

    fun toggleSaved(postId: String) {
        postSaves.toggleSaved(postId)
    }

    fun toggleSaved(post: Post) {
        postSaves.toggleSaved(post)
    }
    fun togglePostFollow(post: Post) {
        postFollows.togglePostFollow(post)
    }


    fun toggleSaved(comment: Comment) {
        postSaves.toggleSaved(comment)
    }

    fun hidePost(postId: String) {
        contentSafety.hidePost(postId)
    }

    fun togglePostHidden(
        post: Post,
        authoritativeHiddenHint: Boolean = false,
    ) {
        contentSafety.togglePostHidden(post, authoritativeHiddenHint)
    }

    fun report(post: Post, reason: String) {
        contentSafety.report(post, reason)
    }

    fun report(comment: Comment, reason: String) {
        contentSafety.report(comment, reason)
    }

    fun setUserBlocked(username: String, blocked: Boolean) {
        contentSafety.setUserBlocked(username, blocked)
    }

    fun markPostRead(postId: String) {
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isEmpty() || normalizedPostId in readPostIds) return
        readPostIds.add(normalizedPostId)
        persistActiveAccountPostActions()
    }

    fun updateHideReadPosts(enabled: Boolean) {
        hideReadPosts = enabled
    }

    fun resetReadPostHistory() {
        if (readPostIds.isEmpty() && !hideReadPosts) return
        readPostIds.clear()
        hideReadPosts = false
        persistActiveAccountPostActions()
    }

    fun vote(postId: String, direction: Int) {
        postVotes.vote(postId, direction)
    }

    fun vote(post: Post, direction: Int) {
        postVotes.vote(post, direction)
    }

    fun vote(comment: Comment, direction: Int) {
        postVotes.vote(comment, direction)
    }

    fun synchronizePostVoteState(postVoteState: PostVoteUiState) {
        postVotes.synchronizePostVoteState(postVoteState)
    }

    private fun clearAuthenticatedPostVoteState() {
        postVotes.clearAuthenticatedPostVoteState()
    }

    fun synchronizeContentSafetyState(contentSafetyState: ContentSafetyUiState) {
        contentSafety.synchronizeContentSafetyState(contentSafetyState)
    }

    private fun clearAuthenticatedContentSafetyState() {
        contentSafety.clearAuthenticatedContentSafetyState()
    }

    fun synchronizePostSaveState(postSaveState: PostSaveUiState) {
        postSaves.synchronizePostSaveState(postSaveState)
    }

    private fun clearAuthenticatedPostSaveState() {
        postSaves.clearAuthenticatedPostSaveState()
    }
    fun synchronizePostFollowState(
        postFollowState: PostFollowUiState,
    ) {
        postFollows.synchronizePostFollowState(postFollowState)
    }

    private fun clearAuthenticatedPostFollowState() {
        postFollows.clearAuthenticatedPostFollowState()
    }


    fun votePoll(postId: String, optionId: String) {
        if (!localMutationsAllowed) return
        val normalizedPostId = postId.trim()
        val normalizedOptionId = optionId.trim()
        if (normalizedPostId.isEmpty() || normalizedOptionId.isEmpty()) return
        pollVotes[normalizedPostId] = normalizedOptionId
        repository.recordPollVote(normalizedPostId, normalizedOptionId)
        persistActiveAccountPostActions()
    }

    fun revealMedia(post: Post) {
        if (post.id !in revealedMediaIds) revealedMediaIds.add(post.id)
        if (post.nsfw) revealedNsfwMediaIds.add(post.id)
    }

    fun revealMedia(postId: String) {
        if (postId !in revealedMediaIds) revealedMediaIds.add(postId)
    }

    fun resetSensitiveMediaReveals() {
        revealedMediaIds.clear()
        revealedNsfwMediaIds.clear()
        activeAnimatedMediaId = null
        reconcileVideoPlayback()
    }

    fun toggleVideoMuted() {
        feedVideoMuted = !feedVideoMuted
    }

    // VIDEO07_PICTURE_IN_PICTURE_V1 (#227): only a full-screen video that is
    // playing may move into the small window, and only with the setting on;
    // read live, so a closed viewer or a paused video is never carried along.
    var inPictureInPicture by mutableStateOf(false)
        private set

    fun updateInPictureInPicture(active: Boolean) {
        inPictureInPicture = active
    }

    fun pictureInPictureVideo(): Post? = selectedMediaPost?.takeIf { post ->
        videoPictureInPictureAllowed(
            enabled = videoPictureInPicture,
            viewerOpen = routeStack.current is SubshellRoute.Media,
            isVideo = post.kind == PostKind.VIDEO,
            playing = activeAnimatedMediaId == post.id,
        )
    }

    /** The feed video whose controls the reader is using (VIDEO03), else null. */
    var inlineVideoControlsMediaId by mutableStateOf<String?>(null)
        private set

    fun updateInlineVideoControls(mediaId: String, visible: Boolean) {
        if (visible) {
            inlineVideoControlsMediaId = mediaId
        } else if (inlineVideoControlsMediaId == mediaId) {
            inlineVideoControlsMediaId = null
        }
    }

    fun isVideoManuallyPaused(postId: String): Boolean = manuallyPausedVideoId == postId

    fun isVideoScrubbing(postId: String): Boolean = scrubbingVideoId == postId

    fun beginVideoScrubbing(postId: String) {
        if (scrubbingVideoId == postId) return
        scrubbingVideoId = postId
    }

    fun endVideoScrubbing(postId: String) {
        if (scrubbingVideoId != postId) return
        scrubbingVideoId = null
        reconcileVideoPlayback()
    }

    fun cancelVideoScrubbing(postId: String) {
        if (scrubbingVideoId == postId) scrubbingVideoId = null
    }

    /**
     * Forced global media state used by lifecycle teardown. Feed visibility must use
     * [setFeedAutoplayAnimatedMedia] so it cannot steal a manually started video.
     */
    fun setActiveAnimatedMedia(mediaId: String?) {
        if (mediaId == null) {
            scrubbingVideoId = null
            feedAutoplayMediaId = null
            manuallyStartedVideoId = null
            activeVideoMediaId = null
            preloadVideoMediaId = null
            feedViewportMotionMetrics = FeedViewportMotionMetrics()
            feedViewportState = FeedViewportState(motion = FeedViewportMotion.FLINGING)
        }
        activeAnimatedMediaId = mediaId
    }

    fun setFeedAutoplayAnimatedMedia(mediaId: String?) {
        feedAutoplayMediaId = mediaId
        if (activeVideoMediaId == null) {
            activeAnimatedMediaId = mediaId
        }
    }

    fun updateVideoFeedContext(
        orderedVideoIds: List<String>,
        terminalVideoId: String?,
    ) {
        if (orderedFeedVideoIds == orderedVideoIds && terminalFeedVideoId == terminalVideoId) return
        orderedFeedVideoIds = orderedVideoIds.distinct()
        terminalFeedVideoId = terminalVideoId?.takeIf { it in orderedFeedVideoIds }
        feedViewportMotionMetrics = FeedViewportMotionMetrics()
        feedViewportState = FeedViewportState(motion = FeedViewportMotion.FLINGING)
        if (manuallyPausedVideoId !in orderedFeedVideoIds &&
            manuallyPausedVideoId?.let(::videoOpenInMediaViewer) != true
        ) {
            manuallyPausedVideoId = null
        }
        if (scrubbingVideoId !in orderedFeedVideoIds) scrubbingVideoId = null
        videoFeedGeneration += 1
        MediaPerformanceTrace.mark(
            event = "video_feed_context_changed",
            mediaId = terminalFeedVideoId,
            transitionId = activeVideoTransitionId,
            details = "video_count=${orderedFeedVideoIds.size} terminal=${terminalFeedVideoId ?: "none"}",
        )
        reconcileVideoPlayback()
    }

    fun videoFeedRank(mediaId: String): Int? = orderedFeedVideoIds.indexOf(mediaId)
        .takeIf { it >= 0 }

    fun isTerminalFeedVideo(mediaId: String): Boolean = terminalFeedVideoId == mediaId

    fun updateFeedViewportState(viewport: FeedViewportState) {
        val effectiveDirection = viewport.direction.takeUnless { it == FeedScrollDirection.NONE }
            ?: feedViewportState.direction
        val normalized = viewport.copy(direction = effectiveDirection)
        if (feedViewportState == normalized) return
        feedViewportState = normalized
        MediaPerformanceTrace.mark(
            event = "feed_viewport_state_changed",
            mediaId = activeVideoMediaId,
            transitionId = activeVideoTransitionId,
            details = buildString {
                append("motion=").append(normalized.motion.name.lowercase())
                append(" direction=").append(normalized.direction.name.lowercase())
                append(" at_start=").append(normalized.atStart)
                append(" at_end=").append(normalized.atEnd)
            },
        )
        reconcileVideoPlayback()
    }

    fun updateFeedViewportMotionMetrics(metrics: FeedViewportMotionMetrics) {
        if (feedViewportMotionMetrics == metrics) return
        feedViewportMotionMetrics = metrics
    }

    // PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1: Home, a community and a
    // profile publish the shared feed autoplay state (video context, GIF, viewport)
    // and clear it when they leave. A destination switch removes the old surface
    // only after its exit transition, when the new one has already published, so
    // the old one cleared the new one's state (Home -> You: the profile's GIF never
    // autoplayed). The surface that entered last owns the state; only it clears.
    private var feedSurfaceOwner: Any? = null

    fun claimFeedSurface(owner: Any) {
        feedSurfaceOwner = owner
    }

    fun releaseFeedSurface(owner: Any) {
        if (feedSurfaceOwner !== owner) return
        feedSurfaceOwner = null
        resetFeedViewportState()
        setFeedAutoplayAnimatedMedia(null)
        updateVideoFeedContext(emptyList(), null)
    }

    fun resetFeedViewportState() {
        feedViewportMotionMetrics = FeedViewportMotionMetrics()
        if (feedViewportState == FeedViewportState(motion = FeedViewportMotion.FLINGING)) return
        feedViewportState = FeedViewportState(motion = FeedViewportMotion.FLINGING)
        reconcileVideoPlayback()
    }

    fun suspendVideoPlaybackForBackground() {
        MediaPerformanceTrace.mark(
            event = "state_background_suspend",
            mediaId = activeVideoMediaId,
            transitionId = activeVideoTransitionId,
        )
        videoPlaybackForeground = false
        googleFeedVideoVisibility.clear()
        googleFeedVisibilityEpoch += 1
        scrubbingVideoId = null
        preloadVideoMediaId = null
        backgroundResumeVideoId = activeVideoMediaId
        backgroundResumeWasManual = activeVideoMediaId != null &&
            manuallyStartedVideoId == activeVideoMediaId
        backgroundResumePending = backgroundResumeVideoId != null
        activeVideoMediaId = null
        activeVideoTransitionId = 0L
        manuallyStartedVideoId = null
        activeAnimatedMediaId = null
        feedViewportMotionMetrics = FeedViewportMotionMetrics()
        feedViewportState = FeedViewportState(motion = FeedViewportMotion.FLINGING)
    }

    fun resumeVideoPlaybackAfterForeground() {
        videoPlaybackForeground = true
        googleFeedVisibilityEpoch += 1
        if (backgroundResumeVideoId == null) {
            reconcileVideoPlayback()
            return
        }
        backgroundResumePending = true
        foregroundResumeRequest += 1
        reconcileVideoPlayback()
    }

    /**
     * Re-read playback authority when a queued feed effect executes, not when it
     * was composed. This dispatch is synchronous on the player/application thread;
     * callbacks must not enqueue another unchecked start. No new eligibility rule.
     */
    internal fun applyCurrentGoogleFeedVideoRequest(
        mediaId: String,
        requestRoute: SubshellRoute,
        requestVisibilityEpoch: Int,
        onApply: (playWhenReady: Boolean, transitionId: Long) -> Unit,
        onRevoked: () -> Unit,
    ) {
        if (!googleFeedOwnsPlayback || routeStack.current != requestRoute ||
            googleFeedVisibilityEpoch != requestVisibilityEpoch
        ) {
            MediaPerformanceTrace.mark(
                event = "feed_playback_request",
                mediaId = mediaId,
                details = "action=stale_scope",
            )
            return
        }
        val playNow = videoPlaybackForeground && activeVideoMediaId == mediaId &&
            activeAnimatedMediaId == mediaId && manuallyPausedVideoId != mediaId
        val manuallyPausedNow = videoPlaybackForeground && manuallyPausedVideoId == mediaId &&
            (activeVideoMediaId == null || activeVideoMediaId == mediaId)
        MediaPerformanceTrace.mark(
            event = "feed_playback_request",
            mediaId = mediaId,
            transitionId = activeVideoTransitionId,
            details = "action=${if (playNow) "play" else if (manuallyPausedNow) "paused" else "revoked"} " +
                "selected=${activeVideoMediaId ?: "none"}",
        )
        when {
            playNow || manuallyPausedNow -> onApply(playNow, activeVideoTransitionId)
            else -> onRevoked()
        }
    }

    internal fun registerGoogleFeedVideoViewport(owner: Any) {
        googleFeedVideoRoute = routeStack.current
        googleFeedVideoViewports[owner] = routeStack.current
        reconcileVideoPlayback()
    }

    internal fun isGoogleFeedVideoViewportCurrent(owner: Any): Boolean =
        googleFeedOwnsPlayback && googleFeedVideoViewports[owner] == routeStack.current

    internal fun removeGoogleFeedVideoViewport(owner: Any) {
        googleFeedVideoViewports.remove(owner)
        googleFeedVideoVisibility.entries.removeAll { it.value.viewportOwner === owner }
        // Keep the route's authority until navigation changes; an empty/disposed feed
        // must not fall back to the old geometry selector during disposal ordering.
        if (googleFeedOwnsPlayback) reconcileVideoPlayback()
    }

    internal fun updateGoogleFeedVideoVisibility(
        viewportOwner: Any,
        surfaceOwner: Any,
        mediaId: String,
        present: Boolean? = null,
        qualified: Boolean? = null,
    ) {
        if (!isGoogleFeedVideoViewportCurrent(viewportOwner) || !videoPlaybackForeground) return
        val previous = googleFeedVideoVisibility[surfaceOwner]
            ?.takeIf { it.mediaId == mediaId && it.viewportOwner === viewportOwner }
            ?: GoogleFeedVideoVisibility(viewportOwner, mediaId)
        val next = previous.copy(
            present = present ?: if (qualified == true) true else previous.present,
            qualified = if (present == false) false else qualified ?: previous.qualified,
        )
        googleFeedVideoVisibility[surfaceOwner] = next
        if (!next.present && manuallyPausedVideoId == mediaId &&
            !videoOpenInMediaViewer(mediaId) &&
            googleFeedVideoVisibility.values.none { it.mediaId == mediaId && it.present }
        ) {
            manuallyPausedVideoId = null
        }
        if (next != previous) {
            MediaPerformanceTrace.mark(
                event = "google_feed_visibility",
                mediaId = mediaId,
                transitionId = activeVideoTransitionId,
                details = "qualified=${next.qualified} present=${next.present}",
            )
        }
        reconcileVideoPlayback()
    }

    // VIDEO06_PAUSED_VIDEO_OPENS_PAUSED_IN_FULL_SCREEN_V1 (#227): the feed card leaving
    // the screen behind the viewer is not the reader leaving the video; the pause
    // stays, so full screen shows the paused frame and its controls (device: it
    // showed the poster and a lone play button).
    private fun videoOpenInMediaViewer(mediaId: String): Boolean =
        routeStack.current is SubshellRoute.Media && selectedMediaPost?.id == mediaId

    internal fun removeGoogleFeedVideoSurface(surfaceOwner: Any) {
        val removed = googleFeedVideoVisibility.remove(surfaceOwner) ?: return
        if (isGoogleFeedVideoViewportCurrent(removed.viewportOwner) &&
            manuallyPausedVideoId == removed.mediaId &&
            !videoOpenInMediaViewer(removed.mediaId) &&
            googleFeedVideoVisibility.values.none { it.mediaId == removed.mediaId && it.present }
        ) {
            manuallyPausedVideoId = null
        }
        if (googleFeedOwnsPlayback) reconcileVideoPlayback()
    }

    private fun reconcileGoogleFeedVideoPlayback() {
        // A return to this feed must requalify here; never leave an old route handoff
        // pending while a poster waits for Google's visibility callback.
        mediaPresentationHandoffId = null
        val surfaces = googleFeedVideoVisibility.values.filter {
            isGoogleFeedVideoViewportCurrent(it.viewportOwner)
        }
        val presentIds = surfaces.filter { it.present }.mapTo(linkedSetOf()) { it.mediaId }
        val qualifiedIds = surfaces.filter { it.qualified && it.present }
            .mapTo(linkedSetOf()) { it.mediaId }
        val resumedManualId = backgroundResumeVideoId?.takeIf {
            backgroundResumePending && backgroundResumeWasManual && it in presentIds
        }
        if (resumedManualId != null || !backgroundResumeWasManual) {
            backgroundResumePending = false
            backgroundResumeVideoId = null
            backgroundResumeWasManual = false
        }
        val manualId = (manuallyStartedVideoId ?: resumedManualId)?.takeIf { it in presentIds }
        val previous = activeVideoMediaId
        val next = when {
            !videoPlaybackForeground -> null
            manualId != null -> manualId
            manuallyPausedVideoId in presentIds -> null
            !videoAutoplayAllowed -> null
            previous in qualifiedIds -> previous
            else -> orderedFeedVideoIds.firstOrNull { it in qualifiedIds }
                ?: qualifiedIds.firstOrNull()
        }
        // Arbitration is app-specific: keep an eligible owner, otherwise choose the
        // first eligible item in display order. Only Google supplies eligibility.
        if (next != previous) {
            if (next == null) {
                MediaPerformanceTrace.mark(
                    event = "candidate_released",
                    mediaId = previous,
                    transitionId = activeVideoTransitionId,
                    details = "reason=google_feed_visibility",
                )
                MediaPerformanceTrace.endTransition(
                    transitionId = activeVideoTransitionId,
                    mediaId = previous,
                    outcome = "google_feed_visibility_exit",
                )
                activeVideoTransitionId = 0L
            } else {
                activeVideoTransitionId = MediaPerformanceTrace.beginTransition(
                    mediaId = next,
                    previousMediaId = previous,
                    reason = "google_feed_visibility",
                )
            }
        }
        if (scrubbingVideoId !in presentIds) scrubbingVideoId = null
        activeVideoMediaId = next
        manuallyStartedVideoId = manualId.takeIf { it == next }
        if (next != null) {
            activeAnimatedMediaId = next
        } else if (activeAnimatedMediaId == previous) {
            activeAnimatedMediaId = feedAutoplayMediaId.takeIf {
                videoPlaybackForeground && videoAutoplayAllowed
            }
        }
    }

    fun updateVideoVisibility(mediaId: String, surfaceOwner: Any, visibleFraction: Float) {
        updateVideoVisibility(
            mediaId = mediaId,
            surfaceOwner = surfaceOwner,
            metrics = VideoSurfaceMetrics(
                visibleFraction = visibleFraction,
                visibleViewportFraction = visibleFraction,
                centerDistanceFraction = 1f - visibleFraction.coerceIn(0f, 1f),
                isTerminalItem = isTerminalFeedVideo(mediaId),
                centerOffsetFraction = Float.MAX_VALUE,
            ),
        )
    }

    fun updateVideoVisibility(
        mediaId: String,
        surfaceOwner: Any,
        metrics: VideoSurfaceMetrics,
    ) {
        val normalized = metrics.normalized().copy(isTerminalItem = isTerminalFeedVideo(mediaId))
        val previous = videoSurfaceVisibility[surfaceOwner]
        if (normalized.visibleFraction <= 0f) {
            videoSurfaceVisibility.remove(surfaceOwner)
        } else if (
            previous?.mediaId != mediaId ||
            previous.metrics.isTerminalItem != normalized.isTerminalItem ||
            abs(previous.metrics.visibleFraction - normalized.visibleFraction) >= 0.01f ||
            abs(previous.metrics.visibleViewportFraction - normalized.visibleViewportFraction) >= 0.005f ||
            abs(previous.metrics.centerDistanceFraction - normalized.centerDistanceFraction) >= 0.005f ||
            abs(previous.metrics.centerOffsetFraction - normalized.centerOffsetFraction) >= 0.005f ||
            abs(previous.metrics.surfaceTopFraction - normalized.surfaceTopFraction) >= 0.005f ||
            abs(previous.metrics.surfaceBottomFraction - normalized.surfaceBottomFraction) >= 0.005f ||
            abs(previous.metrics.surfaceHeightFraction - normalized.surfaceHeightFraction) >= 0.005f
        ) {
            videoSurfaceVisibility[surfaceOwner] = VideoSurfaceVisibility(mediaId, normalized)
        }
        reconcileVideoPlayback()
    }

    fun removeVideoSurface(surfaceOwner: Any) {
        if (videoSurfaceVisibility.remove(surfaceOwner) != null) {
            reconcileVideoPlayback()
        }
    }

    private fun reconcileVideoPlayback() {
        val fullscreenVideoId = selectedMediaPost
            ?.takeIf { it.kind == PostKind.VIDEO }
            ?.id
        if (fullscreenVideoId != null) {
            preloadVideoMediaId = null
            if (!videoPlaybackForeground) return

            if (backgroundResumePending) {
                val resumeMediaId = backgroundResumeVideoId
                val resumeAllowed = resumeMediaId == fullscreenVideoId &&
                    (backgroundResumeWasManual || videoAutoplayAllowed)
                backgroundResumePending = false
                backgroundResumeVideoId = null

                if (resumeAllowed) {
                    activeVideoTransitionId = MediaPerformanceTrace.beginTransition(
                        mediaId = fullscreenVideoId,
                        previousMediaId = null,
                        reason = "fullscreen_foreground_resume",
                        visibleFraction = 1f,
                    )
                    activeVideoMediaId = fullscreenVideoId
                    manuallyStartedVideoId = fullscreenVideoId.takeIf { backgroundResumeWasManual }
                    activeAnimatedMediaId = fullscreenVideoId
                }
                backgroundResumeWasManual = false
            }
            return
        }

        if (mediaPresentationHandoffId != null) {
            if (!googleFeedOwnsPlayback) {
                preloadVideoMediaId = null
                return
            }
        }

        val metricsByMediaId = videoSurfaceVisibility.values
            .groupBy(VideoSurfaceVisibility::mediaId)
            .mapValues { (mediaId, surfaces) ->
                surfaces
                    .map(VideoSurfaceVisibility::metrics)
                    .reduce { first, second -> VideoVisibilityPolicy.preferSurface(first, second) }
                    .copy(isTerminalItem = isTerminalFeedVideo(mediaId))
            }
        val visibleFractions = metricsByMediaId.mapValues { (_, metrics) -> metrics.visibleFraction }

        val nextPreloadMediaId = if (
            videoPlaybackForeground && videoAutoplayAllowed
        ) {
            VideoVisibilityPolicy.selectPreloadCandidate(
                activeVideoId = activeVideoMediaId ?: manuallyPausedVideoId,
                metricsByMediaId = metricsByMediaId,
                orderedVideoIds = orderedFeedVideoIds,
                viewport = feedViewportState,
            )
        } else {
            null
        }
        if (nextPreloadMediaId != preloadVideoMediaId) {
            MediaPerformanceTrace.mark(
                event = "preload_candidate_changed",
                mediaId = nextPreloadMediaId,
                transitionId = activeVideoTransitionId,
                details = "previous=${preloadVideoMediaId ?: "none"} motion=${feedViewportState.motion.name.lowercase()}",
            )
            preloadVideoMediaId = nextPreloadMediaId
        }

        if (googleFeedOwnsPlayback) {
            reconcileGoogleFeedVideoPlayback()
            return
        }

        val currentScrubbingVideoId = scrubbingVideoId
        if (currentScrubbingVideoId != null) {
            if ((visibleFractions[currentScrubbingVideoId] ?: 0f) > 0f) return
            scrubbingVideoId = null
        }

        if (backgroundResumePending && feedViewportState.motion == FeedViewportMotion.IDLE) {
            val resumeMediaId = backgroundResumeVideoId
            val resumeVisibility = resumeMediaId?.let(visibleFractions::get) ?: 0f
            val resumeAllowed = resumeMediaId != null &&
                resumeVisibility >= VideoVisibilityPolicy.OWNER_RETAIN_VISIBLE_FRACTION &&
                (backgroundResumeWasManual || videoAutoplayAllowed)

            backgroundResumePending = false
            backgroundResumeVideoId = null

            if (resumeAllowed) {
                activeVideoTransitionId = MediaPerformanceTrace.beginTransition(
                    mediaId = resumeMediaId,
                    previousMediaId = null,
                    reason = "foreground_resume",
                    visibleFraction = resumeVisibility,
                )
                activeVideoMediaId = resumeMediaId
                manuallyStartedVideoId = resumeMediaId.takeIf { backgroundResumeWasManual }
                backgroundResumeWasManual = false
                activeAnimatedMediaId = resumeMediaId
                return
            }

            backgroundResumeWasManual = false
        }

        val previousActiveVideoId = activeVideoMediaId
        val pausedVisibility = manuallyPausedVideoId
            ?.let { metricsByMediaId[it]?.visibleFraction }
            ?: 0f
        val manualPauseBlocksAutoplay = manuallyPausedVideoId != null &&
            pausedVisibility >= VideoVisibilityPolicy.MANUAL_KEEP_VISIBLE_FRACTION
        val decision = if (manualPauseBlocksAutoplay) {
            VideoVisibilityPolicy.Decision(activeVideoId = null, manualVideoId = null)
        } else {
            VideoVisibilityPolicy.selectViewportOwner(
                activeVideoId = activeVideoMediaId,
                manualVideoId = manuallyStartedVideoId,
                metricsByMediaId = metricsByMediaId.filterKeys { it != manuallyPausedVideoId },
                autoplayEnabled = videoAutoplayAllowed,
                viewport = feedViewportState,
                orderedVideoIds = orderedFeedVideoIds,
                distanceSinceDirectionChangeFraction =
                    feedViewportMotionMetrics.distanceSinceDirectionChangeFraction,
            )
        }
        if (decision.activeVideoId != previousActiveVideoId) {
            if (decision.activeVideoId == null) {
                MediaPerformanceTrace.mark(
                    event = "candidate_released",
                    mediaId = previousActiveVideoId,
                    transitionId = activeVideoTransitionId,
                    details = "reason=visibility_policy",
                )
                MediaPerformanceTrace.endTransition(
                    transitionId = activeVideoTransitionId,
                    mediaId = previousActiveVideoId,
                    outcome = "released_before_first_frame",
                )
                activeVideoTransitionId = 0L
            } else {
                activeVideoTransitionId = MediaPerformanceTrace.beginTransition(
                    mediaId = decision.activeVideoId,
                    previousMediaId = previousActiveVideoId,
                    reason = decision.transitionReason
                        ?: if (previousActiveVideoId == null) "autoplay_acquire" else "autoplay_takeover",
                    visibleFraction = visibleFractions[decision.activeVideoId],
                    scrollDirection = feedViewportState.direction.name.lowercase(),
                    viewportMotion = feedViewportState.motion.name.lowercase(),
                    viewportEdge = when {
                        feedViewportState.atEnd -> "end"
                        feedViewportState.atStart -> "start"
                        else -> "middle"
                    },
                )
            }
        }

        activeVideoMediaId = decision.activeVideoId
        manuallyStartedVideoId = decision.manualVideoId
        if (decision.activeVideoId != null) manuallyPausedVideoId = null

        when {
            decision.activeVideoId != null -> activeAnimatedMediaId = decision.activeVideoId
            activeAnimatedMediaId == previousActiveVideoId -> {
                activeAnimatedMediaId = if (
                    !videoAutoplayAllowed ||
                        feedViewportState.motion != FeedViewportMotion.IDLE
                ) {
                    null
                } else {
                    feedAutoplayMediaId
                }
            }
        }
    }

    fun captureJoinedCommunityIdsByAccount(): Map<String, Set<String>> =
        communityPreferences.captureJoinedCommunityIdsByAccount()

    fun captureFavoriteCommunityNamesByAccount(): Map<String, Set<String>> =
        communityPreferences.captureFavoriteCommunityNamesByAccount()

    fun captureFavoriteCustomFeedPathsByAccount(): Map<String, Set<String>> =
        communityPreferences.captureFavoriteCustomFeedPathsByAccount()

    fun captureHiddenCommunityNamesByAccount(): Map<String, Set<String>> =
        communityPreferences.captureHiddenCommunityNamesByAccount()

    fun captureCommunityMutesByAccount(): Map<String, List<PortableCommunityMute>> =
        communityPreferences.captureCommunityMutesByAccount()

    private fun PostComposerDraft.toPortableBackup04a3(): PortablePostComposerDraft? {
        val owner = accountStableId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (type !in setOf(ComposerPostType.TEXT, ComposerPostType.LINK)) return null
        // CROSSPOST01A1C_NO_LOSSY_PORTABLE_DRAFT_V1
        if (crosspostSource != null) return null
        if (media.isNotEmpty()) return null
        val portable = PortablePostComposerDraft(
            id = id,
            community = community,
            type = type.name,
            title = title,
            body = body,
            linkUrl = linkUrl,
            inputMode = inputMode.name,
            flair = flair?.let { value ->
                PortableComposerFlair(
                    templateId = value.templateId,
                    text = value.text,
                    editable = value.editable,
                    textColor = value.textColor,
                    backgroundColor = value.backgroundColor,
                    richText = value.richText.map { item ->
                        PortableComposerFlairRichText(
                            elementType = item.elementType,
                            text = item.text,
                            emojiRef = item.emojiRef,
                            imageUrl = item.imageUrl,
                        )
                    },
                )
            },
            nsfw = nsfw,
            spoiler = spoiler,
            sendReplies = sendReplies,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
        return normalizePortablePostComposerDraftsByAccount(
            mapOf(owner to listOf(portable)),
        )[owner]?.singleOrNull()
    }

    fun capturePostComposerDraftsByAccount(): Map<String, List<PortablePostComposerDraft>> {
        val raw = linkedMapOf<String, MutableList<PortablePostComposerDraft>>()
        accountSessionCatalog
            .sortedBy { account -> account.stableId.trim() }
            .forEach { account ->
                val stableId = account.stableId.trim().takeIf(String::isNotEmpty)
                    ?: return@forEach
                raw.getOrPut(stableId) { mutableListOf() }
            }
        postComposerDrafts
            .sortedByDescending(PostComposerDraft::updatedAtEpochMs)
            .forEach { draft ->
                val owner = draft.accountStableId?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@forEach
                val portable = draft.toPortableBackup04a3() ?: return@forEach
                raw.getOrPut(owner) { mutableListOf() }.add(portable)
            }
        return normalizePortablePostComposerDraftsByAccount(raw)
    }

    internal fun capturePortableBackupState(): PortableBackupState = PortableBackupState(
        settings = captureSettings(),
        joinedCommunityIdsByAccount = captureJoinedCommunityIdsByAccount(),
        favoriteCommunityNamesByAccount = captureFavoriteCommunityNamesByAccount(),
        favoriteCustomFeedPathsByAccount = captureFavoriteCustomFeedPathsByAccount(),
        hiddenCommunityNamesByAccount = captureHiddenCommunityNamesByAccount(),
        communityMutesByAccount = captureCommunityMutesByAccount(),
        postComposerDraftsByAccount = capturePostComposerDraftsByAccount(),
        moderatorSavedResponses = captureModeratorSavedResponses(),
        userTagsByAccount = captureUserTagsByAccount(),
        savedSearchesByAccount = captureSavedSearchesByAccount(),
    )

    // BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1
    fun captureUserTagsByAccount(): Map<String, Map<String, PortableUserTag>> =
        normalizePortableUserTagsByAccount(
            userTagState.captureByAccount().mapValues { (_, tags) ->
                tags.mapValues { (_, tag) -> tag.toPortableUserTag() }
            },
        )

    // BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1
    fun captureModeratorSavedResponses(): List<PortableModeratorSavedResponse> =
        normalizePortableModeratorSavedResponses(
            moderatorSavedResponses.map { response ->
                PortableModeratorSavedResponse(
                    id = response.id,
                    title = response.title,
                    body = response.body,
                    updatedAtEpochMs = response.updatedAtEpochMs,
                )
            },
        )

    private fun restoredModeratorSavedResponsesOrNull(
        restored: List<PortableModeratorSavedResponse>?,
    ): List<ModeratorSavedResponse>? = restored
        ?.let(::normalizePortableModeratorSavedResponses)
        ?.mapNotNull { response ->
            runCatching {
                ModeratorSavedResponse(
                    id = response.id,
                    title = response.title,
                    body = response.body,
                    updatedAtEpochMs = response.updatedAtEpochMs,
                )
            }.getOrNull()
        }

    private fun normalizeJoinedCommunityRestoreSnapshot(snapshot: Map<String, Set<String>>?): Map<String, Set<String>>? =
        communityPreferences.normalizeJoinedCommunityRestoreSnapshot(snapshot)

    private fun applyRestoredJoinedCommunityIdsToLiveState(restoredByAccount: Map<String, Set<String>>?) {
        communityPreferences.applyRestoredJoinedCommunityIdsToLiveState(restoredByAccount)
    }

    fun restoreJoinedCommunityIdsByAccount(snapshot: Map<String, Set<String>>?) {
        communityPreferences.restoreJoinedCommunityIdsByAccount(snapshot)
    }

    private fun normalizeFavoriteCommunityRestoreSnapshot(snapshot: Map<String, Set<String>>?): Map<String, Set<String>>? =
        communityPreferences.normalizeFavoriteCommunityRestoreSnapshot(snapshot)

    private fun normalizeFavoriteCustomFeedRestoreSnapshot(snapshot: Map<String, Set<String>>?): Map<String, Set<String>>? =
        communityPreferences.normalizeFavoriteCustomFeedRestoreSnapshot(snapshot)

    private fun applyRestoredFavoriteCommunityNamesToLiveState(restoredByAccount: Map<String, Set<String>>?) {
        communityPreferences.applyRestoredFavoriteCommunityNamesToLiveState(restoredByAccount)
    }

    private fun applyRestoredFavoriteCustomFeedPathsToLiveState(restoredByAccount: Map<String, Set<String>>?) {
        communityPreferences.applyRestoredFavoriteCustomFeedPathsToLiveState(restoredByAccount)
    }

    private fun normalizeHiddenCommunityRestoreSnapshot(snapshot: Map<String, Set<String>>?): Map<String, Set<String>>? =
        communityPreferences.normalizeHiddenCommunityRestoreSnapshot(snapshot)

    private fun applyRestoredHiddenCommunityNamesToLiveState(restoredByAccount: Map<String, Set<String>>?) {
        communityPreferences.applyRestoredHiddenCommunityNamesToLiveState(restoredByAccount)
    }

    private fun normalizeCommunityMuteRestoreSnapshot(snapshot: Map<String, List<PortableCommunityMute>>?): Map<String, List<PortableCommunityMute>>? =
        communityPreferences.normalizeCommunityMuteRestoreSnapshot(snapshot)

    private fun encodePortableCommunityMuteEntriesByAccount(restoredByAccount: Map<String, List<PortableCommunityMute>>?): Map<String, Set<String>>? =
        communityPreferences.encodePortableCommunityMuteEntriesByAccount(restoredByAccount)

    private fun applyRestoredCommunityMutesToLiveState(restoredByAccount: Map<String, List<PortableCommunityMute>>?) {
        communityPreferences.applyRestoredCommunityMutesToLiveState(restoredByAccount)
    }

    private fun PortablePostComposerDraft.toNativeBackup04a3(
        accountStableId: String,
    ): PostComposerDraft? {
        val typeValue = ComposerPostType.entries.firstOrNull { it.name == type }
            ?.takeIf { it == ComposerPostType.TEXT || it == ComposerPostType.LINK }
            ?: return null
        val mode = ComposerInputMode.entries.firstOrNull { it.name == inputMode }
            ?: ComposerInputMode.MARKDOWN
        return PostComposerDraft(
            id = id,
            accountStableId = accountStableId,
            community = community,
            type = typeValue,
            title = title,
            body = body,
            linkUrl = linkUrl,
            inputMode = mode,
            media = emptyList(),
            flair = flair?.let { value ->
                ComposerFlair(
                    templateId = value.templateId,
                    text = value.text,
                    editable = value.editable,
                    textColor = value.textColor,
                    backgroundColor = value.backgroundColor,
                    richText = value.richText.map { item ->
                        ComposerFlairRichText(
                            elementType = item.elementType,
                            text = item.text,
                            emojiRef = item.emojiRef,
                            imageUrl = item.imageUrl,
                        )
                    },
                )
            },
            nsfw = nsfw,
            spoiler = spoiler,
            sendReplies = sendReplies,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    private fun PostComposerDraft.isPortableBackup04a3NativeDraft(): Boolean =
        accountStableId?.trim()?.isNotEmpty() == true &&
            type in setOf(ComposerPostType.TEXT, ComposerPostType.LINK) &&
            crosspostSource == null &&
            media.isEmpty()

    private fun mergeRestoredPostComposerDrafts(
        restoredByAccount: Map<String, List<PortablePostComposerDraft>>?,
    ): List<PostComposerDraft>? {
        val normalized = restoredByAccount
            ?.let(::normalizePortablePostComposerDraftsByAccount)
            ?: return null

        val affectedAccounts = normalized.keys
        val existing = postComposerDrafts.toList()
        val preserved = if (normalized.isEmpty()) {
            existing.filterNot { draft -> draft.isPortableBackup04a3NativeDraft() }
        } else {
            existing.filter { draft ->
                !draft.isPortableBackup04a3NativeDraft() ||
                    draft.accountStableId?.trim() !in affectedAccounts
            }
        }.distinctBy(PostComposerDraft::id)

        val reservedIds = preserved.mapTo(mutableSetOf(), PostComposerDraft::id)
        val slots = (MAX_POST_COMPOSER_DRAFTS - preserved.size).coerceAtLeast(0)
        val restored = normalized.entries
            .flatMap { (accountStableId, drafts) ->
                drafts.mapNotNull { draft ->
                    draft.toNativeBackup04a3(accountStableId)
                }
            }
            .sortedWith(
                compareByDescending<PostComposerDraft> { it.updatedAtEpochMs }
                    .thenBy { it.id },
            )
            .filter { draft -> draft.id !in reservedIds }
            .distinctBy(PostComposerDraft::id)
            .take(slots)

        return (preserved + restored)
            .distinctBy(PostComposerDraft::id)
            .sortedByDescending(PostComposerDraft::updatedAtEpochMs)
            .take(MAX_POST_COMPOSER_DRAFTS)
    }

    private fun applyRestoredPostComposerDraftsToLiveState(
        mergedDrafts: List<PostComposerDraft>?,
    ) {
        val restored = mergedDrafts ?: return
        postComposerDrafts.clear()
        postComposerDrafts.addAll(restored)

        restored.firstOrNull { draft -> draft.id == postComposerDraft.id }?.let { matching ->
            postComposerDraft = matching
            return
        }
        if (
            routeStack.current !is SubshellRoute.PostComposer &&
            postComposerDraft.isPortableBackup04a3NativeDraft()
        ) {
            postComposerDraft = PostComposerDraft(
                accountStableId = activeAccountId,
                community = postComposerDraft.community,
            )
        }
    }

    // BACKUP02A2_TRANSACTIONAL_RESTORE_V1
    // BACKUP03C2_FOUR_MODULE_POST_COMMIT_PROJECTION_V1
    // BACKUP04A1_PRIVACY_SENSITIVE_FIFTH_MODULE_POST_COMMIT_V1
    // BACKUP04A2_PRIVACY_SENSITIVE_SIXTH_MODULE_POST_COMMIT_V1
    // BACKUP04A3_PRIVACY_SENSITIVE_SEVENTH_MODULE_POST_COMMIT_V1
    // BACKUP04A5_PRIVACY_SENSITIVE_USER_TAGS_MODULE_POST_COMMIT_V1
    fun restoreBackupState(
        settings: SubshellSettings,
        joinedCommunityIdsByAccount: Map<String, Set<String>>?,
        favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
        favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
        hiddenCommunityNamesByAccount: Map<String, Set<String>>? = null,
        communityMutesByAccount: Map<String, List<PortableCommunityMute>>? = null,
        postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null,
        moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null,
        userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null,
        savedSearchesByAccount: Map<String, List<SavedSearch>>? = null,
    ) {
        val restoredCommunities =
            normalizeJoinedCommunityRestoreSnapshot(joinedCommunityIdsByAccount)
        val restoredFavoriteCommunities =
            normalizeFavoriteCommunityRestoreSnapshot(favoriteCommunityNamesByAccount)
        val restoredFavoriteCustomFeeds =
            normalizeFavoriteCustomFeedRestoreSnapshot(favoriteCustomFeedPathsByAccount)
        val restoredHiddenCommunities =
            normalizeHiddenCommunityRestoreSnapshot(hiddenCommunityNamesByAccount)
        val restoredCommunityMutes =
            normalizeCommunityMuteRestoreSnapshot(communityMutesByAccount)
        val restoredPostComposerDrafts =
            mergeRestoredPostComposerDrafts(postComposerDraftsByAccount)
        // BACKUP04A4: missing module = leave unchanged; present-but-empty = clear.
        val restoredModeratorSavedResponses =
            restoredModeratorSavedResponsesOrNull(moderatorSavedResponses)
        // BACKUP04A5: missing module = leave unchanged; present-but-empty = clear.
        val restoredUserTags = userTagsByAccount
            ?.let(::normalizePortableUserTagsByAccount)
            ?.mapValues { (_, tags) -> tags.mapValues { (_, tag) -> tag.toUserTag() } }
        // ISSUE206: missing module = leave unchanged; present-but-empty = clear.
        val restoredSavedSearches = savedSearchesByAccount?.let(::normalizeSavedSearchesByAccount)

        persistBackupRestoreState(
            settings.toStorageMap(),
            restoredCommunities,
            restoredFavoriteCommunities,
            restoredFavoriteCustomFeeds,
            restoredHiddenCommunities,
            encodePortableCommunityMuteEntriesByAccount(restoredCommunityMutes),
            restoredPostComposerDrafts,
            restoredModeratorSavedResponses,
            restoredUserTags,
            restoredSavedSearches,
        )

        // Live state changes only after the durable transaction succeeds.
        applyRestoredSettingsToLiveState(settings)
        applyRestoredJoinedCommunityIdsToLiveState(restoredCommunities)
        applyRestoredFavoriteCommunityNamesToLiveState(restoredFavoriteCommunities)
        applyRestoredFavoriteCustomFeedPathsToLiveState(restoredFavoriteCustomFeeds)
        applyRestoredHiddenCommunityNamesToLiveState(restoredHiddenCommunities)
        applyRestoredCommunityMutesToLiveState(restoredCommunityMutes)
        applyRestoredPostComposerDraftsToLiveState(restoredPostComposerDrafts)
        restoredModeratorSavedResponses?.let(moderatorResponses::applyRestored)
        userTagState.applyRestored(restoredUserTags)
        savedSearchState.applyRestored(restoredSavedSearches)
    }

    fun toggleCommunity(communityId: String) {
        communityPreferences.toggleCommunity(communityId)
    }

    fun isFollowingUser(username: String): Boolean {
        val normalizedUsername = username.trim().lowercase()
        return normalizedUsername.isNotEmpty() &&
            normalizedUsername != activeAccountUsername.lowercase() &&
            normalizedUsername in followedUsernames
    }

    fun toggleFollowUser(username: String) {
        if (!localMutationsAllowed) return
        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isEmpty() || normalizedUsername == activeAccountUsername.lowercase()) return
        if (!followedUsernames.remove(normalizedUsername)) followedUsernames.add(normalizedUsername)
        persistActiveAccountPostActions()
    }

    fun dismissedInboxActivityIds(accountStableId: String): Set<String> =
        inboxActivity.dismissedInboxActivityIds(accountStableId)

    fun inboxActivityClearBoundary(accountStableId: String): Long =
        inboxActivity.inboxActivityClearBoundary(accountStableId)

    fun inboxActivityPreviousClearBoundary(accountStableId: String): Long =
        inboxActivity.inboxActivityPreviousClearBoundary(accountStableId)

    fun dismissInboxActivityItem(accountStableId: String, itemId: String) {
        inboxActivity.dismissInboxActivityItem(accountStableId, itemId)
    }

    fun dismissInboxActivityItems(accountStableId: String, itemIds: Set<String>) {
        inboxActivity.dismissInboxActivityItems(accountStableId, itemIds)
    }

    fun restoreInboxActivityItems(accountStableId: String, itemIds: Set<String>) {
        inboxActivity.restoreInboxActivityItems(accountStableId, itemIds)
    }

    fun restoreInboxActivityItem(accountStableId: String, itemId: String) {
        inboxActivity.restoreInboxActivityItem(accountStableId, itemId)
    }

    fun clearInboxActivity(accountStableId: String) {
        inboxActivity.clearInboxActivity(accountStableId)
    }

    fun undoInboxActivityClear(accountStableId: String) {
        inboxActivity.undoInboxActivityClear(accountStableId)
    }

    fun markInboxRead(id: String) {
        inboxActivity.markInboxRead(id)
    }

    fun toggleComment(id: String) {
        commentThread.toggleComment(id)
    }

    fun revealNewCommentPaths(
        ancestorIds: Set<String>,
        siblingBranchIds: Set<String>,
        manuallyCollapsedIds: Set<String>,
    ) {
        commentThread.revealNewCommentPaths(ancestorIds, siblingBranchIds, manuallyCollapsedIds)
    }

    fun commentVisitBaseline(accountStableId: String, postId: String): Set<String>? =
        commentThread.commentVisitBaseline(accountStableId, postId)

    fun recordCommentVisit(
        accountStableId: String,
        postId: String,
        commentIds: Set<String>,
    ) {
        commentThread.recordCommentVisit(accountStableId, postId, commentIds)
    }

    fun previewInteractionSide(side: InteractionSide) {
        settings.previewInteractionSide(side)
    }

    fun confirmInteractionSide() {
        settings.confirmInteractionSide()
    }

    fun updateInteractionSide(side: InteractionSide) {
        settings.updateInteractionSide(side)
    }

    fun updateDrawerSidePreference(preference: DrawerSidePreference) {
        settings.updateDrawerSidePreference(preference)
    }

    fun updateCommentActionPreference(preference: CommentActionPreference) {
        settings.updateCommentActionPreference(preference)
    }

    fun updateCommentTimeFormat(format: CommentTimeFormat) {
        settings.updateCommentTimeFormat(format)
    }

    fun updateCommentCollapseMode(mode: CommentCollapseMode) {
        settings.updateCommentCollapseMode(mode)
    }

    fun updateCommentHierarchyStyle(style: CommentHierarchyStyle) {
        settings.updateCommentHierarchyStyle(style)
    }

    fun updateSwipeActionsEnabled(enabled: Boolean) {
        settings.updateSwipeActionsEnabled(enabled)
    }

    fun updatePostSwipeLeftAction(action: PostSwipeAction) {
        settings.updatePostSwipeLeftAction(action)
    }

    fun updatePostSwipeRightAction(action: PostSwipeAction) {
        settings.updatePostSwipeRightAction(action)
    }

    fun updateCommentSwipeLeftAction(action: CommentSwipeAction) {
        settings.updateCommentSwipeLeftAction(action)
    }

    fun updateCommentSwipeRightAction(action: CommentSwipeAction) {
        settings.updateCommentSwipeRightAction(action)
    }


    fun updateHighlightNewComments(enabled: Boolean) {
        settings.updateHighlightNewComments(enabled)
    }

    fun updateShowCommentAvatars(enabled: Boolean) {
        settings.updateShowCommentAvatars(enabled)
    }

    fun updateTopLevelCommentJumpButton(enabled: Boolean) {
        settings.updateTopLevelCommentJumpButton(enabled)
    }

    fun updateUseSuggestedCommentSort(enabled: Boolean) {
        settings.updateUseSuggestedCommentSort(enabled)
    }

    fun updateShowUnreadMarker(enabled: Boolean) {
        settings.updateShowUnreadMarker(enabled)
    }

    fun updateInboxNotificationsEnabled(enabled: Boolean) {
        settings.updateInboxNotificationsEnabled(enabled)
    }

    fun updateNotifyReplies(enabled: Boolean) {
        settings.updateNotifyReplies(enabled)
    }

    fun updateNotifyMentions(enabled: Boolean) {
        settings.updateNotifyMentions(enabled)
    }

    fun updateNotifyPrivateMessages(enabled: Boolean) {
        settings.updateNotifyPrivateMessages(enabled)
    }

    fun updateNotifyRedditNotices(enabled: Boolean) {
        settings.updateNotifyRedditNotices(enabled)
    }

    fun updateNotifyModmail(enabled: Boolean) {
        settings.updateNotifyModmail(enabled)
    }

    fun updateShowNsfwPosts(enabled: Boolean) {
        if (showNsfwPosts == enabled) return
        showNsfwPosts = enabled
        if (!enabled) {
            val knownNsfwMediaIds = revealedNsfwMediaIds.toSet()
            // LOW_NSFW_SWITCH_READS_POSTS_BY_EXACT_KEY_V1 (OCB-528): post(id) falls
            // back to a substr scan of every cached post row (no index serves it),
            // once per revealed media id on the main thread; the account's own row
            // is read by its primary key instead (as ISSUE70's route lookups).
            val account = activeAccountId
            revealedMediaIds.removeAll { postId ->
                postId in knownNsfwMediaIds ||
                    repository.postForAccount(postId, account).valueOrNull()?.nsfw == true
            }
            activeAnimatedMediaId
                ?.takeIf { postId ->
                    postId in knownNsfwMediaIds ||
                        repository.postForAccount(postId, account).valueOrNull()?.nsfw == true
                }
                ?.let { activeAnimatedMediaId = null }
            revealedNsfwMediaIds.clear()
        }
        persistPreference(SubshellSettings.SHOW_NSFW_POSTS, enabled.toString())
        reconcileVideoPlayback()
    }

    fun updateSensitiveMediaPreviewMode(mode: SensitiveMediaPreviewMode) {
        if (sensitiveMediaPreviewMode == mode) return
        sensitiveMediaPreviewMode = mode
        resetSensitiveMediaReveals()
        persistPreference(SubshellSettings.SENSITIVE_MEDIA_PREVIEW_MODE, mode.name)
    }

    fun isPostDeleted(postId: String): Boolean {
        val normalizedPostId = normalizedPostIdentity(postId)
        return normalizedPostId.isNotEmpty() && normalizedPostId in remotelyDeletedPostIds
    }

    fun showsPost(post: Post): Boolean =
        !isPostDeleted(post.id) && postVisibleWithNsfwPreference(post, showNsfwPosts)

    fun sensitiveMediaAutoplayAllowed(post: Post): Boolean =
        sensitiveMediaCanAutoplay(post, revealedMediaIds)

    fun addContentFilter(type: ContentFilterType, value: String, community: String? = null) {
        val updated = community
            ?.takeIf(String::isNotBlank)
            ?.let { contentFilters.addingScoped(type, value, it) }
            ?: contentFilters.adding(type, value)
        if (updated == contentFilters) return
        contentFilters = updated
        persistContentFilters(type, updated)
    }

    fun removeContentFilter(type: ContentFilterType, value: String, community: String? = null) {
        val updated = community
            ?.let { contentFilters.removingScoped(type, value, it) }
            ?: contentFilters.removing(type, value)
        if (updated == contentFilters) return
        contentFilters = updated
        persistContentFilters(type, updated)
    }

    // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1: both keys of the type.
    private fun persistContentFilters(type: ContentFilterType, filters: ContentFilters) {
        val stored = filters.toStorageMap()
        persistPreference(type.storageKey, stored.getValue(type.storageKey))
        persistPreference(type.scopedStorageKey, stored.getValue(type.scopedStorageKey))
    }

    fun updateCommunitySort(sort: CommunitySort) {
        communitySort = sort
        val communityName = selectedSubreddit ?: return
        val normalizedName = runCatching {
            normalizeRedditCommunityName(communityName)
        }.getOrNull() ?: return
        val updated = communitySortPreferences + (normalizedName to sort)
        if (updated == communitySortPreferences) return
        communitySortPreferences = updated
        persistPreference(
            SubshellSettings.COMMUNITY_SORT_PREFERENCES,
            encodeCommunitySortPreferences(updated),
        )
    }

    fun updateListingTimePeriod(timePeriod: RedditListingTimePeriod) {
        if (listingTimePeriod == timePeriod) return
        listingTimePeriod = timePeriod
        persistPreference(SubshellSettings.LISTING_TIME_PERIOD, timePeriod.name)
    }

    fun updateThemeMode(mode: ThemeMode) {
        settings.updateThemeMode(mode)
    }

    fun updateDynamicColor(enabled: Boolean) {
        settings.updateDynamicColor(enabled)
    }

    fun updatePureBlackDarkTheme(enabled: Boolean) {
        settings.updatePureBlackDarkTheme(enabled)
    }

    fun updateAppTextScale(scale: AppTextScale) {
        settings.updateAppTextScale(scale)
    }

    internal val themeChoice: ThemeChoice
        get() = themeChoice(themeMode, pureBlackDarkTheme)

    internal fun updateThemeChoice(choice: ThemeChoice) {
        updateThemeMode(choice.mode)
        updatePureBlackDarkTheme(choice.pureBlack)
    }

    fun updateVoteColorPalette(palette: VoteColorPalette) {
        settings.updateVoteColorPalette(palette)
    }

    fun updateVoteControlMode(mode: VoteControlMode) {
        settings.updateVoteControlMode(mode)
    }

    fun updateCodeFont(font: CodeFont) {
        settings.updateCodeFont(font)
    }

    fun updateDrawerCommunitiesMode(mode: DrawerCommunitiesMode) {
        settings.updateDrawerCommunitiesMode(mode)
    }

    fun updateDrawerRecentCommunities(visible: Boolean) {
        settings.updateDrawerRecentCommunities(visible)
    }

    fun updateFilteredPostsSummary(enabled: Boolean) {
        settings.updateFilteredPostsSummary(enabled)
    }

    fun updateSavedSearchesEnabled(enabled: Boolean) {
        settings.updateSavedSearchesEnabled(enabled)
    }

    fun updateScrollPositionIndicator(enabled: Boolean) {
        settings.updateScrollPositionIndicator(enabled)
    }

    fun updateRandomNsfwCommunityPlacement(placement: RandomNsfwCommunityPlacement) {
        settings.updateRandomNsfwCommunityPlacement(placement)
    }

    fun updatePinnedPostsPresentation(presentation: PinnedPostsPresentation) {
        settings.updatePinnedPostsPresentation(presentation)
    }

    fun updatePostLayout(layout: PostLayout) {
        settings.updatePostLayout(layout)
    }

    fun updateListThumbnailSide(side: ListThumbnailSide) {
        settings.updateListThumbnailSide(side)
    }

    /** ISSUE211: whether "Random NSFW community" shows at [placement] now. */
    fun offersRandomNsfwCommunity(placement: RandomNsfwCommunityPlacement): Boolean =
        showNsfwPosts && activeAccountIsAuthenticated &&
            placement != RandomNsfwCommunityPlacement.OFF &&
            randomNsfwCommunityPlacement == placement

    fun updateBackTwiceToExit(enabled: Boolean) {
        settings.updateBackTwiceToExit(enabled)
    }

    fun updateCommunityDiscoveryLayout(layout: CommunityDiscoveryLayout) {
        settings.updateCommunityDiscoveryLayout(layout)
    }

    fun updateRequireDeviceUnlockForHiddenSubscriptions(required: Boolean) {
        settings.updateRequireDeviceUnlockForHiddenSubscriptions(required)
    }

    fun consumeCommunityExploreSearchFocusRequest(request: Int) {
        uiRequests.consumeCommunityExploreSearchFocusRequest(request)
    }

    fun updateNavigationLabelMode(mode: NavigationLabelMode) {
        settings.updateNavigationLabelMode(mode)
    }

    fun updateTabletLayoutMode(mode: TabletLayoutMode) {
        settings.updateTabletLayoutMode(mode)
    }

    fun updateTopBarBehavior(behavior: ChromeBehavior) {
        topBarBehavior = behavior
        persistPreference("top_bar_behavior", behavior.name)
        if (behavior == ChromeBehavior.ALWAYS_SHOW) showChrome()
    }

    fun updateBottomBarBehavior(behavior: ChromeBehavior) {
        bottomBarBehavior = behavior
        persistPreference("bottom_bar_behavior", behavior.name)
        if (behavior == ChromeBehavior.ALWAYS_SHOW) showChrome()
    }

    fun updateMediaLabelsVisible(visible: Boolean) {
        settings.updateMediaLabelsVisible(visible)
    }

    fun updateMediaCaptionMode(mode: MediaCaptionMode) {
        settings.updateMediaCaptionMode(mode)
    }

    fun updateFullscreenCaptionMode(mode: FullscreenCaptionMode) {
        settings.updateFullscreenCaptionMode(mode)
    }

    fun updateSubredditLabelStyle(style: SubredditLabelStyle) {
        settings.updateSubredditLabelStyle(style)
    }

    fun updateOpenKeyboardOnSearch(enabled: Boolean) {
        settings.updateOpenKeyboardOnSearch(enabled)
    }

    fun updateVideoAutoplay(value: VideoAutoplay) {
        videoAutoplay = value
        persistPreference("video_autoplay", value.name)
        if (!videoAutoplayAllowed) {
            feedAutoplayMediaId = null
        }
        reconcileVideoPlayback()
    }

    fun updateStartVideosMuted(enabled: Boolean) {
        startVideosMuted = enabled
        feedVideoMuted = enabled
        persistPreference("start_videos_muted", enabled.toString())
    }

    fun updateLoopVideos(enabled: Boolean) {
        settings.updateLoopVideos(enabled)
    }

    fun updateWifiVideoQuality(value: VideoQuality) {
        settings.updateWifiVideoQuality(value)
    }

    fun updateMobileVideoQuality(value: VideoQuality) {
        settings.updateMobileVideoQuality(value)
    }

    fun updatePlaybackOptionsPlacement(value: PlaybackOptionsPlacement) {
        settings.updatePlaybackOptionsPlacement(value)
    }

    fun updateVideoSpeedControls(enabled: Boolean) {
        settings.updateVideoSpeedControls(enabled)
    }

    fun updateVideoDoubleTapSeek(enabled: Boolean) {
        settings.updateVideoDoubleTapSeek(enabled)
    }

    fun updateVideoHoldForDoubleSpeed(enabled: Boolean) {
        settings.updateVideoHoldForDoubleSpeed(enabled)
    }

    fun updateVideoPinchToZoom(enabled: Boolean) {
        settings.updateVideoPinchToZoom(enabled)
    }

    fun updateVideoPictureInPicture(enabled: Boolean) {
        settings.updateVideoPictureInPicture(enabled)
    }

    fun updateVideoRotateFullscreen(enabled: Boolean) {
        settings.updateVideoRotateFullscreen(enabled)
    }

    fun updateVideoTechnicalDetails(enabled: Boolean) {
        settings.updateVideoTechnicalDetails(enabled)
    }

    fun updateVideoSubtitleDefault(value: VideoSubtitleDefault) {
        settings.updateVideoSubtitleDefault(value)
    }

    fun updateVideoAudioDefault(value: VideoAudioDefault) {
        settings.updateVideoAudioDefault(value)
    }

    fun updateVideoTimelineSize(value: VideoTimelineSize) {
        settings.updateVideoTimelineSize(value)
    }

    fun updateLandscapeVideoGesturesEnabled(enabled: Boolean) {
        settings.updateLandscapeVideoGesturesEnabled(enabled)
    }

    fun updateLandscapeBrightnessGestureEnabled(enabled: Boolean) {
        settings.updateLandscapeBrightnessGestureEnabled(enabled)
    }

    fun updateLandscapeVolumeGestureEnabled(enabled: Boolean) {
        settings.updateLandscapeVolumeGestureEnabled(enabled)
    }

    fun updateReduceMotion(enabled: Boolean) {
        settings.updateReduceMotion(enabled)
    }

    fun updateFeedPreviewState(value: FeedPreviewState) {
        settings.updateFeedPreviewState(value)
    }

    fun captureSettings(): SubshellSettings = SubshellSettings(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        pureBlackDarkTheme = pureBlackDarkTheme,
        appTextScale = appTextScale,
        voteColorPalette = voteColorPalette,
        voteControlMode = voteControlMode,
        codeFont = codeFont,
        interactionSide = interactionSide,
        drawerSidePreference = drawerSidePreference,
        commentActionPreference = commentActionPreference,
        commentTimeFormat = commentTimeFormat,
        commentCollapseMode = commentCollapseMode,
        commentHierarchyStyle = commentHierarchyStyle,
        swipeActionsEnabled = swipeActionsEnabled,
        postSwipeLeftAction = postSwipeLeftAction,
        postSwipeRightAction = postSwipeRightAction,
        commentSwipeLeftAction = commentSwipeLeftAction,
        commentSwipeRightAction = commentSwipeRightAction,
        highlightNewComments = highlightNewComments,
        showCommentAvatars = showCommentAvatars,
        topLevelCommentJumpButton = topLevelCommentJumpButton,
        useSuggestedCommentSort = useSuggestedCommentSort,
        showUnreadMarker = showUnreadMarker,
        inboxNotificationsEnabled = inboxNotificationsEnabled,
        notifyReplies = notifyReplies,
        notifyMentions = notifyMentions,
        notifyPrivateMessages = notifyPrivateMessages,
        notifyRedditNotices = notifyRedditNotices,
        notifyModmail = notifyModmail,
        showNsfwPosts = showNsfwPosts,
        sensitiveMediaPreviewMode = sensitiveMediaPreviewMode,
        contentFilters = contentFilters,
        communitySortPreferences = communitySortPreferences,
        listingTimePeriod = listingTimePeriod,
        drawerCommunitiesMode = drawerCommunitiesMode,
        drawerRecentCommunities = drawerRecentCommunities,
        backTwiceToExit = backTwiceToExit,
        filteredPostsSummary = filteredPostsSummary,
        savedSearchesEnabled = savedSearchesEnabled,
        scrollPositionIndicator = scrollPositionIndicator,
        randomNsfwCommunityPlacement = randomNsfwCommunityPlacement,
        pinnedPostsPresentation = pinnedPostsPresentation,
        postLayout = postLayout,
        listThumbnailSide = listThumbnailSide,
        mutedCommunityDirectViewMode = mutedCommunityDirectViewMode,
        communityDiscoveryLayout = communityDiscoveryLayout,
        navigationLabelMode = navigationLabelMode,
        tabletLayoutMode = tabletLayoutMode,
        topBarBehavior = topBarBehavior,
        bottomBarBehavior = bottomBarBehavior,
        mediaLabelsVisible = mediaLabelsVisible,
        mediaCaptionMode = mediaCaptionMode,
        fullscreenCaptionMode = fullscreenCaptionMode,
        subredditLabelStyle = subredditLabelStyle,
        openKeyboardOnSearch = openKeyboardOnSearch,
        videoAutoplay = videoAutoplay,
        startVideosMuted = startVideosMuted,
        loopVideos = loopVideos,
        wifiVideoQuality = wifiVideoQuality,
        mobileVideoQuality = mobileVideoQuality,
        videoTimelineSize = videoTimelineSize,
        playbackOptionsPlacement = playbackOptionsPlacement,
        videoSubtitleDefault = videoSubtitleDefault,
        videoAudioDefault = videoAudioDefault,
        videoSpeedControls = videoSpeedControls,
        videoDoubleTapSeek = videoDoubleTapSeek,
        videoHoldForDoubleSpeed = videoHoldForDoubleSpeed,
        videoPinchToZoom = videoPinchToZoom,
        videoPictureInPicture = videoPictureInPicture,
        videoRotateFullscreen = videoRotateFullscreen,
        videoTechnicalDetails = videoTechnicalDetails,
        landscapeVideoGesturesEnabled = landscapeVideoGesturesEnabled,
        landscapeBrightnessGestureEnabled = landscapeBrightnessGestureEnabled,
        landscapeVolumeGestureEnabled = landscapeVolumeGestureEnabled,
        reduceMotion = reduceMotion,
    )

    fun restoreSettings(settings: SubshellSettings) {
        persistSettings(settings.toStorageMap())
        applyRestoredSettingsToLiveState(settings)
    }

    private fun applyRestoredSettingsToLiveState(settings: SubshellSettings) {
        themeMode = settings.themeMode
        dynamicColor = settings.dynamicColor
        pureBlackDarkTheme = settings.pureBlackDarkTheme
        appTextScale = settings.appTextScale
        voteColorPalette = settings.voteColorPalette
        voteControlMode = settings.voteControlMode
        codeFont = settings.codeFont
        interactionSide = settings.interactionSide
        drawerSidePreference = settings.drawerSidePreference
        commentActionPreference = settings.commentActionPreference
        commentActionOnboardingVisible = false
        commentTimeFormat = settings.commentTimeFormat
        commentHierarchyStyle = settings.commentHierarchyStyle
        // ISSUE71_RESTORE_APPLIES_COMMENT_COLLAPSE_MODE_V1: the only setting a
        // restore wrote to disk but left stale in the live state.
        commentCollapseMode = settings.commentCollapseMode
        swipeActionsEnabled = settings.swipeActionsEnabled
        postSwipeLeftAction = settings.postSwipeLeftAction
        postSwipeRightAction = settings.postSwipeRightAction
        commentSwipeLeftAction = settings.commentSwipeLeftAction
        commentSwipeRightAction = settings.commentSwipeRightAction
        highlightNewComments = settings.highlightNewComments
        showCommentAvatars = settings.showCommentAvatars
        topLevelCommentJumpButton = settings.topLevelCommentJumpButton
        useSuggestedCommentSort = settings.useSuggestedCommentSort
        showUnreadMarker = settings.showUnreadMarker
        inboxNotificationsEnabled = settings.inboxNotificationsEnabled
        notifyReplies = settings.notifyReplies
        notifyMentions = settings.notifyMentions
        notifyPrivateMessages = settings.notifyPrivateMessages
        notifyRedditNotices = settings.notifyRedditNotices
        notifyModmail = settings.notifyModmail
        showNsfwPosts = settings.showNsfwPosts
        sensitiveMediaPreviewMode = settings.sensitiveMediaPreviewMode
        revealedMediaIds.clear()
        revealedNsfwMediaIds.clear()
        activeAnimatedMediaId = null
        contentFilters = settings.contentFilters
        communitySortPreferences = settings.communitySortPreferences
        listingTimePeriod = settings.listingTimePeriod
        selectedSubreddit?.let { communityName ->
            communitySort = preferredCommunitySort(communitySortPreferences, communityName)
        }
        drawerCommunitiesMode = settings.drawerCommunitiesMode
        drawerRecentCommunities = settings.drawerRecentCommunities
        backTwiceToExit = settings.backTwiceToExit
        filteredPostsSummary = settings.filteredPostsSummary
        savedSearchesEnabled = settings.savedSearchesEnabled
        scrollPositionIndicator = settings.scrollPositionIndicator
        randomNsfwCommunityPlacement = settings.randomNsfwCommunityPlacement
        pinnedPostsPresentation = settings.pinnedPostsPresentation
        postLayout = settings.postLayout
        listThumbnailSide = settings.listThumbnailSide
        mutedCommunityDirectViewMode = settings.mutedCommunityDirectViewMode
        communityDiscoveryLayout = settings.communityDiscoveryLayout
        navigationLabelMode = settings.navigationLabelMode
        tabletLayoutMode = settings.tabletLayoutMode
        topBarBehavior = settings.topBarBehavior
        bottomBarBehavior = settings.bottomBarBehavior
        mediaLabelsVisible = settings.mediaLabelsVisible
        mediaCaptionMode = settings.mediaCaptionMode
        fullscreenCaptionMode = settings.fullscreenCaptionMode
        subredditLabelStyle = settings.subredditLabelStyle
        openKeyboardOnSearch = settings.openKeyboardOnSearch
        videoAutoplay = settings.videoAutoplay
        startVideosMuted = settings.startVideosMuted
        loopVideos = settings.loopVideos
        wifiVideoQuality = settings.wifiVideoQuality
        mobileVideoQuality = settings.mobileVideoQuality
        videoTimelineSize = settings.videoTimelineSize
        playbackOptionsPlacement = settings.playbackOptionsPlacement
        videoSubtitleDefault = settings.videoSubtitleDefault
        videoAudioDefault = settings.videoAudioDefault
        videoSpeedControls = settings.videoSpeedControls
        videoDoubleTapSeek = settings.videoDoubleTapSeek
        videoHoldForDoubleSpeed = settings.videoHoldForDoubleSpeed
        videoPinchToZoom = settings.videoPinchToZoom
        videoPictureInPicture = settings.videoPictureInPicture
        videoRotateFullscreen = settings.videoRotateFullscreen
        videoTechnicalDetails = settings.videoTechnicalDetails
        landscapeVideoGesturesEnabled = settings.landscapeVideoGesturesEnabled
        landscapeBrightnessGestureEnabled = settings.landscapeBrightnessGestureEnabled
        landscapeVolumeGestureEnabled = settings.landscapeVolumeGestureEnabled
        reduceMotion = settings.reduceMotion
        feedVideoMuted = settings.startVideosMuted
        if (!videoAutoplayAllowed) {
            feedAutoplayMediaId = null
        }
        reconcileVideoPlayback()
        if (topBarBehavior == ChromeBehavior.ALWAYS_SHOW || bottomBarBehavior == ChromeBehavior.ALWAYS_SHOW) {
            showChrome()
        }
    }

    /**
     * MEDIA05_REPLAY_IS_A_MANUAL_PLAY_REQUEST_V1: Replay of an ended video
     * makes it the active, manually started video exactly like Play, so the
     * visibility and route rules that pause a playing video also pause a
     * replayed one. The player coordinator then restarts it from the start.
     */
    fun replayVideo(postId: String) = playVideo(postId)

    fun playVideo(postId: String) {
        scrubbingVideoId = null
        manuallyPausedVideoId = null
        activeVideoTransitionId = MediaPerformanceTrace.beginTransition(
            mediaId = postId,
            previousMediaId = activeVideoMediaId,
            reason = "manual",
            visibleFraction = videoSurfaceVisibility.values
                .filter { it.mediaId == postId }
                .maxOfOrNull { it.metrics.visibleFraction },
        )
        manuallyStartedVideoId = postId
        activeVideoMediaId = postId
        activeAnimatedMediaId = postId
    }

    fun stopVideo(postId: String) {
        if (scrubbingVideoId == postId) scrubbingVideoId = null
        manuallyPausedVideoId = postId
        if (activeVideoMediaId == postId) {
            MediaPerformanceTrace.mark(
                event = "candidate_released",
                mediaId = postId,
                transitionId = activeVideoTransitionId,
                details = "reason=manual_pause",
            )
            MediaPerformanceTrace.endTransition(
                transitionId = activeVideoTransitionId,
                mediaId = postId,
                outcome = "manual_pause",
            )
            activeVideoTransitionId = 0L
        }
        if (backgroundResumeVideoId == postId) {
            backgroundResumeVideoId = null
            backgroundResumePending = false
            backgroundResumeWasManual = false
        }
        if (manuallyStartedVideoId == postId) manuallyStartedVideoId = null
        if (activeVideoMediaId == postId) activeVideoMediaId = null
        if (activeAnimatedMediaId == postId) activeAnimatedMediaId = null
        reconcileVideoPlayback()
    }
}

private fun Comment.threadSize(): Int =
    1 + children.sumOf { child -> child.threadSize() }

internal data class NavigationRouteRestoreSnapshot(
    val selectedPostId: String?,
    val postOriginToken: String?,
    val focusedCommentId: String?,
    val selectedMediaPostId: String?,
    val selectedMediaIndex: Int,
    val postVisitId: Int,
)

internal fun navigationRouteRestoreSnapshot(state: SubshellState): NavigationRouteRestoreSnapshot =
    NavigationRouteRestoreSnapshot(
        selectedPostId = state.selectedPost?.id,
        postOriginToken = state.activeFeedPostRoute?.origin?.let(::encodeFeedPostOrigin),
        focusedCommentId = state.activeFeedPostRoute?.focusedCommentId,
        selectedMediaPostId = state.selectedMediaPost?.id,
        selectedMediaIndex = state.selectedMediaIndex,
        postVisitId = state.postVisitId,
    )

internal const val ACTIVE_ACCOUNT_USERNAME = "active_account_username"
internal const val ACTIVE_ACCOUNT_ID = "active_account_id"

// MODRESPONSE01A5_LAST_USED_MESSAGE_TYPE_V1
private const val MODERATOR_REMOVAL_MESSAGE_TYPE_PREFIX = "mod_removal_message_type:"

internal fun moderatorRemovalMessageTypeKey(accountStableId: String): String =
    MODERATOR_REMOVAL_MESSAGE_TYPE_PREFIX + accountStableId.trim()

internal fun loadModeratorRemovalMessageTypes(
    preferences: android.content.SharedPreferences,
): Map<String, RedditRemovalMessageType> = preferences.all
    .asSequence()
    .filter { (key, _) -> key.startsWith(MODERATOR_REMOVAL_MESSAGE_TYPE_PREFIX) }
    .mapNotNull { (key, value) ->
        val account = key.removePrefix(MODERATOR_REMOVAL_MESSAGE_TYPE_PREFIX).takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val type = RedditRemovalMessageType.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        account to type
    }
    .toMap()

// Request key of the post a draft would send (the key TextPostWriteRequest derives).
private fun PostComposerDraft.textPostWriteRequestKeyOrNull(): String? {
    val draft = this
    return runCatching {
        val draftGalleryItems = draft.media
            .takeIf {
                draft.type == ComposerPostType.MEDIA && it.size > 1
            }
            .orEmpty()
            .map { media ->
                TextPostWriteGalleryItem(
                    mediaUri = media.uri,
                    mediaMimeType = media.mimeType,
                    caption = media.caption,
                    outboundUrl = media.outboundUrl,
                )
            }
        textPostWriteRequestKey(
            community = draft.community,
            title = draft.title,
            text = draft.body,
            sendReplies = draft.sendReplies,
            nsfw = draft.nsfw,
            spoiler = draft.spoiler,
            linkUrl = draft.linkUrl.takeIf {
                draft.type == ComposerPostType.LINK
            },
            mediaUri = draft.media.singleOrNull()?.uri.takeIf {
                draft.type == ComposerPostType.MEDIA
            },
            mediaMimeType = draft.media.singleOrNull()?.mimeType.takeIf {
                draft.type == ComposerPostType.MEDIA
            },
            galleryItems = draftGalleryItems,
            mediaAsGif = draft.media.singleOrNull()
                ?.isComposerVideoGif() == true,
            flairId = draft.flair?.templateId,
            flairText = draft.flair?.text,
        )
    }.getOrNull()
}
