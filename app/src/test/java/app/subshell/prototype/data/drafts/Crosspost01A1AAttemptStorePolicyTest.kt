package app.subshell.prototype.data.drafts

import android.content.SharedPreferences
import app.subshell.prototype.data.account.TextPostWriteRequest
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1AAttemptStorePolicyTest {
    @Test
    fun unresolvedCrosspostRetainsAccountParentDestinationAndKeyAcrossStoreRecreation() {
        val fixture = Crosspost01A1AMemoryPreferences()
        val request = TextPostWriteRequest(
            accountStableId = "reddit:redreader_compatibility:t2_crossposter",
            community = "subshell", title = "Shared original", text = "",
            crosspostFullname = "t3_source123", flairId = "target-flair", flairText = "News",
        )
        val original = TextPostSubmitAttemptStore(fixture.preferences) { 42_000L }
        original.stage(request)
        assertNull(original.load(request.accountStableId)?.dispatchedAtEpochMs)
        original.markDispatched(request.accountStableId, request.copy(crosspostFullname = "t3_other").requestKey)
        assertNull(original.load(request.accountStableId)?.dispatchedAtEpochMs)
        original.markDispatched(request.accountStableId, request.requestKey)

        val restoredStore = TextPostSubmitAttemptStore(fixture.preferences)
        val restored = requireNotNull(restoredStore.load(request.accountStableId))
        assertEquals(request, restored.request)
        assertEquals(request.requestKey, restored.request.requestKey)
        assertEquals(42_000L, restored.dispatchedAtEpochMs)
        restoredStore.clear(request.accountStableId, request.copy(crosspostFullname = "t3_other").requestKey)
        assertNotNull(restoredStore.load(request.accountStableId))
        restoredStore.clear(request.accountStableId, request.requestKey)
        assertNull(restoredStore.load(request.accountStableId))
    }

    @Test
    fun preCrosspostAttemptFixtureStillDecodesWithItsOriginalKey() {
        val fixture = Crosspost01A1AMemoryPreferences()
        // Literal pre-CROSSPOST01A1A schema; intentionally not derived from the current encoder.
        fixture.values["composer09_text_post_submit_attempt_v1"] = """
            {
              "accountStableId":"reddit:redreader_compatibility:t2_crossposter",
              "community":"subshell","title":"Legacy title","text":"Legacy body",
              "sendReplies":true,"nsfw":false,"spoiler":false,
              "linkUrl":null,"linkPreviewImageUrl":null,"mediaUri":null,"mediaMimeType":null,
              "galleryItems":[],"flairId":null,"flairText":null,"mediaAsGif":false,
              "dispatchedAtEpochMs":42000
            }
        """.trimIndent()
        val restored = requireNotNull(TextPostSubmitAttemptStore(fixture.preferences)
            .load("reddit:redreader_compatibility:t2_crossposter"))
        assertNull(restored.request.crosspostFullname)
        assertEquals("5a9b4f7815ded74602292a278f105c137a4bdb542eb0400ff2f2a1327702065e", restored.request.requestKey)
        assertEquals(42_000L, restored.dispatchedAtEpochMs)
    }
}

// Test-only in-memory implementation; never obtains Context or app preferences.
internal class Crosspost01A1AMemoryPreferences {
    val values = linkedMapOf<String, String?>()
    val preferences: SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "getString" -> if (values.containsKey(args!![0] as String)) {
                values[args[0] as String]
            } else args[1]
            "contains" -> values.containsKey(args!![0] as String)
            "getAll" -> values.toMap()
            "edit" -> editor()
            "toString" -> "Crosspost01A1AMemoryPreferences"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            else -> error("Unexpected preference operation: ${method.name}")
        }
    } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        val pending = linkedMapOf<String, String?>()
        val removed = mutableSetOf<String>()
        var cleared = false
        return Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> { pending[args!![0] as String] = args[1] as String?; proxy }
                "remove" -> { removed += args!![0] as String; proxy }
                "clear" -> { cleared = true; proxy }
                "commit", "apply" -> {
                    if (cleared) values.clear()
                    removed.forEach(values::remove)
                    pending.forEach { (key, value) ->
                        if (value == null) values.remove(key) else values[key] = value
                    }
                    if (method.name == "commit") true else null
                }
                "toString" -> "Crosspost01A1AEditor"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> error("Unexpected editor operation: ${method.name}")
            }
        } as SharedPreferences.Editor
    }
}
