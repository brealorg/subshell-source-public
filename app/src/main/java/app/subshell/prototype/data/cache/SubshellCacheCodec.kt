package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.CodeSample
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.InlineMedia
import app.subshell.prototype.model.MediaAsset
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.model.Poll
import app.subshell.prototype.model.PollOption
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import app.subshell.prototype.model.UserFixture

internal object SubshellCacheCodec {
    fun encodeMutationFields(vararg values: String?): String =
        pack(*values)

    fun decodeMutationFields(
        payload: String,
        expectedSize: Int,
    ): List<String?> = unpack(payload, expectedSize)

    fun encodePost(post: Post): String = pack(
        post.id,
        post.community,
        post.author,
        post.age,
        post.title,
        post.body,
        post.score.toString(),
        post.comments.toString(),
        post.kind.name,
        post.mediaLabel,
        post.mediaAsset?.name,
        post.remoteVideoUri,
        post.localMediaUri,
        post.localMediaMimeType,
        encodeList(post.gallery, ::encodeGalleryItem),
        post.domain,
        post.crosspost?.let(::encodeCrosspost),
        post.poll?.let(::encodePoll),
        post.flair,
        post.pinned.toString(),
        post.moderatorPost.toString(),
        post.spoiler.toString(),
        post.nsfw.toString(),
        post.saved.toString(),
        post.vote.toString(),
        post.remoteImageUri,
        post.videoWidth?.toString(),
        post.videoHeight?.toString(),
        post.externalUrl,
        post.permalink,
        post.remoteVideoHlsUri,
        post.remoteVideoFallbackUri,
        post.authorFlairText,
        post.authorFlairTextColor,
        post.authorFlairBackgroundColor,
        encodeList(
            post.authorFlairRichText,
            ::encodeFlairRichTextSegment,
        ),
        // ISSUE44_POST_FLAIR_RICHTEXT_V1: field 36; older payloads end at 35.
        encodeList(
            post.flairRichText,
            ::encodeFlairRichTextSegment,
        ),
    )

    fun decodePost(payload: String): Post {
        val fields = unpack(payload)
        require((fields.size in 23..26 ||
                fields.size == 28 ||
                fields.size == 30 ||
                fields.size == 32) || fields.size == 35 || fields.size == 36 ||
                fields.size == 37) {
            "Expected 23 through 26, 28, 30, or 32 post cache fields but found ${fields.size}"
        }
        val mediaFieldOffset = if (fields.size >= 25) 2 else 0
        return Post(
            id = fields.required(0),
            community = normalizeCachedCommunityName(fields.required(1)),
            author = fields.required(2),
            age = fields.required(3),
            title = fields.required(4),
            body = fields.required(5),
            score = fields.int(6),
            comments = fields.int(7),
            kind = fields.enum(8, PostKind.entries),
            mediaLabel = fields.required(9),
            mediaAsset = fields.enumOrNull(10, MediaAsset.entries),
            remoteVideoUri = fields[11],
            localMediaUri = if (mediaFieldOffset == 2) fields[12] else null,
            localMediaMimeType = if (mediaFieldOffset == 2) fields[13] else null,
            gallery = decodeList(fields.required(12 + mediaFieldOffset), ::decodeGalleryItem),
            domain = fields[13 + mediaFieldOffset],
            crosspost = fields[14 + mediaFieldOffset]?.let(::decodeCrosspost),
            poll = fields[15 + mediaFieldOffset]?.let(::decodePoll),
            flair = fields[16 + mediaFieldOffset],
            pinned = fields.boolean(17 + mediaFieldOffset),
            moderatorPost = fields.boolean(18 + mediaFieldOffset),
            spoiler = fields.boolean(19 + mediaFieldOffset),
            nsfw = fields.boolean(20 + mediaFieldOffset),
            saved = fields.boolean(21 + mediaFieldOffset),
            vote = fields.int(22 + mediaFieldOffset),
            remoteImageUri = fields.getOrNull(23 + mediaFieldOffset),
            videoWidth = fields.getOrNull(26)?.toIntOrNull()?.takeIf { it > 0 },
            videoHeight = fields.getOrNull(27)?.toIntOrNull()?.takeIf { it > 0 },
            externalUrl = fields.getOrNull(28),
            permalink = fields.getOrNull(29),
            remoteVideoHlsUri = fields.getOrNull(30),
            remoteVideoFallbackUri = fields.getOrNull(31),
            authorFlairText = fields.getOrNull(32),
            authorFlairTextColor = fields.getOrNull(33),
            authorFlairBackgroundColor = fields.getOrNull(34),
            authorFlairRichText = fields.getOrNull(35)
                ?.let {
                    decodeList(
                        it,
                        ::decodeFlairRichTextSegment,
                    )
                }
                .orEmpty(),
            flairRichText = fields.getOrNull(36)
                ?.let { decodeList(it, ::decodeFlairRichTextSegment) }
                .orEmpty(),
        )
    }

