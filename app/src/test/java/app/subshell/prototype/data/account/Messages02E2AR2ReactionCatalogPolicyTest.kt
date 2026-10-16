package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02E2AR2ReactionCatalogPolicyTest {
    @Test
    fun capturedRedditReactionCatalogHas42UniqueAssets() {
        assertEquals(42, redditDirectChatReactionCatalog.size)
        assertEquals(
            42,
            redditDirectChatReactionCatalog.map(DirectChatReactionAsset::key).distinct().size,
        )
        assertTrue(
            redditDirectChatReactionCatalog.all { asset ->
                asset.uri == "https://i.redd.it/${asset.key}"
            },
        )
    }

    @Test
    fun runtimeProvenReactionKeysArePresent() {
        assertNotNull(redditDirectChatReactionAsset("g6akcitga7081.gif"))
        assertNotNull(redditDirectChatReactionAsset("foyijyyga7081.gif"))
        assertNull(redditDirectChatReactionAsset("🔥"))
    }

    @Test
    fun catalogContains41GifAssetsAndOnePngAsset() {
        assertEquals(41, redditDirectChatReactionCatalog.count(DirectChatReactionAsset::animated))
        val still = redditDirectChatReactionCatalog.single { asset -> !asset.animated }
        assertEquals("ytv3x0sfa7081.png", still.key)
    }
}
