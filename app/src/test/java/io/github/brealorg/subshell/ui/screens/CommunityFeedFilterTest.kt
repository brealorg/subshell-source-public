package io.github.brealorg.subshell.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityFeedFilterTest {
    @Test
    fun identityCasingAndDisplayPrefixesDoNotHideCachedPosts() {
        assertTrue(communityNamesMatch("standupcomedy", "Standupcomedy"))
        assertTrue(communityNamesMatch("r/StandUpComedy", "/r/standupcomedy"))
        assertFalse(communityNamesMatch("standupcomedy", "subshell"))
    }
}
