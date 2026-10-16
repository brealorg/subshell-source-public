package app.subshell.prototype

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.SubmittedTextPostGalleryItem
import app.subshell.prototype.data.account.TextPostWriteGalleryItem
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.COMPOSER_FLAIR_TEXT_MAX_LENGTH
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.ComposerCommunityPolicy
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.ComposerFlairRichText
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.ComposerPostRequirementsStatus
import app.subshell.prototype.ui.ComposerPostType
import app.subshell.prototype.ui.ComposerSubmitPreflightAction
import app.subshell.prototype.ui.ComposerValidationField
import app.subshell.prototype.ui.MarkdownAction
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.applyMarkdownAction
import app.subshell.prototype.ui.reconcileComposerFlairSelection
import app.subshell.prototype.ui.buildLocalPost
import app.subshell.prototype.ui.composerSubmitPreflightAction
import app.subshell.prototype.ui.components.MarkdownBlock
import app.subshell.prototype.ui.components.markdownPreviewSource
import app.subshell.prototype.ui.components.parseMarkdownBlocks
import app.subshell.prototype.ui.components.resolvedPostBodyMaxLines
import app.subshell.prototype.ui.screens.v4ComposerLinkItemIndex
import app.subshell.prototype.ui.validatePostComposerDraft
import app.subshell.prototype.ui.validatePostComposerDraftIssue
import app.subshell.prototype.ui.validateRemoteTextPostComposerDraft
import app.subshell.prototype.ui.isComposerVideoGif
import app.subshell.prototype.ui.isComposerVideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostComposerContractTest {
    private val communities = SampleData.communities.map { it.name }

    @Test
    fun markdownSelectionToolsStoreRedditMarkdown() {
        val bold = applyMarkdownAction("hello world", 6, 11, MarkdownAction.BOLD)
        assertEquals("hello **world**", bold.text)
        val link = applyMarkdownAction("", 0, 0, MarkdownAction.LINK)
        assertEquals("[link text](https://)", link.text)
        assertEquals("https://", link.text.substring(link.selectionStart, link.selectionEnd))
        assertEquals(
            "> one\n> two",
            applyMarkdownAction("one\ntwo", 0, 7, MarkdownAction.QUOTE).text,
        )
        assertEquals(
            "1. one\n2. two",
            applyMarkdownAction("one\ntwo", 0, 7, MarkdownAction.NUMBERED_LIST).text,
        )
        assertEquals(
            "    one\n    two",
            applyMarkdownAction("one\ntwo", 0, 7, MarkdownAction.CODE_BLOCK).text,
        )
    }

    @Test
    fun markdownPreviewPreservesBlockSemanticsAndOneFeedBudget() {
        val blocks = parseMarkdownBlocks(
            """
            Before

            > quoted text

                val answer = 42

            ```kotlin
            val doubled = answer * 2
            ```
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Before"),
                MarkdownBlock.Quote("quoted text"),
                MarkdownBlock.Code("val answer = 42", null),
                MarkdownBlock.Code("val doubled = answer * 2", "kotlin"),
            ),
            blocks,
        )
        assertEquals(
            "Before\nquoted text\nval answer = 42\nval doubled = answer * 2",
            markdownPreviewSource(blocks),
        )
        assertEquals(2, resolvedPostBodyMaxLines(PostKind.IMAGE, 4))
        assertEquals(Int.MAX_VALUE, resolvedPostBodyMaxLines(PostKind.GALLERY, Int.MAX_VALUE))
    }

    @Test
    fun pollIsGoneAndMainTypesAreTextLinkMedia() {
        assertEquals(
            listOf(ComposerPostType.TEXT, ComposerPostType.LINK, ComposerPostType.MEDIA),
            ComposerPostType.standaloneTypes,
        )
    }

    @Test
    fun authenticatedSubmitPreflightWaitsForAuthoritativePostRequirements() {
        val base = ComposerCommunityPolicy(community = "norge")

        assertEquals(
            ComposerSubmitPreflightAction.AUTHORIZE,
            composerSubmitPreflightAction(
                base.copy(
                    postRequirementsStatus =
                        ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED,
                ),
            ),
        )
        assertEquals(
            ComposerSubmitPreflightAction.VALIDATE,
            composerSubmitPreflightAction(
                base.copy(
                    postRequirementsStatus = ComposerPostRequirementsStatus.LOADED,
                    flairRequired = true,
                ),
            ),
        )
        assertEquals(
            ComposerSubmitPreflightAction.FALLBACK_TO_REDDIT,
            composerSubmitPreflightAction(
                base.copy(
                    postRequirementsStatus = ComposerPostRequirementsStatus.UNAVAILABLE,
                ),
            ),
        )
    }

    @Test
    fun validationUsesCommunityPolicyForBodyFlairAndAllowedTypes() {
        val flair = ComposerFlair("template-1", "Support")
        val policy = ComposerCommunityPolicy(
            community = "subshell",
            allowedPostTypes = setOf(ComposerPostType.TEXT),
            titleMinLength = 4,
            titleMaxLength = 20,
            bodyRequired = true,
            bodyMinLength = 7,
            flairRequired = true,
            flairOptions = listOf(flair),
        )
        assertEquals(
            "Use at least 4 characters in the title",
            validatePostComposerDraft(PostComposerDraft(title = "Hey"), communities, policy),
        )
        assertEquals(
            "Post flair is required",
            validatePostComposerDraft(PostComposerDraft(title = "Hello"), communities, policy),
        )
        val flairIssue = validatePostComposerDraftIssue(
            PostComposerDraft(title = "Hello"),
            communities,
            policy,
        )
        requireNotNull(flairIssue)
        assertEquals(ComposerValidationField.FLAIR, flairIssue.field)
        assertEquals("Post flair is required", flairIssue.message)
        assertEquals(
            "Add post body text",
            validatePostComposerDraft(
                PostComposerDraft(title = "Hello", flair = flair),
                communities,
                policy,
            ),
        )
        assertEquals(
            "Use at least 7 characters in the post body",
            validatePostComposerDraft(
                PostComposerDraft(title = "Hello", body = "Short", flair = flair),
                communities,
                policy,
            ),
        )
        assertNull(
            validatePostComposerDraft(
                PostComposerDraft(title = "Hello", body = "Details", flair = flair),
                communities,
                policy,
            ),
        )
    }

    @Test
    fun flairSelectionRefreshesCanonicalMetadataAndDropsUnavailableTemplates() {
        val selected = ComposerFlair(
            templateId = "template-1",
            text = "Custom support",
            editable = true,
            textColor = "light",
            backgroundColor = "#111111",
        )
        val refreshedTemplate = ComposerFlair(
            templateId = "template-1",
            text = "Support",
            editable = true,
            textColor = "dark",
            backgroundColor = "#eeeeee",
            richText = listOf(
                ComposerFlairRichText(
                    elementType = "text",
                    text = "Support",
                ),
            ),
        )

        assertEquals(
            refreshedTemplate.copy(text = "Custom support", richText = emptyList()),
            reconcileComposerFlairSelection(selected, listOf(refreshedTemplate)),
        )
        assertNull(reconcileComposerFlairSelection(selected, emptyList()))
        assertEquals(
            refreshedTemplate.copy(editable = false, text = "Locked"),
            reconcileComposerFlairSelection(
                selected,
                listOf(refreshedTemplate.copy(editable = false, text = "Locked")),
            ),
        )
        assertEquals(
            refreshedTemplate,
            reconcileComposerFlairSelection(
                selected.copy(text = "x".repeat(COMPOSER_FLAIR_TEXT_MAX_LENGTH + 1)),
                listOf(refreshedTemplate),
            ),
        )
    }

    @Test
    fun mediaBuildsImageVideoGifAndGalleryWithoutFakePolls() {
        fun media(uri: String, mime: String, kind: ComposerMediaKind) =
            ComposerMediaAttachment(uri, mime, kind)

        val image = buildLocalPost(
            PostComposerDraft(
                type = ComposerPostType.MEDIA,
                title = "Photo",
                media = listOf(media("content://photo", "image/png", ComposerMediaKind.IMAGE)),
            ),
            "root",
            "image",
        )
        assertEquals(PostKind.IMAGE, image.kind)

        val video = buildLocalPost(
            PostComposerDraft(
                type = ComposerPostType.MEDIA,
                title = "Video",
                media = listOf(media("content://video", "video/mp4", ComposerMediaKind.VIDEO)),
            ),
            "root",
            "video",
        )
        assertEquals(PostKind.VIDEO, video.kind)

        val gallery = buildLocalPost(
            PostComposerDraft(
                type = ComposerPostType.MEDIA,
                title = "Gallery",
                media = listOf(
                    media("content://one", "image/png", ComposerMediaKind.IMAGE),
                    media("content://two", "image/gif", ComposerMediaKind.GIF),
                ),
            ),
            "root",
            "gallery",
        )
        assertEquals(PostKind.GALLERY, gallery.kind)
        assertEquals(listOf("content://one", "content://two"), gallery.gallery.map { it.localMediaUri })
        assertNull(gallery.poll)
    }

    @Test
    fun closingArchivesDraftAndCreateStartsFresh() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openPostComposer()
        val draft = state.postComposerDraft.copy(
            community = "android",
            title = "Archived draft",
            body = "Still here later.",
        )
        state.updatePostComposerDraft(draft)
        state.closePostComposer()

        assertFalse(state.postComposerVisible)
        assertTrue(state.postComposerDrafts.any { it.id == draft.id })
        assertFalse(state.postComposerDraft.hasContent)

        state.openPostComposer()
        assertFalse(state.postComposerDraft.hasContent)
        assertTrue(state.postComposerDraft.id != draft.id)
        state.closePostComposer()

        assertTrue(state.openPostComposerDraft(draft.id))
        assertEquals("Archived draft", state.postComposerDraft.title)
        val published = state.publishPostComposerDraft()
        assertEquals(PostKind.TEXT, published?.kind)
        assertEquals(published, state.selectedPost)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
    }

    @Test
    fun flairAndOptionsParticipateInPinnedRequestIdentity() {
        val first = TextPostWriteRequest(
            accountStableId = "reddit:test:t2_user",
            community = "subshell",
            title = "Post",
            text = "Body",
            flairId = "flair-a",
            flairText = "Help",
            spoiler = true,
        )
        val second = first.copy(flairText = "Solved")
        assertTrue(first.requestKey != second.requestKey)
    }

    @Test
    fun mediaParticipatesInPinnedRequestIdentity() {
        val first = TextPostWriteRequest(
            accountStableId = "reddit:test:t2_user",
            community = "subshell",
            title = "Photo",
            text = "Body",
            mediaUri = "content://one",
            mediaMimeType = "image/jpeg",
        )
        assertTrue(first.requestKey != first.copy(mediaUri = "content://two").requestKey)
        assertTrue(first.requestKey != first.copy(mediaMimeType = "image/png").requestKey)
    }

    @Test
    fun galleryOrderCaptionAndOutboundUrlParticipateInPinnedRequestIdentity() {
        val first = TextPostWriteGalleryItem(
            mediaUri = "content://one",
            mediaMimeType = "image/png",
            caption = "First",
            outboundUrl = "https://example.com/one",
        )
        val second = TextPostWriteGalleryItem(
            mediaUri = "content://two",
            mediaMimeType = "image/gif",
            caption = "Second",
        )
        val base = TextPostWriteRequest(
            accountStableId = "reddit:test:t2_user",
            community = "subshell",
            title = "Gallery",
            text = "Body",
            galleryItems = listOf(first, second),
        )

        assertTrue(base.requestKey != base.copy(galleryItems = listOf(second, first)).requestKey)
        assertTrue(
            base.requestKey != base.copy(
                galleryItems = listOf(first.copy(caption = "Changed"), second),
            ).requestKey,
        )
        assertTrue(
            base.requestKey != base.copy(
                galleryItems = listOf(
                    first.copy(outboundUrl = "https://example.com/changed"),
                    second,
                ),
            ).requestKey,
        )
    }

    @Test
    fun incompleteGalleryOutboundUrlIsRejectedByComposerValidation() {
        val draft = PostComposerDraft(
            type = ComposerPostType.MEDIA,
            community = "subshell",
            title = "Gallery",
            media = listOf(
                ComposerMediaAttachment(
                    "content://one",
                    "image/png",
                    ComposerMediaKind.IMAGE,
                ),
                ComposerMediaAttachment(
                    "content://two",
                    "image/jpeg",
                    ComposerMediaKind.IMAGE,
                    outboundUrl = "https:",
                ),
            ),
        )

        assertEquals(
            "Use a complete web link for each gallery item",
            validateRemoteTextPostComposerDraft(draft),
        )
    }

    @Test
    fun editableFlairUsesReddits64CharacterSubmitLimit() {
        val accepted = TextPostWriteRequest(
            accountStableId = "reddit:test:t2_user",
            community = "subshell",
            title = "Post",
            text = "Body",
            flairId = "flair-a",
            flairText = "x".repeat(64),
        )
        assertEquals(64, accepted.flairText?.length)
        assertThrows(IllegalArgumentException::class.java) {
            accepted.copy(flairText = "x".repeat(65))
        }
    }

    @Test
    fun authenticatedLinkImageGalleryVideoAndSingleGifCanSubmit() {
        assertNull(
            validateRemoteTextPostComposerDraft(
                PostComposerDraft(
                    type = ComposerPostType.LINK,
                    community = "subshell",
                    title = "Link",
                    body = "Context that accompanies the shared link.",
                    linkUrl = "https://example.com",
                ),
            ),
        )
        assertNull(
            validateRemoteTextPostComposerDraft(
                PostComposerDraft(
                    type = ComposerPostType.MEDIA,
                    community = "subshell",
                    title = "Image",
                    media = listOf(
                        ComposerMediaAttachment(
                            "content://photo",
                            "image/png",
                            ComposerMediaKind.IMAGE,
                        ),
                    ),
                ),
            ),
        )
        val galleryDraft = PostComposerDraft(
            type = ComposerPostType.MEDIA,
            community = "subshell",
            title = "Gallery",
            media = listOf(
                ComposerMediaAttachment(
                    "content://one",
                    "image/png",
                    ComposerMediaKind.IMAGE,
                    caption = "First",
                ),
                ComposerMediaAttachment(
                    "content://two",
                    "image/gif",
                    ComposerMediaKind.GIF,
                    caption = "Second",
                    outboundUrl = "https://example.com/second",
                ),
            ),
        )
        assertNull(validateRemoteTextPostComposerDraft(galleryDraft))
        assertEquals(
            "This community doesn't allow gallery posts",
            validateRemoteTextPostComposerDraft(
                galleryDraft,
                ComposerCommunityPolicy(
                    community = "subshell",
                    allowImages = false,
                    allowGalleries = false,
                ),
            ),
        )
        assertNull(
            validateRemoteTextPostComposerDraft(
                PostComposerDraft(
                    type = ComposerPostType.MEDIA,
                    community = "subshell",
                    title = "GIF",
                    media = listOf(
                        ComposerMediaAttachment("content://gif", "image/gif", ComposerMediaKind.GIF),
                    ),
                ),
            ),
        )
        assertEquals(
            "Choose a GIF image",
            validateRemoteTextPostComposerDraft(
                PostComposerDraft(
                    type = ComposerPostType.MEDIA,
                    community = "subshell",
                    title = "Invalid GIF",
                    media = listOf(
                        ComposerMediaAttachment(
                            "content://gif-invalid",
                            "application/octet-stream",
                            ComposerMediaKind.GIF,
                        ),
                    ),
                ),
            ),
        )
        val videoDraft = PostComposerDraft(
            type = ComposerPostType.MEDIA,
            community = "subshell",
            title = "Video",
            media = listOf(
                ComposerMediaAttachment(
                    "content://video",
                    "video/mp4",
                    ComposerMediaKind.VIDEO,
                ),
            ),
        )
        assertNull(validateRemoteTextPostComposerDraft(videoDraft))
        assertEquals(
            "This community doesn't allow video posts",
            validateRemoteTextPostComposerDraft(
                videoDraft,
                ComposerCommunityPolicy(
                    community = "subshell",
                    allowVideo = false,
                ),
            ),
        )
        assertEquals(
            "Choose an MP4, MOV, or WebM video",
            validateRemoteTextPostComposerDraft(
                videoDraft.copy(
                    media = listOf(
                        ComposerMediaAttachment(
                            "content://video",
                            "application/octet-stream",
                            ComposerMediaKind.VIDEO,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun remoteSuccessClearsOnlyMatchingDraftVersion() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val draft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            title = "Connected post",
            body = "Body",
            flair = ComposerFlair("flair-1", "Support"),
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = draft.community,
            title = draft.title,
            text = draft.body,
            flairId = draft.flair?.templateId,
            flairText = draft.flair?.text,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(draft)
        val created = state.completeRemoteTextPostComposer(
            account.stableId,
            request.requestKey,
            SubmittedTextPost(
                "t3_proof1",
                "proof1",
                "subshell",
                draft.title,
                draft.body,
                "/r/subshell/comments/proof1/post/",
            ),
        )
        assertEquals("proof1", created.id)
        assertFalse(state.postComposerDraft.hasContent)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
    }

    @Test
    fun remoteImageSuccessClearsMatchingDraftAndKeepsImmediateMediaIdentity() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val attachment = ComposerMediaAttachment(
            uri = "content://photo",
            mimeType = "image/jpeg",
            kind = ComposerMediaKind.IMAGE,
        )
        val draft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            type = ComposerPostType.MEDIA,
            title = "Connected image",
            body = "Body that accompanies the image.",
            media = listOf(attachment),
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = draft.community,
            title = draft.title,
            text = draft.body,
            mediaUri = attachment.uri,
            mediaMimeType = attachment.mimeType,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(draft)

        val created = state.completeRemoteTextPostComposer(
            accountStableId = account.stableId,
            requestKey = request.requestKey,
            submittedPost = SubmittedTextPost(
                fullname = "t3_imageproof",
                id = "imageproof",
                community = "subshell",
                title = draft.title,
                text = draft.body,
                permalink = "/r/subshell/comments/imageproof/connected_image/",
                mediaLocalUri = attachment.uri,
                mediaMimeType = attachment.mimeType,
                mediaRemoteImageUrl = "https://i.redd.it/imageproof.jpg",
            ),
        )

        assertEquals(PostKind.IMAGE, created.kind)
        assertEquals(attachment.uri, created.localMediaUri)
        assertEquals(attachment.mimeType, created.localMediaMimeType)
        assertEquals("https://i.redd.it/imageproof.jpg", created.remoteImageUri)
        assertFalse(state.postComposerDraft.hasContent)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
    }

    @Test
    fun remoteGifSuccessKeepsImmediateAnimatedMediaIdentity() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val attachment = ComposerMediaAttachment(
            uri = "content://gifproof",
            mimeType = "image/gif",
            kind = ComposerMediaKind.GIF,
        )
        val draft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            type = ComposerPostType.MEDIA,
            title = "Connected GIF",
            body = "Body that accompanies the GIF.",
            media = listOf(attachment),
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = draft.community,
            title = draft.title,
            text = draft.body,
            mediaUri = attachment.uri,
            mediaMimeType = attachment.mimeType,
        )

        state.openPostComposer()
        state.updatePostComposerDraft(draft)

        val remoteUrl =
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/t2_test/gifproof.gif"
        val created = state.completeRemoteTextPostComposer(
            accountStableId = account.stableId,
            requestKey = request.requestKey,
            submittedPost = SubmittedTextPost(
                fullname = "t3_gifproof",
                id = "gifproof",
                community = draft.community,
                title = draft.title,
                text = draft.body,
                permalink = "/r/subshell/comments/gifproof/connected_gif/",
                mediaLocalUri = attachment.uri,
                mediaMimeType = attachment.mimeType,
                mediaRemoteImageUrl = remoteUrl,
            ),
        )

        assertEquals(PostKind.GIF, created.kind)
        assertEquals("GIF", created.mediaLabel)
        assertEquals(attachment.uri, created.localMediaUri)
        assertEquals(attachment.mimeType, created.localMediaMimeType)
        assertEquals(remoteUrl, created.remoteImageUri)
        assertFalse(state.postComposerDraft.hasContent)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
    }

    @Test
    fun remoteGallerySuccessClearsMatchingDraftAndKeepsOrderedImmediateMediaIdentity() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val first = ComposerMediaAttachment(
            uri = "content://one",
            mimeType = "image/png",
            kind = ComposerMediaKind.IMAGE,
            caption = "First image",
        )
        val second = ComposerMediaAttachment(
            uri = "content://two",
            mimeType = "image/gif",
            kind = ComposerMediaKind.GIF,
            caption = "Second image",
            outboundUrl = "https://example.com/second",
        )
        val draft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            type = ComposerPostType.MEDIA,
            title = "Connected gallery",
            body = "Body that accompanies the gallery.",
            media = listOf(first, second),
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = draft.community,
            title = draft.title,
            text = draft.body,
            galleryItems = listOf(
                TextPostWriteGalleryItem(
                    first.uri,
                    first.mimeType,
                    first.caption,
                    first.outboundUrl,
                ),
                TextPostWriteGalleryItem(
                    second.uri,
                    second.mimeType,
                    second.caption,
                    second.outboundUrl,
                ),
            ),
        )
        state.openPostComposer()
        state.updatePostComposerDraft(draft)

        val created = state.completeRemoteTextPostComposer(
            accountStableId = account.stableId,
            requestKey = request.requestKey,
            submittedPost = SubmittedTextPost(
                fullname = "t3_galleryproof",
                id = "galleryproof",
                community = "subshell",
                title = draft.title,
                text = draft.body,
                permalink = "/r/subshell/comments/galleryproof/connected_gallery/",
                galleryItems = listOf(
                    SubmittedTextPostGalleryItem(
                        mediaLocalUri = first.uri,
                        mediaMimeType = first.mimeType,
                        mediaRemoteImageUrl = "https://i.redd.it/galleryproof-one.png",
                        caption = first.caption,
                    ),
                    SubmittedTextPostGalleryItem(
                        mediaLocalUri = second.uri,
                        mediaMimeType = second.mimeType,
                        mediaRemoteImageUrl = "https://i.redd.it/galleryproof-two.gif",
                        caption = second.caption,
                        outboundUrl = second.outboundUrl,
                    ),
                ),
            ),
        )

        assertEquals(PostKind.GALLERY, created.kind)
        assertEquals(listOf(first.uri, second.uri), created.gallery.map { it.localMediaUri })
        assertEquals(
            listOf(first.caption, second.caption),
            created.gallery.map { it.caption },
        )
        assertEquals(
            listOf(first.outboundUrl, second.outboundUrl),
            created.gallery.map { it.outboundUrl },
        )
        assertEquals(
            listOf(
                "https://i.redd.it/galleryproof-one.png",
                "https://i.redd.it/galleryproof-two.gif",
            ),
            created.gallery.map { it.remoteImageUri },
        )
        assertEquals(listOf("First image", "Second image"), created.gallery.map { it.label })
        assertFalse(state.postComposerDraft.hasContent)
        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
    }

    @Test
    fun remoteLinkSuccessUsesSubmittedSnapshotWhilePreservingLaterDraftEdits() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val sentDraft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            type = ComposerPostType.LINK,
            title = "Connected link",
            body = "Context that belongs to the submitted link.",
            linkUrl = "https://www.example.com/story",
            flair = ComposerFlair("flair-1", "News"),
            nsfw = true,
            spoiler = true,
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = sentDraft.community,
            title = sentDraft.title,
            text = sentDraft.body,
            linkUrl = sentDraft.linkUrl,
            flairId = sentDraft.flair?.templateId,
            flairText = sentDraft.flair?.text,
            nsfw = sentDraft.nsfw,
            spoiler = sentDraft.spoiler,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(sentDraft)
        state.updatePostComposerDraft(sentDraft.copy(title = "Edited while sending"))

        val created = state.completeRemoteTextPostComposer(
            accountStableId = account.stableId,
            requestKey = request.requestKey,
            submittedPost = SubmittedTextPost(
                fullname = "t3_linkproof",
                id = "linkproof",
                community = "subshell",
                title = sentDraft.title,
                text = sentDraft.body,
                permalink = "/r/subshell/comments/linkproof/connected_link/",
                linkUrl = sentDraft.linkUrl,
                linkPreviewImageUrl = "https://cdn.example.com/story.jpg",
                flairText = sentDraft.flair?.text,
                nsfw = sentDraft.nsfw,
                spoiler = sentDraft.spoiler,
            ),
        )

        assertEquals(PostKind.LINK, created.kind)
        assertEquals(sentDraft.body, created.body)
        assertEquals("example.com", created.domain)
        assertEquals(sentDraft.linkUrl, created.mediaLabel)
        assertEquals("https://cdn.example.com/story.jpg", created.remoteImageUri)
        assertEquals("News", created.flair)
        assertTrue(created.nsfw)
        assertTrue(created.spoiler)
        assertEquals("Edited while sending", state.postComposerDraft.title)
        assertTrue(state.postComposerDrafts.any { it.id == sentDraft.id })
    }

    @Test
    fun linkFocusTargetIndexTracksConditionalComposerRows() {
        assertEquals(
            3,
            v4ComposerLinkItemIndex(
                hasPolicyStatus = false,
                hasFlairAction = false,
                hasFlairNotice = false,
                hasGeneralValidation = false,
            ),
        )
        assertEquals(
            7,
            v4ComposerLinkItemIndex(
                hasPolicyStatus = true,
                hasFlairAction = true,
                hasFlairNotice = true,
                hasGeneralValidation = true,
            ),
        )
    }
    @Test
    fun remoteVideoSuccessKeepsImmediateLocalVideoIdentity() {
        val account = AccountSessionSummary(
            stableId = "reddit:redreader_compatibility:t2_writer",
            username = "brealorg",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val attachment = ComposerMediaAttachment(
            uri = "content://video",
            mimeType = "video/mp4",
            kind = ComposerMediaKind.VIDEO,
        )
        val draft = PostComposerDraft(
            accountStableId = account.stableId,
            community = "subshell",
            type = ComposerPostType.MEDIA,
            title = "Connected video",
            body = "Body that accompanies the video.",
            media = listOf(attachment),
        )
        val request = TextPostWriteRequest(
            accountStableId = account.stableId,
            community = draft.community,
            title = draft.title,
            text = draft.body,
            mediaUri = attachment.uri,
            mediaMimeType = attachment.mimeType,
        )

        state.openPostComposer()
        state.updatePostComposerDraft(draft)

        val created = state.completeRemoteTextPostComposer(
            accountStableId = account.stableId,
            requestKey = request.requestKey,
            submittedPost = SubmittedTextPost(
                fullname = "t3_video1",
                id = "video1",
                community = draft.community,
                title = draft.title,
                text = draft.body,
                permalink = "/r/subshell/comments/video1/connected_video/",
                mediaLocalUri = attachment.uri,
                mediaMimeType = attachment.mimeType,
                mediaRemoteImageUrl =
                    "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/" +
                        "t2_test/video-poster.jpg",
                videoWidth = 720,
                videoHeight = 1600,
            ),
        )

        assertEquals(PostKind.VIDEO, created.kind)
        assertEquals(attachment.uri, created.localMediaUri)
        assertEquals(attachment.mimeType, created.localMediaMimeType)
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/" +
                "t2_test/video-poster.jpg",
            created.remoteImageUri,
        )
        assertEquals(720, created.videoWidth)
        assertEquals(1600, created.videoHeight)
        assertFalse(state.postComposerDraft.hasContent)
    }

    @Test
    fun nativeVideoGifKeepsVideoPlaybackPathAndDistinctSubmitIdentity() {
        val attachment = ComposerMediaAttachment(
            uri = "content://video-gif",
            mimeType = "video/mp4",
            kind = ComposerMediaKind.GIF,
        )
        val draft = PostComposerDraft(
            type = ComposerPostType.MEDIA,
            community = "subshell",
            title = "Video GIF",
            media = listOf(attachment),
        )

        assertEquals(true, attachment.isComposerVideoSource())
        assertEquals(true, attachment.isComposerVideoGif())
        assertNull(validateRemoteTextPostComposerDraft(draft))

        val local = buildLocalPost(
            draft = draft,
            author = "root",
            id = "videogif",
        )
        assertEquals(PostKind.VIDEO, local.kind)
        assertEquals("GIF", local.mediaLabel)

        val videoRequest = TextPostWriteRequest(
            accountStableId = "reddit:test:t2_user",
            community = "subshell",
            title = "Video",
            text = "",
            mediaUri = attachment.uri,
            mediaMimeType = attachment.mimeType,
        )
        val gifRequest = videoRequest.copy(mediaAsGif = true)

        assertFalse(videoRequest.requestKey == gifRequest.requestKey)

        assertThrows(IllegalArgumentException::class.java) {
            TextPostWriteRequest(
                accountStableId = "reddit:test:t2_user",
                community = "subshell",
                title = "Bad GIF",
                text = "",
                mediaUri = "content://photo",
                mediaMimeType = "image/jpeg",
                mediaAsGif = true,
            )
        }
    }

    @Test
    fun videoAndVideogifCapabilitiesAreValidatedIndependently() {
        val videoDraft = PostComposerDraft(
            type = ComposerPostType.MEDIA,
            community = "subshell",
            title = "Video policy",
            media = listOf(
                ComposerMediaAttachment(
                    uri = "content://video-policy",
                    mimeType = "video/mp4",
                    kind = ComposerMediaKind.VIDEO,
                ),
            ),
        )
        val videoGifDraft = videoDraft.copy(
            title = "Video GIF policy",
            media = listOf(
                videoDraft.media.single().copy(kind = ComposerMediaKind.GIF),
            ),
        )

        val bothAllowed = ComposerCommunityPolicy(
            community = "subshell",
            allowVideo = true,
            allowVideoGifs = true,
        )
        assertNull(validateRemoteTextPostComposerDraft(videoDraft, bothAllowed))
        assertNull(validateRemoteTextPostComposerDraft(videoGifDraft, bothAllowed))

        val videoOnly = ComposerCommunityPolicy(
            community = "subshell",
            allowVideo = true,
            allowVideoGifs = false,
        )
        assertNull(validateRemoteTextPostComposerDraft(videoDraft, videoOnly))
        assertEquals(
            "This community doesn't allow GIF-style video posts",
            validateRemoteTextPostComposerDraft(videoGifDraft, videoOnly),
        )

        val videoGifOnly = ComposerCommunityPolicy(
            community = "subshell",
            allowVideo = false,
            allowVideoGifs = true,
        )
        assertEquals(
            "This community doesn't allow video posts",
            validateRemoteTextPostComposerDraft(videoDraft, videoGifOnly),
        )
        assertNull(validateRemoteTextPostComposerDraft(videoGifDraft, videoGifOnly))

        val neitherAllowed = ComposerCommunityPolicy(
            community = "subshell",
            allowVideo = false,
            allowVideoGifs = false,
        )
        assertEquals(
            "This community doesn't allow video posts",
            validateRemoteTextPostComposerDraft(videoDraft, neitherAllowed),
        )
        assertEquals(
            "This community doesn't allow GIF-style video posts",
            validateRemoteTextPostComposerDraft(videoGifDraft, neitherAllowed),
        )
    }

    @Test
    fun staleOutcomeUnknownIsScopedToItsOriginalDraftRequest() {
        val stale = app.subshell.prototype.data.account.TextPostWriteUiState(
            phase = app.subshell.prototype.data.account.TextPostWritePhase.OUTCOME_UNKNOWN,
            accountStableId = "reddit:test:t2_writer",
            requestKey = "old-request",
            failureKind = app.subshell.prototype.data.account.TextPostWriteFailureKind.API,
            completionRevision = 7L,
        )

        val same = app.subshell.prototype.ui.screens.textPostWriteStateForRequest(
            state = stale,
            requestKey = "old-request",
        )
        val changed = app.subshell.prototype.ui.screens.textPostWriteStateForRequest(
            state = stale,
            requestKey = "new-request",
        )
        val inProgress = app.subshell.prototype.ui.screens.textPostWriteStateForRequest(
            state = stale.copy(
                phase = app.subshell.prototype.data.account.TextPostWritePhase.SENDING,
                failureKind = null,
            ),
            requestKey = "new-request",
        )

        assertEquals(
            app.subshell.prototype.data.account.TextPostWritePhase.OUTCOME_UNKNOWN,
            same.phase,
        )
        assertEquals(
            app.subshell.prototype.data.account.TextPostWritePhase.IDLE,
            changed.phase,
        )
        assertEquals(7L, changed.completionRevision)
        assertEquals(
            app.subshell.prototype.data.account.TextPostWritePhase.SENDING,
            inProgress.phase,
        )
    }

}
