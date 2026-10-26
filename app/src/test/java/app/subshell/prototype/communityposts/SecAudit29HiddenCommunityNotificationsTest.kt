package app.subshell.prototype.communityposts

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.hiddenCommunityNamesPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECAUDIT29 (security audit #29): a community hidden from the Communities
 * list can be watched for new posts, and its name then showed in the shade.
 * Its notifications now say no more than hidden content does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit29HiddenCommunityNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = "reddit:redreader:t2_me"

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, 0).edit().clear().commit()
    }

    private fun post(community: String) = Post(
        id = "t3_${community}1",
        community = community,
        author = "someone",
        age = "1m",
        title = "A title about $community",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        permalink = "/r/$community/comments/${community}1/slug/",
    )

    private fun shownTexts(): Set<String> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).activeNotifications
            .flatMap { shown ->
                listOf(
                    shown.notification.extras.getCharSequence("android.title").toString(),
                    shown.notification.extras.getCharSequence("android.text").toString(),
                )
            }
            .toSet()

    @Test
    fun aHiddenCommunitysPostsAreNotifiedWithoutItsName() {
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, 0).edit()
            .putStringSet(hiddenCommunityNamesPreferenceKey(account), setOf("SecretHobby"))
            .commit()

        assertEquals(1, CommunityPostNotificationCenter.postNewPosts(context, account, "secrethobby", listOf(post("secrethobby"))))

        val texts = shownTexts()
        assertEquals(setOf("subshell", "New posts"), texts)
    }

    @Test
    fun aVisibleCommunityKeepsItsName() {
        assertEquals(1, CommunityPostNotificationCenter.postNewPosts(context, account, "kotlin", listOf(post("kotlin"))))

        val texts = shownTexts()
        assertEquals(true, "r/kotlin" in texts)
        assertEquals(true, "A title about kotlin" in texts)
    }
}
