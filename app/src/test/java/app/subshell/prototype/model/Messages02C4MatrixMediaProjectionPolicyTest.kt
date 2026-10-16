package app.subshell.prototype.model

import org.junit.Assert.assertEquals
import org.junit.Test

class Messages02C4MatrixMediaProjectionPolicyTest {
    @Test
    fun redditMatrixThumbnailIsCanonicalizedToOriginalDownloadAndAnimatedCapableMedia() {
        val thumbnail =
            "https://matrix.redditspace.com/_matrix/media/v3/thumbnail/reddit.com/9vj34eq42llh1?height=256"
        val projection = projectCommentContent(thumbnail)

        assertEquals("", projection.displayText)
        assertEquals(1, projection.media.size)
        assertEquals(
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/9vj34eq42llh1",
            projection.media.single().uri,
        )
        assertEquals(RemoteCommentMediaKind.GIF, projection.media.single().kind)
    }

    @Test
    fun ordinaryMatrixDownloadRemainsCanonicalAndAnimatedCapable() {
        val download =
            "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/9vj34eq42llh1"

        assertEquals(download, canonicalRemoteCommentMediaUri(download))
        assertEquals(RemoteCommentMediaKind.GIF, projectCommentContent(download).media.single().kind)
    }
}
