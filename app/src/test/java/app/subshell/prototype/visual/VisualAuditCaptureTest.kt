package app.subshell.prototype.visual

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.theme.ThemeMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/**
 * VISUAL01_CAPTURE_SUITE_V1: renders the app's main surfaces from SampleData,
 * signed in as SampleData's accounts (see VisualFixtures), and writes one PNG per surface,
 * theme and window, so the UI can be looked at (and compared before/after a
 * change) without a phone.
 *
 * Without SUBSHELL_VISUAL_OUT the suite renders each surface once (phone,
 * light) as a smoke test and writes nothing. With it set to a directory
 * outside the repo, every variant runs and the PNGs land there:
 *
 *   SUBSHELL_VISUAL_OUT=/tmp/visual ./gradlew --offline --no-daemon \
 *     --no-configuration-cache :app:testDebugUnitTest \
 *     --tests 'app.subshell.prototype.visual.*'
 *
 * Files are named `<surface>_<theme>_<window>.png`; sheets and dialogs are
 * drawn over the app as they appear on screen. Robolectric's renderer
 * is not a phone: system fonts, no network images, no dynamic colour, no
 * system bars, and dialog windows fill the width without the device's side
 * margins. Look for those on the phone, not here.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualAuditCaptureTest(
    private val surface: VisualSurface,
    private val theme: ThemeMode,
) {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phone() = capture("phone", smoke = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp", fontScale = 2f)
    fun phoneLargeText() = capture("phone-text200")

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun foldable() = capture("foldable")

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tablet() = capture("tablet")

    private fun capture(window: String, smoke: Boolean = false) {
        val out = System.getenv(OUT_ENV)?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue(out != null || (smoke && theme == ThemeMode.LIGHT))

        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialThemeMode = theme,
            initialInteractionSideConfigured = true,
            initialAccountSessions = VisualFixtures.accounts,
            initialActiveAccountId = VisualFixtures.active.stableId,
            repository = VisualRepository(),
            identityMetadataRepository = VisualIdentityRepository,
        )
        if (!surface.firstRunDialogs) {
            SettingsBackupRepository(context, appVersion = "visual").dismissFolderOnboarding()
        }
        var comments by mutableStateOf(RedditCommentsUiState())
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = VisualFixtures.subscriptions),
                redditAccountHistoryState = VisualFixtures.profileComments,
                redditComments = CommentLoadingEntryPoints(
                    redditCommentsState = comments,
                    onLoadRedditComments = { _, postId, sort ->
                        val post = VisualFixtures.original(postId)
                        comments = VisualFixtures.comments(postId, sort, state.commentsForPost(post))
                    },
                ),
                redditInbox = InboxEntryPoints(redditInboxState = VisualFixtures.inbox),
            )
        }
        composeRule.waitForIdle()
        surface.open(state)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        if (surface.scrolled) {
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = bottom * 0.85f, endY = top + height * 0.15f)
            }
            composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            composeRule.onRoot().performTouchInput {
                swipeDown(startY = top + height * 0.4f, endY = top + height * 0.55f)
            }
            composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            composeRule.waitForIdle()
        }

        val image = captureScreen()
        if (out == null) return
        out.mkdirs()
        val name = "${surface.id}_${theme.name.lowercase()}_$window.png"
        File(out, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val OUT_ENV = "SUBSHELL_VISUAL_OUT"
        private const val SETTLE_MILLIS = 1_500L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> =
            VisualSurface.entries.flatMap { surface ->
                listOf(ThemeMode.LIGHT, ThemeMode.DARK).map { arrayOf<Any>(surface, it) }
            }
    }
}

/**
 * Draws every window the app has open (the activity, then sheets, dialogs
 * and popups in the order they were added) into one bitmap, dimming behind
 * windows that ask for it, so a sheet is captured the way it is seen.
 */