    fun encodeCommunity(value: Community): String = pack(
        value.id,
        value.name,
        value.description,
        value.members,
        value.accent.toString(),
        value.joined.toString(),
        value.iconImageUri,
        value.bannerImageUri,
    )

    fun decodeCommunity(payload: String): Community {
        val fields = unpack(payload)
        require(fields.size in 6..8) {
            "Expected 6 through 8 community cache fields but found ${fields.size}"
        }
        return Community(
            id = fields.required(0),
            name = normalizeCachedCommunityName(fields.required(1)),
            description = fields.required(2),
            members = fields.required(3),
            accent = fields.long(4),
            joined = fields.boolean(5),
            iconImageUri = fields.getOrNull(6),
            bannerImageUri = fields.getOrNull(7),
        )
    }

    fun encodeUser(value: UserFixture): String = pack(
        value.username,
        value.description,
        value.karma,
        value.accountAge,
        value.followers,
        value.bannerAsset?.name,
        value.avatarImageUri,
        value.bannerImageUri,
    )

    fun decodeUser(payload: String): UserFixture {
        val fields = unpack(payload)
        require(fields.size in 6..8) {
            "Expected 6 through 8 user cache fields but found ${fields.size}"
        }
        return UserFixture(
            username = fields.required(0),
            description = fields.required(1),
            karma = fields.required(2),
            accountAge = fields.required(3),
            followers = fields.required(4),
            bannerAsset = fields.enumOrNull(5, MediaAsset.entries),
            avatarImageUri = fields.getOrNull(6),
            bannerImageUri = fields.getOrNull(7),
        )
    }

    fun encodeComment(value: Comment): String = pack(
        value.id,
        value.author,
        value.age,
        value.body,
        value.score.toString(),
        value.depth.toString(),
        value.accent.toString(),
        value.award,
        value.isOp.toString(),
        value.isModerator.toString(),
        value.inlineMedia?.let(::encodeInlineMedia),
        value.localMediaUri,
        value.localMediaMimeType,
        value.codeSample?.let(::encodeCodeSample),
        encodeList(value.children, ::encodeComment),
        value.createdEpochSeconds?.toString(),
        value.parentId,
        encodeList(value.moreChildIds) { childId -> childId },
        value.moreCount.toString(),
        value.vote.toString(),
        // COMMENTSAVE01A1_COMMENT_CACHE_SAVED_FIELD_V1
        value.saved.toString(),
        value.authorFlairText,
        value.authorFlairTextColor,
        value.authorFlairBackgroundColor,
        encodeList(
            value.authorFlairRichText,
            ::encodeFlairRichTextSegment,
        ),
        // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: field 26; older 25-field
        // payloads read as not removed.
        value.removedByModerator.toString(),
    )

