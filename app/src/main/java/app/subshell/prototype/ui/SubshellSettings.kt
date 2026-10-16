package app.subshell.prototype.ui

import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.ui.theme.ThemeMode

enum class NavigationLabelMode(val label: String) {
    ON("On"),
    PARTIAL("Partial"),
    OFF("Off"),
}

// ISSUE211_RANDOM_NSFW_COMMUNITY_V1 (#211, owner): where "Random NSFW
// community" is offered, chosen in Settings; Off by default, and shown only
// while NSFW posts are.
enum class RandomNsfwCommunityPlacement(val label: String) {
    OFF("Off"),
    SEARCH("Search"),
    COMMUNITIES("Communities"),
    MENU("Navigation menu"),
}

enum class TabletLayoutMode(val label: String) {
    SPLIT("Split"),
    SINGLE("Single"),
}

enum class CommunityDiscoveryLayout(val label: String) {
    CAROUSEL("Carousel"),
    LIST("List"),
}

enum class MediaCaptionMode(val label: String) {
    OVERLAY("Overlay"),
    BELOW_MEDIA("Below media"),
    HIDDEN("Hidden"),
}

enum class FullscreenCaptionMode(val label: String) {
    WITH_CONTROLS("With controls"),
    ALWAYS_VISIBLE("Always visible"),
}

enum class CommentCollapseMode(val label: String) {
    HEADER("Header"),
    COMMENT("Comment"),
}

enum class CommentHierarchyStyle(val label: String) {
    GUIDE_LINES("Guide lines"),
    PARENT_CONNECTORS("Parent connectors"),
}

enum class DrawerCommunitiesMode(val label: String) {
    CURRENT("Simple"),
    COLLAPSIBLE("Dropdown"),
    FULL("Full list"),
}

enum class VoteColorPalette(val label: String) {
    NEUTRAL("Theme accent"),
    RED_BLUE("Red & blue"),
}

enum class VoteControlMode(val label: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact"),
}

// ISSUE16_CONFIGURABLE_SWIPE_ACTIONS_V1
enum class PostSwipeAction(val label: String) {
    NONE("None"),
    UPVOTE("Upvote"),
    DOWNVOTE("Downvote"),
    SAVE("Save"),
    HIDE("Hide"),
}

enum class CommentSwipeAction(val label: String) {
    NONE("None"),
    UPVOTE("Upvote"),
    DOWNVOTE("Downvote"),
}

// NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1 (owner, 2026-10-06): inbox
// notifications are on unless the reader turned them off — in the main app
// only; the dev1/dev2 lane builds keep them off so several installed builds do
// not notify the same message.
internal val DefaultSubshellSettings: SubshellSettings by lazy { SubshellSettings() }

internal val INBOX_NOTIFICATIONS_ON_BY_DEFAULT: Boolean =
    app.subshell.prototype.BuildConfig.DEBUG_SLOT.isBlank()

