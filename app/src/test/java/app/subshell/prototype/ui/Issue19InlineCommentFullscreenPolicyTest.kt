package app.subshell.prototype.ui

import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.RemoteCommentMediaKind
import app.subshell.prototype.model.projectCommentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue19InlineCommentFullscreenPolicyTest {
    @Test
    fun commentMediaOverlayPreservesOriginatingPostRouteForBack() {
        val origin = FeedPostOrigin.MainFeed(
            destination = MainDestination.HOME,
            source = FeedSource.HOME,
            sort = FeedSort.BEST,
        )
        val postRoute = FeedPostRoute(
            postId = "post19",
            focusedCommentId = "comment19",
            visitId = 19,
            origin = origin,
        )
        val postStack = SubshellRouteStack
            .forDestination(
                destination = MainDestination.HOME,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            )
            .push(SubshellRoute.Post(postRoute))
        val overlay = SubshellRoute.CommentMedia(
            commentId = "comment19",
            uri = "https://i.redd.it/issue19.jpg",
        )
        val fullscreen = postStack.push(overlay)

        assertEquals(overlay, fullscreen.current)
        assertEquals(postStack.current, fullscreen.surface)
        assertEquals(postStack, fullscreen.pop())
    }

    @Test
    fun processSnapshotDropsEphemeralCommentMediaOverlay() {
        val origin = FeedPostOrigin.MainFeed(
            destination = MainDestination.HOME,
            source = FeedSource.HOME,
            sort = FeedSort.BEST,
        )
        val postRoute = FeedPostRoute(
            postId = "post19",
            focusedCommentId = "comment19",
            visitId = 19,
            origin = origin,
        )
        val stack = SubshellRouteStack
            .forDestination(
                destination = MainDestination.HOME,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            )
            .push(SubshellRoute.Post(postRoute))
            .push(
                SubshellRoute.CommentMedia(
                    commentId = "comment19",
                    uri = "https://i.redd.it/issue19.jpg",
                ),
            )
        val encoded = encodeSubshellProcessSnapshot(
            SubshellProcessSnapshot(
                routeStack = stack,
                scrollAnchors = emptyMap(),
                userProfileTabs = emptyMap(),
                postComposerDraft = PostComposerDraft(),
                feedVideoMuted = true,
                backgroundResumeMediaId = null,
                backgroundResumeWasManual = false,
            ),
        )
        val decoded = requireNotNull(
            decodeSubshellProcessSnapshot(
                encoded = encoded,
                catalog = SubshellProcessRestoreCatalog(
                    postKindsById = mapOf("post19" to PostKind.IMAGE),
                    canonicalCommunitiesByNormalizedName = emptyMap(),
                ),
            ),
        )

        assertEquals(
            listOf("Main", "Post"),
            decoded.routeStack.entries.map(SubshellRoute::kindName),
        )
        assertTrue(decoded.routeStack.current is SubshellRoute.Post)
    }

    @Test
    fun differentInlineImagesRetainDistinctMediaIdentity() {
        val first = SubshellRoute.CommentMedia(
            commentId = "comment19",
            uri = "https://preview.redd.it/first.jpg?width=1181&format=pjpg",
        )
        val second = SubshellRoute.CommentMedia(
            commentId = "comment19",
            uri = "https://preview.redd.it/second.jpg?width=1181&format=pjpg",
        )
        assertNotEquals(first, second)
    }

    @Test
    fun ordinaryTextLinkRemainsTextWhileRedditImageProjectsAsMedia() {
        val projection = projectCommentContent(
            "Read [docs](https://example.com/help)\n\n" +
                "https://preview.redd.it/full-source.jpg?width=1181&format=pjpg",
        )

        assertTrue(projection.displayText.contains("[docs](https://example.com/help)"))
        assertEquals(1, projection.media.size)
        assertEquals(RemoteCommentMediaKind.IMAGE, projection.media.single().kind)
        assertEquals(
            "https://preview.redd.it/full-source.jpg?width=1181&format=pjpg",
            projection.media.single().uri,
        )
    }

    @Test
    fun invalidImagePlaceholderDoesNotCreateFullscreenTarget() {
        val projection = projectCommentContent("Broken ![img](not-a-url)")
        assertTrue(projection.media.isEmpty())
        assertTrue(projection.displayText.contains("not-a-url"))
    }
}
