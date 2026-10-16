package app.subshell.prototype.data.cache

import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Poll
import app.subshell.prototype.model.PollOption
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (crosspost01): a crosspost's source kept in the cache by an older
 * app still reads — the first, four-field form (community, author, title,
 * score) and the 21–23-field forms written before the poll, the HLS and the
 * fallback video fields — and a payload of any other length is refused.
 * Test-only: the positional codec is not changed.
 */
class Step3CrosspostCacheHistoryTest {
    private val source = CrosspostPreview(
        community = "pics",
        author = "photographer",
        title = "The source",
        score = 10,
        id = "t3_source",
        age = "3h",
        body = "Source body",
        comments = 2,
        kind = PostKind.VIDEO,
        remoteVideoUri = "https://v.redd.it/a/DASH_720.mp4",
        permalink = "/r/pics/comments/source/",
        poll = Poll(options = listOf(PollOption(id = "1", label = "Yes", votes = 1)), totalVotes = 1, closesIn = "1d", resultsVisible = true),
        remoteVideoHlsUri = "https://v.redd.it/a/HLSPlaylist.m3u8",
        remoteVideoFallbackUri = "https://v.redd.it/a/DASH_480.mp4",
    )

    private val post = Post(
        id = "t3_wrapper",
        community = "target",
        author = "crossposter",
        age = "1h",
        title = "Shared",
        score = 1,
        comments = 0,
        kind = PostKind.CROSSPOST,
        crosspost = source,
    )

    private val crosspostIndex = 16

    /** The current post payload with its crosspost field cut to [size] fields. */
    private fun withCrosspostFields(size: Int): String {
        val fields = SubshellCacheCodec.decodeMutationFields(SubshellCacheCodec.encodePost(post), expectedSize = 37)
        val crosspostFields = SubshellCacheCodec.decodeMutationFields(requireNotNull(fields[crosspostIndex]), expectedSize = 25)
        val cut = SubshellCacheCodec.encodeMutationFields(*crosspostFields.take(size).toTypedArray())
        return SubshellCacheCodec.encodeMutationFields(
            *fields.mapIndexed { index, value -> if (index == crosspostIndex) cut else value }.toTypedArray(),
        )
    }

    @Test
    fun theFirstFourFieldFormStillReads() {
        val decoded = SubshellCacheCodec.decodePost(withCrosspostFields(4)).crosspost

        assertEquals(CrosspostPreview(community = "pics", author = "photographer", title = "The source", score = 10), decoded)
    }

    @Test
    fun formsWrittenBeforeThePollAndVideoFieldsStillRead() {
        val before21 = SubshellCacheCodec.decodePost(withCrosspostFields(21)).crosspost
        val before22 = SubshellCacheCodec.decodePost(withCrosspostFields(22)).crosspost
        val before23 = SubshellCacheCodec.decodePost(withCrosspostFields(23)).crosspost

        assertEquals(source.copy(poll = null, remoteVideoHlsUri = null, remoteVideoFallbackUri = null), before21)
        assertEquals(source.copy(remoteVideoHlsUri = null, remoteVideoFallbackUri = null), before22)
        assertEquals(source.copy(remoteVideoFallbackUri = null), before23)
    }

    @Test
    fun aPayloadOfAnyOtherLengthIsRefused() {
        for (size in listOf(5, 12, 20)) {
            assertTrue("$size fields", runCatching { SubshellCacheCodec.decodePost(withCrosspostFields(size)) }.isFailure)
        }
    }
}
