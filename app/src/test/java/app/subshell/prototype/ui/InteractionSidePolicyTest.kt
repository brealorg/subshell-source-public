package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionSidePolicyTest {
    @Test
    fun followPreferencesResolveFromTheInteractionSide() {
        assertEquals(
            InteractionSide.LEFT,
            DrawerSidePreference.FOLLOW.resolve(InteractionSide.LEFT),
        )
        assertEquals(
            InteractionSide.RIGHT,
            DrawerSidePreference.FOLLOW.resolve(InteractionSide.RIGHT),
        )
        assertEquals(
            CommentActionPlacement.LEFT,
            CommentActionPreference.FOLLOW.resolve(InteractionSide.LEFT),
        )
        assertEquals(
            CommentActionPlacement.RIGHT,
            CommentActionPreference.FOLLOW.resolve(InteractionSide.RIGHT),
        )
    }

    @Test
    fun followLabelsExposeTheResolvedInteractionSide() {
        assertEquals("Follow · Left", InteractionSide.LEFT.followLabel())
        assertEquals("Follow · Right", InteractionSide.RIGHT.followLabel())
    }

    @Test
    fun floatingActionsFollowTheInteractionSideDirectly() {
        assertTrue(InteractionSide.LEFT.floatingActionAtStart())
        assertFalse(InteractionSide.RIGHT.floatingActionAtStart())
    }

    @Test
    fun mediaActionsFollowTheInteractionSideDirectly() {
        assertTrue(InteractionSide.LEFT.mediaActionsAtStart())
        assertFalse(InteractionSide.RIGHT.mediaActionsAtStart())
    }

    @Test
    fun composerActionsFollowTheInteractionSideDirectly() {
        assertTrue(InteractionSide.LEFT.composerActionsAtStart())
        assertFalse(InteractionSide.RIGHT.composerActionsAtStart())
    }

    @Test
    fun explicitOverridesStayFixedWhenInteractionSideChanges() {
        assertEquals(
            InteractionSide.LEFT,
            DrawerSidePreference.LEFT.resolve(InteractionSide.RIGHT),
        )
        assertEquals(
            CommentActionPlacement.BALANCED,
            CommentActionPreference.BALANCED.resolve(InteractionSide.LEFT),
        )
    }

    @Test
    fun legacyPlacementSeedsNewPreferencesWithoutLosingBalanced() {
        assertEquals(InteractionSide.LEFT, legacyInteractionSide(CommentActionPlacement.LEFT))
        assertEquals(InteractionSide.RIGHT, legacyInteractionSide(CommentActionPlacement.RIGHT))
        assertEquals(
            CommentActionPreference.FOLLOW,
            legacyCommentActionPreference(CommentActionPlacement.LEFT),
        )
        assertEquals(
            CommentActionPreference.BALANCED,
            legacyCommentActionPreference(CommentActionPlacement.BALANCED),
        )
    }

    @Test
    fun statePersistsInteractionDrawerAndCommentPreferencesTogether() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            persistPreference = { key, value -> persisted[key] = value },
        )

        assertFalse(state.commentActionOnboardingVisible)
        state.updateInteractionSide(InteractionSide.LEFT)
        state.updateDrawerSidePreference(DrawerSidePreference.RIGHT)
        state.updateCommentActionPreference(CommentActionPreference.BALANCED)

        assertEquals(InteractionSide.LEFT, state.interactionSide)
        assertEquals(InteractionSide.RIGHT, state.resolvedDrawerSide)
        assertEquals(CommentActionPlacement.BALANCED, state.commentActionPlacement)
        assertEquals(InteractionSide.LEFT.name, persisted[SubshellSettings.INTERACTION_SIDE])
        assertEquals(
            DrawerSidePreference.RIGHT.name,
            persisted[SubshellSettings.DRAWER_SIDE_PREFERENCE],
        )
        assertEquals(
            CommentActionPreference.BALANCED.name,
            persisted[SubshellSettings.COMMENT_ACTION_PREFERENCE],
        )
        assertTrue(SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT in persisted)
    }
}
