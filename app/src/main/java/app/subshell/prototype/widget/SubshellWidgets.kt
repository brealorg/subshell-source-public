package app.subshell.prototype.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.subshell.prototype.MainActivity
import app.subshell.prototype.inbox.INBOX_NOTIFICATION_ACCOUNT_EXTRA
import app.subshell.prototype.inbox.INBOX_NOTIFICATION_ACTION_OPEN
import app.subshell.prototype.inbox.INBOX_NOTIFICATION_EVENT_EXTRA
import app.subshell.prototype.inbox.INBOX_NOTIFICATION_ITEM_EXTRA
import app.subshell.prototype.inbox.NOTIFICATION_LAUNCH_KEY_EXTRA
import app.subshell.prototype.inbox.NotificationLaunchKey
import app.subshell.prototype.shortcuts.LauncherShortcutTarget
import app.subshell.prototype.shortcuts.launcherShortcutTargetIntent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ISSUE267_INBOX_AND_POSTS_WIDGETS_V1 (#267): two home-screen widgets, built
 * with Google's Glance and the launcher's Material You colours.
 * - Inbox: the unread count and the newest Inbox items; an item opens as its
 *   notification would (same launch key, same account rules), the header
 *   opens the Inbox.
 * - Posts: the stored posts of Home or one community the reader picks
 *   (`PostsWidgetConfigureActivity`); a post opens like a Reddit link.
 * They are drawn again whenever the stored data may have changed: after the
 * background Inbox check, when the app goes to the background, when the app
 * lock changes, and by the launcher every 30 minutes.
 */
internal object SubshellWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestRefresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext) }
    }

    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        listOf(InboxWidget(), PostsWidget()).forEach { widget ->
            try {
                widget.updateAll(appContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A widget that cannot be drawn now is drawn at its next refresh.
            }
        }
    }
}

class InboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InboxWidget()
}

class PostsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PostsWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        PostsWidgetSourceStore.forget(context, appWidgetIds)
    }
}

internal class InboxWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = SubshellWidgetData.inbox(context)
        val nowEpochSeconds = System.currentTimeMillis() / 1_000
        val actions = InboxWidgetActions(context)
        provideContent {
            GlanceTheme {
                InboxWidgetBody(content, nowEpochSeconds, actions)
            }
        }
    }
}

internal class PostsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val content = SubshellWidgetData.posts(context, PostsWidgetSourceStore.read(context, appWidgetId))
        val actions = PostsWidgetActions(context)
        provideContent {
            GlanceTheme {
                PostsWidgetBody(content, actions)
            }
        }
    }
}

/** The intents an Inbox widget starts; the item intent is the notification's own. */
internal class InboxWidgetActions(private val context: Context) {
    fun openApp(): Intent = mainActivityIntent(context)

