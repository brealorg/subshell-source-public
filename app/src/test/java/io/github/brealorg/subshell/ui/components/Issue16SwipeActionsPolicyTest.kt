package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.ui.s
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import io.github.brealorg.subshell.harness.readSourceText
import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import io.github.brealorg.subshell.ui.CommentSwipeAction
import io.github.brealorg.subshell.ui.PostSwipeAction
import io.github.brealorg.subshell.ui.SubshellSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class Issue16SwipeActionsPolicyTest {
    @Test
    fun swipeActionsAreOnByDefaultWithUsefulMappingsReady() {
        val settings = SubshellSettings()

        // DEFAULTS102_OWNER_SETTINGS_BECOME_DEFAULTS_V1 (owner 2026-11-11): on by default.
        assertTrue(settings.swipeActionsEnabled)
        // Settings audit (owner 2026-10-13, SETTINGSAUDIT01_DEFAULTS_V1): swipe left
        // upvotes, swipe right downvotes, for posts and comments.
        assertEquals(PostSwipeAction.UPVOTE, settings.postSwipeLeftAction)
        assertEquals(PostSwipeAction.DOWNVOTE, settings.postSwipeRightAction)
        assertEquals(CommentSwipeAction.UPVOTE, settings.commentSwipeLeftAction)
        assertEquals(CommentSwipeAction.DOWNVOTE, settings.commentSwipeRightAction)
    }

    // SWIPEACTION02_ANCHORED_DRAGGABLE_V1: sides are physical by construction (the
    // drag offset is never mirrored), so layout direction no longer enters the mapping.
    @Test
    fun physicalLeftRightMappingsFollowTheFinger() {
        assertEquals(PhysicalSwipeSide.RIGHT, swipeSideForOffset(12f))
        assertEquals(PhysicalSwipeSide.LEFT, swipeSideForOffset(-12f))
        assertNull(swipeSideForOffset(0f))
        assertEquals(PhysicalSwipeSide.RIGHT, physicalSwipeSide(SwipeAnchor.RIGHT))
        assertEquals(PhysicalSwipeSide.LEFT, physicalSwipeSide(SwipeAnchor.LEFT))
        assertNull(physicalSwipeSide(SwipeAnchor.SETTLED))
    }

    @Test
    fun affordanceLabelsDescribeTheExistingMutationSemantics() {
        assertEquals(
            "Remove upvote",
            postSwipeActionLabel(PostSwipeAction.UPVOTE, vote = 1, saved = false, hidden = false).s,
        )
        assertEquals(
            "Unsave",
            postSwipeActionLabel(PostSwipeAction.SAVE, vote = 0, saved = true, hidden = false).s,
        )
        assertEquals(
            "Unhide",
            postSwipeActionLabel(PostSwipeAction.HIDE, vote = 0, saved = false, hidden = true).s,
        )
        assertEquals(
            "Remove downvote",
            commentSwipeActionLabel(CommentSwipeAction.DOWNVOTE, vote = -1).s,
        )
    }

    @Test
    fun swipeSettingsRoundTripThroughExistingBackupAllowlist() {
        val configured = SubshellSettings(
            swipeActionsEnabled = true,
            postSwipeLeftAction = PostSwipeAction.HIDE,
            postSwipeRightAction = PostSwipeAction.DOWNVOTE,
            commentSwipeLeftAction = CommentSwipeAction.UPVOTE,
            commentSwipeRightAction = CommentSwipeAction.NONE,
        )
        assertEquals(configured, SubshellSettings.fromStorageMap(configured.toStorageMap()))

        val legacy = configured.toStorageMap() - setOf(
            SubshellSettings.SWIPE_ACTIONS_ENABLED,
            SubshellSettings.POST_SWIPE_LEFT_ACTION,
            SubshellSettings.POST_SWIPE_RIGHT_ACTION,
            SubshellSettings.COMMENT_SWIPE_LEFT_ACTION,
            SubshellSettings.COMMENT_SWIPE_RIGHT_ACTION,
        )
        val restoredLegacy = SubshellSettings.fromStorageMap(legacy)
        assertTrue(restoredLegacy.swipeActionsEnabled) // a backup without the key gets the default (on)
        assertEquals(PostSwipeAction.UPVOTE, restoredLegacy.postSwipeLeftAction)
        assertEquals(PostSwipeAction.DOWNVOTE, restoredLegacy.postSwipeRightAction)
    }

    @Test
    fun productionUsesStableGoogleMaterial3SwipeStateMachineWithoutCustomPhysics() {
        val swipe = source("ui/components/SwipeActionSurface.kt")
        val app = source("ui/SubshellApp.kt")
        val common = source("ui/components/Common.kt")
        val post = source("ui/screens/PostScreen.kt")
        val settings = source("ui/screens/SettingsScreen.kt")

        // SWIPEACTION02_ANCHORED_DRAGGABLE_V1 (owner 2026-11-19): SwipeToDismissBox slid the
        // row off before the action ran and its confirmValueChange is deprecated in M3 1.5;
        // the successor is Foundation's AnchoredDraggable with its default fling behaviour.
        assertTrue(swipe.contains("SWIPEACTION02_ANCHORED_DRAGGABLE_V1"))
        assertTrue(swipe.contains("SWIPEACTION02_THE_ACTION_RUNS_ON_RELEASE_AND_THE_ROW_SPRINGS_BACK_V1"))
        assertTrue(swipe.contains("AnchoredDraggableState(initialValue = SwipeAnchor.SETTLED)"))
        assertTrue(swipe.contains(".anchoredDraggable("))
        assertTrue(swipe.contains("AnchoredDraggableDefaults.flingBehavior("))
        assertTrue(swipe.contains("enabled = leftAnchored || rightAnchored,"))
        assertTrue(swipe.contains("SWIPEACTION02_ANCHORS_DO_NOT_FOLLOW_A_WRITE_IN_FLIGHT_V1"))
        assertTrue(swipe.contains("rememberUpdatedState(leftAction)"))
        assertTrue(swipe.contains("rememberUpdatedState(onLeft)"))
        assertTrue(swipe.contains("state.animateTo(SwipeAnchor.SETTLED)"))
        assertTrue(swipe.contains("state.snapTo(SwipeAnchor.SETTLED)"))
        assertTrue(swipe.contains("HapticFeedbackType.LongPress"))
        assertFalse(swipe.contains("confirmValueChange"))
        assertFalse(swipe.contains("SwipeToDismissBox("))

        listOf(
            "pointerInput(",
            "PointerEventPass",
            "awaitEachGesture",
            "swipeGestureDecision(",
            "committedSwipeDirection(",
            "visualSwipeOffsetPx(",
            "maximumVisualOffsetPx",
            "Animatable(",
            "systemGestureExclusion",
        ).forEach { forbidden ->
            assertFalse("Custom/deprecated swipe engine remains: $forbidden", swipe.contains(forbidden))
        }

        assertTrue(swipe.contains("state.vote(post, 1)"))
        assertTrue(swipe.contains("state.vote(post, -1)"))
        assertTrue(swipe.contains("state.toggleSaved(post)"))
        assertTrue(swipe.contains("state.togglePostHidden(post, authoritativeHiddenHint)"))
        assertTrue(swipe.contains("state.vote(comment, 1)"))
        assertTrue(swipe.contains("state.vote(comment, -1)"))

        assertTrue(common.contains("ISSUE16_POST_SWIPE_ACTION_SURFACE_V1"))
        assertTrue(common.contains("state.swipeActionsEnabled && swipeActionsEnabled"))
        assertTrue(common.contains("saveActionsEnabled = state.postSaveControlEnabled(post)"))
        assertTrue(app.contains("ISSUE16_DRAWER_GESTURE_ARBITRATION_V1"))
        // ISSUE23: the arbitration moved into drawerGesturesEnabled(), which
        // gives up only drag-to-open; Issue23DrawerGesturePolicyTest covers it.
        assertTrue(app.contains("val drawerGesturesEnabled = drawerGesturesEnabled("))
        assertTrue(app.contains("swipeActionsEnabled = state.swipeActionsEnabled,"))
        assertTrue(swipe.contains("state.postSaveControlEnabled(post)"))
        assertTrue(post.contains("ISSUE16_COMMENT_SWIPE_ACTION_SURFACE_V1"))
        // ISSUE217: the owner reversed the post-detail opt-out; the open post swipes
        // (behaviour in Issue217OpenPostSwipeComposeTest).
        assertTrue(post.contains("ISSUE217_OPEN_POST_TAKES_SWIPE_ACTIONS_V1"))
        assertTrue(settings.contains("ISSUE16_CONFIGURABLE_SWIPE_ACTIONS_SETTINGS_V1"))
    }

    private fun source(relative: String): String =
        File("src/main/java/${SUBSHELL_PACKAGE_DIR}/$relative").readSourceText()
}