// SETTINGSAUDIT01_DEFAULTS_V1 (settings audit, owner 2026-10-13): the theme
// follows the system; the unread marker is on like Highlight new comments;
// swipe left upvotes and swipe right downvotes, for posts and comments (the
// master switch stays off). They apply to everyone who never changed them.
// Start-up and the SubshellState constructor read these through
// DefaultSubshellSettings, so the defaults live in one place.
data class SubshellSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pureBlackDarkTheme: Boolean = false,
    val appTextScale: AppTextScale = AppTextScale.DEFAULT,
    val voteColorPalette: VoteColorPalette = VoteColorPalette.NEUTRAL,
    val voteControlMode: VoteControlMode = VoteControlMode.COMFORTABLE,
    val codeFont: CodeFont = CodeFont.JETBRAINS_MONO,
    val interactionSide: InteractionSide = InteractionSide.RIGHT,
    val drawerSidePreference: DrawerSidePreference = DrawerSidePreference.FOLLOW,
    val commentActionPreference: CommentActionPreference = CommentActionPreference.FOLLOW,
    val commentTimeFormat: CommentTimeFormat = CommentTimeFormat.RELATIVE,
    val commentCollapseMode: CommentCollapseMode = CommentCollapseMode.HEADER,
    val commentHierarchyStyle: CommentHierarchyStyle = CommentHierarchyStyle.GUIDE_LINES,
    val swipeActionsEnabled: Boolean = false,
    val postSwipeLeftAction: PostSwipeAction = PostSwipeAction.UPVOTE,
    val postSwipeRightAction: PostSwipeAction = PostSwipeAction.DOWNVOTE,
    val commentSwipeLeftAction: CommentSwipeAction = CommentSwipeAction.UPVOTE,
    val commentSwipeRightAction: CommentSwipeAction = CommentSwipeAction.DOWNVOTE,
    val highlightNewComments: Boolean = true,
    // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
    val showCommentAvatars: Boolean = true,
    val topLevelCommentJumpButton: Boolean = false,
    val useSuggestedCommentSort: Boolean = true,
    val showUnreadMarker: Boolean = true,
    val inboxNotificationsEnabled: Boolean = INBOX_NOTIFICATIONS_ON_BY_DEFAULT,
    // NOTIFY01A1_GRANULAR_NOTIFICATION_POLICY_V1
    val notifyReplies: Boolean = true,
    val notifyMentions: Boolean = true,
    val notifyPrivateMessages: Boolean = true,
    val notifyRedditNotices: Boolean = true,
    val notifyModmail: Boolean = true,
    val showNsfwPosts: Boolean = false,
    val sensitiveMediaPreviewMode: SensitiveMediaPreviewMode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD,
    val contentFilters: ContentFilters = ContentFilters(),
    val communitySortPreferences: Map<String, CommunitySort> = emptyMap(),
    val listingTimePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    val drawerCommunitiesMode: DrawerCommunitiesMode = DrawerCommunitiesMode.COLLAPSIBLE,
    val drawerRecentCommunities: Boolean = false,
    val backTwiceToExit: Boolean = false,
    // CONTENTFILTER04_FILTERED_SUMMARY_V1: optional, off by default.
    val filteredPostsSummary: Boolean = false,
    // ISSUE206_SAVED_SEARCHES_V1: off by default (new optional UI).
    val savedSearchesEnabled: Boolean = false,
    // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1: off by default.
    val scrollPositionIndicator: Boolean = false,
    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: off by default; only while NSFW is shown.
    val randomNsfwCommunityPlacement: RandomNsfwCommunityPlacement = RandomNsfwCommunityPlacement.OFF,
    val communityDiscoveryLayout: CommunityDiscoveryLayout = CommunityDiscoveryLayout.CAROUSEL,
    val navigationLabelMode: NavigationLabelMode = NavigationLabelMode.ON,
    val tabletLayoutMode: TabletLayoutMode = TabletLayoutMode.SPLIT,
    val topBarBehavior: ChromeBehavior = ChromeBehavior.HIDE_ON_SCROLL,
    val bottomBarBehavior: ChromeBehavior = ChromeBehavior.HIDE_ON_SCROLL,
    val mediaLabelsVisible: Boolean = false,
    val mediaCaptionMode: MediaCaptionMode = MediaCaptionMode.OVERLAY,
    val fullscreenCaptionMode: FullscreenCaptionMode = FullscreenCaptionMode.WITH_CONTROLS,
    val subredditLabelStyle: SubredditLabelStyle = SubredditLabelStyle.MODERN,
    val openKeyboardOnSearch: Boolean = true,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.ALWAYS,
    val startVideosMuted: Boolean = true,
    val loopVideos: Boolean = false,
    val wifiVideoQuality: VideoQuality = VideoQuality.HIGH,
    val mobileVideoQuality: VideoQuality = VideoQuality.MEDIUM,
    val videoTimelineSize: VideoTimelineSize = VideoTimelineSize.STANDARD,
    val playbackOptionsPlacement: PlaybackOptionsPlacement = PlaybackOptionsPlacement.POST_AND_FULLSCREEN,
    val videoSubtitleDefault: VideoSubtitleDefault = VideoSubtitleDefault.WHEN_MARKED,
    val videoAudioDefault: VideoAudioDefault = VideoAudioDefault.VIDEO_DEFAULT,
    val videoSpeedControls: Boolean = false,
    val videoDoubleTapSeek: Boolean = false,
    val videoHoldForDoubleSpeed: Boolean = false,
    val videoPictureInPicture: Boolean = false,
    val videoRotateFullscreen: Boolean = false,
    val videoTechnicalDetails: Boolean = false,
    val landscapeVideoGesturesEnabled: Boolean = true,
    val landscapeBrightnessGestureEnabled: Boolean = true,
    val landscapeVolumeGestureEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
) {
    fun toStorageMap(): Map<String, String> = linkedMapOf(
        THEME_MODE to themeMode.name,
        DYNAMIC_COLOR to dynamicColor.toString(),
        PURE_BLACK_DARK_THEME to pureBlackDarkTheme.toString(),
        APP_TEXT_SCALE to appTextScale.name,
        VOTE_COLOR_PALETTE to voteColorPalette.name,
        VOTE_CONTROL_MODE to voteControlMode.name,
        CODE_FONT to codeFont.name,
        INTERACTION_SIDE to interactionSide.name,
        DRAWER_SIDE_PREFERENCE to drawerSidePreference.name,
        COMMENT_ACTION_PREFERENCE to commentActionPreference.name,
        COMMENT_TIME_FORMAT to commentTimeFormat.name,
        COMMENT_COLLAPSE_MODE to commentCollapseMode.name,
        COMMENT_HIERARCHY_STYLE to commentHierarchyStyle.name,
        SWIPE_ACTIONS_ENABLED to swipeActionsEnabled.toString(),
        POST_SWIPE_LEFT_ACTION to postSwipeLeftAction.name,
        POST_SWIPE_RIGHT_ACTION to postSwipeRightAction.name,
        COMMENT_SWIPE_LEFT_ACTION to commentSwipeLeftAction.name,
        COMMENT_SWIPE_RIGHT_ACTION to commentSwipeRightAction.name,
        HIGHLIGHT_NEW_COMMENTS to highlightNewComments.toString(),
        SHOW_COMMENT_AVATARS to showCommentAvatars.toString(),
        TOP_LEVEL_COMMENT_JUMP_BUTTON to topLevelCommentJumpButton.toString(),
        USE_SUGGESTED_COMMENT_SORT to useSuggestedCommentSort.toString(),
        SHOW_UNREAD_MARKER to showUnreadMarker.toString(),
        INBOX_NOTIFICATIONS_ENABLED to inboxNotificationsEnabled.toString(),
        NOTIFY_REPLIES to notifyReplies.toString(),
        NOTIFY_MENTIONS to notifyMentions.toString(),
        NOTIFY_PRIVATE_MESSAGES to notifyPrivateMessages.toString(),
        NOTIFY_REDDIT_NOTICES to notifyRedditNotices.toString(),
        NOTIFY_MODMAIL to notifyModmail.toString(),
        SHOW_NSFW_POSTS to showNsfwPosts.toString(),
        SENSITIVE_MEDIA_PREVIEW_MODE to sensitiveMediaPreviewMode.name,
        COMMUNITY_SORT_PREFERENCES to encodeCommunitySortPreferences(communitySortPreferences),
        LISTING_TIME_PERIOD to listingTimePeriod.name,
        DRAWER_COMMUNITIES_MODE to drawerCommunitiesMode.name,
        DRAWER_RECENT_COMMUNITIES to drawerRecentCommunities.toString(),
        BACK_TWICE_TO_EXIT to backTwiceToExit.toString(),
        FILTERED_POSTS_SUMMARY to filteredPostsSummary.toString(),
        SAVED_SEARCHES_ENABLED to savedSearchesEnabled.toString(),
        SCROLL_POSITION_INDICATOR to scrollPositionIndicator.toString(),
        RANDOM_NSFW_COMMUNITY_PLACEMENT to randomNsfwCommunityPlacement.name,
        COMMUNITY_DISCOVERY_LAYOUT to communityDiscoveryLayout.name,
        NAVIGATION_LABEL_MODE to navigationLabelMode.name,
        TABLET_LAYOUT_MODE to tabletLayoutMode.name,
        TOP_BAR_BEHAVIOR to topBarBehavior.name,
        BOTTOM_BAR_BEHAVIOR to bottomBarBehavior.name,
        MEDIA_LABELS_VISIBLE to mediaLabelsVisible.toString(),
        MEDIA_CAPTION_MODE to mediaCaptionMode.name,
        FULLSCREEN_CAPTION_MODE to fullscreenCaptionMode.name,
        SUBREDDIT_LABEL_STYLE to subredditLabelStyle.name,
        OPEN_KEYBOARD_ON_SEARCH to openKeyboardOnSearch.toString(),
        VIDEO_AUTOPLAY to videoAutoplay.name,
        START_VIDEOS_MUTED to startVideosMuted.toString(),
        LOOP_VIDEOS to loopVideos.toString(),
        VIDEO_QUALITY_WIFI to wifiVideoQuality.name,
        VIDEO_QUALITY_MOBILE to mobileVideoQuality.name,
        VIDEO_TIMELINE_SIZE to videoTimelineSize.name,
        PLAYBACK_OPTIONS_PLACEMENT to playbackOptionsPlacement.name,
        VIDEO_SUBTITLE_DEFAULT to videoSubtitleDefault.name,
        VIDEO_AUDIO_DEFAULT to videoAudioDefault.name,
        VIDEO_SPEED_CONTROLS to videoSpeedControls.toString(),
        VIDEO_DOUBLE_TAP_SEEK to videoDoubleTapSeek.toString(),
        VIDEO_HOLD_FOR_DOUBLE_SPEED to videoHoldForDoubleSpeed.toString(),
        VIDEO_PICTURE_IN_PICTURE to videoPictureInPicture.toString(),
        VIDEO_ROTATE_FULLSCREEN to videoRotateFullscreen.toString(),
        VIDEO_TECHNICAL_DETAILS to videoTechnicalDetails.toString(),
        LANDSCAPE_VIDEO_GESTURES_ENABLED to landscapeVideoGesturesEnabled.toString(),
        LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED to landscapeBrightnessGestureEnabled.toString(),
        LANDSCAPE_VOLUME_GESTURE_ENABLED to landscapeVolumeGestureEnabled.toString(),
        REDUCE_MOTION to reduceMotion.toString(),
    ).apply {
        putAll(contentFilters.toStorageMap())
    }

    companion object {
        const val THEME_MODE = "theme_mode"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val PURE_BLACK_DARK_THEME = "pure_black_dark_theme"
        const val APP_TEXT_SCALE = "app_text_scale"
        const val VOTE_COLOR_PALETTE = "vote_color_palette"
        const val VOTE_CONTROL_MODE = "vote_control_mode"
        const val CODE_FONT = "code_font"
        const val INTERACTION_SIDE = "interaction_side"
        const val DRAWER_SIDE_PREFERENCE = "drawer_side_preference"
        const val COMMENT_ACTION_PREFERENCE = "comment_action_preference"
        const val LEGACY_COMMENT_ACTION_PLACEMENT = "comment_action_placement"
        const val COMMENT_TIME_FORMAT = "comment_time_format"
        const val COMMENT_COLLAPSE_MODE = "comment_collapse_mode"
        const val COMMENT_HIERARCHY_STYLE = "comment_hierarchy_style"
        const val SWIPE_ACTIONS_ENABLED = "swipe_actions_enabled"
        const val POST_SWIPE_LEFT_ACTION = "post_swipe_left_action"
        const val POST_SWIPE_RIGHT_ACTION = "post_swipe_right_action"
        const val COMMENT_SWIPE_LEFT_ACTION = "comment_swipe_left_action"
        const val COMMENT_SWIPE_RIGHT_ACTION = "comment_swipe_right_action"
        const val HIGHLIGHT_NEW_COMMENTS = "highlight_new_comments"
        const val SHOW_COMMENT_AVATARS = "show_comment_avatars"
        const val TOP_LEVEL_COMMENT_JUMP_BUTTON = "top_level_comment_jump_button"
        const val USE_SUGGESTED_COMMENT_SORT = "use_suggested_comment_sort"
        const val SHOW_UNREAD_MARKER = "show_unread_marker"
        const val INBOX_NOTIFICATIONS_ENABLED = "inbox_notifications_enabled"
        const val NOTIFY_REPLIES = "notify_replies"
        const val NOTIFY_MENTIONS = "notify_mentions"
        const val NOTIFY_PRIVATE_MESSAGES = "notify_private_messages"
        const val NOTIFY_REDDIT_NOTICES = "notify_reddit_notices"
        const val NOTIFY_MODMAIL = "notify_modmail"
        const val SHOW_NSFW_POSTS = "show_nsfw_posts"
        const val SENSITIVE_MEDIA_PREVIEW_MODE = "sensitive_media_preview_mode"
        const val COMMENT_VISIT_HISTORY = "comment_visit_history"
        const val COMMUNITY_SORT_PREFERENCES = "community_sort_preferences"
        const val LISTING_TIME_PERIOD = "listing_time_period"
        const val DISCOVER_EXPANDED = "discover_expanded"
        const val SUBS_EXPLORE_SHORTCUT_MODE = "subs_explore_shortcut_mode"
        const val DRAWER_COMMUNITIES_MODE = "drawer_communities_mode"
        const val DRAWER_VIEW_ALL_COMMUNITIES = "drawer_view_all_communities"
        const val DRAWER_RECENT_COMMUNITIES = "drawer_recent_communities"
        const val BACK_TWICE_TO_EXIT = "back_twice_to_exit"
        const val FILTERED_POSTS_SUMMARY = "filtered_posts_summary"
        const val SAVED_SEARCHES_ENABLED = "saved_searches_enabled"
        const val SCROLL_POSITION_INDICATOR = "scroll_position_indicator"
        const val RANDOM_NSFW_COMMUNITY_PLACEMENT = "random_nsfw_community_placement"
        const val COMMUNITY_DISCOVERY_LAYOUT = "community_discovery_layout"
        const val NAVIGATION_LABEL_MODE = "navigation_label_mode"
        const val TABLET_LAYOUT_MODE = "tablet_layout_mode"
        const val LEGACY_NAVIGATION_LABELS_VISIBLE = "navigation_labels_visible"
        const val TOP_BAR_BEHAVIOR = "top_bar_behavior"
        const val BOTTOM_BAR_BEHAVIOR = "bottom_bar_behavior"
        const val MEDIA_LABELS_VISIBLE = "media_labels_visible"
        const val MEDIA_CAPTION_MODE = "media_caption_mode"
        const val FULLSCREEN_CAPTION_MODE = "fullscreen_caption_mode"
        const val SUBREDDIT_LABEL_STYLE = "subreddit_label_style"
        const val OPEN_KEYBOARD_ON_SEARCH = "open_keyboard_on_search"
        const val VIDEO_AUTOPLAY = "video_autoplay"
        const val START_VIDEOS_MUTED = "start_videos_muted"
        const val LOOP_VIDEOS = "loop_videos"
        const val LEGACY_VIDEO_QUALITY = "video_quality"
        const val VIDEO_QUALITY_WIFI = "video_quality_wifi"
        const val VIDEO_QUALITY_MOBILE = "video_quality_mobile"
        const val VIDEO_TIMELINE_SIZE = "video_timeline_size"
        const val PLAYBACK_OPTIONS_PLACEMENT = "playback_options_placement"
        const val VIDEO_SUBTITLE_DEFAULT = "video_subtitle_default"
        const val VIDEO_AUDIO_DEFAULT = "video_audio_default"
        const val VIDEO_SPEED_CONTROLS = "video_speed_controls"
        const val VIDEO_DOUBLE_TAP_SEEK = "video_double_tap_seek"
        const val VIDEO_HOLD_FOR_DOUBLE_SPEED = "video_hold_for_double_speed"
        const val VIDEO_PICTURE_IN_PICTURE = "video_picture_in_picture"
        const val VIDEO_ROTATE_FULLSCREEN = "video_rotate_fullscreen"
        const val VIDEO_TECHNICAL_DETAILS = "video_technical_details"
        const val LANDSCAPE_VIDEO_GESTURES_ENABLED = "landscape_video_gestures_enabled"
        const val LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED = "landscape_brightness_gesture_enabled"
        const val LANDSCAPE_VOLUME_GESTURE_ENABLED = "landscape_volume_gesture_enabled"
        const val REDUCE_MOTION = "reduce_motion"
        const val FEED_PREVIEW_STATE = "feed_preview_state"

        /**
         * SETTINGSAUDIT01_BACKUP_CLEANUP_V1 (settings audit, owner 2026-10-13): keys
         * older backups carry but nothing reads any more (three constants written
         * for settings long gone, and the debug-only Feed preview, now device-local).
         * New backups leave them out; reading an older backup drops them quietly
         * instead of keeping them as unreadable values.
         */
        internal val RETIRED_SETTING_KEYS = setOf(
            DISCOVER_EXPANDED,
            SUBS_EXPLORE_SHORTCUT_MODE,
            DRAWER_VIEW_ALL_COMMUNITIES,
            FEED_PREVIEW_STATE,
        )

        val storageKeys = setOf(
            THEME_MODE,
            DYNAMIC_COLOR,
            PURE_BLACK_DARK_THEME,
            APP_TEXT_SCALE,
            VOTE_COLOR_PALETTE,
            VOTE_CONTROL_MODE,
            CODE_FONT,
            INTERACTION_SIDE,
            DRAWER_SIDE_PREFERENCE,
            COMMENT_ACTION_PREFERENCE,
            COMMENT_TIME_FORMAT,
            COMMENT_COLLAPSE_MODE,
            COMMENT_HIERARCHY_STYLE,
            SWIPE_ACTIONS_ENABLED,
            POST_SWIPE_LEFT_ACTION,
            POST_SWIPE_RIGHT_ACTION,
            COMMENT_SWIPE_LEFT_ACTION,
            COMMENT_SWIPE_RIGHT_ACTION,
            HIGHLIGHT_NEW_COMMENTS,
            SHOW_COMMENT_AVATARS,
            TOP_LEVEL_COMMENT_JUMP_BUTTON,
            USE_SUGGESTED_COMMENT_SORT,
            SHOW_UNREAD_MARKER,
            INBOX_NOTIFICATIONS_ENABLED,
            NOTIFY_REPLIES,
            NOTIFY_MENTIONS,
            NOTIFY_PRIVATE_MESSAGES,
            NOTIFY_REDDIT_NOTICES,
            NOTIFY_MODMAIL,
            SHOW_NSFW_POSTS,
            SENSITIVE_MEDIA_PREVIEW_MODE,
            COMMUNITY_SORT_PREFERENCES,
            LISTING_TIME_PERIOD,
            DRAWER_COMMUNITIES_MODE,
            DRAWER_RECENT_COMMUNITIES,
            BACK_TWICE_TO_EXIT,
            FILTERED_POSTS_SUMMARY,
            SAVED_SEARCHES_ENABLED,
            SCROLL_POSITION_INDICATOR,
            RANDOM_NSFW_COMMUNITY_PLACEMENT,
            COMMUNITY_DISCOVERY_LAYOUT,
            NAVIGATION_LABEL_MODE,
            TABLET_LAYOUT_MODE,
            TOP_BAR_BEHAVIOR,
            BOTTOM_BAR_BEHAVIOR,
            MEDIA_LABELS_VISIBLE,
            MEDIA_CAPTION_MODE,
            FULLSCREEN_CAPTION_MODE,
            SUBREDDIT_LABEL_STYLE,
            OPEN_KEYBOARD_ON_SEARCH,
            VIDEO_AUTOPLAY,
            START_VIDEOS_MUTED,
            LOOP_VIDEOS,
            VIDEO_QUALITY_WIFI,
            VIDEO_QUALITY_MOBILE,
            VIDEO_TIMELINE_SIZE,
            PLAYBACK_OPTIONS_PLACEMENT,
            VIDEO_SUBTITLE_DEFAULT,
            VIDEO_AUDIO_DEFAULT,
            VIDEO_SPEED_CONTROLS,
            VIDEO_DOUBLE_TAP_SEEK,
            VIDEO_HOLD_FOR_DOUBLE_SPEED,
            VIDEO_PICTURE_IN_PICTURE,
            VIDEO_ROTATE_FULLSCREEN,
            VIDEO_TECHNICAL_DETAILS,
            LANDSCAPE_VIDEO_GESTURES_ENABLED,
            LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED,
            LANDSCAPE_VOLUME_GESTURE_ENABLED,
            REDUCE_MOTION,
        ) + ContentFilterType.entries.map(ContentFilterType::storageKey) +
            // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1
            ContentFilterType.entries.map(ContentFilterType::scopedStorageKey)

        /**
         * BACKUP07_ONLY_BACKED_UP_SETTINGS_OVERWRITE_V1
         * Splits a backup's settings into values this build can read and the
         * rest (unknown keys, or a value a newer app wrote, such as a new enum
         * constant). The rest is kept as written instead of rejecting the
         * whole backup, and restore leaves those settings unchanged.
         */
        internal fun splitBackupSettingsValues(
            values: Map<String, String>,
        ): Pair<Map<String, String>, Map<String, String>> {
            // LOWUI_LEGACY_NAVIGATION_LABELS_KEY_RESTORES_V1 (OCB-296): the old
            // navigation-labels key is migrated like the other two legacy keys
            // instead of being set aside as unreadable.
            val known = storageKeys + LEGACY_COMMENT_ACTION_PLACEMENT + LEGACY_VIDEO_QUALITY +
                LEGACY_NAVIGATION_LABELS_VISIBLE
            val defaults = SubshellSettings().toStorageMap()
            val readable = linkedMapOf<String, String>()
            val unread = linkedMapOf<String, String>()
            values.forEach { (key, value) ->
                if (key in RETIRED_SETTING_KEYS) return@forEach
                val usable = key in known &&
                    runCatching { fromStorageMap(defaults + (key to value)) }.isSuccess
                if (usable) readable[key] = value else unread[key] = value
            }
            return readable to unread
        }

        /** The storage keys a backup says something about, legacy keys mapped to their successors. */
        internal fun includedStorageKeys(readable: Map<String, String>): Set<String> = buildSet {
            addAll(readable.keys.filter(storageKeys::contains))
            if (LEGACY_COMMENT_ACTION_PLACEMENT in readable) {
                add(INTERACTION_SIDE)
                add(COMMENT_ACTION_PREFERENCE)
            }
            if (LEGACY_VIDEO_QUALITY in readable) {
                add(VIDEO_QUALITY_WIFI)
                add(VIDEO_QUALITY_MOBILE)
            }
            if (LEGACY_NAVIGATION_LABELS_VISIBLE in readable) add(NAVIGATION_LABEL_MODE)
        }

        fun fromStorageMap(values: Map<String, String>): SubshellSettings {
            require(values.isNotEmpty()) { "Backup settings are empty" }
            val defaults = SubshellSettings().toStorageMap()
            val compatibleValues = defaults + values.filterKeys(storageKeys::contains)
            val legacyCommentActionPlacement = values[LEGACY_COMMENT_ACTION_PLACEMENT]
                ?.let { stored ->
                    CommentActionPlacement.entries.firstOrNull { it.name == stored }
                        ?: throw IllegalArgumentException(
                            "Backup contains an invalid value for $LEGACY_COMMENT_ACTION_PLACEMENT",
                        )
                }
            val legacyVideoQuality = values[LEGACY_VIDEO_QUALITY]?.let { stored ->
                VideoQuality.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException(
                        "Backup contains an invalid value for $LEGACY_VIDEO_QUALITY",
                    )
            }
            return SubshellSettings(
                themeMode = enumValue(THEME_MODE, compatibleValues, ThemeMode.entries),
                dynamicColor = booleanValue(DYNAMIC_COLOR, compatibleValues),
                pureBlackDarkTheme = booleanValue(PURE_BLACK_DARK_THEME, compatibleValues),
                appTextScale = enumValue(APP_TEXT_SCALE, compatibleValues, AppTextScale.entries),
                voteColorPalette = enumValue(
                    VOTE_COLOR_PALETTE,
                    compatibleValues,
                    VoteColorPalette.entries,
                ),
                voteControlMode = enumValue(
                    VOTE_CONTROL_MODE,
                    compatibleValues,
                    VoteControlMode.entries,
                ),
                codeFont = enumValue(CODE_FONT, compatibleValues, CodeFont.entries),
                interactionSide = enumValueOrFallback(
                    key = INTERACTION_SIDE,
                    rawValues = values,
                    entries = InteractionSide.entries,
                    fallback = legacyInteractionSide(legacyCommentActionPlacement),
                ),
                drawerSidePreference = enumValueOrFallback(
                    key = DRAWER_SIDE_PREFERENCE,
                    rawValues = values,
                    entries = DrawerSidePreference.entries,
                    fallback = DrawerSidePreference.FOLLOW,
                ),
                commentActionPreference = enumValueOrFallback(
                    key = COMMENT_ACTION_PREFERENCE,
                    rawValues = values,
                    entries = CommentActionPreference.entries,
                    fallback = legacyCommentActionPreference(legacyCommentActionPlacement),
                ),
                commentTimeFormat = enumValue(
                    COMMENT_TIME_FORMAT,
                    compatibleValues,
                    CommentTimeFormat.entries,
                ),
                commentCollapseMode = enumValue(
                    COMMENT_COLLAPSE_MODE,
                    compatibleValues,
                    CommentCollapseMode.entries,
                ),
                commentHierarchyStyle = enumValue(
                    COMMENT_HIERARCHY_STYLE,
                    compatibleValues,
                    CommentHierarchyStyle.entries,
                ),
                swipeActionsEnabled = booleanValue(SWIPE_ACTIONS_ENABLED, compatibleValues),
                postSwipeLeftAction = enumValue(
                    POST_SWIPE_LEFT_ACTION,
                    compatibleValues,
                    PostSwipeAction.entries,
                ),
                postSwipeRightAction = enumValue(
                    POST_SWIPE_RIGHT_ACTION,
                    compatibleValues,
                    PostSwipeAction.entries,
                ),
                commentSwipeLeftAction = enumValue(
                    COMMENT_SWIPE_LEFT_ACTION,
                    compatibleValues,
                    CommentSwipeAction.entries,
                ),
                commentSwipeRightAction = enumValue(
                    COMMENT_SWIPE_RIGHT_ACTION,
                    compatibleValues,
                    CommentSwipeAction.entries,
                ),
                highlightNewComments = booleanValue(HIGHLIGHT_NEW_COMMENTS, compatibleValues),
                showCommentAvatars = booleanValue(SHOW_COMMENT_AVATARS, compatibleValues),
                topLevelCommentJumpButton = booleanValue(TOP_LEVEL_COMMENT_JUMP_BUTTON, compatibleValues),
                useSuggestedCommentSort = booleanValue(
                    USE_SUGGESTED_COMMENT_SORT,
                    compatibleValues,
                ),
                showUnreadMarker = booleanValue(SHOW_UNREAD_MARKER, compatibleValues),
                inboxNotificationsEnabled = booleanValue(
                    INBOX_NOTIFICATIONS_ENABLED,
                    compatibleValues,
                ),
                notifyReplies = booleanValue(NOTIFY_REPLIES, compatibleValues),
                notifyMentions = booleanValue(NOTIFY_MENTIONS, compatibleValues),
                notifyPrivateMessages = booleanValue(
                    NOTIFY_PRIVATE_MESSAGES,
                    compatibleValues,
                ),
                notifyRedditNotices = booleanValue(
                    NOTIFY_REDDIT_NOTICES,
                    compatibleValues,
                ),
                notifyModmail = booleanValue(NOTIFY_MODMAIL, compatibleValues),
                showNsfwPosts = booleanValue(SHOW_NSFW_POSTS, compatibleValues),
                sensitiveMediaPreviewMode = enumValue(
                    SENSITIVE_MEDIA_PREVIEW_MODE,
                    compatibleValues,
                    SensitiveMediaPreviewMode.entries,
                ),
                contentFilters = ContentFilters.fromStorageMap(compatibleValues),
                communitySortPreferences = decodeCommunitySortPreferences(
                    compatibleValues.getValue(COMMUNITY_SORT_PREFERENCES),
                ),
                listingTimePeriod = enumValue(
                    LISTING_TIME_PERIOD,
                    compatibleValues,
                    RedditListingTimePeriod.entries,
                ),
                drawerCommunitiesMode = enumValue(
                    DRAWER_COMMUNITIES_MODE,
                    compatibleValues,
                    DrawerCommunitiesMode.entries,
                ),
                drawerRecentCommunities = booleanValue(
                    DRAWER_RECENT_COMMUNITIES,
                    compatibleValues,
                ),
                backTwiceToExit = booleanValue(BACK_TWICE_TO_EXIT, compatibleValues),
                filteredPostsSummary = booleanValue(FILTERED_POSTS_SUMMARY, compatibleValues),
                savedSearchesEnabled = booleanValue(SAVED_SEARCHES_ENABLED, compatibleValues),
                scrollPositionIndicator = booleanValue(SCROLL_POSITION_INDICATOR, compatibleValues),
                randomNsfwCommunityPlacement = enumValue(
                    RANDOM_NSFW_COMMUNITY_PLACEMENT,
                    compatibleValues,
                    RandomNsfwCommunityPlacement.entries,
                ),
                communityDiscoveryLayout = enumValue(
                    COMMUNITY_DISCOVERY_LAYOUT,
                    compatibleValues,
                    CommunityDiscoveryLayout.entries,
                ),
                navigationLabelMode = navigationLabelMode(values),
                tabletLayoutMode = enumValue(
                    TABLET_LAYOUT_MODE,
                    compatibleValues,
                    TabletLayoutMode.entries,
                ),
                topBarBehavior = enumValue(TOP_BAR_BEHAVIOR, compatibleValues, ChromeBehavior.entries),
                bottomBarBehavior = enumValue(BOTTOM_BAR_BEHAVIOR, compatibleValues, ChromeBehavior.entries),
                mediaLabelsVisible = booleanValue(MEDIA_LABELS_VISIBLE, compatibleValues),
                mediaCaptionMode = enumValue(
                    MEDIA_CAPTION_MODE,
                    compatibleValues,
                    MediaCaptionMode.entries,
                ),
                fullscreenCaptionMode = enumValue(
                    FULLSCREEN_CAPTION_MODE,
                    compatibleValues,
                    FullscreenCaptionMode.entries,
                ),
                subredditLabelStyle = enumValue(
                    SUBREDDIT_LABEL_STYLE,
                    compatibleValues,
                    SubredditLabelStyle.entries,
                ),
                openKeyboardOnSearch = booleanValue(OPEN_KEYBOARD_ON_SEARCH, compatibleValues),
                videoAutoplay = enumValue(VIDEO_AUTOPLAY, compatibleValues, VideoAutoplay.entries),
                startVideosMuted = booleanValue(START_VIDEOS_MUTED, compatibleValues),
                loopVideos = booleanValue(LOOP_VIDEOS, compatibleValues),
                wifiVideoQuality = videoQualityValue(
                    key = VIDEO_QUALITY_WIFI,
                    rawValues = values,
                    legacyValue = legacyVideoQuality,
                    fallback = VideoQuality.HIGH,
                ),
                mobileVideoQuality = videoQualityValue(
                    key = VIDEO_QUALITY_MOBILE,
                    rawValues = values,
                    legacyValue = legacyVideoQuality,
                    fallback = VideoQuality.MEDIUM,
                ),
                videoTimelineSize = enumValue(
                    VIDEO_TIMELINE_SIZE,
                    compatibleValues,
                    VideoTimelineSize.entries,
                ),
                playbackOptionsPlacement = enumValue(
                    PLAYBACK_OPTIONS_PLACEMENT,
                    compatibleValues,
                    PlaybackOptionsPlacement.entries,
                ),
                videoSubtitleDefault = enumValue(
                    VIDEO_SUBTITLE_DEFAULT,
                    compatibleValues,
                    VideoSubtitleDefault.entries,
                ),
                videoAudioDefault = enumValue(
                    VIDEO_AUDIO_DEFAULT,
                    compatibleValues,
                    VideoAudioDefault.entries,
                ),
                videoSpeedControls = booleanValue(VIDEO_SPEED_CONTROLS, compatibleValues),
                videoDoubleTapSeek = booleanValue(VIDEO_DOUBLE_TAP_SEEK, compatibleValues),
                videoHoldForDoubleSpeed = booleanValue(VIDEO_HOLD_FOR_DOUBLE_SPEED, compatibleValues),
                videoPictureInPicture = booleanValue(VIDEO_PICTURE_IN_PICTURE, compatibleValues),
                videoRotateFullscreen = booleanValue(VIDEO_ROTATE_FULLSCREEN, compatibleValues),
                videoTechnicalDetails = booleanValue(VIDEO_TECHNICAL_DETAILS, compatibleValues),
                landscapeVideoGesturesEnabled =
                    booleanValue(LANDSCAPE_VIDEO_GESTURES_ENABLED, compatibleValues),
                landscapeBrightnessGestureEnabled =
                    booleanValue(LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED, compatibleValues),
                landscapeVolumeGestureEnabled =
                    booleanValue(LANDSCAPE_VOLUME_GESTURE_ENABLED, compatibleValues),
                reduceMotion = booleanValue(REDUCE_MOTION, compatibleValues),
            )
        }

        private fun <T : Enum<T>> enumValueOrFallback(
            key: String,
            rawValues: Map<String, String>,
            entries: List<T>,
            fallback: T,
        ): T = rawValues[key]?.let { stored ->
            entries.firstOrNull { it.name == stored }
                ?: throw IllegalArgumentException("Backup contains an invalid value for $key")
        } ?: fallback

        private fun videoQualityValue(
            key: String,
            rawValues: Map<String, String>,
            legacyValue: VideoQuality?,
            fallback: VideoQuality,
        ): VideoQuality = rawValues[key]?.let { stored ->
            VideoQuality.entries.firstOrNull { it.name == stored }
                ?: throw IllegalArgumentException("Backup contains an invalid value for $key")
        } ?: legacyValue ?: fallback

        private fun booleanValue(key: String, values: Map<String, String>): Boolean = when (values[key]) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Backup contains an invalid value for $key")
        }

        private fun navigationLabelMode(values: Map<String, String>): NavigationLabelMode {
            values[NAVIGATION_LABEL_MODE]?.let { stored ->
                return NavigationLabelMode.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Backup contains an invalid value for $NAVIGATION_LABEL_MODE")
            }
            return when (values[LEGACY_NAVIGATION_LABELS_VISIBLE]) {
                "true" -> NavigationLabelMode.ON
                "false" -> NavigationLabelMode.PARTIAL
                null -> NavigationLabelMode.ON
                else -> throw IllegalArgumentException(
                    "Backup contains an invalid value for $LEGACY_NAVIGATION_LABELS_VISIBLE",
                )
            }
        }

        private fun <T : Enum<T>> enumValue(key: String, values: Map<String, String>, entries: List<T>): T =
            entries.firstOrNull { it.name == values[key] }
                ?: throw IllegalArgumentException("Backup contains an invalid value for $key")
    }
}
