package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomFeedManagementPolicyTest {
    @Test
    fun countLabelUsesSingularAndPlural() {
        assertEquals("0 feeds", customFeedCountLabel(0))
        assertEquals("1 feed", customFeedCountLabel(1))
        assertEquals("2 feeds", customFeedCountLabel(2))
    }

    @Test
    fun redditMultiPathIsParsedWithoutResluggingDisplayName() {
        assertEquals(
            listOf("user", "brealorg", "m", "android"),
            customFeedPathSegments("/user/brealorg/m/android"),
        )
        assertEquals(
            listOf("api", "multi", "user", "brealorg", "m", "android"),
            customFeedApiPathSegments("/user/brealorg/m/android"),
        )
    }

    @Test
    fun malformedMultiPathsAreRejected() {
        assertNull(customFeedPathSegments("/r/android"))
        assertNull(customFeedPathSegments("/user/brealorg/m/android/extra"))
        assertNull(customFeedPathSegments("/user/brealorg/x/android"))
    }
}
