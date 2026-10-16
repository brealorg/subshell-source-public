package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.ui.NewMessagePrefill
import app.subshell.prototype.ui.normalizedPostIdentity

/**
 * One-shot UI requests: the monotonic counters that screens observe
 * (drawer, account switcher, home scroll-to-top, search focus, foreground
 * resume) and the pending post-action tokens consumed once by the screen
 * that owns the action. Extracted verbatim from SubshellState, which holds
 * one instance and delegates to it; the clusters that raise a counter
 * still do so inline through delegated properties. Behaviour is unchanged.
 */
internal class UiRequestState {
    var searchFocusRequest by mutableIntStateOf(0)
    var communityExploreSearchFocusRequest by mutableIntStateOf(0)
        internal set
    var drawerOpenRequest by mutableIntStateOf(0)
    var accountSwitcherOpenRequest by mutableIntStateOf(0)
    var homeScrollToTopRequest by mutableIntStateOf(0)
    private var handledHomeScrollToTopRequest = 0
    var foregroundResumeRequest by mutableIntStateOf(0)
        internal set
    var pendingPostEditId: String? = null
        internal set
    var pendingPostDeleteId: String? = null
        internal set
    var pendingPostManageId: String? = null
        internal set

    internal fun consumeHomeScrollToTopRequest(): Boolean {
        val request = homeScrollToTopRequest
        if (request <= handledHomeScrollToTopRequest) return false
        handledHomeScrollToTopRequest = request
        return true
    }

    fun requestDrawer() {
        drawerOpenRequest += 1
    }

    fun requestAccountSwitcher() {
        accountSwitcherOpenRequest += 1
    }

    // REDDITURL04_COMPOSE_LINK_V1: a compose link opens New message with
    // the recipient and text filled in; the screen takes it once.
    var pendingNewMessagePrefill by mutableStateOf<NewMessagePrefill?>(null)
        private set

    fun requestNewMessagePrefill(prefill: NewMessagePrefill) {
        pendingNewMessagePrefill = prefill
    }

    fun consumeNewMessagePrefill(): NewMessagePrefill? =
        pendingNewMessagePrefill.also { pendingNewMessagePrefill = null }

    // ISSUE33_SHARE_INTO_SUBSHELL_V1: the composer opens its community picker
    // once for this draft (a share leaves the community empty).
    var pendingComposerCommunityPickerDraftId by mutableStateOf<String?>(null)
        private set

    fun requestComposerCommunityPicker(draftId: String) {
        pendingComposerCommunityPickerDraftId = draftId
    }

    fun consumeComposerCommunityPicker(draftId: String): Boolean {
        if (pendingComposerCommunityPickerDraftId != draftId) return false
        pendingComposerCommunityPickerDraftId = null
        return true
    }

    // REDDITURL06_WIKI_PAGE_LINK_V1: a wiki page to open in a community's
    // about sheet; that community's screen takes it once.
    var pendingCommunityWikiPage by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun requestCommunityWikiPage(communityName: String, page: String) {
        pendingCommunityWikiPage = communityName to page
    }

    fun consumeCommunityWikiPage(communityName: String): String? {
        val (community, page) = pendingCommunityWikiPage ?: return null
        if (!community.equals(communityName, ignoreCase = true)) return null
        pendingCommunityWikiPage = null
        return page
    }

    // REDDITURL04_MULTIREDDIT_LINK_V1: a custom feed path to open from a
    // link; SubshellApp owns the open feed and takes it once.
    var pendingCustomFeedLinkPath by mutableStateOf<String?>(null)
        private set

    fun requestCustomFeedLink(path: String) {
        pendingCustomFeedLinkPath = path
    }

    fun consumeCustomFeedLinkPath(): String? =
        pendingCustomFeedLinkPath.also { pendingCustomFeedLinkPath = null }

    fun requestPostManage(postId: String) {
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isNotEmpty()) {
            pendingPostManageId = normalizedPostId
        }
    }

    fun consumePostManageRequest(postId: String): Boolean {
        val normalizedPostId = normalizedPostIdentity(postId)
        val requested =
            normalizedPostId.isNotEmpty() &&
                pendingPostManageId == normalizedPostId
        if (requested) {
            pendingPostManageId = null
        }
        return requested
    }

    fun requestPostDelete(postId: String) {
        val normalizedPostId = normalizedPostIdentity(postId)
        if (normalizedPostId.isNotEmpty()) pendingPostDeleteId = normalizedPostId
    }

    fun consumePostDeleteRequest(postId: String): Boolean {
        val normalizedPostId = normalizedPostIdentity(postId)
        val requested = normalizedPostId.isNotEmpty() && pendingPostDeleteId == normalizedPostId
        if (requested) pendingPostDeleteId = null
        return requested
    }

    fun requestPostEdit(postId: String) {
        val normalizedPostId = postId.trim().removePrefix("t3_")
        if (normalizedPostId.isNotEmpty()) {
            pendingPostEditId = normalizedPostId
        }
    }

    fun consumePostEditRequest(postId: String): Boolean {
        val normalizedPostId = postId.trim().removePrefix("t3_")
        val requested =
            normalizedPostId.isNotEmpty() &&
                pendingPostEditId == normalizedPostId
        if (requested) {
            pendingPostEditId = null
        }
        return requested
    }

    fun consumeCommunityExploreSearchFocusRequest(request: Int) {
        if (communityExploreSearchFocusRequest == request) {
            communityExploreSearchFocusRequest = 0
        }
    }
}
