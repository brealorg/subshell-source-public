package app.subshell.prototype.ui.state

import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.ui.theme.AppUiScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.CodeFont
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.CommentHierarchyStyle
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.DrawerSidePreference
import app.subshell.prototype.ui.explicitDrawerSidePreference
import app.subshell.prototype.ui.FeedPreviewState
import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.REQUIRE_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS
import app.subshell.prototype.ui.SubredditLabelStyle
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.TabletLayoutMode
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.VideoQuality
import app.subshell.prototype.ui.VideoTimelineSize
import app.subshell.prototype.ui.PlaybackOptionsPlacement
import app.subshell.prototype.ui.VideoAudioDefault
import app.subshell.prototype.ui.VideoSubtitleDefault
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.resolve
import app.subshell.prototype.ui.theme.ThemeMode

/**
 * Preference state: every user setting SubshellState exposes, the update
 * function that persists it through the preference writer, the
 * interaction-side trio and the two derived placements. Extracted verbatim
 * from SubshellState, which holds one instance and delegates to it
 * (properties through property references, functions through wrappers).
 * The four setters that also reach into chrome or video state stay in
 * SubshellState. Behaviour is unchanged.
 */
internal class SettingsState(
    initialThemeMode: ThemeMode,
    initialDynamicColor: Boolean,
    initialPureBlackDarkTheme: Boolean,
    initialAppTextScale: AppTextScale,
    initialAppUiScale: AppUiScale,
    initialVoteColorPalette: VoteColorPalette,
    initialVoteControlMode: VoteControlMode,
    initialCodeFont: CodeFont,
    initialInteractionSide: InteractionSide,
    initialDrawerSidePreference: DrawerSidePreference,
    initialCommentActionPreference: CommentActionPreference,
    initialInteractionSideConfigured: Boolean,
    initialCommentTimeFormat: CommentTimeFormat,
    initialCommentCollapseMode: CommentCollapseMode,
    initialCommentHierarchyStyle: CommentHierarchyStyle,
    initialSwipeActionsEnabled: Boolean,
    initialPostSwipeLeftAction: PostSwipeAction,
    initialPostSwipeRightAction: PostSwipeAction,
    initialCommentSwipeLeftAction: CommentSwipeAction,
    initialCommentSwipeRightAction: CommentSwipeAction,
    initialHighlightNewComments: Boolean,
    initialShowCommentAvatars: Boolean,
    initialTopLevelCommentJumpButton: Boolean,
    initialUseSuggestedCommentSort: Boolean,
    initialShowUnreadMarker: Boolean,
    initialInboxNotificationsEnabled: Boolean,
    initialNotifyReplies: Boolean,
    initialNotifyMentions: Boolean,
    initialNotifyPrivateMessages: Boolean,
    initialNotifyRedditNotices: Boolean,
    initialNotifyModmail: Boolean,
    initialDrawerCommunitiesMode: DrawerCommunitiesMode,
    initialDrawerRecentCommunities: Boolean,
    initialBackTwiceToExit: Boolean,
    initialFilteredPostsSummary: Boolean = false,
    initialSavedSearchesEnabled: Boolean = false,
    initialScrollPositionIndicator: Boolean = false,
    initialRandomNsfwCommunityPlacement: app.subshell.prototype.ui.RandomNsfwCommunityPlacement =
        app.subshell.prototype.ui.RandomNsfwCommunityPlacement.OFF,
    initialPinnedPostsPresentation: app.subshell.prototype.ui.PinnedPostsPresentation =
        app.subshell.prototype.ui.PinnedPostsPresentation.CARDS,
    initialPostLayout: app.subshell.prototype.ui.PostLayout = app.subshell.prototype.ui.PostLayout.LARGE,
    initialListThumbnailSide: app.subshell.prototype.ui.ListThumbnailSide =
        app.subshell.prototype.ui.ListThumbnailSide.RIGHT,
    initialCommunityDiscoveryLayout: CommunityDiscoveryLayout,
    initialRequireDeviceUnlockForHiddenSubscriptions: Boolean,
    initialNavigationLabelMode: NavigationLabelMode,
    initialTabletLayoutMode: TabletLayoutMode,
    initialTopBarBehavior: ChromeBehavior,
    initialBottomBarBehavior: ChromeBehavior,
    initialMediaLabelsVisible: Boolean,
    initialMediaCaptionMode: MediaCaptionMode,
    initialFullscreenCaptionMode: FullscreenCaptionMode,
    initialSubredditLabelStyle: SubredditLabelStyle,
    initialOpenKeyboardOnSearch: Boolean,
    initialVideoAutoplay: VideoAutoplay,
    initialStartVideosMuted: Boolean,
    initialLoopVideos: Boolean,
    initialWifiVideoQuality: VideoQuality,
    initialMobileVideoQuality: VideoQuality,
    initialVideoTimelineSize: VideoTimelineSize,
    initialPlaybackOptionsPlacement: PlaybackOptionsPlacement,
    initialVideoSubtitleDefault: VideoSubtitleDefault,
    initialVideoAudioDefault: VideoAudioDefault,
    initialVideoSpeedControls: Boolean,
    initialVideoDoubleTapSeek: Boolean,
    initialVideoHoldForDoubleSpeed: Boolean,
    initialVideoPinchToZoom: Boolean,
    initialVideoPictureInPicture: Boolean,
    initialVideoRotateFullscreen: Boolean,
    initialVideoTechnicalDetails: Boolean,
    initialLandscapeVideoGesturesEnabled: Boolean,
    initialLandscapeBrightnessGestureEnabled: Boolean,
    initialLandscapeVolumeGestureEnabled: Boolean,
    initialReduceMotion: Boolean,
    initialFeedPreviewState: FeedPreviewState,
    private val persistPreference: (String, String) -> Unit,
) {
    var settingsQuery by mutableStateOf("")
    var themeMode by mutableStateOf(initialThemeMode)
    var dynamicColor by mutableStateOf(initialDynamicColor)
    var pureBlackDarkTheme by mutableStateOf(initialPureBlackDarkTheme)
    var appTextScale by mutableStateOf(initialAppTextScale)
    var appUiScale by mutableStateOf(initialAppUiScale)
    var voteColorPalette by mutableStateOf(initialVoteColorPalette)
        internal set
    var voteControlMode by mutableStateOf(initialVoteControlMode)
        internal set
    var codeFont by mutableStateOf(initialCodeFont)
    var interactionSide by mutableStateOf(initialInteractionSide)
        internal set
    var drawerSidePreference by mutableStateOf(initialDrawerSidePreference)
        internal set
    var commentActionPreference by mutableStateOf(initialCommentActionPreference)
        internal set
    var commentActionOnboardingVisible by mutableStateOf(!initialInteractionSideConfigured)
        internal set
    var commentTimeFormat by mutableStateOf(initialCommentTimeFormat)
    var commentCollapseMode by mutableStateOf(initialCommentCollapseMode)
        internal set
    var commentHierarchyStyle by mutableStateOf(initialCommentHierarchyStyle)
        internal set
    var swipeActionsEnabled by mutableStateOf(initialSwipeActionsEnabled)
        internal set
    var postSwipeLeftAction by mutableStateOf(initialPostSwipeLeftAction)
        internal set
    var postSwipeRightAction by mutableStateOf(initialPostSwipeRightAction)
        internal set
    var commentSwipeLeftAction by mutableStateOf(initialCommentSwipeLeftAction)
        internal set
    var commentSwipeRightAction by mutableStateOf(initialCommentSwipeRightAction)
        internal set
    var highlightNewComments by mutableStateOf(initialHighlightNewComments)
    var showCommentAvatars by mutableStateOf(initialShowCommentAvatars)
        internal set
    var topLevelCommentJumpButton by mutableStateOf(initialTopLevelCommentJumpButton)
        internal set
    var useSuggestedCommentSort by mutableStateOf(initialUseSuggestedCommentSort)
        internal set
    var showUnreadMarker by mutableStateOf(initialShowUnreadMarker)
    var inboxNotificationsEnabled by mutableStateOf(initialInboxNotificationsEnabled)
        internal set
    var notifyReplies by mutableStateOf(initialNotifyReplies)
        internal set
    var notifyMentions by mutableStateOf(initialNotifyMentions)
        internal set
    var notifyPrivateMessages by mutableStateOf(initialNotifyPrivateMessages)
        internal set
    var notifyRedditNotices by mutableStateOf(initialNotifyRedditNotices)
        internal set
    var notifyModmail by mutableStateOf(initialNotifyModmail)
        internal set
    var drawerCommunitiesMode by mutableStateOf(initialDrawerCommunitiesMode)
        internal set
    var drawerRecentCommunities by mutableStateOf(initialDrawerRecentCommunities)
    var backTwiceToExit by mutableStateOf(initialBackTwiceToExit)
        internal set
    var filteredPostsSummary by mutableStateOf(initialFilteredPostsSummary)
        internal set
    var savedSearchesEnabled by mutableStateOf(initialSavedSearchesEnabled)
        internal set
    var scrollPositionIndicator by mutableStateOf(initialScrollPositionIndicator)
        internal set
    var randomNsfwCommunityPlacement by mutableStateOf(initialRandomNsfwCommunityPlacement)
        internal set
    var pinnedPostsPresentation by mutableStateOf(initialPinnedPostsPresentation)
        internal set
    var postLayout by mutableStateOf(initialPostLayout)
        internal set
    var listThumbnailSide by mutableStateOf(initialListThumbnailSide)
        internal set
    var communityDiscoveryLayout by mutableStateOf(initialCommunityDiscoveryLayout)
        internal set
    var requireDeviceUnlockForHiddenSubscriptions by mutableStateOf(
        initialRequireDeviceUnlockForHiddenSubscriptions,
    )
        internal set
    var navigationLabelMode by mutableStateOf(initialNavigationLabelMode)
    var tabletLayoutMode by mutableStateOf(initialTabletLayoutMode)
        internal set
    var topBarBehavior by mutableStateOf(initialTopBarBehavior)
    var bottomBarBehavior by mutableStateOf(initialBottomBarBehavior)
    var mediaLabelsVisible by mutableStateOf(initialMediaLabelsVisible)
    var mediaCaptionMode by mutableStateOf(initialMediaCaptionMode)
        internal set
    var fullscreenCaptionMode by mutableStateOf(initialFullscreenCaptionMode)
        internal set
    var subredditLabelStyle by mutableStateOf(initialSubredditLabelStyle)
    var openKeyboardOnSearch by mutableStateOf(initialOpenKeyboardOnSearch)
    var videoAutoplay by mutableStateOf(initialVideoAutoplay)
    var startVideosMuted by mutableStateOf(initialStartVideosMuted)
    var loopVideos by mutableStateOf(initialLoopVideos)
    var wifiVideoQuality by mutableStateOf(initialWifiVideoQuality)
    var mobileVideoQuality by mutableStateOf(initialMobileVideoQuality)
    var videoTimelineSize by mutableStateOf(initialVideoTimelineSize)
    var playbackOptionsPlacement by mutableStateOf(initialPlaybackOptionsPlacement)
    var videoSubtitleDefault by mutableStateOf(initialVideoSubtitleDefault)
    var videoAudioDefault by mutableStateOf(initialVideoAudioDefault)
    var videoSpeedControls by mutableStateOf(initialVideoSpeedControls)
    var videoDoubleTapSeek by mutableStateOf(initialVideoDoubleTapSeek)
    var videoHoldForDoubleSpeed by mutableStateOf(initialVideoHoldForDoubleSpeed)
    var videoPinchToZoom by mutableStateOf(initialVideoPinchToZoom)
    var videoPictureInPicture by mutableStateOf(initialVideoPictureInPicture)
    var videoRotateFullscreen by mutableStateOf(initialVideoRotateFullscreen)
    var videoTechnicalDetails by mutableStateOf(initialVideoTechnicalDetails)
    var landscapeVideoGesturesEnabled by mutableStateOf(initialLandscapeVideoGesturesEnabled)
        internal set
    var landscapeBrightnessGestureEnabled by mutableStateOf(initialLandscapeBrightnessGestureEnabled)
        internal set
    var landscapeVolumeGestureEnabled by mutableStateOf(initialLandscapeVolumeGestureEnabled)
        internal set
    var reduceMotion by mutableStateOf(initialReduceMotion)

    // SETTINGSREVIEW01_REDUCE_MOTION_FOLLOWS_ANDROID_V1 (owner 2026-10-17): Android's
    // "Remove animations" (animator duration scale 0) reduces motion in subshell
    // too, whatever the reader's own switch says; read by SystemMotionEffect.
    var systemRemovesAnimations by mutableStateOf(false)
    val motionReduced: Boolean
        get() = reduceMotion || systemRemovesAnimations
    var feedPreviewState by mutableStateOf(initialFeedPreviewState)
    val resolvedDrawerSide: InteractionSide
        get() = drawerSidePreference.resolve(interactionSide)
    val commentActionPlacement: CommentActionPlacement
        get() = commentActionPreference.resolve(interactionSide)

    fun previewInteractionSide(side: InteractionSide) {
        interactionSide = side
    }

    fun confirmInteractionSide() {
        // SETTINGSREVIEW01_ONBOARDING_SIDE_MOVES_THE_MENU_V1: the launch-time
        // migration made the drawer side explicit before the reader was asked,
        // so the answer sets the menu side too, as the dialog promises.
        drawerSidePreference = explicitDrawerSidePreference(DrawerSidePreference.FOLLOW, interactionSide)
        persistInteractionSidePreferences()
        commentActionOnboardingVisible = false
    }

    fun updateInteractionSide(side: InteractionSide) {
        interactionSide = side
        persistInteractionSidePreferences()
    }

    fun updateDrawerSidePreference(preference: DrawerSidePreference) {
        drawerSidePreference = preference
        // Only the drawer side: the launch-time FOLLOW -> explicit migration
        // calls this before the user has been asked which side to favor, and
        // a persisted INTERACTION_SIDE (or legacy placement) is what marks
        // the side as configured on the next launch.
        persistPreference(SubshellSettings.DRAWER_SIDE_PREFERENCE, drawerSidePreference.name)
    }

    fun updateCommentActionPreference(preference: CommentActionPreference) {
        commentActionPreference = preference
        persistInteractionSidePreferences()
    }

    private fun persistInteractionSidePreferences() {
        persistPreference(SubshellSettings.INTERACTION_SIDE, interactionSide.name)
        persistPreference(SubshellSettings.DRAWER_SIDE_PREFERENCE, drawerSidePreference.name)
        persistPreference(SubshellSettings.COMMENT_ACTION_PREFERENCE, commentActionPreference.name)
        persistPreference(
            SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT,
            commentActionPlacement.name,
        )
    }

    fun updateCommentTimeFormat(format: CommentTimeFormat) {
        commentTimeFormat = format
        persistPreference(SubshellSettings.COMMENT_TIME_FORMAT, format.name)
    }

    fun updateCommentCollapseMode(mode: CommentCollapseMode) {
        commentCollapseMode = mode
        persistPreference(SubshellSettings.COMMENT_COLLAPSE_MODE, mode.name)
    }

    fun updateCommentHierarchyStyle(style: CommentHierarchyStyle) {
        commentHierarchyStyle = style
        persistPreference(SubshellSettings.COMMENT_HIERARCHY_STYLE, style.name)
    }

    fun updateSwipeActionsEnabled(enabled: Boolean) {
        swipeActionsEnabled = enabled
        persistPreference(SubshellSettings.SWIPE_ACTIONS_ENABLED, enabled.toString())
    }

    fun updatePostSwipeLeftAction(action: PostSwipeAction) {
        postSwipeLeftAction = action
        persistPreference(SubshellSettings.POST_SWIPE_LEFT_ACTION, action.name)
    }

    fun updatePostSwipeRightAction(action: PostSwipeAction) {
        postSwipeRightAction = action
        persistPreference(SubshellSettings.POST_SWIPE_RIGHT_ACTION, action.name)
    }

    fun updateCommentSwipeLeftAction(action: CommentSwipeAction) {
        commentSwipeLeftAction = action
        persistPreference(SubshellSettings.COMMENT_SWIPE_LEFT_ACTION, action.name)
    }

    fun updateCommentSwipeRightAction(action: CommentSwipeAction) {
        commentSwipeRightAction = action
        persistPreference(SubshellSettings.COMMENT_SWIPE_RIGHT_ACTION, action.name)
    }

    fun updateHighlightNewComments(enabled: Boolean) {
        highlightNewComments = enabled
        persistPreference(SubshellSettings.HIGHLIGHT_NEW_COMMENTS, enabled.toString())
    }

    // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
    fun updateShowCommentAvatars(enabled: Boolean) {
        showCommentAvatars = enabled
        persistPreference(SubshellSettings.SHOW_COMMENT_AVATARS, enabled.toString())
    }

    fun updateTopLevelCommentJumpButton(enabled: Boolean) {
        topLevelCommentJumpButton = enabled
        persistPreference(SubshellSettings.TOP_LEVEL_COMMENT_JUMP_BUTTON, enabled.toString())
    }

    fun updateUseSuggestedCommentSort(enabled: Boolean) {
        useSuggestedCommentSort = enabled
        persistPreference(SubshellSettings.USE_SUGGESTED_COMMENT_SORT, enabled.toString())
    }

    fun updateShowUnreadMarker(enabled: Boolean) {
        showUnreadMarker = enabled
        persistPreference(SubshellSettings.SHOW_UNREAD_MARKER, enabled.toString())
    }

    fun updateInboxNotificationsEnabled(enabled: Boolean) {
        inboxNotificationsEnabled = enabled
        persistPreference(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, enabled.toString())
    }

    fun updateNotifyReplies(enabled: Boolean) {
        notifyReplies = enabled
        persistPreference(SubshellSettings.NOTIFY_REPLIES, enabled.toString())
    }

    fun updateNotifyMentions(enabled: Boolean) {
        notifyMentions = enabled
        persistPreference(SubshellSettings.NOTIFY_MENTIONS, enabled.toString())
    }

    fun updateNotifyPrivateMessages(enabled: Boolean) {
        notifyPrivateMessages = enabled
        persistPreference(SubshellSettings.NOTIFY_PRIVATE_MESSAGES, enabled.toString())
    }

    fun updateNotifyRedditNotices(enabled: Boolean) {
        notifyRedditNotices = enabled
        persistPreference(SubshellSettings.NOTIFY_REDDIT_NOTICES, enabled.toString())
    }

    fun updateNotifyModmail(enabled: Boolean) {
        notifyModmail = enabled
        persistPreference(SubshellSettings.NOTIFY_MODMAIL, enabled.toString())
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        persistPreference(SubshellSettings.THEME_MODE, mode.name)
    }

    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        persistPreference(SubshellSettings.DYNAMIC_COLOR, enabled.toString())
    }

    fun updatePureBlackDarkTheme(enabled: Boolean) {
        pureBlackDarkTheme = enabled
        persistPreference(SubshellSettings.PURE_BLACK_DARK_THEME, enabled.toString())
    }

    fun updateAppTextScale(scale: AppTextScale) {
        appTextScale = scale
        persistPreference(SubshellSettings.APP_TEXT_SCALE, scale.name)
    }

    fun updateAppUiScale(scale: AppUiScale) {
        appUiScale = scale
        persistPreference(SubshellSettings.APP_UI_SCALE, scale.name)
    }

    fun updateVoteColorPalette(palette: VoteColorPalette) {
        voteColorPalette = palette
        persistPreference(SubshellSettings.VOTE_COLOR_PALETTE, palette.name)
    }

    fun updateVoteControlMode(mode: VoteControlMode) {
        voteControlMode = mode
        persistPreference(SubshellSettings.VOTE_CONTROL_MODE, mode.name)
    }

    fun updateCodeFont(font: CodeFont) {
        codeFont = font
        persistPreference(SubshellSettings.CODE_FONT, font.name)
    }

    fun updateDrawerCommunitiesMode(mode: DrawerCommunitiesMode) {
        drawerCommunitiesMode = mode
        persistPreference(SubshellSettings.DRAWER_COMMUNITIES_MODE, mode.name)
    }

    fun updateDrawerRecentCommunities(visible: Boolean) {
        drawerRecentCommunities = visible
        persistPreference(SubshellSettings.DRAWER_RECENT_COMMUNITIES, visible.toString())
    }

    fun updateFilteredPostsSummary(enabled: Boolean) {
        filteredPostsSummary = enabled
        persistPreference(SubshellSettings.FILTERED_POSTS_SUMMARY, enabled.toString())
    }

    fun updateSavedSearchesEnabled(enabled: Boolean) {
        savedSearchesEnabled = enabled
        persistPreference(SubshellSettings.SAVED_SEARCHES_ENABLED, enabled.toString())
    }

    fun updateScrollPositionIndicator(enabled: Boolean) {
        scrollPositionIndicator = enabled
        persistPreference(SubshellSettings.SCROLL_POSITION_INDICATOR, enabled.toString())
    }

    fun updateRandomNsfwCommunityPlacement(placement: app.subshell.prototype.ui.RandomNsfwCommunityPlacement) {
        randomNsfwCommunityPlacement = placement
        persistPreference(SubshellSettings.RANDOM_NSFW_COMMUNITY_PLACEMENT, placement.name)
    }

    fun updatePinnedPostsPresentation(presentation: app.subshell.prototype.ui.PinnedPostsPresentation) {
        pinnedPostsPresentation = presentation
        persistPreference(SubshellSettings.PINNED_POSTS_PRESENTATION, presentation.name)
    }

    fun updatePostLayout(layout: app.subshell.prototype.ui.PostLayout) {
        postLayout = layout
        persistPreference(SubshellSettings.POST_LAYOUT, layout.name)
    }

    fun updateListThumbnailSide(side: app.subshell.prototype.ui.ListThumbnailSide) {
        listThumbnailSide = side
        persistPreference(SubshellSettings.LIST_THUMBNAIL_SIDE, side.name)
    }

    fun updateBackTwiceToExit(enabled: Boolean) {
        backTwiceToExit = enabled
        persistPreference(SubshellSettings.BACK_TWICE_TO_EXIT, enabled.toString())
    }

    fun updateCommunityDiscoveryLayout(layout: CommunityDiscoveryLayout) {
        communityDiscoveryLayout = layout
        persistPreference(SubshellSettings.COMMUNITY_DISCOVERY_LAYOUT, layout.name)
    }

    fun updateRequireDeviceUnlockForHiddenSubscriptions(required: Boolean) {
        requireDeviceUnlockForHiddenSubscriptions = required
        persistPreference(
            REQUIRE_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS,
            required.toString(),
        )
    }

    fun updateNavigationLabelMode(mode: NavigationLabelMode) {
        navigationLabelMode = mode
        persistPreference(SubshellSettings.NAVIGATION_LABEL_MODE, mode.name)
    }

    fun updateTabletLayoutMode(mode: TabletLayoutMode) {
        tabletLayoutMode = mode
        persistPreference(SubshellSettings.TABLET_LAYOUT_MODE, mode.name)
    }

    fun updateMediaLabelsVisible(visible: Boolean) {
        mediaLabelsVisible = visible
        persistPreference("media_labels_visible", visible.toString())
    }

    fun updateMediaCaptionMode(mode: MediaCaptionMode) {
        mediaCaptionMode = mode
        persistPreference(SubshellSettings.MEDIA_CAPTION_MODE, mode.name)
    }

    fun updateFullscreenCaptionMode(mode: FullscreenCaptionMode) {
        fullscreenCaptionMode = mode
        persistPreference(SubshellSettings.FULLSCREEN_CAPTION_MODE, mode.name)
    }

    fun updateSubredditLabelStyle(style: SubredditLabelStyle) {
        subredditLabelStyle = style
        persistPreference("subreddit_label_style", style.name)
    }

    fun updateOpenKeyboardOnSearch(enabled: Boolean) {
        openKeyboardOnSearch = enabled
        persistPreference("open_keyboard_on_search", enabled.toString())
    }

    fun updateLoopVideos(enabled: Boolean) {
        loopVideos = enabled
        persistPreference(SubshellSettings.LOOP_VIDEOS, enabled.toString())
    }

    fun updateWifiVideoQuality(value: VideoQuality) {
        wifiVideoQuality = value
        persistPreference(SubshellSettings.VIDEO_QUALITY_WIFI, value.name)
    }

    fun updateMobileVideoQuality(value: VideoQuality) {
        mobileVideoQuality = value
        persistPreference(SubshellSettings.VIDEO_QUALITY_MOBILE, value.name)
    }

    fun updateVideoSpeedControls(enabled: Boolean) {
        videoSpeedControls = enabled
        persistPreference(SubshellSettings.VIDEO_SPEED_CONTROLS, enabled.toString())
    }

    fun updateVideoDoubleTapSeek(enabled: Boolean) {
        videoDoubleTapSeek = enabled
        persistPreference(SubshellSettings.VIDEO_DOUBLE_TAP_SEEK, enabled.toString())
    }

    fun updateVideoHoldForDoubleSpeed(enabled: Boolean) {
        videoHoldForDoubleSpeed = enabled
        persistPreference(SubshellSettings.VIDEO_HOLD_FOR_DOUBLE_SPEED, enabled.toString())
    }

    fun updateVideoPinchToZoom(enabled: Boolean) {
        videoPinchToZoom = enabled
        persistPreference(SubshellSettings.VIDEO_PINCH_TO_ZOOM, enabled.toString())
    }

    fun updateVideoPictureInPicture(enabled: Boolean) {
        videoPictureInPicture = enabled
        persistPreference(SubshellSettings.VIDEO_PICTURE_IN_PICTURE, enabled.toString())
    }

    fun updateVideoRotateFullscreen(enabled: Boolean) {
        videoRotateFullscreen = enabled
        persistPreference(SubshellSettings.VIDEO_ROTATE_FULLSCREEN, enabled.toString())
    }

    fun updateVideoTechnicalDetails(enabled: Boolean) {
        videoTechnicalDetails = enabled
        persistPreference(SubshellSettings.VIDEO_TECHNICAL_DETAILS, enabled.toString())
    }

    fun updateVideoSubtitleDefault(value: VideoSubtitleDefault) {
        videoSubtitleDefault = value
        persistPreference(SubshellSettings.VIDEO_SUBTITLE_DEFAULT, value.name)
    }

    fun updateVideoAudioDefault(value: VideoAudioDefault) {
        videoAudioDefault = value
        persistPreference(SubshellSettings.VIDEO_AUDIO_DEFAULT, value.name)
    }

    fun updatePlaybackOptionsPlacement(value: PlaybackOptionsPlacement) {
        playbackOptionsPlacement = value
        persistPreference(SubshellSettings.PLAYBACK_OPTIONS_PLACEMENT, value.name)
    }

    fun updateVideoTimelineSize(value: VideoTimelineSize) {
        videoTimelineSize = value
        persistPreference(SubshellSettings.VIDEO_TIMELINE_SIZE, value.name)
    }

    fun updateLandscapeVideoGesturesEnabled(enabled: Boolean) {
        landscapeVideoGesturesEnabled = enabled
        persistPreference(SubshellSettings.LANDSCAPE_VIDEO_GESTURES_ENABLED, enabled.toString())
    }

    fun updateLandscapeBrightnessGestureEnabled(enabled: Boolean) {
        landscapeBrightnessGestureEnabled = enabled
        persistPreference(SubshellSettings.LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED, enabled.toString())
    }

    fun updateLandscapeVolumeGestureEnabled(enabled: Boolean) {
        landscapeVolumeGestureEnabled = enabled
        persistPreference(SubshellSettings.LANDSCAPE_VOLUME_GESTURE_ENABLED, enabled.toString())
    }

    fun updateReduceMotion(enabled: Boolean) {
        reduceMotion = enabled
        persistPreference("reduce_motion", enabled.toString())
    }

    fun updateFeedPreviewState(value: FeedPreviewState) {
        feedPreviewState = value
        persistPreference(SubshellSettings.FEED_PREVIEW_STATE, value.name)
    }
}
