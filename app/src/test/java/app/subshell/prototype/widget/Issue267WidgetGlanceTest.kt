package app.subshell.prototype.widget

import android.app.Application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE267_INBOX_AND_POSTS_WIDGETS_V1 (#267): what the drawn widgets say, and
 * that a locked app shows no sender, message or post.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue267WidgetGlanceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val inbox = InboxWidgetContent.Shown(
        accountStableId = "reddit:me",
        unreadCount = 2,
        rows = listOf(
            InboxWidgetRow("t1_a", "Startech17", "Replied to your comment", "You staying around", true, 1_000),
            InboxWidgetRow("t1_b", "lostmyaccount", "Replied to your post", "Be able to zoom", false, 1_000),
        ),
    )

    @Test
    fun theInboxWidgetShowsTheUnreadCountAndTheNewestItems() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 300.dp))
        setContext(context)
        provideComposable {
            GlanceTheme { InboxWidgetBody(inbox, nowEpochSeconds = 1_000 + 2 * 3_600, InboxWidgetActions(context)) }
        }

        onNode(hasText("2 unread")).assertExists()
        onNode(hasText("Startech17")).assertExists()
        onNode(hasText("Be able to zoom")).assertExists()
        onAllNodes(hasText("2h")).assertCountEquals(2)
    }

    @Test
    fun aLockedInboxWidgetShowsNoContent() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 300.dp))
        setContext(context)
        provideComposable {
            GlanceTheme { InboxWidgetBody(InboxWidgetContent.Locked, 0, InboxWidgetActions(context)) }
        }

        onNode(hasText("subshell is locked")).assertExists()
        onNode(hasText("Startech17")).assertDoesNotExist()
        onNode(hasText("unread")).assertDoesNotExist()
    }

    @Test
    fun thePostsWidgetNamesItsPlaceAndShowsItsPosts() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 300.dp))
        setContext(context)
        provideComposable {
            GlanceTheme {
                PostsWidgetBody(
                    PostsWidgetContent.Shown(
                        source = PostsWidgetSource.Home,
                        rows = listOf(
                            PostsWidgetRow("t3_a", "Subshell 0.89 is out", "subshell", 1_234, 1, "https://www.reddit.com/r/subshell/"),
                        ),
                    ),
                    PostsWidgetActions(context),
                )
            }
        }

        onNode(hasText("Home")).assertExists()
        onNode(hasText("Subshell 0.89 is out")).assertExists()
        onNode(hasText("r/subshell · 1.2k points · 1 comment")).assertExists()
    }

    @Test
    fun aPostsWidgetWithNothingStoredSaysWhereToFillIt() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 300.dp))
        setContext(context)
        provideComposable {
            GlanceTheme {
                PostsWidgetBody(PostsWidgetContent.NotStored(PostsWidgetSource("norge")), PostsWidgetActions(context))
            }
        }

        onNode(hasText("Open r/norge in subshell once to fill this widget.")).assertExists()
    }
}
