package app.subshell.prototype.ui.components

import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.ui.unit.LayoutDirection
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.SubshellSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue16SwipeActionsPolicyTest {
    @Test
    fun swipeActionsAreBackwardCompatibleOffWithUsefulMappingsReady() {
        val settings = SubshellSettings()

        assertFalse(settings.swipeActionsEnabled)
        // Settings audit (owner 2026-10-13, SETTINGSAUDIT01_DEFAULTS_V1): swipe left
        // upvotes, swipe right downvotes, for posts and comments.
        assertEquals(PostSwipeAction.UPVOTE, settings.postSwipeLeftAction)
        assertEquals(PostSwipeAction.DOWNVOTE, settings.postSwipeRightAction)
        assertEquals(CommentSwipeAction.UPVOTE, settings.commentSwipeLeftAction)
        assertEquals(CommentSwipeAction.DOWNVOTE, settings.commentSwipeRightAction)
    }

    @Test
    fun physicalLeftRightMappingsRemainCorrectAcrossLayoutDirection() {
        assertEquals(
            PhysicalSwipeSide.RIGHT,
            physicalSwipeSide(SwipeToDismissBoxValue.StartToEnd, LayoutDirection.Ltr),
        )
        assertEquals(
            PhysicalSwipeSide.LEFT,
            physicalSwipeSide(SwipeToDismissBoxValue.EndToStart, LayoutDirection.Ltr),
        )
        assertEquals(
            PhysicalSwipeSide.LEFT,
            physicalSwipeSide(SwipeToDismissBoxValue.StartToEnd, LayoutDirection.Rtl),
        )
        assertEquals(
            PhysicalSwipeSide.RIGHT,
            physicalSwipeSide(SwipeToDismissBoxValue.EndToStart, LayoutDirection.Rtl),
        )
        assertNull(
            physicalSwipeSide(SwipeToDismissBoxValue.Settled, LayoutDirection.Ltr),
        )
    }

    @Test
    fun affordanceLabelsDescribeTheExistingMutationSemantics() {
        assertEquals(
            "Remove upvote",
            postSwipeActionLabel(PostSwipeAction.UPVOTE, vote = 1, saved = false, hidden = false),
        )
        assertEquals(
            "Unsave",
            postSwipeActionLabel(PostSwipeAction.SAVE, vote = 0, saved = true, hidden = false),
        )
        assertEquals(
            "Unhide",
            postSwipeActionLabel(PostSwipeAction.HIDE, vote = 0, saved = false, hidden = true),
        )
        assertEquals(
            "Remove downvote",
            commentSwipeActionLabel(CommentSwipeAction.DOWNVOTE, vote = -1),
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
        assertFalse(restoredLegacy.swipeActionsEnabled)
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

        assertTrue(swipe.contains("ISSUE16_GOOGLE_M3_SWIPE_TO_DISMISS_V1"))
        assertTrue(swipe.contains("SwipeToDismissBox("))
        assertTrue(swipe.contains("rememberSwipeToDismissBoxState("))
        assertTrue(swipe.contains("SwipeToDismissBoxDefaults.positionalThreshold"))
        assertTrue(swipe.contains("gesturesEnabled = enabled && (leftAvailable || rightAvailable)"))
        assertTrue(swipe.contains("enableDismissFromStartToEnd = startToEndAvailable"))
        assertTrue(swipe.contains("enableDismissFromEndToStart = endToStartAvailable"))
        assertTrue(swipe.contains("ISSUE16_M3_STABLE_ONDISMISS_CALLBACK_V1"))
        assertTrue(swipe.contains("rememberUpdatedState(leftAction)"))
        assertTrue(swipe.contains("rememberUpdatedState(onLeft)"))
        assertTrue(swipe.contains("val stableOnDismiss = remember(swipeState)"))
        assertTrue(swipe.contains("onDismiss = stableOnDismiss"))
        assertFalse(swipe.contains("onDismiss = { direction ->"))
        assertTrue(swipe.contains("swipeState.reset()"))
        assertTrue(swipe.contains("swipeState.snapTo(SwipeToDismissBoxValue.Settled)"))
        assertTrue(swipe.contains("LaunchedEffect(swipeState.targetValue)"))
        assertTrue(swipe.contains("HapticFeedbackType.LongPress"))

        listOf(
            "pointerInput(",
            "PointerEventPass",
            "awaitEachGesture",
            "swipeGestureDecision(",
            "committedSwipeDirection(",
            "visualSwipeOffsetPx(",
            "maximumVisualOffsetPx",
            "Animatable(",
            "confirmValueChange",
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
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
