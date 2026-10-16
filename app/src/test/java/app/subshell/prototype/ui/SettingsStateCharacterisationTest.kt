package app.subshell.prototype.ui

import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation of the preference state inside SubshellState as it
 * behaves today, written before it moves to SettingsState. Current
 * behaviour, not a specification.
 */
class SettingsStateCharacterisationTest {
    private class Setter(
        val name: String,
        val samples: List<Any>,
        val apply: (SubshellState, Any) -> Unit,
        val read: (SubshellState) -> Any,
        val persisted: ((Any) -> Pair<String, String>)?,
    )

    private val setters = listOf(
        Setter("updateInteractionSide", InteractionSide.entries, { s, v -> s.updateInteractionSide(v as InteractionSide) }, { it.interactionSide }, null),
        Setter("updateDrawerSidePreference", DrawerSidePreference.entries, { s, v -> s.updateDrawerSidePreference(v as DrawerSidePreference) }, { it.drawerSidePreference }, null),
        Setter("updateCommentActionPreference", CommentActionPreference.entries, { s, v -> s.updateCommentActionPreference(v as CommentActionPreference) }, { it.commentActionPreference }, null),
        Setter("updateCommentTimeFormat", CommentTimeFormat.entries, { s, v -> s.updateCommentTimeFormat(v as CommentTimeFormat) }, { it.commentTimeFormat }, { v -> SubshellSettings.COMMENT_TIME_FORMAT to (v as CommentTimeFormat).name }),
        Setter("updateCommentCollapseMode", CommentCollapseMode.entries, { s, v -> s.updateCommentCollapseMode(v as CommentCollapseMode) }, { it.commentCollapseMode }, { v -> SubshellSettings.COMMENT_COLLAPSE_MODE to (v as CommentCollapseMode).name }),
        Setter("updateCommentHierarchyStyle", CommentHierarchyStyle.entries, { s, v -> s.updateCommentHierarchyStyle(v as CommentHierarchyStyle) }, { it.commentHierarchyStyle }, { v -> SubshellSettings.COMMENT_HIERARCHY_STYLE to (v as CommentHierarchyStyle).name }),
        Setter("updateSwipeActionsEnabled", listOf(true, false), { s, v -> s.updateSwipeActionsEnabled(v as Boolean) }, { it.swipeActionsEnabled }, { v -> SubshellSettings.SWIPE_ACTIONS_ENABLED to v.toString() }),
        Setter("updatePostSwipeLeftAction", PostSwipeAction.entries, { s, v -> s.updatePostSwipeLeftAction(v as PostSwipeAction) }, { it.postSwipeLeftAction }, { v -> SubshellSettings.POST_SWIPE_LEFT_ACTION to (v as PostSwipeAction).name }),
        Setter("updatePostSwipeRightAction", PostSwipeAction.entries, { s, v -> s.updatePostSwipeRightAction(v as PostSwipeAction) }, { it.postSwipeRightAction }, { v -> SubshellSettings.POST_SWIPE_RIGHT_ACTION to (v as PostSwipeAction).name }),
        Setter("updateCommentSwipeLeftAction", CommentSwipeAction.entries, { s, v -> s.updateCommentSwipeLeftAction(v as CommentSwipeAction) }, { it.commentSwipeLeftAction }, { v -> SubshellSettings.COMMENT_SWIPE_LEFT_ACTION to (v as CommentSwipeAction).name }),
        Setter("updateCommentSwipeRightAction", CommentSwipeAction.entries, { s, v -> s.updateCommentSwipeRightAction(v as CommentSwipeAction) }, { it.commentSwipeRightAction }, { v -> SubshellSettings.COMMENT_SWIPE_RIGHT_ACTION to (v as CommentSwipeAction).name }),
        Setter("updateHighlightNewComments", listOf(true, false), { s, v -> s.updateHighlightNewComments(v as Boolean) }, { it.highlightNewComments }, { v -> SubshellSettings.HIGHLIGHT_NEW_COMMENTS to v.toString() }),
        Setter("updateShowCommentAvatars", listOf(false, true), { s, v -> s.updateShowCommentAvatars(v as Boolean) }, { it.showCommentAvatars }, { v -> SubshellSettings.SHOW_COMMENT_AVATARS to v.toString() }),
        Setter("updateTopLevelCommentJumpButton", listOf(true, false), { s, v -> s.updateTopLevelCommentJumpButton(v as Boolean) }, { it.topLevelCommentJumpButton }, { v -> SubshellSettings.TOP_LEVEL_COMMENT_JUMP_BUTTON to v.toString() }),
        Setter("updateUseSuggestedCommentSort", listOf(true, false), { s, v -> s.updateUseSuggestedCommentSort(v as Boolean) }, { it.useSuggestedCommentSort }, { v -> SubshellSettings.USE_SUGGESTED_COMMENT_SORT to v.toString() }),
        Setter("updateShowUnreadMarker", listOf(true, false), { s, v -> s.updateShowUnreadMarker(v as Boolean) }, { it.showUnreadMarker }, { v -> SubshellSettings.SHOW_UNREAD_MARKER to v.toString() }),
        Setter("updateInboxNotificationsEnabled", listOf(true, false), { s, v -> s.updateInboxNotificationsEnabled(v as Boolean) }, { it.inboxNotificationsEnabled }, { v -> SubshellSettings.INBOX_NOTIFICATIONS_ENABLED to v.toString() }),
        Setter("updateNotifyReplies", listOf(true, false), { s, v -> s.updateNotifyReplies(v as Boolean) }, { it.notifyReplies }, { v -> SubshellSettings.NOTIFY_REPLIES to v.toString() }),
        Setter("updateNotifyMentions", listOf(true, false), { s, v -> s.updateNotifyMentions(v as Boolean) }, { it.notifyMentions }, { v -> SubshellSettings.NOTIFY_MENTIONS to v.toString() }),
        Setter("updateNotifyPrivateMessages", listOf(true, false), { s, v -> s.updateNotifyPrivateMessages(v as Boolean) }, { it.notifyPrivateMessages }, { v -> SubshellSettings.NOTIFY_PRIVATE_MESSAGES to v.toString() }),
        Setter("updateNotifyRedditNotices", listOf(true, false), { s, v -> s.updateNotifyRedditNotices(v as Boolean) }, { it.notifyRedditNotices }, { v -> SubshellSettings.NOTIFY_REDDIT_NOTICES to v.toString() }),
        Setter("updateNotifyModmail", listOf(true, false), { s, v -> s.updateNotifyModmail(v as Boolean) }, { it.notifyModmail }, { v -> SubshellSettings.NOTIFY_MODMAIL to v.toString() }),
        Setter("updateThemeMode", ThemeMode.entries, { s, v -> s.updateThemeMode(v as ThemeMode) }, { it.themeMode }, { v -> SubshellSettings.THEME_MODE to (v as ThemeMode).name }),
        Setter("updateDynamicColor", listOf(true, false), { s, v -> s.updateDynamicColor(v as Boolean) }, { it.dynamicColor }, { v -> SubshellSettings.DYNAMIC_COLOR to v.toString() }),
        Setter("updatePureBlackDarkTheme", listOf(true, false), { s, v -> s.updatePureBlackDarkTheme(v as Boolean) }, { it.pureBlackDarkTheme }, { v -> SubshellSettings.PURE_BLACK_DARK_THEME to v.toString() }),
        Setter("updateAppTextScale", AppTextScale.entries, { s, v -> s.updateAppTextScale(v as AppTextScale) }, { it.appTextScale }, { v -> SubshellSettings.APP_TEXT_SCALE to (v as AppTextScale).name }),
        Setter("updateVoteColorPalette", VoteColorPalette.entries, { s, v -> s.updateVoteColorPalette(v as VoteColorPalette) }, { it.voteColorPalette }, { v -> SubshellSettings.VOTE_COLOR_PALETTE to (v as VoteColorPalette).name }),
        Setter("updateVoteControlMode", VoteControlMode.entries, { s, v -> s.updateVoteControlMode(v as VoteControlMode) }, { it.voteControlMode }, { v -> SubshellSettings.VOTE_CONTROL_MODE to (v as VoteControlMode).name }),
        Setter("updateCodeFont", CodeFont.entries, { s, v -> s.updateCodeFont(v as CodeFont) }, { it.codeFont }, { v -> SubshellSettings.CODE_FONT to (v as CodeFont).name }),
        Setter("updateDrawerCommunitiesMode", DrawerCommunitiesMode.entries, { s, v -> s.updateDrawerCommunitiesMode(v as DrawerCommunitiesMode) }, { it.drawerCommunitiesMode }, { v -> SubshellSettings.DRAWER_COMMUNITIES_MODE to (v as DrawerCommunitiesMode).name }),
        Setter("updateDrawerRecentCommunities", listOf(true, false), { s, v -> s.updateDrawerRecentCommunities(v as Boolean) }, { it.drawerRecentCommunities }, { v -> SubshellSettings.DRAWER_RECENT_COMMUNITIES to v.toString() }),
        Setter("updateBackTwiceToExit", listOf(true, false), { s, v -> s.updateBackTwiceToExit(v as Boolean) }, { it.backTwiceToExit }, { v -> SubshellSettings.BACK_TWICE_TO_EXIT to v.toString() }),
        Setter("updateCommunityDiscoveryLayout", CommunityDiscoveryLayout.entries, { s, v -> s.updateCommunityDiscoveryLayout(v as CommunityDiscoveryLayout) }, { it.communityDiscoveryLayout }, { v -> SubshellSettings.COMMUNITY_DISCOVERY_LAYOUT to (v as CommunityDiscoveryLayout).name }),
        Setter("updateRequireDeviceUnlockForHiddenSubscriptions", listOf(true, false), { s, v -> s.updateRequireDeviceUnlockForHiddenSubscriptions(v as Boolean) }, { it.requireDeviceUnlockForHiddenSubscriptions }, { v -> "require_device_unlock_hidden_subscriptions" to v.toString() }),
        Setter("updateNavigationLabelMode", NavigationLabelMode.entries, { s, v -> s.updateNavigationLabelMode(v as NavigationLabelMode) }, { it.navigationLabelMode }, { v -> SubshellSettings.NAVIGATION_LABEL_MODE to (v as NavigationLabelMode).name }),
        Setter("updateTabletLayoutMode", TabletLayoutMode.entries, { s, v -> s.updateTabletLayoutMode(v as TabletLayoutMode) }, { it.tabletLayoutMode }, { v -> SubshellSettings.TABLET_LAYOUT_MODE to (v as TabletLayoutMode).name }),
        Setter("updateTopBarBehavior", ChromeBehavior.entries, { s, v -> s.updateTopBarBehavior(v as ChromeBehavior) }, { it.topBarBehavior }, { v -> "top_bar_behavior" to (v as ChromeBehavior).name }),
        Setter("updateBottomBarBehavior", ChromeBehavior.entries, { s, v -> s.updateBottomBarBehavior(v as ChromeBehavior) }, { it.bottomBarBehavior }, { v -> "bottom_bar_behavior" to (v as ChromeBehavior).name }),
        Setter("updateMediaLabelsVisible", listOf(true, false), { s, v -> s.updateMediaLabelsVisible(v as Boolean) }, { it.mediaLabelsVisible }, { v -> "media_labels_visible" to v.toString() }),
        Setter("updateMediaCaptionMode", MediaCaptionMode.entries, { s, v -> s.updateMediaCaptionMode(v as MediaCaptionMode) }, { it.mediaCaptionMode }, { v -> SubshellSettings.MEDIA_CAPTION_MODE to (v as MediaCaptionMode).name }),
        Setter("updateFullscreenCaptionMode", FullscreenCaptionMode.entries, { s, v -> s.updateFullscreenCaptionMode(v as FullscreenCaptionMode) }, { it.fullscreenCaptionMode }, { v -> SubshellSettings.FULLSCREEN_CAPTION_MODE to (v as FullscreenCaptionMode).name }),
        Setter("updateSubredditLabelStyle", SubredditLabelStyle.entries, { s, v -> s.updateSubredditLabelStyle(v as SubredditLabelStyle) }, { it.subredditLabelStyle }, { v -> "subreddit_label_style" to (v as SubredditLabelStyle).name }),
        Setter("updateOpenKeyboardOnSearch", listOf(true, false), { s, v -> s.updateOpenKeyboardOnSearch(v as Boolean) }, { it.openKeyboardOnSearch }, { v -> "open_keyboard_on_search" to v.toString() }),
        Setter("updateVideoAutoplay", VideoAutoplay.entries, { s, v -> s.updateVideoAutoplay(v as VideoAutoplay) }, { it.videoAutoplay }, { v -> "video_autoplay" to (v as VideoAutoplay).name }),
        Setter("updateStartVideosMuted", listOf(true, false), { s, v -> s.updateStartVideosMuted(v as Boolean) }, { it.startVideosMuted }, { v -> "start_videos_muted" to v.toString() }),
        Setter("updateLoopVideos", listOf(true, false), { s, v -> s.updateLoopVideos(v as Boolean) }, { it.loopVideos }, { v -> SubshellSettings.LOOP_VIDEOS to v.toString() }),
        Setter("updateWifiVideoQuality", VideoQuality.entries, { s, v -> s.updateWifiVideoQuality(v as VideoQuality) }, { it.wifiVideoQuality }, { v -> SubshellSettings.VIDEO_QUALITY_WIFI to (v as VideoQuality).name }),
        Setter("updateMobileVideoQuality", VideoQuality.entries, { s, v -> s.updateMobileVideoQuality(v as VideoQuality) }, { it.mobileVideoQuality }, { v -> SubshellSettings.VIDEO_QUALITY_MOBILE to (v as VideoQuality).name }),
        Setter("updateVideoSpeedControls", listOf(true, false), { s, v -> s.updateVideoSpeedControls(v as Boolean) }, { it.videoSpeedControls }, { v -> SubshellSettings.VIDEO_SPEED_CONTROLS to v.toString() }),
        Setter("updateVideoDoubleTapSeek", listOf(true, false), { s, v -> s.updateVideoDoubleTapSeek(v as Boolean) }, { it.videoDoubleTapSeek }, { v -> SubshellSettings.VIDEO_DOUBLE_TAP_SEEK to v.toString() }),
        Setter("updateVideoHoldForDoubleSpeed", listOf(true, false), { s, v -> s.updateVideoHoldForDoubleSpeed(v as Boolean) }, { it.videoHoldForDoubleSpeed }, { v -> SubshellSettings.VIDEO_HOLD_FOR_DOUBLE_SPEED to v.toString() }),
        Setter("updateVideoPictureInPicture", listOf(true, false), { s, v -> s.updateVideoPictureInPicture(v as Boolean) }, { it.videoPictureInPicture }, { v -> SubshellSettings.VIDEO_PICTURE_IN_PICTURE to v.toString() }),
        Setter("updateVideoRotateFullscreen", listOf(true, false), { s, v -> s.updateVideoRotateFullscreen(v as Boolean) }, { it.videoRotateFullscreen }, { v -> SubshellSettings.VIDEO_ROTATE_FULLSCREEN to v.toString() }),
        Setter("updateVideoTechnicalDetails", listOf(true, false), { s, v -> s.updateVideoTechnicalDetails(v as Boolean) }, { it.videoTechnicalDetails }, { v -> SubshellSettings.VIDEO_TECHNICAL_DETAILS to v.toString() }),
        Setter("updateVideoSubtitleDefault", VideoSubtitleDefault.entries, { s, v -> s.updateVideoSubtitleDefault(v as VideoSubtitleDefault) }, { it.videoSubtitleDefault }, { v -> SubshellSettings.VIDEO_SUBTITLE_DEFAULT to (v as VideoSubtitleDefault).name }),
        Setter("updateVideoAudioDefault", VideoAudioDefault.entries, { s, v -> s.updateVideoAudioDefault(v as VideoAudioDefault) }, { it.videoAudioDefault }, { v -> SubshellSettings.VIDEO_AUDIO_DEFAULT to (v as VideoAudioDefault).name }),
        Setter("updatePlaybackOptionsPlacement", PlaybackOptionsPlacement.entries, { s, v -> s.updatePlaybackOptionsPlacement(v as PlaybackOptionsPlacement) }, { it.playbackOptionsPlacement }, { v -> SubshellSettings.PLAYBACK_OPTIONS_PLACEMENT to (v as PlaybackOptionsPlacement).name }),
        Setter("updateVideoTimelineSize", VideoTimelineSize.entries, { s, v -> s.updateVideoTimelineSize(v as VideoTimelineSize) }, { it.videoTimelineSize }, { v -> SubshellSettings.VIDEO_TIMELINE_SIZE to (v as VideoTimelineSize).name }),
        Setter("updateLandscapeVideoGesturesEnabled", listOf(true, false), { s, v -> s.updateLandscapeVideoGesturesEnabled(v as Boolean) }, { it.landscapeVideoGesturesEnabled }, { v -> SubshellSettings.LANDSCAPE_VIDEO_GESTURES_ENABLED to v.toString() }),
        Setter("updateLandscapeBrightnessGestureEnabled", listOf(true, false), { s, v -> s.updateLandscapeBrightnessGestureEnabled(v as Boolean) }, { it.landscapeBrightnessGestureEnabled }, { v -> SubshellSettings.LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED to v.toString() }),
        Setter("updateLandscapeVolumeGestureEnabled", listOf(true, false), { s, v -> s.updateLandscapeVolumeGestureEnabled(v as Boolean) }, { it.landscapeVolumeGestureEnabled }, { v -> SubshellSettings.LANDSCAPE_VOLUME_GESTURE_ENABLED to v.toString() }),
        Setter("updateReduceMotion", listOf(true, false), { s, v -> s.updateReduceMotion(v as Boolean) }, { it.reduceMotion }, { v -> "reduce_motion" to v.toString() }),
        Setter("updateFeedPreviewState", FeedPreviewState.entries, { s, v -> s.updateFeedPreviewState(v as FeedPreviewState) }, { it.feedPreviewState }, { v -> SubshellSettings.FEED_PREVIEW_STATE to (v as FeedPreviewState).name }),
    )

