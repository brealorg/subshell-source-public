package app.subshell.prototype

import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.backup.BackupRetention
import app.subshell.prototype.backup.BackupRetentionPolicy
import app.subshell.prototype.backup.SettingsSnapshot
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.backup.describeExternalStorageTree
import app.subshell.prototype.backup.snapshotIdsToPrune
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.CodeFont
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.CommunitySort
import app.subshell.prototype.ui.ContentFilterType
import app.subshell.prototype.ui.ContentFilters
import app.subshell.prototype.ui.DrawerSidePreference
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.FeedPreviewState
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.SubredditLabelStyle
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.VideoQuality
import app.subshell.prototype.ui.VideoTimelineSize
import app.subshell.prototype.ui.PlaybackOptionsPlacement
import app.subshell.prototype.ui.VideoAudioDefault
import app.subshell.prototype.ui.VideoSubtitleDefault
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupContractTest {
    private val customizedSettings = SubshellSettings(
        themeMode = ThemeMode.LIGHT,
        dynamicColor = true,
        pureBlackDarkTheme = true,
        appTextScale = AppTextScale.LARGER,
        voteColorPalette = VoteColorPalette.RED_BLUE,
        voteControlMode = VoteControlMode.COMPACT,
        codeFont = CodeFont.RECURSIVE_MONO_CASUAL,
        interactionSide = InteractionSide.LEFT,
        drawerSidePreference = DrawerSidePreference.RIGHT,
        commentActionPreference = CommentActionPreference.BALANCED,
        commentTimeFormat = CommentTimeFormat.LOCAL_24_HOUR,
        highlightNewComments = false,
        useSuggestedCommentSort = false,
        showUnreadMarker = true,
        showNsfwPosts = true,
        sensitiveMediaPreviewMode = SensitiveMediaPreviewMode.LOAD_ON_TAP,
        showNsfwMedia = true,
        contentFilters = ContentFilters(
            words = setOf("giveaway"),
            communities = setOf("example"),
            users = setOf("spammer"),
            domains = setOf("example.com"),
            flairs = setOf("meme"),
        ),
        communitySortPreferences = mapOf(
            "android" to CommunitySort.NEW,
            "homelab" to CommunitySort.TOP,
        ),
        listingTimePeriod = RedditListingTimePeriod.WEEK,
        drawerCommunitiesMode = DrawerCommunitiesMode.FULL,
        drawerRecentCommunities = true,
        backTwiceToExit = true,
        topLevelCommentJumpButton = true,
        navigationLabelMode = NavigationLabelMode.OFF,
        topBarBehavior = ChromeBehavior.ALWAYS_SHOW,
        bottomBarBehavior = ChromeBehavior.ALWAYS_SHOW,
        mediaLabelsVisible = true,
        mediaCaptionMode = MediaCaptionMode.BELOW_MEDIA,
        fullscreenCaptionMode = FullscreenCaptionMode.ALWAYS_VISIBLE,
        subredditLabelStyle = SubredditLabelStyle.CLASSIC,
        openKeyboardOnSearch = false,
        videoAutoplay = VideoAutoplay.WIFI_ONLY,
        startVideosMuted = false,
        loopVideos = true,
        wifiVideoQuality = VideoQuality.HIGH,
        mobileVideoQuality = VideoQuality.LOW,
        videoTimelineSize = VideoTimelineSize.LARGE,
        playbackOptionsPlacement = PlaybackOptionsPlacement.EVERYWHERE,
        videoSubtitleDefault = VideoSubtitleDefault.SYSTEM_CAPTIONS,
        videoAudioDefault = VideoAudioDefault.DEVICE_LANGUAGE,
        videoSpeedControls = true,
        videoDoubleTapSeek = true,
        videoHoldForDoubleSpeed = true,
        videoPinchToZoom = true,
        videoPictureInPicture = true,
        videoRotateFullscreen = true,
        videoTechnicalDetails = true,
        landscapeVideoGesturesEnabled = false,
        landscapeBrightnessGestureEnabled = false,
        landscapeVolumeGestureEnabled = true,
        reduceMotion = true,
    )

    @Test
    fun completeSettingsRoundTripUsesOnlyTheExplicitAllowList() {
        val stored = customizedSettings.toStorageMap()

        assertEquals(SubshellSettings.storageKeys, stored.keys)
        assertEquals(customizedSettings, SubshellSettings.fromStorageMap(stored))
        assertTrue(stored.keys.none { it.contains("token") || it.contains("account") || it.contains("cache") })
    }

    @Test
    fun olderBackupsReceiveDefaultsForSettingsAddedLater() {
        val olderBackup = customizedSettings.toStorageMap() -
            setOf(
                SubshellSettings.VIDEO_QUALITY_WIFI,
                SubshellSettings.VIDEO_QUALITY_MOBILE,
                SubshellSettings.VIDEO_TIMELINE_SIZE,
                SubshellSettings.PLAYBACK_OPTIONS_PLACEMENT,
                SubshellSettings.VIDEO_SUBTITLE_DEFAULT,
                SubshellSettings.VIDEO_AUDIO_DEFAULT,
                SubshellSettings.VIDEO_SPEED_CONTROLS,
                SubshellSettings.VIDEO_DOUBLE_TAP_SEEK,
                SubshellSettings.VIDEO_HOLD_FOR_DOUBLE_SPEED,
                SubshellSettings.VIDEO_PINCH_TO_ZOOM,
                SubshellSettings.VIDEO_PICTURE_IN_PICTURE,
                SubshellSettings.VIDEO_ROTATE_FULLSCREEN,
                SubshellSettings.VIDEO_TECHNICAL_DETAILS,
                SubshellSettings.LOOP_VIDEOS,
                SubshellSettings.LANDSCAPE_VIDEO_GESTURES_ENABLED,
                SubshellSettings.LANDSCAPE_BRIGHTNESS_GESTURE_ENABLED,
                SubshellSettings.LANDSCAPE_VOLUME_GESTURE_ENABLED,
                SubshellSettings.CODE_FONT,
                SubshellSettings.VOTE_COLOR_PALETTE,
                SubshellSettings.VOTE_CONTROL_MODE,
                SubshellSettings.INTERACTION_SIDE,
                SubshellSettings.DRAWER_SIDE_PREFERENCE,
                SubshellSettings.COMMENT_ACTION_PREFERENCE,
                SubshellSettings.COMMENT_TIME_FORMAT,
                SubshellSettings.HIGHLIGHT_NEW_COMMENTS,
                SubshellSettings.USE_SUGGESTED_COMMENT_SORT,
                SubshellSettings.SHOW_UNREAD_MARKER,
                SubshellSettings.SHOW_NSFW_POSTS,
                SubshellSettings.SENSITIVE_MEDIA_PREVIEW_MODE,
                SubshellSettings.SHOW_NSFW_MEDIA,
                SubshellSettings.COMMUNITY_SORT_PREFERENCES,
                SubshellSettings.LISTING_TIME_PERIOD,
                SubshellSettings.DRAWER_COMMUNITIES_MODE,
                SubshellSettings.DRAWER_VIEW_ALL_COMMUNITIES,
                SubshellSettings.DRAWER_RECENT_COMMUNITIES,
                SubshellSettings.BACK_TWICE_TO_EXIT,
                SubshellSettings.PURE_BLACK_DARK_THEME,
                SubshellSettings.APP_TEXT_SCALE,
                SubshellSettings.TOP_LEVEL_COMMENT_JUMP_BUTTON,
            ) - ContentFilterType.entries.map(ContentFilterType::storageKey)

        val restored = SubshellSettings.fromStorageMap(olderBackup)

        assertEquals(VideoQuality.HIGH, restored.wifiVideoQuality)
        assertEquals(VideoQuality.MEDIUM, restored.mobileVideoQuality)
        assertEquals(VideoTimelineSize.STANDARD, restored.videoTimelineSize)
        assertEquals(PlaybackOptionsPlacement.POST_AND_FULLSCREEN, restored.playbackOptionsPlacement)
        assertEquals(VideoSubtitleDefault.WHEN_MARKED, restored.videoSubtitleDefault)
        assertEquals(VideoAudioDefault.VIDEO_DEFAULT, restored.videoAudioDefault)
        assertEquals(false, restored.videoSpeedControls)
        assertEquals(false, restored.videoDoubleTapSeek)
        assertEquals(false, restored.videoHoldForDoubleSpeed)
        assertEquals(false, restored.videoPinchToZoom)
        assertEquals(false, restored.videoPictureInPicture)
        assertEquals(false, restored.videoRotateFullscreen)
        assertEquals(false, restored.videoTechnicalDetails)
        assertEquals(false, restored.loopVideos)
        assertEquals(true, restored.landscapeVideoGesturesEnabled)
        assertEquals(true, restored.landscapeBrightnessGestureEnabled)
        assertEquals(true, restored.landscapeVolumeGestureEnabled)
        assertEquals(CodeFont.JETBRAINS_MONO, restored.codeFont)
        assertEquals(VoteColorPalette.NEUTRAL, restored.voteColorPalette)
        assertEquals(VoteControlMode.COMFORTABLE, restored.voteControlMode)
        assertEquals(InteractionSide.RIGHT, restored.interactionSide)
        assertEquals(DrawerSidePreference.FOLLOW, restored.drawerSidePreference)
        assertEquals(CommentActionPreference.FOLLOW, restored.commentActionPreference)
        assertEquals(CommentTimeFormat.RELATIVE, restored.commentTimeFormat)
        assertEquals(true, restored.highlightNewComments)
        assertEquals(true, restored.useSuggestedCommentSort)
        assertEquals(true, restored.showUnreadMarker) // settings audit 2026-10-13
        assertEquals(false, restored.showNsfwPosts)
        assertEquals(SensitiveMediaPreviewMode.BLUR_AND_PRELOAD, restored.sensitiveMediaPreviewMode)
        assertEquals(false, restored.showNsfwMedia)
        assertEquals(ContentFilters(), restored.contentFilters)
        assertEquals(emptyMap<String, CommunitySort>(), restored.communitySortPreferences)
        assertEquals(RedditListingTimePeriod.ALL, restored.listingTimePeriod)
        assertEquals(DrawerCommunitiesMode.COLLAPSIBLE, restored.drawerCommunitiesMode)
        assertEquals(false, restored.drawerRecentCommunities)
        assertEquals(false, restored.backTwiceToExit)
        assertEquals(false, restored.pureBlackDarkTheme)
        assertEquals(AppTextScale.DEFAULT, restored.appTextScale)
        assertEquals(false, restored.topLevelCommentJumpButton)
        assertEquals(customizedSettings.themeMode, restored.themeMode)
    }

    @Test
    fun legacyGlobalVideoQualityMigratesToBothNetworkProfiles() {
        val legacy = customizedSettings.toStorageMap() -
            setOf(
                SubshellSettings.VIDEO_QUALITY_WIFI,
                SubshellSettings.VIDEO_QUALITY_MOBILE,
            ) + (SubshellSettings.LEGACY_VIDEO_QUALITY to VideoQuality.LOW.name)

        val restored = SubshellSettings.fromStorageMap(legacy)

        assertEquals(VideoQuality.LOW, restored.wifiVideoQuality)
        assertEquals(VideoQuality.LOW, restored.mobileVideoQuality)
        assertTrue(SubshellSettings.LEGACY_VIDEO_QUALITY !in restored.toStorageMap())
    }

    @Test
    fun legacyNavigationLabelPreferenceMigratesToEquivalentModes() {
        val current = customizedSettings.toStorageMap() - SubshellSettings.NAVIGATION_LABEL_MODE

        assertEquals(
            NavigationLabelMode.ON,
            SubshellSettings.fromStorageMap(
                current + (SubshellSettings.LEGACY_NAVIGATION_LABELS_VISIBLE to "true"),
            ).navigationLabelMode,
        )
        assertEquals(
            NavigationLabelMode.PARTIAL,
            SubshellSettings.fromStorageMap(
                current + (SubshellSettings.LEGACY_NAVIGATION_LABELS_VISIBLE to "false"),
            ).navigationLabelMode,
        )
    }


    @Test
    fun legacyCommentPlacementMigratesToInteractionSideAndFollowPreferences() {
        val withoutCurrentSideKeys = customizedSettings.toStorageMap() -
            setOf(
                SubshellSettings.INTERACTION_SIDE,
                SubshellSettings.DRAWER_SIDE_PREFERENCE,
                SubshellSettings.COMMENT_ACTION_PREFERENCE,
            )

        val left = SubshellSettings.fromStorageMap(
            withoutCurrentSideKeys +
                (SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT to CommentActionPlacement.LEFT.name),
        )
        assertEquals(InteractionSide.LEFT, left.interactionSide)
        assertEquals(DrawerSidePreference.FOLLOW, left.drawerSidePreference)
        assertEquals(CommentActionPreference.FOLLOW, left.commentActionPreference)

        val balanced = SubshellSettings.fromStorageMap(
            withoutCurrentSideKeys +
                (SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT to CommentActionPlacement.BALANCED.name),
        )
        assertEquals(InteractionSide.RIGHT, balanced.interactionSide)
        assertEquals(CommentActionPreference.BALANCED, balanced.commentActionPreference)
        assertTrue(SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT !in balanced.toStorageMap())
    }

    @Test
    fun mediaCaptionPreferencesAreCuratedAndMissingLegacyValuesUseDefaults() {
        assertEquals(
            listOf("Overlay", "Below media", "Hidden"),
            MediaCaptionMode.entries.map { it.label },
        )
        assertEquals(
            listOf("With controls", "Always visible"),
            FullscreenCaptionMode.entries.map { it.label },
        )

        val legacy = customizedSettings.toStorageMap() -
            setOf(
                SubshellSettings.MEDIA_CAPTION_MODE,
                SubshellSettings.FULLSCREEN_CAPTION_MODE,
            )
        val restored = SubshellSettings.fromStorageMap(legacy)

        assertEquals(MediaCaptionMode.OVERLAY, restored.mediaCaptionMode)
        assertEquals(
            FullscreenCaptionMode.WITH_CONTROLS,
            restored.fullscreenCaptionMode,
        )
    }

    @Test
    fun videoTimelineSizePresetsRemainCuratedAndTouchSafe() {
        assertEquals(listOf("Thin", "Standard", "Large"), VideoTimelineSize.entries.map { it.label })
        assertEquals(listOf(3, 4, 6), VideoTimelineSize.entries.map { it.trackHeightDp })
        assertEquals(listOf(10, 12, 16), VideoTimelineSize.entries.map { it.restingThumbDiameterDp })
        assertEquals(listOf(14, 16, 20), VideoTimelineSize.entries.map { it.scrubbingThumbDiameterDp })
        assertTrue(VideoTimelineSize.entries.all { it.touchTargetHeightDp >= 48 })
    }

    @Test
    fun defaultRetentionKeepsTenChangesFiveSafetyAndEveryManualSetup() {
        val changes = (0L..14L).map { index ->
            snapshot("change-$index", 10_000L - index, SnapshotKind.CHANGE)
        }
        val safety = (0L..7L).map { index ->
            snapshot("safety-$index", 20_000L - index, SnapshotKind.SAFETY)
        }
        val manual = listOf(
            snapshot("manual-a", 1L, SnapshotKind.MANUAL),
            snapshot("manual-b", 2L, SnapshotKind.MANUAL),
        )

        val pruned = snapshotIdsToPrune(changes + safety + manual)

        assertTrue((0L..9L).none { "change-$it" in pruned })
        assertTrue((10L..14L).all { "change-$it" in pruned })
        assertTrue((0L..4L).none { "safety-$it" in pruned })
        assertTrue((5L..7L).all { "safety-$it" in pruned })
        assertTrue(manual.none { it.id in pruned })
    }

    @Test
    fun automaticAndSafetyRetentionAreIndependentAndUnlimitedIsLossless() {
        val changes = (0L..8L).map { index ->
            snapshot("change-$index", 10_000L - index, SnapshotKind.CHANGE)
        }
        val safety = (0L..8L).map { index ->
            snapshot("safety-$index", 20_000L - index, SnapshotKind.SAFETY)
        }
        val snapshots = changes + safety

        val limited = snapshotIdsToPrune(
            snapshots = snapshots,
            retentionPolicy = BackupRetentionPolicy(
                automaticHistory = BackupRetention.FIVE,
                safetySnapshots = BackupRetention.TWENTY,
            ),
        )
        val unlimited = snapshotIdsToPrune(
            snapshots = snapshots,
            retentionPolicy = BackupRetentionPolicy(
                automaticHistory = BackupRetention.UNLIMITED,
                safetySnapshots = BackupRetention.UNLIMITED,
            ),
        )

        assertTrue((0L..4L).none { "change-$it" in limited })
        assertTrue((5L..8L).all { "change-$it" in limited })
        assertTrue(safety.none { it.id in limited })
        assertTrue(unlimited.isEmpty())
    }

    @Test
    fun retentionStrictnessOnlyRequiresConfirmationWhenTheLimitShrinks() {
        assertTrue(BackupRetention.FIVE.isStricterThan(BackupRetention.TEN))
        assertTrue(BackupRetention.TEN.isStricterThan(BackupRetention.UNLIMITED))
        assertEquals(false, BackupRetention.TWENTY.isStricterThan(BackupRetention.TEN))
        assertEquals(false, BackupRetention.UNLIMITED.isStricterThan(BackupRetention.FIVE))
    }

    @Test
    fun restoringASetupUpdatesLiveStateAndPersistsEverySetting() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            persistSettings = { values -> persisted.putAll(values) },
        )

        state.restoreSettings(customizedSettings)

        assertEquals(customizedSettings, state.captureSettings())
        assertEquals(SubshellSettings.storageKeys, persisted.keys)
    }

    @Test
    fun selectedExternalStorageTreeBecomesAHumanReadableLocation() {
        assertEquals("Internal storage / subshell", describeExternalStorageTree("primary:subshell"))
        assertEquals(
            "Internal storage / Documents / Subshell Backups",
            describeExternalStorageTree("primary:Documents/Subshell Backups"),
        )
        assertEquals("1234-5678 / Backups", describeExternalStorageTree("1234-5678:Backups"))
    }

    private fun snapshot(id: String, timestamp: Long, kind: SnapshotKind) = SettingsSnapshot(
        id = id,
        name = id,
        createdAtEpochMillis = timestamp,
        appVersion = "test",
        kind = kind,
        deviceId = "device-test",
        settings = customizedSettings,
    )

}
