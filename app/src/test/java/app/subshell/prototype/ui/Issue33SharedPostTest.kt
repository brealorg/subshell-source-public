package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #33: what a share becomes, and how the composer opens for it. */
class Issue33SharedPostTest {
    private fun image(n: Int, kind: ComposerMediaKind = ComposerMediaKind.IMAGE) =
        ComposerMediaAttachment(uri = "content://files/$n.jpg", mimeType = "image/jpeg", kind = kind)

    @Test
    fun aSharedUrlStartsALinkPostTitledBySubject() {
        assertEquals(
            SharedPostContent(ComposerPostType.LINK, title = "An article", linkUrl = "https://example.com/a?b=1"),
            sharedPostFromText("https://example.com/a?b=1", "An article"),
        )
    }

    @Test
    fun aUrlWithAShortCaptionTakesTheCaptionAsTitle() {
        assertEquals(
            SharedPostContent(ComposerPostType.LINK, title = "Look at this", linkUrl = "https://example.com/x"),
            sharedPostFromText("Look at this https://example.com/x.", null),
        )
    }

    @Test
    fun otherTextStartsATextPost() {
        assertEquals(
            SharedPostContent(ComposerPostType.TEXT, title = "Notes", body = "Line one\nsee https://a.example and https://b.example"),
            sharedPostFromText("Line one\nsee https://a.example and https://b.example", "Notes"),
        )
        assertEquals(
            SharedPostContent(ComposerPostType.TEXT, body = "Just words"),
            sharedPostFromText("  Just words ", null),
        )
        assertNull(sharedPostFromText("  ", null))
    }

    @Test
    fun imagesStartAnImageOrGalleryPostCappedAtTwenty() {
        val one = sharedPostFromImages(listOf(image(1)), text = "Caption", subject = "Title", skipped = 0)!!
        assertEquals(ComposerPostType.MEDIA, one.type)
        assertEquals("Title", one.title)
        assertEquals("Caption", one.body)
        assertEquals(1, one.media.size)

        val many = sharedPostFromImages((1..23).map(::image), text = null, subject = null, skipped = 2)!!
        assertEquals(20, many.media.size)
        assertEquals(5, many.skippedCount)
        assertNull(sharedPostFromImages(emptyList(), null, null, skipped = 1))
    }

    @Test
    fun onlyImageTypesRedditAcceptsAreKept() {
        assertEquals(ComposerMediaKind.IMAGE, sharedImageKindOrNull("image/jpeg"))
        assertEquals(ComposerMediaKind.IMAGE, sharedImageKindOrNull("IMAGE/PNG"))
        assertEquals(ComposerMediaKind.IMAGE, sharedImageKindOrNull("image/webp"))
        assertEquals(ComposerMediaKind.GIF, sharedImageKindOrNull("image/gif"))
        assertNull(sharedImageKindOrNull("image/heic"))
        assertNull(sharedImageKindOrNull(null))
    }

    @Test
    fun aShareOpensANewDraftAndKeepsTheDraftInProgress() {
        val state = signedIn("me")
        state.openPostComposer()
        state.updatePostComposerDraft(state.postComposerDraft.copy(title = "Half written"))
        val inProgress = state.postComposerDraft.id

        state.openSharedPostComposer(
            SharedPostContent(ComposerPostType.LINK, title = "Shared", linkUrl = "https://example.com"),
        )

        val draft = state.postComposerDraft
        assertTrue(draft.id != inProgress)
        assertEquals(ComposerPostType.LINK, draft.type)
        assertEquals("Shared", draft.title)
        assertEquals("https://example.com", draft.linkUrl)
        assertEquals("", draft.community)
        assertEquals("reddit:me", draft.accountStableId)
        assertTrue(state.postComposerDrafts.any { it.id == inProgress && it.title == "Half written" })
        assertEquals(SubshellRoute.PostComposer, state.currentNavigationRoute)
        assertTrue(state.consumeComposerCommunityPicker(draft.id))
        assertFalse(state.consumeComposerCommunityPicker(draft.id))
    }

    @Test
    fun theComposerNamesThePostingAccountOnlyWithSeveralAccounts() {
        assertNull(app.subshell.prototype.ui.screens.composerPostingAsUsername(signedIn("me")))
        assertEquals("me", app.subshell.prototype.ui.screens.composerPostingAsUsername(signedIn("me", "other")))
    }

    @Test
    fun theShareTargetShipsDisabledWithSendFilters() {
        val manifest = File(repositoryRoot(), "app/src/main/AndroidManifest.xml").readText()
        val alias = manifest.substringAfter("android:name=\"app.subshell.prototype.ShareIntoSubshell\"")
            .substringBefore("</activity-alias>")
        assertTrue(alias.contains("android:enabled=\"false\""))
        // LOW_EXTERNAL_LAUNCHES_USE_SUBSHELLS_OWN_TASK_V1 (OCB-577): the alias targets
        // the forwarding activity, which hands the share to MainActivity.
        assertTrue(alias.contains("android:targetActivity=\"app.subshell.prototype.ExternalLaunchActivity\""))
        assertEquals(2, Regex("android.intent.action.SEND\"").findAll(alias).count())
        assertTrue(alias.contains("android.intent.action.SEND_MULTIPLE"))
        assertTrue(alias.contains("android:mimeType=\"text/plain\""))
        assertEquals(2, Regex("android:mimeType=\"image/\\*\"").findAll(alias).count())
    }

    private fun signedIn(vararg usernames: String): SubshellState {
        val accounts = usernames.map { name ->
            AccountSessionSummary(
                stableId = "reddit:$name",
                username = name,
                source = AccountSessionSource.AUTHENTICATED,
                state = AccountSessionState.CONNECTED,
                providerLabel = "Reddit",
            )
        }
        return SubshellState(
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir")!!).canonicalFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory && File(it, "tools").isDirectory }
}