    fun decodeComment(payload: String): Comment {
        val fields = unpack(payload)
        require((fields.size in setOf(13, 14, 15, 17, 19, 20, 21)) || fields.size in 24..26) {
            "Expected a supported legacy, 21-field, 24-field, or 26-field comment cache payload but found ${fields.size} fields"
        }
        // USERFLAIR01A2_24_FIELD_COMMENT_CACHE_MEDIA_OFFSET_V1
        val mediaFieldOffset =
            if (
                fields.size == 15 ||
                fields.size == 19 ||
                fields.size == 20 ||
                fields.size == 21 ||
                fields.size == 24 ||
                fields.size == 25 ||
                fields.size == 26
            ) {
                2
            } else {
                0
            }
        return Comment(
            id = fields.required(0),
            author = fields.required(1),
            age = fields.required(2),
            body = fields.required(3),
            score = fields.int(4),
            depth = fields.int(5),
            accent = fields.long(6),
            award = fields[7],
            isOp = fields.boolean(8),
            isModerator = fields.boolean(9),
            inlineMedia = fields[10]?.let(::decodeInlineMedia),
            localMediaUri = if (mediaFieldOffset == 2) fields[11] else null,
            localMediaMimeType = if (mediaFieldOffset == 2) fields[12] else null,
            codeSample = fields[11 + mediaFieldOffset]?.let(::decodeCodeSample),
            children = decodeList(fields.required(12 + mediaFieldOffset), ::decodeComment),
            createdEpochSeconds = fields.getOrNull(13 + mediaFieldOffset)?.toLong(),
            parentId = fields.getOrNull(14 + mediaFieldOffset),
            moreChildIds = fields.getOrNull(15 + mediaFieldOffset)
                ?.let { encoded -> decodeList(encoded) { childId -> childId } }
                .orEmpty(),
            moreCount = fields.getOrNull(16 + mediaFieldOffset)?.toInt() ?: 0,
            vote = fields.getOrNull(17 + mediaFieldOffset)?.toInt() ?: 0,
            saved = fields.getOrNull(18 + mediaFieldOffset)
                ?.toBooleanStrictOrNull()
                ?: false,
            authorFlairText = fields.getOrNull(21),
            authorFlairTextColor = fields.getOrNull(22),
            authorFlairBackgroundColor = fields.getOrNull(23),
            authorFlairRichText = fields.getOrNull(24)
                ?.let {
                    decodeList(
                        it,
                        ::decodeFlairRichTextSegment,
                    )
                }
                .orEmpty(),
            removedByModerator = fields.getOrNull(25)
                ?.toBooleanStrictOrNull()
                ?: false,
        )
    }

    fun encodeProfileComment(value: ProfileCommentFixture): String = pack(
        value.id,
        value.author,
        value.postId,
        value.age,
        value.body,
        value.score.toString(),
        value.placement.name,
        value.community,
        value.postTitle,
    )

    fun decodeProfileComment(payload: String): ProfileCommentFixture {
        val fields = unpack(payload)
        require(fields.size in 7..9) {
            "Expected 7 through 9 profile comment cache fields but found ${fields.size}"
        }
        return ProfileCommentFixture(
            id = fields.required(0),
            author = fields.required(1),
            postId = fields.required(2),
            age = fields.required(3),
            body = fields.required(4),
            score = fields.int(5),
            placement = fields.enum(6, ThreadPlacement.entries),
            community = fields.getOrNull(7)?.takeIf(String::isNotBlank),
            postTitle = fields.getOrNull(8)?.takeIf(String::isNotBlank),
        )
    }

    fun encodeInboxItem(value: InboxItem): String = pack(
        value.id,
        value.kind.name,
        value.title,
        value.sender,
        value.age,
        value.preview,
        value.unread.toString(),
        value.targetPostId,
        value.targetCommentId,
        encodeList(value.conversation, ::encodeMessageBubble),
        value.createdEpochSeconds?.toString(),
        value.subreddit,
        value.postTitle,
        value.contextPermalink,
    )

    fun decodeInboxItem(payload: String): InboxItem {
        val fields = unpack(payload)
        require(fields.size in 10..14) {
            "Expected 10 through 14 inbox cache fields but found ${fields.size}"
        }
        return InboxItem(
            id = fields.required(0),
            kind = fields.enum(1, ActivityKind.entries),
            title = fields.required(2),
            sender = fields.required(3),
            age = fields.required(4),
            preview = fields.required(5),
            unread = fields.boolean(6),
            targetPostId = fields[7],
            targetCommentId = fields[8],
            conversation = decodeList(fields.required(9), ::decodeMessageBubble),
            createdEpochSeconds = fields.getOrNull(10)?.toLongOrNull(),
            subreddit = fields.getOrNull(11)?.takeIf(String::isNotBlank),
            postTitle = fields.getOrNull(12)?.takeIf(String::isNotBlank),
            contextPermalink = fields.getOrNull(13)?.takeIf(String::isNotBlank),
        )
    }

    private fun encodeGalleryItem(value: GalleryItem): String = pack(
        value.id,
        value.label,
        value.asset?.name,
        value.format.name,
        value.remoteImageUri,
        value.localMediaUri,
        value.localMediaMimeType,
        value.caption,
        value.outboundUrl,
    )

    private fun decodeGalleryItem(payload: String): GalleryItem {
        val fields = unpack(payload)
        require(fields.size in 4..9) {
            "Expected 4 to 9 gallery fields but found ${fields.size}"
        }
        return GalleryItem(
            id = fields.required(0),
            label = fields.required(1),
            asset = fields.enumOrNull(2, MediaAsset.entries),
            format = fields.enum(3, MediaFormat.entries),
            remoteImageUri = fields.getOrNull(4),
            localMediaUri = fields.getOrNull(5),
            localMediaMimeType = fields.getOrNull(6),
            caption = fields.getOrNull(7).orEmpty(),
            outboundUrl = fields.getOrNull(8).orEmpty(),
        )
    }