private fun captureScreen(): Bitmap {
    // WindowManagerGlobal is hidden from the SDK stubs; Robolectric runs the real class.
    val windowManagerGlobal = ReflectionHelpers.callStaticMethod<Any>(
        Class.forName("android.view.WindowManagerGlobal"),
        "getInstance",
    )
    val views = ReflectionHelpers.getField<List<View>>(windowManagerGlobal, "mViews")
        .filter { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
    val base = views.first()
    val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    views.forEach { view ->
        val params = view.layoutParams as? WindowManager.LayoutParams
        if (view !== base && params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
            canvas.drawColor(Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
        }
        // Robolectric does not lay out windows; place each by its gravity.
        val bounds = Rect()
        Gravity.apply(
            params?.gravity ?: Gravity.TOP,
            view.width,
            view.height,
            Rect(0, 0, base.width, base.height),
            params?.x ?: 0,
            params?.y ?: 0,
            bounds,
        )
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        view.draw(canvas)
        canvas.restore()
    }
    return bitmap
}

/** One surface of the app, opened from a freshly rendered SubshellApp. */
enum class VisualSurface(
    val id: String,
    val open: (SubshellState) -> Unit,
    /** Keeps the dialogs a fresh install opens by itself (backup folder). */
    val firstRunDialogs: Boolean = false,
    /**
     * Scrolls the list down and a little back up, so a route's top bar is seen
     * over scrolled content (#223: surfaceContainer, back after enterAlways).
     */
    val scrolled: Boolean = false,
) {
    HOME("home", {}),
    FIRST_RUN("first-run", {}, firstRunDialogs = true),
    DRAWER("drawer", { it.requestDrawer() }),
    ACCOUNT_SWITCHER("account-switcher", { it.requestAccountSwitcher() }),
    POST_TEXT("post-text", { it.openFromMainFeed(firstPost(PostKind.TEXT)) }),
    POST_IMAGE("post-image", { it.openFromMainFeed(firstPost(PostKind.IMAGE)) }),
    MEDIA_VIEWER("media-viewer", { it.openMedia(firstPost(PostKind.IMAGE)) }),
    COMMUNITY("community", { it.openSubreddit(SampleData.communities.first().name) }),
    COMMUNITY_SCROLLED("community-scrolled", { it.openSubreddit(SampleData.communities.first().name) }, scrolled = true),
    COMMUNITIES("communities", { it.navigate(MainDestination.SUBS) }),
    EXPLORE("explore", { it.openCommunityExplore() }),
    SEARCH("search", { it.openSearch(refocus = false) }),
    SEARCH_RESULTS("search-results", {
        it.openSearch(refocus = false)
        it.updateSearchQuery("android")
        it.submitSearch()
    }),
    INBOX("inbox", { it.navigate(MainDestination.INBOX) }),
    YOU("you", { it.navigate(MainDestination.YOU) }),
    YOU_COMMENTS("you-comments", {
        it.navigate(MainDestination.YOU)
        it.selectUserProfileTab(SampleData.LOCAL_USERNAME, 1)
    }),
    SETTINGS("settings", { it.openSecondary(SecondaryDestination.SETTINGS) }),
    HELP("help", { it.openSecondary(SecondaryDestination.HELP) }),
    POST_COMPOSER("post-composer", { it.openPostComposer() }),
    // #232: the comment composer and the reply composers, before the review.
    COMMENT_COMPOSER("comment-composer", {
        val post = firstPost(PostKind.TEXT)
        it.openFromMainFeed(post)
        it.openCommentComposer(post)
    }),
    COMMENT_COMPOSER_LONG("comment-composer-long", {
        val post = firstPost(PostKind.TEXT)
        it.openFromMainFeed(post)
        it.openCommentComposer(post)
        it.commentComposerDraft?.let { draft -> it.updateCommentComposerDraft(draft.copy(body = LONG_REPLY)) }
    }),
    COMMENT_REPLY_COMPOSER("comment-reply-composer", {
        val post = firstPost(PostKind.TEXT)
        it.openFromMainFeed(post)
        it.openCommentComposer(post, it.commentsForPost(VisualFixtures.original(post.id)).first())
    }),
    MESSAGE_CONVERSATION("message-conversation", {
        it.navigate(MainDestination.INBOX)
        it.openInboxItem(SampleData.inbox.first { item -> item.kind == ActivityKind.MESSAGE })
    }),
}

private val LONG_REPLY = List(6) { paragraph ->
    "Paragraph ${paragraph + 1}: a long reply with **bold**, *italic* and a [link](https://example.com), " +
        "written to see how the composer holds more than a quick comment."
}.joinToString("\n\n")

private fun firstPost(kind: PostKind) = VisualFixtures.posts.first { it.kind == kind }