    @Test
    fun everySetterUpdatesItsFieldAndPersistsExactlyOnePreferenceWithTheExpectedKeyAndValue() {
        assertEquals(63, setters.size)
        setters.forEach { setter ->
            val writes = mutableListOf<Pair<String, String>>()
            val state = state(writes)
            setter.samples.forEach { value ->
                writes.clear()
                setter.apply(state, value)
                assertEquals(setter.name, value, setter.read(state))
                val expected = setter.persisted
                if (expected != null) {
                    assertEquals(setter.name, listOf(expected(value)), writes)
                }
            }
        }
    }

    @Test
    fun interactionSidePreferencesPersistTogetherExceptTheDrawerSideAndPreviewDoesNotPersist() {
        val writes = mutableListOf<Pair<String, String>>()
        val state = state(writes)
        assertTrue(state.commentActionOnboardingVisible)

        state.previewInteractionSide(InteractionSide.LEFT)
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        assertTrue(writes.isEmpty())
        assertTrue(state.commentActionOnboardingVisible)

        state.confirmInteractionSide()
        assertFalse(state.commentActionOnboardingVisible)
        assertEquals(
            listOf(
                SubshellSettings.INTERACTION_SIDE to "LEFT",
                SubshellSettings.DRAWER_SIDE_PREFERENCE to state.drawerSidePreference.name,
                SubshellSettings.COMMENT_ACTION_PREFERENCE to state.commentActionPreference.name,
                SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT to state.commentActionPlacement.name,
            ),
            writes,
        )

        writes.clear()
        state.updateDrawerSidePreference(DrawerSidePreference.entries.last())
        // The drawer side alone: this setter also runs from the launch-time
        // FOLLOW -> explicit migration, before the side has been chosen.
        assertEquals(
            listOf(SubshellSettings.DRAWER_SIDE_PREFERENCE to DrawerSidePreference.entries.last().name),
            writes,
        )
        writes.clear()
        state.updateCommentActionPreference(CommentActionPreference.entries.last())
        assertEquals(4, writes.size)
        writes.clear()
        state.updateInteractionSide(InteractionSide.RIGHT)
        assertEquals(4, writes.size)
        assertEquals(SubshellSettings.INTERACTION_SIDE to "RIGHT", writes[0])
        // Derived values follow the preferences.
        assertEquals(state.drawerSidePreference.resolve(state.interactionSide), state.resolvedDrawerSide)
        assertEquals(state.commentActionPreference.resolve(state.interactionSide), state.commentActionPlacement)
    }