    private fun encodeCrosspost(value: CrosspostPreview): String = pack(
        value.community,
        value.author,
        value.title,
        value.score.toString(),
        value.id,
        value.age,
        value.body,
        value.comments.toString(),
        value.kind.name,
        value.mediaLabel,
        value.remoteVideoUri,
        encodeList(value.gallery, ::encodeGalleryItem),
        value.domain,
        value.flair,
        value.spoiler.toString(),
        value.nsfw.toString(),
        value.remoteImageUri,
        value.videoWidth?.toString(),
        value.videoHeight?.toString(),
        value.externalUrl,
        value.permalink,
        value.poll?.let(::encodePoll),
        value.remoteVideoHlsUri,
        value.remoteVideoFallbackUri,
        // ISSUE44_POST_FLAIR_RICHTEXT_V1: field 24; older payloads end at 23.
        encodeList(value.flairRichText, ::encodeFlairRichTextSegment),
    )

    private fun decodeCrosspost(payload: String): CrosspostPreview {
        val fields = unpack(payload)
        require(fields.size == 4 || fields.size in 21..24 || fields.size == 25) {
            "Expected 4 or 21 through 24 crosspost fields but found ${fields.size}"
        }
        return CrosspostPreview(
            community = normalizeCachedCommunityName(fields.required(0)),
            author = fields.required(1),
            title = fields.required(2),
            score = fields.int(3),
            id = fields.getOrNull(4).orEmpty(),
            age = fields.getOrNull(5).orEmpty(),
            body = fields.getOrNull(6).orEmpty(),
            comments = fields.getOrNull(7)?.toIntOrNull() ?: 0,
            kind = fields.getOrNull(8)
                ?.let { value -> runCatching { PostKind.valueOf(value) }.getOrNull() }
                ?: PostKind.TEXT,
            mediaLabel = fields.getOrNull(9).orEmpty(),
            remoteVideoUri = fields.getOrNull(10),
            gallery = fields.getOrNull(11)
                ?.let { value -> decodeList(value, ::decodeGalleryItem) }
                .orEmpty(),
            domain = fields.getOrNull(12),
            flair = fields.getOrNull(13),
            spoiler = fields.getOrNull(14)?.toBooleanStrictOrNull() ?: false,
            nsfw = fields.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
            remoteImageUri = fields.getOrNull(16),
            videoWidth = fields.getOrNull(17)?.toIntOrNull()?.takeIf { it > 0 },
            videoHeight = fields.getOrNull(18)?.toIntOrNull()?.takeIf { it > 0 },
            externalUrl = fields.getOrNull(19),
            permalink = fields.getOrNull(20),
            poll = fields.getOrNull(21)?.let(::decodePoll),
            remoteVideoHlsUri = fields.getOrNull(22),
            remoteVideoFallbackUri = fields.getOrNull(23),
            flairRichText = fields.getOrNull(24)
                ?.let { decodeList(it, ::decodeFlairRichTextSegment) }
                .orEmpty(),
        )
    }

    private fun encodePoll(value: Poll): String = pack(
        encodeList(value.options, ::encodePollOption),
        value.totalVotes.toString(),
        value.closesIn,
        value.resultsVisible.toString(),
        value.isClosed.toString(),
    )