    fun openInbox(accountStableId: String): Intent =
        launcherShortcutTargetIntent(LauncherShortcutTarget.Inbox(accountStableId, requestId = 0L))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openItem(accountStableId: String, itemId: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(INBOX_NOTIFICATION_ACTION_OPEN)
            .putExtra(INBOX_NOTIFICATION_ACCOUNT_EXTRA, accountStableId)
            .putExtra(INBOX_NOTIFICATION_ITEM_EXTRA, itemId)
            // A fresh key per drawing, so the same item opens again after the last tap.
            .putExtra(INBOX_NOTIFICATION_EVENT_EXTRA, "widget:$itemId:${System.nanoTime()}")
            .putExtra(NOTIFICATION_LAUNCH_KEY_EXTRA, NotificationLaunchKey.value(context))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** Posts and places open as Reddit links do, inside Subshell. */
internal class PostsWidgetActions(private val context: Context) {
    fun openApp(): Intent = mainActivityIntent(context)

    fun openSource(source: PostsWidgetSource): Intent = when (val community = source.community) {
        null -> mainActivityIntent(context)
        else -> openLink("https://www.reddit.com/r/$community/")
    }

    fun openLink(link: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

private fun mainActivityIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)

@Composable
internal fun InboxWidgetBody(
    content: InboxWidgetContent,
    nowEpochSeconds: Long,
    actions: InboxWidgetActions,
) {
    when (content) {
        InboxWidgetContent.SignedOut -> WidgetMessage(
            title = "Inbox",
            message = "Sign in to Reddit in subshell to see your inbox here.",
            onClick = actionStartActivity(actions.openApp()),
        )
        InboxWidgetContent.Locked -> WidgetMessage(
            title = "subshell",
            message = "subshell is locked. Open it to see your inbox.",
            onClick = actionStartActivity(actions.openApp()),
        )
        is InboxWidgetContent.NotStored -> WidgetMessage(
            title = "Inbox",
            message = "Open the Inbox in subshell once to fill this widget.",
            onClick = actionStartActivity(actions.openInbox(content.accountStableId)),
        )
        is InboxWidgetContent.Shown -> WidgetFrame(
            title = "Inbox",
            badge = content.unreadCount.takeIf { count -> count > 0 }?.let { count -> "$count unread" },
            onHeaderClick = actionStartActivity(actions.openInbox(content.accountStableId)),
        ) {
            if (content.rows.isEmpty()) {
                WidgetHint("Nothing in your inbox.")
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(content.rows, itemId = { row -> row.id.hashCode().toLong() }) { row ->
                        InboxWidgetRowView(
                            row = row,
                            age = widgetAge(row.createdEpochSeconds, nowEpochSeconds),
                            onClick = actionStartActivity(actions.openItem(content.accountStableId, row.id)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PostsWidgetBody(
    content: PostsWidgetContent,
    actions: PostsWidgetActions,
) {
    when (content) {
        PostsWidgetContent.SignedOut -> WidgetMessage(
            title = "Posts",
            message = "Sign in to Reddit in subshell to see posts here.",
            onClick = actionStartActivity(actions.openApp()),
        )
        PostsWidgetContent.Locked -> WidgetMessage(
            title = "subshell",
            message = "subshell is locked. Open it to see posts.",
            onClick = actionStartActivity(actions.openApp()),
        )
        is PostsWidgetContent.NotStored -> WidgetMessage(
            title = content.source.label,
            message = "Open ${content.source.label} in subshell once to fill this widget.",
            onClick = actionStartActivity(actions.openSource(content.source)),
        )
        is PostsWidgetContent.Shown -> WidgetFrame(
            title = content.source.label,
            badge = null,
            onHeaderClick = actionStartActivity(actions.openSource(content.source)),
        ) {
            if (content.rows.isEmpty()) {
                WidgetHint("No posts to show.")
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(content.rows, itemId = { row -> row.id.hashCode().toLong() }) { row ->
                        PostsWidgetRowView(
                            row = row,
                            showCommunity = content.source.community == null,
                            onClick = actionStartActivity(actions.openLink(row.link)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetFrame(
    title: String,
    badge: String?,
    onHeaderClick: Action,
    body: @Composable () -> Unit,
) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).clickable(onHeaderClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        body()
    }
}

@Composable
private fun WidgetMessage(title: String, message: String, onClick: Action) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(16.dp)
            .clickable(onClick),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = message,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

@Composable
private fun WidgetHint(text: String) {
    Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

@Composable
private fun InboxWidgetRowView(row: InboxWidgetRow, age: String?, onClick: Action) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick)
                .semantics {
                    contentDescription = buildString {
                        if (row.unread) append("Unread. ")
                        append(row.sender).append(". ").append(row.title).append(". ").append(row.preview)
                    }
                },
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.unread) {
                    Box(
                        GlanceModifier
                            .width(8.dp)
                            .height(8.dp)
                            .cornerRadius(4.dp)
                            .background(GlanceTheme.colors.primary),
                    ) {}
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    text = row.sender,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Medium,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (age != null) {
                    Text(
                        text = age,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = row.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (row.unread) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (row.preview.isNotBlank()) {
                Text(
                    text = row.preview,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun PostsWidgetRowView(row: PostsWidgetRow, showCommunity: Boolean, onClick: Action) {
    val meta = buildString {
        if (showCommunity) append("r/").append(row.community).append(" · ")
        append(widgetCount(row.score)).append(" points · ")
        append(widgetCount(row.comments)).append(if (row.comments == 1) " comment" else " comments")
    }
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick),
        ) {
            Text(
                text = row.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 3,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = meta,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 2,
            )
        }
    }
}