    @Test
    fun initialValuesComeFromTheConstructorAndOnboardingHidesWhenTheSideWasConfigured() {
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialThemeMode = ThemeMode.entries.last(),
            initialDynamicColor = true,
            initialInteractionSideConfigured = true,
            initialInteractionSide = InteractionSide.LEFT,
            initialVideoTimelineSize = VideoTimelineSize.entries.last(),
            initialReduceMotion = true,
            initialFeedPreviewState = FeedPreviewState.entries.last(),
        )
        assertEquals(ThemeMode.entries.last(), state.themeMode)
        assertTrue(state.dynamicColor)
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        assertFalse(state.commentActionOnboardingVisible)
        assertEquals(VideoTimelineSize.entries.last(), state.videoTimelineSize)
        assertTrue(state.reduceMotion)
        assertEquals(FeedPreviewState.entries.last(), state.feedPreviewState)
        assertEquals("", state.settingsQuery)
    }

    @Test
    fun settingsQueryAndFeedPreviewStateAreWritableFromOutsideWithoutPersisting() {
        val writes = mutableListOf<Pair<String, String>>()
        val state = state(writes)
        state.settingsQuery = "video"
        state.feedPreviewState = FeedPreviewState.entries.last()
        assertEquals("video", state.settingsQuery)
        assertEquals(FeedPreviewState.entries.last(), state.feedPreviewState)
        assertTrue(writes.isEmpty())
    }

    private fun state(writes: MutableList<Pair<String, String>>): SubshellState =
        SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            persistPreference = { key, value -> writes += key to value },
        )
}
