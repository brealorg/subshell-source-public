package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsSnapshot
import app.subshell.prototype.backup.SettingsSnapshotCodec
import app.subshell.prototype.backup.SnapshotKind
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.components.POST_THUMBNAIL_TEST_TAG
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.PostLayoutMenuWidth
import app.subshell.prototype.ui.components.PostMedia
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.settingsSearchResult
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.visual.VisualFixtures
import app.subshell.prototype.visual.VisualIdentityRepository
import app.subshell.prototype.visual.VisualRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE234_FEED_POST_LAYOUTS_V1 (owner 2026-10-18): feeds show posts as Large
 * (default), Compact, List or Grid; Compact and List have no vote buttons; List's
 * thumbnail side is a choice; Grid is even rows; both settings are backed up;
 * the load-on-tap cover is no taller than 4:5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue234FeedPostLayoutsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(context)
    }

    private val posts: List<Post> = SampleData.posts.take(5).mapIndexed { index, post -> post.copy(id = "p$index") }

    @Test
    fun gridRowsAreKeyedByTheirFirstPost() {
        assertEquals(listOf("p0", "p2", "p4"), feedPostItemKeys(posts, PostLayout.GRID, 2))
        assertEquals(posts.map(Post::id), feedPostItemKeys(posts, PostLayout.LIST, 2))
        assertEquals(2, feedGridColumnsFor(379f))
        assertEquals(3, feedGridColumnsFor(600f))
        assertEquals(PostLayout.LIST, PostLayout.GRID.forFeed(gridAllowed = false))
        assertEquals(PostLayout.GRID, PostLayout.GRID.forFeed(gridAllowed = true))
    }

    @Test
    fun bothSettingsTravelInABackupAndAnOlderBackupLeavesThemUnchanged() {
        val chosen = SubshellSettings(postLayout = PostLayout.GRID, listThumbnailSide = ListThumbnailSide.LEFT)
        assertTrue(SubshellSettings.POST_LAYOUT in SubshellSettings.storageKeys)
        assertTrue(SubshellSettings.LIST_THUMBNAIL_SIDE in SubshellSettings.storageKeys)
        val restored = SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(snapshot(chosen)))
            .settingsRestoredOver(SubshellSettings())
        assertEquals(PostLayout.GRID, restored.postLayout)
        assertEquals(ListThumbnailSide.LEFT, restored.listThumbnailSide)

        val encoded = Json.parseToJsonElement(SettingsSnapshotCodec.encode(snapshot(SubshellSettings()))).jsonObject
        val settings = encoded.getValue("settings").jsonObject
        val older = JsonObject(
            encoded + ("settings" to JsonObject(settings - SubshellSettings.POST_LAYOUT - SubshellSettings.LIST_THUMBNAIL_SIDE)),
        ).toString()
        val keptCurrent = SettingsSnapshotCodec.decode(older).settingsRestoredOver(chosen)
        assertEquals(PostLayout.GRID, keptCurrent.postLayout)
        assertEquals(ListThumbnailSide.LEFT, keptCurrent.listThumbnailSide)
    }

    @Test
    fun aChoiceIsCapturedAndARestoreAppliesItToTheLiveState() {
        val source = SubshellState(initialInteractionSideConfigured = true)
        assertEquals(PostLayout.LARGE, source.postLayout)
        source.updatePostLayout(PostLayout.COMPACT)
        source.updateListThumbnailSide(ListThumbnailSide.LEFT)
        val captured = source.captureSettings()
        assertEquals(PostLayout.COMPACT, captured.postLayout)

        val target = SubshellState(initialInteractionSideConfigured = true)
        target.restoreSettings(captured)
        assertEquals(PostLayout.COMPACT, target.postLayout)
        assertEquals(ListThumbnailSide.LEFT, target.listThumbnailSide)
    }

    private fun showHome(layout: PostLayout): SubshellState {
        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialAccountSessions = VisualFixtures.accounts,
            initialActiveAccountId = VisualFixtures.active.stableId,
            repository = VisualRepository(),
            identityMetadataRepository = VisualIdentityRepository,
            initialPostLayout = layout,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = VisualFixtures.subscriptions),
            )
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        return state
    }

    private val upvoteButton = hasContentDescription("Upvote") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Test
    fun homeInLargeKeepsTheVoteButtons() {
        showHome(PostLayout.LARGE)
        assertTrue(composeRule.onAllNodes(upvoteButton).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag(POST_THUMBNAIL_TEST_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun homeInListHasNoVoteButtonsButOffersVotingAsACustomAction() {
        showHome(PostLayout.LIST)
        assertTrue(composeRule.onAllNodes(upvoteButton).fetchSemanticsNodes().isEmpty())
        val rows = composeRule.onAllNodes(
            SemanticsMatcher("has an Upvote custom action") { node ->
                node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.CustomActions) { null }
                    ?.any { it.label == "Upvote" || it.label == "Remove upvote" } == true
            },
        ).fetchSemanticsNodes()
        assertTrue("list rows offer voting to TalkBack", rows.size >= 2)
        assertTrue(composeRule.onAllNodes(hasContentDescription("Post actions"), useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun homeInCompactHasNoVoteButtons() {
        showHome(PostLayout.COMPACT)
        assertTrue(composeRule.onAllNodes(upvoteButton).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasContentDescription("Post actions"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun homeInGridPutsTwoPostsSideBySide() {
        showHome(PostLayout.GRID)
        val tops = composeRule.onAllNodes(hasTestTag(POST_THUMBNAIL_TEST_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertTrue("grid shows media tiles", tops.size >= 2)
        val firstRow = tops.filter { it.top == tops.first().top }
        assertEquals(2, firstRow.size)
        assertTrue(firstRow[0].left != firstRow[1].left)
    }

    @Test
    fun theHomeHeaderChangesTheLayoutWhereTheFeedIsRead() {
        val state = showHome(PostLayout.LARGE)
        composeRule.onNode(hasContentDescription("Post layout: Large")).performClick()
        composeRule.onNode(hasText("Grid")).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(PostLayout.GRID, state.postLayout)
        assertTrue(composeRule.onAllNodes(hasContentDescription("Post layout: Grid")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(upvoteButton).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theLayoutMenuOpensInTheSamePlaceWhateverIsChosen() {
        showHome(PostLayout.LARGE)
        fun menuBounds(current: String) = run {
            composeRule.onNode(hasContentDescription("Post layout: $current")).performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodes(hasText("Grid")).fetchSemanticsNodes().single().boundsInRoot
        }
        val withLarge = menuBounds("Large")
        composeRule.onNode(hasText("Compact")).performClick()
        composeRule.waitForIdle()
        val withCompact = menuBounds("Compact")
        assertEquals(withLarge.left, withCompact.left, 0.5f)
        assertEquals(withLarge.right, withCompact.right, 0.5f)
        // Robolectric's fonts give every choice the same natural width, so the shift
        // the owner saw on the phone only shows here as a menu narrower than its set width.
        val menuWidthPx = with(composeRule.density) { PostLayoutMenuWidth.toPx() }
        assertEquals(menuWidthPx, withCompact.width, 0.5f)
    }

    @Test
    fun theCommunitySortRowOffersTheLayoutAndListOffersTheThumbnailSide() {
        val state = showHome(PostLayout.LIST)
        composeRule.runOnIdle { state.openSubreddit(SampleData.communities.first().name) }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Post layout: List")).performClick()
        composeRule.onNode(hasText("Thumbnail left")).performClick()
        composeRule.waitForIdle()
        assertEquals(ListThumbnailSide.LEFT, state.listThumbnailSide)
    }

    private fun showListRow(post: Post, side: ListThumbnailSide, mode: SensitiveMediaPreviewMode = SensitiveMediaPreviewMode.BLUR_AND_PRELOAD) {
        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialListThumbnailSide = side,
            initialSensitiveMediaPreviewMode = mode,
        )
        composeRule.setContent {
            SubshellTheme {
                LazyColumn {
                    item { PostCard(post = post, state = state, onOpen = {}, layout = PostLayout.LIST) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val imagePost = SampleData.posts.first { it.kind == PostKind.IMAGE }.copy(id = "img1")

    @Test
    fun theListThumbnailSitsOnTheChosenSide() {
        showListRow(imagePost, ListThumbnailSide.RIGHT)
        val title = composeRule.onAllNodes(hasText(imagePost.title), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val thumb = composeRule.onAllNodes(hasTestTag(POST_THUMBNAIL_TEST_TAG), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("thumbnail right of the title", thumb.left >= title.right)
    }

    @Test
    fun leftPutsTheThumbnailBeforeTheTitle() {
        showListRow(imagePost, ListThumbnailSide.LEFT)
        val title = composeRule.onAllNodes(hasText(imagePost.title), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val thumb = composeRule.onAllNodes(hasTestTag(POST_THUMBNAIL_TEST_TAG), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("thumbnail left of the title", thumb.right <= title.left)
    }

    @Test
    fun aHiddenSensitivePictureStaysHiddenInTheList() {
        showListRow(imagePost.copy(nsfw = true), ListThumbnailSide.RIGHT, SensitiveMediaPreviewMode.HIDE_PREVIEW)
        assertTrue(
            composeRule.onAllNodes(hasContentDescription("Sensitive media hidden"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun theLoadOnTapCoverIsNoTallerThanFourToFive() {
        val tallVideo = SampleData.posts.first { it.kind == PostKind.VIDEO }
            .copy(id = "tall", nsfw = true, videoWidth = 1080, videoHeight = 1920)
        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialSensitiveMediaPreviewMode = SensitiveMediaPreviewMode.LOAD_ON_TAP,
        )
        composeRule.setContent {
            SubshellTheme {
                Box(Modifier.width(400.dp).testTag("media")) { PostMedia(post = tallVideo, state = state) }
            }
        }
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag("media").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("cover height $height", height.value <= 500.5f)
    }

    @Test
    fun theThumbnailRowShowsOnlyWithListAndSearchFindsTheLayout() {
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = app.subshell.prototype.backup.SettingsBackupRepository(context, "test"),
                    onBack = {},
                    initialDestinationName = SettingsDestination.FEEDS.name,
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodes(hasText("Post layout")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasText("Thumbnail")).fetchSemanticsNodes().isEmpty())
        composeRule.runOnIdle { state.updatePostLayout(PostLayout.LIST) }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodes(hasText("Thumbnail")).fetchSemanticsNodes().isNotEmpty())

        listOf("grid", "compact", "dense", "thumbnail").forEach { query ->
            assertTrue(query, "post-layout" in settingsSearchResult(query).itemKeys)
        }
    }

    private fun snapshot(settings: SubshellSettings) = SettingsSnapshot(
        id = "backup-1",
        name = "Backup",
        createdAtEpochMillis = 1L,
        appVersion = "0.82-beta",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = settings,
    )
}
