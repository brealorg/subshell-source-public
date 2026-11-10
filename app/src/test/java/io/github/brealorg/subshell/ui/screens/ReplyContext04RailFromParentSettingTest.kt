package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// REPLYCONTEXT04_RAIL_FROM_PARENT_IS_A_SETTING_V1
class ReplyContext04RailFromParentSettingTest {
    @Test
    fun onIsTheDefaultAndTheChoiceIsStoredAndRestored() {
        assertTrue(SubshellSettings().commentRailFromParent)
        assertEquals("comment_rail_from_parent", SubshellSettings.COMMENT_RAIL_FROM_PARENT)
        val off = SubshellSettings(commentRailFromParent = false).toStorageMap()
        assertFalse(SubshellSettings.fromStorageMap(off).commentRailFromParent)
        val on = SubshellSettings(commentRailFromParent = true).toStorageMap()
        assertTrue(SubshellSettings.fromStorageMap(on).commentRailFromParent)
        // A settings file from before the option keeps the line on, as REPLYCONTEXT03 shipped it.
        val legacy = SubshellSettings().toStorageMap() - SubshellSettings.COMMENT_RAIL_FROM_PARENT
        assertTrue(SubshellSettings.fromStorageMap(legacy).commentRailFromParent)
    }

    @Test
    fun theStatePersistsTheChoice() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })
        assertTrue(state.commentRailFromParent)
        state.updateCommentRailFromParent(false)
        assertFalse(state.commentRailFromParent)
        assertEquals("false", persisted[SubshellSettings.COMMENT_RAIL_FROM_PARENT])
    }
}
