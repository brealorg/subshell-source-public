package app.subshell.prototype.communityposts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.subshell.prototype.MainActivity
import app.subshell.prototype.R
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.applock.hidesNotificationContent
import app.subshell.prototype.data.auth.CommunityComment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.hiddenCommunityNamesPreferenceKey

internal const val COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID = "subshell_community_posts"
internal const val COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID = "subshell_community_comments"

/** What one new-post notification says; NSFW posts and hidden content say less. */
internal data class CommunityPostNotificationText(val title: String, val text: String)

// ISSUE239_NSFW_POSTS_ARE_NOTIFIED_WITHOUT_THEIR_TITLE_V1 (owner): the lock
// screen may be seen by others. ISSUE38_APP_LOCK_V1: "Hide notification
// content" says no more than the lock-screen version.
internal fun communityPostNotificationText(
    communityName: String,
    post: Post,
    hideContent: Boolean,
): CommunityPostNotificationText = when {
    hideContent -> CommunityPostNotificationText(title = "subshell", text = COMMUNITY_POSTS_PUBLIC_TEXT)
    post.nsfw -> CommunityPostNotificationText(title = "r/$communityName", text = "New NSFW post")
    else -> CommunityPostNotificationText(title = "r/$communityName", text = post.title.ifBlank { "New post" })
}

/** The post's Reddit link, opened in Subshell like any shared Reddit link. */
internal fun communityPostLink(communityName: String, post: Post): String {
    val permalink = post.permalink?.trim()?.takeIf { it.startsWith("/r/", ignoreCase = true) }
    return "https://www.reddit.com" + (permalink ?: "/r/$communityName/comments/${post.id.removePrefix("t3_")}/")
}

// ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: who wrote it and where; a
// comment in an NSFW post says neither its post nor its text.
internal fun communityCommentNotificationText(
    communityName: String,
    comment: CommunityComment,
    hideContent: Boolean,
): CommunityPostNotificationText {
    val body = comment.body.replace(Regex("\\s+"), " ").trim().take(COMMENT_TEXT_MAX_CHARS)
    return when {
        hideContent -> CommunityPostNotificationText(title = "subshell", text = COMMUNITY_COMMENTS_PUBLIC_TEXT)
        comment.nsfw -> CommunityPostNotificationText(title = "r/$communityName", text = "New comment in an NSFW post")
        else -> CommunityPostNotificationText(
            title = "r/$communityName · u/${comment.author}",
            text = if (comment.postTitle.isBlank()) body else "On “${comment.postTitle}”: $body",
        )
    }
}

/** The comment's Reddit link, opened in Subshell like any shared Reddit link. */
internal fun communityCommentLink(communityName: String, comment: CommunityComment): String {
    val permalink = comment.permalink?.trim()?.takeIf { it.startsWith("/r/", ignoreCase = true) }
    return "https://www.reddit.com" + (permalink ?: "/r/$communityName/comments/")
}

internal object CommunityPostNotificationCenter {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID,
                "New posts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New posts in the communities you get notifications from"
            },
        )
    }

    fun ensureCommentChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID,
                "New comments",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New comments in the communities you get notifications from"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        val appContext = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    /** One notification per new post, grouped per community (owner). Returns how many were posted. */
    fun postNewPosts(
        context: Context,
        accountStableId: String,
        communityName: String,
        posts: List<Post>,
    ): Int {
        if (posts.isEmpty()) return 0
        ensureChannel(context.applicationContext)
        return postGroup(
            context = context,
            accountStableId = accountStableId,
            communityName = communityName,
            channelId = COMMUNITY_POSTS_NOTIFICATION_CHANNEL_ID,
            publicText = COMMUNITY_POSTS_PUBLIC_TEXT,
            items = posts.map { post ->
                GroupItem(
                    key = "community_post:$accountStableId:${post.id}",
                    text = { hide -> communityPostNotificationText(communityName, post, hide) },
                    link = communityPostLink(communityName, post),
                )
            },
        )
    }

    /** ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: as [postNewPosts], on its own channel. */
    fun postNewComments(
        context: Context,
        accountStableId: String,
        communityName: String,
        comments: List<CommunityComment>,
    ): Int {
        if (comments.isEmpty()) return 0
        ensureCommentChannel(context.applicationContext)
        return postGroup(
            context = context,
            accountStableId = accountStableId,
            communityName = communityName,
            channelId = COMMUNITY_COMMENTS_NOTIFICATION_CHANNEL_ID,
            publicText = COMMUNITY_COMMENTS_PUBLIC_TEXT,
            items = comments.map { comment ->
                GroupItem(
                    key = "community_comment:$accountStableId:${comment.id}",
                    text = { hide -> communityCommentNotificationText(communityName, comment, hide) },
                    link = communityCommentLink(communityName, comment),
                )
            },
        )
    }

    private class GroupItem(
        val key: String,
        val text: (hideContent: Boolean) -> CommunityPostNotificationText,
        val link: String,
    )

    @SuppressLint("MissingPermission") // canPost checked the permission first.
    private fun postGroup(
        context: Context,
        accountStableId: String,
        communityName: String,
        channelId: String,
        publicText: String,
        items: List<GroupItem>,
    ): Int {
        val appContext = context.applicationContext
        if (items.isEmpty() || !canPost(appContext)) return 0
        val manager = NotificationManagerCompat.from(appContext)
        // SECAUDIT29_HIDDEN_COMMUNITIES_ARE_NOTIFIED_WITHOUT_THEIR_NAME_V1
        // (security audit #29): a community the reader hid from the Communities
        // list can still be watched; its notifications say no more than hidden
        // content does, so its name does not appear in the shade.
        val hideContent = AppLockSettingsStore.read(appContext).hidesNotificationContent ||
            isHiddenCommunity(appContext, accountStableId, communityName)
        val group = groupKey(channelId, accountStableId, communityName)
        val publicVersion = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_subshell)
            .setContentTitle("subshell")
            .setContentText(publicText)
            .build()
        var posted = 0
        items.forEach { item ->
            val text = item.text(hideContent)
            val open = PendingIntent.getActivity(
                appContext,
                item.key.hashCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                    .setClass(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle(text.title)
                .setContentText(text.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(group)
                .build()
            runCatching { manager.notify(group, item.key.hashCode(), notification) }
                .onSuccess { posted += 1 }
        }
        if (posted > 0) {
            val summaryText = items.first().text(hideContent)
            val summary = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle(if (hideContent) summaryText.title else "r/$communityName")
                .setContentText(publicText)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(group, SUMMARY_NOTIFICATION_ID, summary) }
        }
        return posted
    }

    private fun isHiddenCommunity(context: Context, accountStableId: String, communityName: String): Boolean =
        runCatching {
            context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE)
                .getStringSet(hiddenCommunityNamesPreferenceKey(accountStableId), emptySet())
                .orEmpty()
                .any { hidden -> hidden.equals(communityName.removePrefix("r/"), ignoreCase = true) }
        }.getOrDefault(false)

    private fun groupKey(channelId: String, accountStableId: String, communityName: String): String =
        "$channelId:" + Base64.encodeToString(
            accountStableId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        ) + ":" + communityName.lowercase()
}

private const val COMMUNITY_POSTS_PUBLIC_TEXT = "New posts"
private const val COMMUNITY_COMMENTS_PUBLIC_TEXT = "New comments"
private const val COMMENT_TEXT_MAX_CHARS = 300
private const val SUMMARY_NOTIFICATION_ID = 0
