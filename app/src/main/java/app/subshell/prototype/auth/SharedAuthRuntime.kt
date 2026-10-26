package app.subshell.prototype.auth

import android.app.Application
import androidx.lifecycle.viewmodel.CreationExtras
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AUTHSPLIT01_ONE_OWNER_OF_THE_AUTH_RUNTIME_V1: Google keeps one ViewModel per
 * screen area, and ViewModels do not reach into each other. The area
 * ViewModels of one activity share the runtime AuthConnectViewModel owns
 * (sign-in and accounts), handed to them through [AuthRuntimeKey] in their
 * CreationExtras, so they live in the same ViewModelStore and the runtime's
 * lifetime is the one it had. What an area needs from another goes through
 * here, never through the other ViewModel:
 * - [capabilityRoute]: the capability authorization callbacks of an area's
 *   coordinators, which the callback route table in AuthConnectViewModel
 *   hands on;
 * - [moderatedComments]: a moderator removal/approval of a comment, which the
 *   comment thread applies;
 * - [inboxBackgroundSync]: the one inbox sync schedule inbox and modmail keep;
 * - [capabilityAuthorizations]: a capability authorization another area
 *   completed, after which the composer policy is read again.
 */
internal class SharedAuthRuntime(
    val runtime: AuthApplicationRuntime,
    val application: Application,
) {
    val inboxBackgroundSync = InboxBackgroundSyncKeeper(application)

    // ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1 (#211): searches and discovery
    // send include_over_18 following "Show NSFW posts", read from the
    // settings file the moment the request starts (the setting is persisted
    // as it changes), so no UI call needs a new parameter.
    fun showNsfwPosts(): Boolean =
        application
            .getSharedPreferences("subshell_preferences", android.content.Context.MODE_PRIVATE)
            .getBoolean(app.subshell.prototype.ui.SubshellSettings.SHOW_NSFW_POSTS, false)

    /** The active account when it is a signed-in Reddit account. */
    fun activeAuthenticatedAccountId(): String? {
        val snapshot =
            runtime.accountSessionDirectory?.snapshot() ?: return null
        return snapshot.accounts
            .firstOrNull { account ->
                account.stableId == snapshot.activeAccountId &&
                    account.source == app.subshell.prototype.data.account.AccountSessionSource.AUTHENTICATED
            }
            ?.stableId
    }

    private val capabilityRoutes =
        ConcurrentHashMap<AuthCapabilityAuthorizationOwner, CapabilityCallbackRoute>()

    /** An area ViewModel registers the routes of the owners it holds, once, when it is made. */
    fun registerCapabilityRoutes(routes: Map<AuthCapabilityAuthorizationOwner, CapabilityCallbackRoute>) {
        capabilityRoutes.putAll(routes)
    }

    fun capabilityRoute(owner: AuthCapabilityAuthorizationOwner): CapabilityCallbackRoute? =
        capabilityRoutes[owner]

    private val _moderatedComments = MutableSharedFlow<ModeratedComment>(extraBufferCapacity = 64)
    val moderatedComments: SharedFlow<ModeratedComment> = _moderatedComments.asSharedFlow()

    fun commentModerated(comment: ModeratedComment) {
        _moderatedComments.tryEmit(comment)
    }

    private val _capabilityAuthorizations = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val capabilityAuthorizations: SharedFlow<Unit> = _capabilityAuthorizations.asSharedFlow()

    fun capabilityAuthorizationCompleted() {
        _capabilityAuthorizations.tryEmit(Unit)
    }
}

/**
 * One callback route per authorization owner: [handle] takes the result token
 * of a browser callback, [resume] a callback stored while the app was away.
 * Both reach only the owner's own coordinator and never the connect flow.
 */
internal class CapabilityCallbackRoute(
    val handle: suspend (resultToken: String, contextKey: String?) -> Unit,
    val resume: suspend (contextKey: String?) -> Unit,
)

internal data class ModeratedComment(
    val accountStableId: String,
    val commentFullname: String,
    val removed: Boolean,
)

internal object AuthRuntimeKey : CreationExtras.Key<SharedAuthRuntime>