    private fun decodePoll(payload: String): Poll {
        val fields = unpack(payload)
        require(fields.size == 3 || fields.size == 5) {
            "Expected 3 or 5 poll fields but found ${fields.size}"
        }
        return Poll(
            options = decodeList(fields.required(0), ::decodePollOption),
            totalVotes = fields.int(1),
            closesIn = fields.required(2),
            resultsVisible = fields.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            isClosed = fields.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun encodePollOption(value: PollOption): String = pack(
        value.id,
        value.label,
        value.votes.toString(),
    )

    private fun decodePollOption(payload: String): PollOption {
        val fields = unpack(payload, 3)
        return PollOption(
            id = fields.required(0),
            label = fields.required(1),
            votes = fields.int(2),
        )
    }

    private fun encodeInlineMedia(value: InlineMedia): String = pack(
        value.label,
        value.asset.name,
        value.format.name,
    )

    private fun decodeInlineMedia(payload: String): InlineMedia {
        val fields = unpack(payload, 3)
        return InlineMedia(
            label = fields.required(0),
            asset = fields.enum(1, MediaAsset.entries),
            format = fields.enum(2, MediaFormat.entries),
        )
    }

    private fun encodeCodeSample(value: CodeSample): String = pack(
        value.language,
        value.source,
    )

    private fun decodeCodeSample(payload: String): CodeSample {
        val fields = unpack(payload, 2)
        return CodeSample(
            language = fields.required(0),
            source = fields.required(1),
        )
    }

    private fun encodeMessageBubble(value: MessageBubble): String = pack(
        value.id,
        value.sender,
        value.age,
        value.body,
        value.outgoing.toString(),
        value.createdEpochSeconds?.toString(),
    )

    private fun decodeMessageBubble(payload: String): MessageBubble {
        val fields = unpack(payload)
        require(fields.size in 5..6) {
            "Expected 5 or 6 message bubble cache fields but found ${fields.size}"
        }
        return MessageBubble(
            id = fields.required(0),
            sender = fields.required(1),
            age = fields.required(2),
            body = fields.required(3),
            outgoing = fields.boolean(4),
            createdEpochSeconds = fields.getOrNull(5)?.toLongOrNull(),
        )
    }

    // USERFLAIR01A3A_FLAIR_RICHTEXT_CACHE_V1
    private fun encodeFlairRichTextSegment(
        value: FlairRichTextSegment,
    ): String = pack(
        value.elementType,
        value.text,
        value.emojiRef,
        value.imageUrl,
    )

    private fun decodeFlairRichTextSegment(
        payload: String,
    ): FlairRichTextSegment {
        val fields = unpack(payload, 4)
        return FlairRichTextSegment(
            elementType = fields.required(0),
            text = fields[1],
            emojiRef = fields[2],
            imageUrl = fields[3],
        )
    }

    private fun <T> encodeList(
        values: List<T>,
        encoder: (T) -> String,
    ): String = pack(*values.map(encoder).toTypedArray())

    private fun <T> decodeList(
        payload: String,
        decoder: (String) -> T,
    ): List<T> = unpack(payload).map { item ->
        decoder(item ?: error("Null list element is not supported"))
    }

    private fun pack(vararg values: String?): String = buildString {
        values.forEach { value ->
            if (value == null) {
                append("-1:")
            } else {
                append(value.length)
                append(':')
                append(value)
            }
        }
    }

    private fun unpack(
        payload: String,
        expectedSize: Int? = null,
    ): List<String?> {
        val values = mutableListOf<String?>()
        var cursor = 0
        while (cursor < payload.length) {
            val separator = payload.indexOf(':', cursor)
            require(separator > cursor) { "Malformed cache payload length" }
            val length = payload.substring(cursor, separator).toInt()
            cursor = separator + 1
            if (length == -1) {
                values += null
            } else {
                require(length >= 0 && cursor + length <= payload.length) {
                    "Malformed cache payload value"
                }
                values += payload.substring(cursor, cursor + length)
                cursor += length
            }
        }
        require(cursor == payload.length) { "Trailing cache payload bytes" }
        expectedSize?.let { expected ->
            require(values.size == expected) {
                "Expected $expected cache fields but found ${values.size}"
            }
        }
        return values
    }

    private fun List<String?>.required(index: Int): String =
        get(index) ?: error("Required cache field $index was null")

    private fun List<String?>.int(index: Int): Int =
        required(index).toInt()

    private fun List<String?>.long(index: Int): Long =
        required(index).toLong()

    private fun List<String?>.boolean(index: Int): Boolean =
        required(index).toBooleanStrict()

    private fun <T : Enum<T>> List<String?>.enum(
        index: Int,
        entries: List<T>,
    ): T = entries.firstOrNull { entry -> entry.name == required(index) }
        ?: error("Unknown cached enum value ${required(index)}")

    private fun <T : Enum<T>> List<String?>.enumOrNull(
        index: Int,
        entries: List<T>,
    ): T? = get(index)?.let { encoded ->
        entries.firstOrNull { entry -> entry.name == encoded }
            ?: error("Unknown cached enum value $encoded")
    }
}

internal fun normalizeCachedCommunityName(value: String): String {
    val trimmed = value.trim()
    val normalized = when {
        trimmed.startsWith("/r/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("r/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    return normalized.takeIf(String::isNotBlank) ?: trimmed
}
