package app.subshell.prototype.auth

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import app.subshell.prototype.MainActivity
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTHSPLIT01: the moderator area has its own ViewModel (Google: one per
 * screen area). It shares the runtime AuthConnectViewModel owns, handed over
 * in its CreationExtras, and registers the capability callback routes of the
 * moderation owners there; the route table itself is
 * AuthConnectViewModelCallbackOwnerRoutingTest, the comment a moderator
 * removed reaching the thread Issue226RemovedCommentTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSplit01ModerationViewModelTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val moderationOwners = listOf(
        AuthCapabilityAuthorizationOwner.MODERATION_ACTION,
        AuthCapabilityAuthorizationOwner.MODERATION_REASON,
        AuthCapabilityAuthorizationOwner.MODMAIL_READ,
        AuthCapabilityAuthorizationOwner.MODERATION_LOG,
        AuthCapabilityAuthorizationOwner.MODERATION_USERS,
        AuthCapabilityAuthorizationOwner.MODERATION_TEAM,
    )

    private fun auth() = AuthConnectViewModel(application, unavailableAuthApplicationRuntime())

    @Test
    fun theFactoryBuildsItOnTheSharedRuntime() {
        val auth = auth()
        val moderation = ViewModelProvider.create(
            store = ViewModelStore(),
            factory = ModerationViewModel.Factory,
            extras = MutableCreationExtras().apply { set(AuthRuntimeKey, auth.sharedRuntime) },
        )[ModerationViewModel::class]

        assertNotNull(moderation)
        // Made on the shared runtime: its routes are registered there.
        assertNotNull(auth.sharedRuntime.capabilityRoute(AuthCapabilityAuthorizationOwner.MODERATION_TEAM))
    }

    @Test
    fun theModerationOwnersHaveARouteOnceItIsMade() {
        val auth = auth()
        moderationOwners.forEach { owner -> assertNull("$owner before", auth.sharedRuntime.capabilityRoute(owner)) }

        ModerationViewModel(auth.sharedRuntime)

        moderationOwners.forEach { owner -> assertNotNull("$owner after", auth.sharedRuntime.capabilityRoute(owner)) }
    }

    // MainActivity makes the area ViewModels (moderation, inbox) in the store it keeps the
    // AuthConnectViewModel in, so a callback finds the moderation routes.
    @Test
    fun mainActivityRegistersTheModerationRoutesInItsStore() {
        ensureWorkManagerInitialized(application)
        AuthRuntimePreparation.clearForTests()
        runBlocking { runCatching { AuthRuntimePreparation.prepare(application).await() } }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val auth = ViewModelProvider(controller.get(), AuthConnectViewModel.Factory)[AuthConnectViewModel::class.java]
            moderationOwners.forEach { owner -> assertNotNull("$owner", auth.sharedRuntime.capabilityRoute(owner)) }
            // AUTHSPLIT02: and the inbox read authorization's (InboxViewModel).
            assertNotNull(auth.sharedRuntime.capabilityRoute(AuthCapabilityAuthorizationOwner.INBOX_READ))
            // AUTHSPLIT03: and the post composing owners' (PostComposerViewModel).
            listOf(
                AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE,
                AuthCapabilityAuthorizationOwner.POST_EDIT,
                AuthCapabilityAuthorizationOwner.POST_DELETE,
                AuthCapabilityAuthorizationOwner.POST_MANAGE,
                AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR,
                AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS,
            ).forEach { owner -> assertNotNull("$owner", auth.sharedRuntime.capabilityRoute(owner)) }
            // AUTHSPLIT04: and the communities owners' (CommunitiesViewModel).
            listOf(
                AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP,
                AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE,
                AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ,
                AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE,
            ).forEach { owner -> assertNotNull("$owner", auth.sharedRuntime.capabilityRoute(owner)) }
            // AUTHSPLIT05: every owner has an area that registered its route.
            AuthCapabilityAuthorizationOwner.entries.forEach { owner ->
                assertNotNull("$owner", auth.sharedRuntime.capabilityRoute(owner))
            }
        } finally {
            controller.destroy()
            AuthRuntimePreparation.clearForTests()
        }
    }
}
