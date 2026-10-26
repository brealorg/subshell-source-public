package app.subshell.prototype.ui

import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.data.valueOrNull
import app.subshell.prototype.model.PostKind
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val SUBSHELL_PROCESS_SNAPSHOT_VERSION = "v1"
// LOWUI1_PROCESS_SNAPSHOT_FITS_THE_BUNDLE_V1 (OCB-098): 750 000 chars parcel
// to about 1.5 MB (UTF-16), over the ~1 MB Binder limit of the saved state.
internal const val SUBSHELL_PROCESS_SNAPSHOT_MAX_CHARS = 250_000
// LOWUI6_PROFILE_TAB_SURVIVES_PROCESS_DEATH_V1 (OCB-097): the own profile has
// eight destinations (Posts … Drafts); a tab past Comments was restored as 2.
internal val SUBSHELL_PROCESS_SNAPSHOT_MAX_PROFILE_TAB: Int
    get() = app.subshell.prototype.ui.screens.PROFILE_OWN_DESTINATION_LABELS.lastIndex
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_ROUTES = 32
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_ANCHORS = 256
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_FIELD_CHARS = 100_000
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_INDEX = 1_000_000
// RECREATE01 (#241): message drafts share a budget so one long draft cannot push
// the whole snapshot over SUBSHELL_PROCESS_SNAPSHOT_MAX_CHARS (then nothing is
// restored); settled votes and saves are capped per kind.
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_DRAFT_CHARS = 20_000
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES = 256
private const val SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_MEDIA = 16

internal data class SubshellProcessSnapshot(
    val routeStack: SubshellRouteStack,
    val scrollAnchors: Map<FeedScrollKey, FeedScrollAnchor>,
    val userProfileTabs: Map<String, Int>,
    val postComposerDraft: PostComposerDraft,
    val feedVideoMuted: Boolean,
    val backgroundResumeMediaId: String?,
    val backgroundResumeWasManual: Boolean,
    val commentComposerVisible: Boolean = false,
    val commentComposerDraft: CommentComposerDraft? = null,
    val messageConversationDrafts: Map<String, String> = emptyMap(),
    val messageConversationMedia: Map<String, ComposerMediaAttachment> = emptyMap(),
    val settledOverrides: SettledAccountOverrides? = null,
)

// RECREATE01_SETTLED_VOTES_SURVIVE_RECREATION_V1 (#241): the votes and saves
// Reddit confirmed for one signed-in account, restored only to that account.
internal data class SettledAccountOverrides(
    val accountStableId: String,
    val votes: Map<String, Int>,
    val saves: Map<String, Boolean>,
    val hidden: Map<String, Boolean> = emptyMap(),
    val follows: Map<String, Boolean> = emptyMap(),
    val blocks: Map<String, Boolean> = emptyMap(),
)

// ISSUE48_RESTORE_LOOKS_UP_THE_CACHE_V1: restore validates each saved route
// by looking its post or community up (the same cache the screens read), not
// by membership in list pages. The production repository's default post and
// inbox pages are always empty and its community page stops at 100 rows, so
// the old catalog dropped every open post, media viewer and most communities.
internal class SubshellProcessRestoreCatalog(
    private val postKindLookup: (String) -> PostKind?,
    private val cachedCommunityLookup: (String) -> String?,
) {
    constructor(
        postKindsById: Map<String, PostKind>,
        canonicalCommunitiesByNormalizedName: Map<String, String>,
    ) : this(
        postKindLookup = postKindsById::get,
        cachedCommunityLookup = { name ->
            canonicalCommunitiesByNormalizedName[normalizeFeedScrollToken(name)]
        },
    )

    private val postKinds = mutableMapOf<String, PostKind?>()

    private fun postKind(postId: String): PostKind? =
        postKinds.getOrPut(postId) { postKindLookup(postId) }

    fun canonicalCommunity(name: String?): String? {
        val requested = name
            ?.trim()
            ?.removePrefix("/r/")
            ?.removePrefix("r/")
            ?.takeIf(::isRestorableCommunityName)
            ?: return null
        return cachedCommunityLookup(requested) ?: requested
    }

    fun containsPost(postId: String?): Boolean =
        postId != null && postKind(postId) != null

    fun containsMediaPost(postId: String?): Boolean =
        postId != null && postKind(postId) in setOf(
            PostKind.VIDEO,
            PostKind.IMAGE,
            PostKind.GIF,
            PostKind.GALLERY,
        )
}

private const val RESTORABLE_COMMUNITY_NAME_MAX_LENGTH = 100
private const val RESTORABLE_ITEM_ID_MAX_LENGTH = 200

private fun isRestorableCommunityName(name: String): Boolean =
    name.length in 1..RESTORABLE_COMMUNITY_NAME_MAX_LENGTH &&
        name.all { character -> character.isLetterOrDigit() || character == '_' }

// ISSUE49_COMMENT_DRAFT_SURVIVES_RESTORE_V1: an unsent reply is the user's
// own text; it is kept for any well-formed post id, cached or not.
// ISSUE48_CONVERSATION_WAITS_FOR_THE_LIVE_INBOX_V1: a conversation route is
// kept for any well-formed id; its host waits for the live inbox.
internal fun isRestorableItemId(id: String): Boolean =
    id.isNotBlank() &&
        id.length <= RESTORABLE_ITEM_ID_MAX_LENGTH &&
        id.none(Char::isWhitespace)

internal fun subshellProcessRestoreCatalog(
    repository: SubshellRepository,
): SubshellProcessRestoreCatalog =
    SubshellProcessRestoreCatalog(
        postKindLookup = { postId -> repository.post(postId).valueOrNull()?.kind },
        cachedCommunityLookup = { name -> repository.community(name).valueOrNull()?.name },
    )

internal fun defaultSubshellProcessRestoreCatalog(): SubshellProcessRestoreCatalog =
    subshellProcessRestoreCatalog(createFixtureFreeLocalSubshellRepository())

internal fun encodeSubshellProcessSnapshot(snapshot: SubshellProcessSnapshot): String {
    val records = mutableListOf(snapshotRecord("VERSION", SUBSHELL_PROCESS_SNAPSHOT_VERSION))
    // ISSUE19_COMMENT_MEDIA_EPHEMERAL_PROCESS_OVERLAY_V1
    // LOWUI_DEEP_STACK_KEEPS_THE_CURRENT_SCREEN_V1 (OCB-094): past the cap the
    // root and the newest routes are kept (the screen on top), not the oldest.
    snapshot.routeStack.entries
        .filterNot { route -> route is SubshellRoute.CommentMedia }
        .let { routes ->
            if (routes.size <= SUBSHELL_PROCESS_SNAPSHOT_MAX_ROUTES) {
                routes
            } else {
                listOf(routes.first()) + routes.takeLast(SUBSHELL_PROCESS_SNAPSHOT_MAX_ROUTES - 1)
            }
        }
        .forEach { route -> records += encodeRouteRecord(route) }

    // OCB-095: the store's map is in use order; the most recent are kept,
    // then written sorted so the same anchors always encode the same bytes.
    snapshot.scrollAnchors.entries
        .toList()
        .takeLast(SUBSHELL_PROCESS_SNAPSHOT_MAX_ANCHORS)
        .map { (key, anchor) -> encodeAnchorRecord(key, anchor) }
        .sorted()
        .forEach(records::add)

    snapshot.userProfileTabs
        .toSortedMap()
        .forEach { (username, tab) ->
            records += snapshotRecord(
                "USER_TAB",
                normalizeFeedScrollToken(username),
                tab.coerceIn(0, SUBSHELL_PROCESS_SNAPSHOT_MAX_PROFILE_TAB).toString(),
            )
        }
    // LOWUI4_UNREAD_TAB_STATE_RETIRED_V1: no USER_ACTIVITY_TAB, LIBRARY_TAB or
    // YOU_TAB records any more; an older snapshot's are ignored on decode.
    records += encodeComposerRecord(snapshot.postComposerDraft)
    snapshot.commentComposerDraft?.let { draft ->
        records += encodeCommentComposerRecord(
            visible = snapshot.commentComposerVisible,
            draft = draft,
        )
    }
    records += encodeMessageDraftRecords(snapshot.messageConversationDrafts)
    records += encodeMessageMediaRecords(snapshot.messageConversationMedia)
    snapshot.settledOverrides?.let { overrides -> records += encodeSettledOverrideRecords(overrides) }
    records += snapshotRecord(
        "MEDIA_STATE",
        snapshot.feedVideoMuted.toString(),
        snapshot.backgroundResumeMediaId,
        (snapshot.backgroundResumeWasManual && snapshot.backgroundResumeMediaId != null).toString(),
    )

    return records.joinToString("\n")
}

// LOWUI1_PROCESS_SNAPSHOT_FITS_THE_BUNDLE_V1 (OCB-098): a snapshot over the
// budget is not saved (the app starts fresh after process death) instead of
// throwing inside the Saver and crashing the app on every save.
internal fun subshellProcessSnapshotWithinBudget(encoded: String): String? =
    encoded.takeIf { it.length <= SUBSHELL_PROCESS_SNAPSHOT_MAX_CHARS }

internal fun decodeSubshellProcessSnapshot(
    encoded: String?,
    catalog: SubshellProcessRestoreCatalog = defaultSubshellProcessRestoreCatalog(),
): SubshellProcessSnapshot? {
    if (encoded.isNullOrBlank() || encoded.length > SUBSHELL_PROCESS_SNAPSHOT_MAX_CHARS) return null

    return runCatching {
        val records = encoded.lineSequence()
            .filter(String::isNotBlank)
            .map { line -> decodeSnapshotRecord(line) ?: error("Malformed snapshot record") }
            .toList()
        val version = records.firstOrNull { record -> record.kind == "VERSION" }
            ?.fields
            ?.firstOrNull()
        if (version != SUBSHELL_PROCESS_SNAPSHOT_VERSION) return null

        val routeStack = decodeRouteStack(records, catalog) ?: return null
        val scrollAnchors = decodeAnchors(records, catalog)
        val userTabs = decodeTabMap(records, "USER_TAB", max = SUBSHELL_PROCESS_SNAPSHOT_MAX_PROFILE_TAB)
        val composer = decodeComposer(records, catalog)
        val commentComposer = decodeCommentComposer(records, catalog, routeStack)
        val mediaState = decodeMediaState(records, catalog)
        val messageDrafts = decodeMessageDrafts(records)
        val messageMedia = decodeMessageMedia(records)
        val settledOverrides = decodeSettledOverrides(records)

        SubshellProcessSnapshot(
            routeStack = routeStack,
            scrollAnchors = scrollAnchors,
            userProfileTabs = userTabs,
            postComposerDraft = composer,
            commentComposerVisible = commentComposer.visible,
            commentComposerDraft = commentComposer.draft,
            feedVideoMuted = mediaState.feedVideoMuted,
            backgroundResumeMediaId = mediaState.backgroundResumeMediaId,
            backgroundResumeWasManual = mediaState.backgroundResumeWasManual,
            messageConversationDrafts = messageDrafts,
            messageConversationMedia = messageMedia,
            settledOverrides = settledOverrides,
        )
    }.getOrNull()
}

private data class SnapshotRecord(
    val kind: String,
    val fields: List<String?>,
)

private data class ProcessCommentComposerState(
    val visible: Boolean,
    val draft: CommentComposerDraft?,
)

private data class ProcessMediaState(
    val feedVideoMuted: Boolean,
    val backgroundResumeMediaId: String?,
    val backgroundResumeWasManual: Boolean,
)

private fun snapshotRecord(kind: String, vararg fields: String?): String =
    buildList {
        add(kind)
        fields.forEach { field -> add(encodeSnapshotField(field)) }
    }.joinToString("|")

private fun encodeSnapshotField(value: String?): String = when (value) {
    null -> "n"
    else -> "s" + Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeSnapshotRecord(line: String): SnapshotRecord? {
    val parts = line.split('|')
    val kind = parts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
    val fields = mutableListOf<String?>()
    for (token in parts.drop(1)) {
        val value = when {
            token == "n" -> null
            token.startsWith("s") -> runCatching {
                String(
                    Base64.getUrlDecoder().decode(token.drop(1)),
                    StandardCharsets.UTF_8,
                )
            }.getOrNull() ?: return null
            else -> return null
        }
        if (value != null && value.length > SUBSHELL_PROCESS_SNAPSHOT_MAX_FIELD_CHARS) return null
        fields += value
    }
    return SnapshotRecord(kind = kind, fields = fields)
}

private fun encodeRouteRecord(route: SubshellRoute): String = when (route) {
    is SubshellRoute.Main -> snapshotRecord(
        "ROUTE",
        "MAIN",
        route.destination.name,
        route.source.name,
        route.sort.name,
    )
    is SubshellRoute.Community -> snapshotRecord(
        "ROUTE",
        "COMMUNITY",
        route.destination.name,
        route.communityName,
        route.sort.name,
    )
    is SubshellRoute.Search -> snapshotRecord(
        "ROUTE",
        "SEARCH",
        route.destination.name,
        route.originCommunity,
        route.query,
        route.scope.name,
        route.area.name,
        route.submittedQuery,
    )
    is SubshellRoute.Post -> snapshotRecord(
        "ROUTE",
        "POST",
        route.postRoute.postId,
        route.postRoute.focusedCommentId,
        route.postRoute.visitId.toString(),
        encodeFeedPostOrigin(route.postRoute.origin),
    )
    is SubshellRoute.User -> snapshotRecord(
        "ROUTE",
        "USER",
        route.destination.name,
        route.username,
    )
    is SubshellRoute.Conversation -> snapshotRecord(
        "ROUTE",
        "CONVERSATION",
        route.destination.name,
        route.inboxItemId,
    )
    is SubshellRoute.Secondary -> snapshotRecord(
        "ROUTE",
        "SECONDARY",
        route.destination.name,
        route.secondaryDestination.name,
    )
    is SubshellRoute.Media -> snapshotRecord(
        "ROUTE",
        "MEDIA",
        route.postId,
        route.index.toString(),
    )
    is SubshellRoute.CommentMedia ->
        error("Ephemeral comment media overlays must be filtered before process snapshot encoding")
    SubshellRoute.PostComposer -> snapshotRecord("ROUTE", "POST_COMPOSER")
}

private fun decodeRouteStack(
    records: List<SnapshotRecord>,
    catalog: SubshellProcessRestoreCatalog,
): SubshellRouteStack? {
    val routeRecords = records
        .asSequence()
        .filter { record -> record.kind == "ROUTE" }
        .take(SUBSHELL_PROCESS_SNAPSHOT_MAX_ROUTES)
        .toList()
    val rootRecord = routeRecords.firstOrNull() ?: return null
    if (rootRecord.fields.firstOrNull() != "MAIN") return null

    val rootSource = rootRecord.fields.getOrNull(2)
        .enumOrDefault(FeedSource.HOME)
    val rootSort = rootRecord.fields.getOrNull(3)
        .enumOrDefault(FeedSort.BEST)
    var stack = SubshellRouteStack.forDestination(
        destination = MainDestination.HOME,
        source = rootSource,
        sort = rootSort,
    )
    var dropDependentRouteRecords = false

    routeRecords.drop(1).forEach { record ->
        if (dropDependentRouteRecords) return@forEach
        when (record.fields.firstOrNull()) {
            "MAIN" -> {
                val destination = record.fields.getOrNull(1)
                    .enumOrDefault(MainDestination.HOME)
                val source = record.fields.getOrNull(2)
                    .enumOrDefault(FeedSource.HOME)
                val sort = record.fields.getOrNull(3)
                    .enumOrDefault(FeedSort.BEST)
                if (destination != MainDestination.HOME) {
                    stack = stack.push(
                        SubshellRoute.Main(
                            destination = destination,
                            source = source,
                            sort = sort,
                        ),
                    )
                }
            }
            "COMMUNITY" -> {
                val communityName = catalog.canonicalCommunity(record.fields.getOrNull(2))
                    ?: return@forEach
                stack = stack.push(
                    SubshellRoute.Community(
                        destination = record.fields.getOrNull(1)
                            .enumOrDefault(stack.current.destinationOrNull() ?: MainDestination.HOME),
                        communityName = communityName,
                        sort = record.fields.getOrNull(3)
                            .enumOrDefault(CommunitySort.HOT),
                    ),
                )
            }
            "SEARCH" -> {
                val originCommunity = catalog.canonicalCommunity(record.fields.getOrNull(2))
                val requestedArea = record.fields.getOrNull(5)
                    .enumOrDefault(SearchArea.ALL_SUBSHELL)
                val area = if (
                    requestedArea == SearchArea.CURRENT_COMMUNITY &&
                    originCommunity == null
                ) {
                    SearchArea.ALL_SUBSHELL
                } else {
                    requestedArea
                }
                val requestedScope = record.fields.getOrNull(4)
                    .enumOrDefault(SearchScope.POSTS)
                val scope = normalizeSearchScope(requestedScope, area)
                val draftQuery = record.fields.getOrNull(3).orEmpty()
                stack = stack.push(
                    SubshellRoute.Search(
                        destination = record.fields.getOrNull(1)
                            .enumOrDefault(stack.current.destinationOrNull() ?: MainDestination.HOME),
                        originCommunity = originCommunity,
                        query = draftQuery,
                        submittedQuery = record.fields.getOrNull(6) ?: draftQuery,
                        scope = scope,
                        area = area,
                    ),
                )
            }
            "POST" -> {
                val postId = record.fields.getOrNull(1)
                    ?.takeIf(catalog::containsPost)
                if (postId == null) {
                    dropDependentRouteRecords = true
                    return@forEach
                }
                val fallbackOrigin = stack.captureProcessPostOrigin()
                val decodedOrigin = decodeFeedPostOrigin(record.fields.getOrNull(4))
                val origin = sanitizeProcessPostOrigin(
                    origin = decodedOrigin,
                    fallback = fallbackOrigin,
                    catalog = catalog,
                )
                stack = stack.push(
                    SubshellRoute.Post(
                        FeedPostRoute(
                            postId = postId,
                            focusedCommentId = record.fields.getOrNull(2)
                                ?.takeIf(String::isNotBlank),
                            visitId = record.fields.getOrNull(3)
                                .toSnapshotInt(default = 0),
                            origin = origin,
                        ),
                    ),
                )
            }
            "USER" -> {
                val username = record.fields.getOrNull(2)
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: return@forEach
                stack = stack.push(
                    SubshellRoute.User(
                        destination = record.fields.getOrNull(1)
                            .enumOrDefault(stack.current.destinationOrNull() ?: MainDestination.HOME),
                        username = username,
                    ),
                )
            }
            "CONVERSATION" -> {
                val inboxItemId = record.fields.getOrNull(2)
                    ?.takeIf(::isRestorableItemId)
                    ?: return@forEach
                stack = stack.push(
                    SubshellRoute.Conversation(
                        destination = record.fields.getOrNull(1)
                            .enumOrDefault(MainDestination.INBOX),
                        inboxItemId = inboxItemId,
                    ),
                )
            }
            // LOWUI4_LIBRARY_RETIRED_V1: a Library route from an older build is
            // skipped, so the restore shows the screen it was opened from.
            "SECONDARY" -> if (record.fields.getOrNull(2) != "LIBRARY") {
                stack = stack.push(
                    SubshellRoute.Secondary(
                        destination = record.fields.getOrNull(1)
                            .enumOrDefault(stack.current.destinationOrNull() ?: MainDestination.HOME),
                        secondaryDestination = record.fields.getOrNull(2)
                            .enumOrDefault(SecondaryDestination.SETTINGS),
                    ),
                )
            }
            "MEDIA" -> {
                val postId = record.fields.getOrNull(1)
                    ?.takeIf(catalog::containsMediaPost)
                    ?: return@forEach
                stack = stack.push(
                    SubshellRoute.Media(
                        postId = postId,
                        index = record.fields.getOrNull(2)
                            .toSnapshotInt(default = 0),
                    ),
                )
            }
            "POST_COMPOSER" -> if (stack.current !is SubshellRoute.PostComposer) {
                stack = stack.push(SubshellRoute.PostComposer)
            }
            else -> Unit
        }
    }

    return stack
}

private fun SubshellRouteStack.captureProcessPostOrigin(): FeedPostOrigin {
    val surface = entries.lastOrNull { route ->
        route !is SubshellRoute.Media &&
            route !is SubshellRoute.CommentMedia &&
            route !is SubshellRoute.PostComposer
    } ?: return FeedPostOrigin.MainFeed(
        destination = MainDestination.HOME,
        source = FeedSource.HOME,
        sort = FeedSort.BEST,
    )
    return when (surface) {
        is SubshellRoute.Main -> if (surface.destination == MainDestination.INBOX) {
            FeedPostOrigin.Inbox
        } else {
            FeedPostOrigin.MainFeed(
                destination = surface.destination,
                source = surface.source,
                sort = surface.sort,
            )
        }
        is SubshellRoute.Community -> FeedPostOrigin.Community(
            destination = surface.destination,
            communityName = surface.communityName,
            sort = surface.sort,
        )
        is SubshellRoute.Search -> FeedPostOrigin.Search(
            destination = surface.destination,
            originCommunity = surface.originCommunity,
            query = surface.query,
            scope = surface.scope,
            area = surface.area,
        )
        is SubshellRoute.Post -> surface.postRoute.origin
        is SubshellRoute.User -> FeedPostOrigin.User(
            destination = surface.destination,
            username = surface.username,
        )
        is SubshellRoute.Conversation -> FeedPostOrigin.Inbox
        is SubshellRoute.Secondary -> FeedPostOrigin.Secondary(
            destination = surface.destination,
            secondaryDestination = surface.secondaryDestination,
        )
        is SubshellRoute.Media,
        is SubshellRoute.CommentMedia,
        SubshellRoute.PostComposer,
        -> error("Overlay routes are excluded")
    }
}

private fun sanitizeProcessPostOrigin(
    origin: FeedPostOrigin?,
    fallback: FeedPostOrigin,
    catalog: SubshellProcessRestoreCatalog,
): FeedPostOrigin = when (origin) {
    null -> fallback
    is FeedPostOrigin.MainFeed -> origin
    is FeedPostOrigin.Community -> catalog.canonicalCommunity(origin.communityName)
        ?.let { communityName -> origin.copy(communityName = communityName) }
        ?: fallback
    is FeedPostOrigin.Search -> {
        val originCommunity = catalog.canonicalCommunity(origin.originCommunity)
        val area = if (
            origin.area == SearchArea.CURRENT_COMMUNITY &&
            originCommunity == null
        ) {
            SearchArea.ALL_SUBSHELL
        } else {
            origin.area
        }
        origin.copy(
            originCommunity = originCommunity,
            scope = normalizeSearchScope(origin.scope, area),
            area = area,
        )
    }
    is FeedPostOrigin.User -> origin.username
        .trim()
        .takeIf(String::isNotBlank)
        ?.let { username -> origin.copy(username = username) }
        ?: fallback
    FeedPostOrigin.Inbox -> FeedPostOrigin.Inbox
    is FeedPostOrigin.Secondary -> origin
}

private fun normalizeSearchScope(scope: SearchScope, area: SearchArea): SearchScope = when (area) {
    SearchArea.ALL_SUBSHELL -> scope
    SearchArea.MY_COMMUNITIES -> scope.takeIf {
        it == SearchScope.ALL ||
            it == SearchScope.POSTS ||
            it == SearchScope.COMMUNITIES
    } ?: SearchScope.ALL
    SearchArea.CURRENT_COMMUNITY -> SearchScope.POSTS
}

private fun encodeAnchorRecord(
    key: FeedScrollKey,
    anchor: FeedScrollAnchor,
): String = when (key) {
    is FeedScrollKey.MainFeed -> snapshotRecord(
        "ANCHOR",
        "MAIN",
        key.source.name,
        key.sort.name,
        key.timePeriod.name,
        anchor.itemKey,
        anchor.fallbackIndex.toString(),
        anchor.pixelOffset.toString(),
    )
    is FeedScrollKey.Community -> snapshotRecord(
        "ANCHOR",
        "COMMUNITY",
        key.communityName,
        key.sort.name,
        key.timePeriod.name,
        anchor.itemKey,
        anchor.fallbackIndex.toString(),
        anchor.pixelOffset.toString(),
    )
    is FeedScrollKey.Search -> snapshotRecord(
        "ANCHOR",
        "SEARCH",
        key.destination.name,
        key.originCommunity,
        key.query,
        key.scope.name,
        key.area.name,
        anchor.itemKey,
        anchor.fallbackIndex.toString(),
        anchor.pixelOffset.toString(),
    )
    is FeedScrollKey.User -> snapshotRecord(
        "ANCHOR",
        "USER",
        key.username,
        key.tab.toString(),
        key.activityTab.toString(),
        anchor.itemKey,
        anchor.fallbackIndex.toString(),
        anchor.pixelOffset.toString(),
    )
    is FeedScrollKey.You -> snapshotRecord(
        "ANCHOR",
        "YOU",
        key.tab.toString(),
        anchor.itemKey,
        anchor.fallbackIndex.toString(),
        anchor.pixelOffset.toString(),
    )
}

private fun decodeAnchors(
    records: List<SnapshotRecord>,
    catalog: SubshellProcessRestoreCatalog,
): Map<FeedScrollKey, FeedScrollAnchor> {
    val anchors = linkedMapOf<FeedScrollKey, FeedScrollAnchor>()
    records.asSequence()
        .filter { record -> record.kind == "ANCHOR" }
        .take(SUBSHELL_PROCESS_SNAPSHOT_MAX_ANCHORS)
        .forEach { record ->
            val fields = record.fields
            val key = when (fields.firstOrNull()) {
                "MAIN" -> mainFeedScrollKey(
                    source = fields.getOrNull(1).enumOrDefault(FeedSource.HOME),
                    sort = fields.getOrNull(2).enumOrDefault(FeedSort.BEST),
                    timePeriod = if (fields.size >= 7) {
                        fields.getOrNull(3).enumOrDefault(
                            app.subshell.prototype.data.RedditListingTimePeriod.ALL,
                        )
                    } else {
                        app.subshell.prototype.data.RedditListingTimePeriod.ALL
                    },
                )
                "COMMUNITY" -> {
                    val communityName = catalog.canonicalCommunity(fields.getOrNull(1))
                        ?: return@forEach
                    communityFeedScrollKey(
                        communityName = communityName,
                        sort = fields.getOrNull(2).enumOrDefault(CommunitySort.HOT),
                        timePeriod = if (fields.size >= 7) {
                            fields.getOrNull(3).enumOrDefault(
                                app.subshell.prototype.data.RedditListingTimePeriod.ALL,
                            )
                        } else {
                            app.subshell.prototype.data.RedditListingTimePeriod.ALL
                        },
                    )
                }
                "SEARCH" -> {
                    val originCommunity = catalog.canonicalCommunity(fields.getOrNull(2))
                        ?.let(::normalizeFeedScrollToken)
                    val requestedArea = fields.getOrNull(5)
                        .enumOrDefault(SearchArea.ALL_SUBSHELL)
                    val area = if (
                        requestedArea == SearchArea.CURRENT_COMMUNITY &&
                        originCommunity == null
                    ) {
                        SearchArea.ALL_SUBSHELL
                    } else {
                        requestedArea
                    }
                    FeedScrollKey.Search(
                        destination = fields.getOrNull(1)
                            .enumOrDefault(MainDestination.HOME),
                        originCommunity = originCommunity,
                        query = normalizeFeedScrollToken(fields.getOrNull(3).orEmpty()),
                        scope = normalizeSearchScope(
                            fields.getOrNull(4).enumOrDefault(SearchScope.POSTS),
                            area,
                        ),
                        area = area,
                    )
                }
                "USER" -> {
                    val username = fields.getOrNull(1)
                        ?.takeIf(String::isNotBlank)
                        ?.let(::normalizeFeedScrollToken)
                        ?: return@forEach
                    // LOWUI_PROFILE_TAB_ANCHORS_KEEP_THEIR_TAB_V1 (OCB-096): the own
                    // profile's tabs past Comments (LOWUI6 OCB-097 did the selected
                    // tab) kept their scroll position under tab 2.
                    val tab = fields.getOrNull(2).toSnapshotInt(default = 0, max = SUBSHELL_PROCESS_SNAPSHOT_MAX_PROFILE_TAB)
                    FeedScrollKey.User(
                        username = username,
                        tab = tab,
                        activityTab = if (tab == 2) {
                            fields.getOrNull(3).toSnapshotInt(default = 0, max = 1)
                        } else {
                            0
                        },
                    )
                }
                // "LIBRARY" anchors from an older build are dropped (LOWUI4).
                "YOU" -> FeedScrollKey.You(
                    tab = fields.getOrNull(1).toSnapshotInt(default = 0, max = SUBSHELL_PROCESS_SNAPSHOT_MAX_PROFILE_TAB),
                )
                else -> null
            } ?: return@forEach

            val anchorFields = when (fields.firstOrNull()) {
                "MAIN", "COMMUNITY" -> if (fields.size >= 7) {
                    Triple(
                        fields.getOrNull(4),
                        fields.getOrNull(5),
                        fields.getOrNull(6),
                    )
                } else {
                    Triple(
                        fields.getOrNull(3),
                        fields.getOrNull(4),
                        fields.getOrNull(5),
                    )
                }
                "SEARCH" -> Triple(
                    fields.getOrNull(6),
                    fields.getOrNull(7),
                    fields.getOrNull(8),
                )
                "USER" -> Triple(
                    fields.getOrNull(4),
                    fields.getOrNull(5),
                    fields.getOrNull(6),
                )
                "YOU" -> Triple(
                    fields.getOrNull(2),
                    fields.getOrNull(3),
                    fields.getOrNull(4),
                )
                else -> return@forEach
            }
            anchors[key] = FeedScrollAnchor(
                itemKey = anchorFields.first?.takeIf(String::isNotBlank),
                fallbackIndex = anchorFields.second.toSnapshotInt(default = 0),
                pixelOffset = anchorFields.third.toSnapshotInt(default = 0),
            )
        }
    return anchors
}

private fun decodeTabMap(
    records: List<SnapshotRecord>,
    kind: String,
    max: Int,
): Map<String, Int> {
    val result = linkedMapOf<String, Int>()
    records.asSequence()
        .filter { record -> record.kind == kind }
        .take(SUBSHELL_PROCESS_SNAPSHOT_MAX_ANCHORS)
        .forEach { record ->
            val username = record.fields.getOrNull(0)
                ?.takeIf(String::isNotBlank)
                ?.let(::normalizeFeedScrollToken)
                ?: return@forEach
            result[username] = record.fields.getOrNull(1)
                .toSnapshotInt(default = 0, max = max)
        }
    return result
}

private fun encodeComposerRecord(draft: PostComposerDraft): String =
    snapshotRecord(
        "COMPOSER",
        "id=${draft.id}",
        "account=${draft.accountStableId.orEmpty()}",
        draft.community,
        draft.type.name,
        draft.title,
        draft.body,
        draft.linkUrl,
        "mode=${draft.inputMode.name}",
        "flairId=${draft.flair?.templateId.orEmpty()}",
        "flairText=${draft.flair?.text.orEmpty()}",
        "flairEditable=${draft.flair?.editable == true}",
        "flairTextColor=${draft.flair?.textColor.orEmpty()}",
        "flairBackgroundColor=${draft.flair?.backgroundColor.orEmpty()}",
        "nsfw=${draft.nsfw}",
        "spoiler=${draft.spoiler}",
        "sendReplies=${draft.sendReplies}",
        "created=${draft.createdAtEpochMs}",
        "updated=${draft.updatedAtEpochMs}",
        // CROSSPOST01A1D_PROCESS_SOURCE_CAPSULE_V1
        *draft.crosspostSource?.let { source ->
            listOf(
                "crosspost.fullname=${source.fullname}",
                "crosspost.community=${source.community}",
                "crosspost.author=${source.author}",
                "crosspost.title=${source.title}",
                "crosspost.kind=${source.kind.name}",
                "crosspost.nsfw=${source.nsfw}",
                "crosspost.spoiler=${source.spoiler}",
            )
        }.orEmpty().toTypedArray(),
        *draft.media.take(20).flatMapIndexed { index, media ->
            listOf(
                "media.$index.uri=${media.uri}",
                "media.$index.mime=${media.mimeType}",
                "media.$index.kind=${media.kind.name}",
                "media.$index.source=${media.sourceLinkUri.orEmpty()}",
                "media.$index.caption=${media.caption}",
                "media.$index.outbound=${media.outboundUrl}",
            )
        }.toTypedArray(),
        *draft.flair?.richText.orEmpty().take(20).flatMapIndexed { index, item ->
            listOf(
                "flairRich.$index.type=${item.elementType}",
                "flairRich.$index.text=${item.text.orEmpty()}",
                "flairRich.$index.emoji=${item.emojiRef.orEmpty()}",
                "flairRich.$index.image=${item.imageUrl.orEmpty()}",
            )
        }.toTypedArray(),
    )

private fun decodeComposer(
    records: List<SnapshotRecord>,
    catalog: SubshellProcessRestoreCatalog,
): PostComposerDraft {
    val fields = records.lastOrNull { record -> record.kind == "COMPOSER" }
        ?.fields
        ?: return PostComposerDraft()
    // LOWUI_COMPOSER_TAGS_OUTSIDE_THE_USER_TEXT_V1 (OCB-099): fields 2–6 are
    // the user's own community, type, title, body and link; a title or body
    // starting with "crosspost." or "mode=" was read as a tag (and could reject
    // the whole snapshot). Tags are looked for outside those fields.
    val header = fields.take(2)
    val id = header.firstOrNull { it?.startsWith("id=") == true }
        ?.removePrefix("id=")
        ?.takeIf(String::isNotBlank)
        ?: newPostComposerDraftId()
    val accountStableId = header.firstOrNull { it?.startsWith("account=") == true }
        ?.removePrefix("account=")
        ?.takeIf(String::isNotBlank)
    val positional = (2..6).map(fields::getOrNull)
    val trailingFields = fields.drop(7)
    val encodedInputMode = trailingFields.firstOrNull { value ->
        value?.startsWith("mode=") == true
    }
        ?.removePrefix("mode=")
    fun tagged(prefix: String): String? = trailingFields
        .firstOrNull { value -> value?.startsWith(prefix) == true }
        ?.removePrefix(prefix)
    val media = (0 until 20).mapNotNull { index ->
        val uri = tagged("media.$index.uri=")?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        ComposerMediaAttachment(
            uri = uri,
            mimeType = tagged("media.$index.mime=")?.takeIf(String::isNotBlank) ?: "image/*",
            kind = tagged("media.$index.kind=").enumOrDefault(ComposerMediaKind.IMAGE),
            sourceLinkUri = tagged("media.$index.source=")?.takeIf(String::isNotBlank),
            caption = tagged("media.$index.caption=").orEmpty(),
            outboundUrl = tagged("media.$index.outbound=").orEmpty(),
        )
    }
    val composerType = positional.getOrNull(1).enumOrDefault(ComposerPostType.TEXT)
    val hasCrosspostSourceTags = trailingFields.any { value ->
        value?.startsWith("crosspost.") == true
    }
    val crosspostSource = when {
        composerType == ComposerPostType.CROSSPOST ->
            decodeComposerCrosspostSource(trailingFields)
                ?: error("Invalid source-bound crosspost process snapshot")
        hasCrosspostSourceTags ->
            error("Crosspost source capsule requires CROSSPOST composer type")
        else -> null
    }
    val flairId = tagged("flairId=")?.takeIf(String::isNotBlank)
    val flairRichText = (0 until 20).mapNotNull { index ->
        val elementType = tagged("flairRich.$index.type=")
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        ComposerFlairRichText(
            elementType = elementType,
            text = tagged("flairRich.$index.text=")?.takeIf(String::isNotBlank),
            emojiRef = tagged("flairRich.$index.emoji=")?.takeIf(String::isNotBlank),
            imageUrl = tagged("flairRich.$index.image=")?.takeIf(String::isNotBlank),
        )
    }
    return PostComposerDraft(
        id = id,
        accountStableId = accountStableId,
        community = decodeComposerCommunity(positional.getOrNull(0), catalog),
        type = composerType,
        title = positional.getOrNull(2).orEmpty(),
        body = positional.getOrNull(3).orEmpty(),
        linkUrl = positional.getOrNull(4).orEmpty(),
        inputMode = encodedInputMode.enumOrDefault(ComposerInputMode.MARKDOWN),
        media = media,
        flair = flairId?.let {
            ComposerFlair(
                templateId = it,
                text = tagged("flairText=").orEmpty(),
                editable = tagged("flairEditable=")?.toBooleanStrictOrNull() == true,
                textColor = tagged("flairTextColor=")?.takeIf(String::isNotBlank),
                backgroundColor = tagged("flairBackgroundColor=")?.takeIf(String::isNotBlank),
                richText = flairRichText,
            )
        },
        nsfw = tagged("nsfw=")?.toBooleanStrictOrNull() == true,
        spoiler = tagged("spoiler=")?.toBooleanStrictOrNull() == true,
        sendReplies = tagged("sendReplies=")?.toBooleanStrictOrNull() != false,
        createdAtEpochMs = tagged("created=")?.toLongOrNull() ?: System.currentTimeMillis(),
        updatedAtEpochMs = tagged("updated=")?.toLongOrNull() ?: System.currentTimeMillis(),
        crosspostSource = crosspostSource,
    ).also { draft ->
        if (
            composerType == ComposerPostType.CROSSPOST &&
            !draft.isValidSourceBoundCrosspostDraft()
        ) {
            error("Invalid source-bound crosspost draft after process restore")
        }
    }
}

// CROSSPOST01A1D_PROCESS_SOURCE_CAPSULE_V1
private fun decodeComposerCrosspostSource(
    fields: List<String?>,
): ComposerCrosspostSource? {
    fun tagged(prefix: String): String? = fields
        .firstOrNull { value -> value?.startsWith(prefix) == true }
        ?.removePrefix(prefix)

    val fullname = tagged("crosspost.fullname=")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val community = tagged("crosspost.community=")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val author = tagged("crosspost.author=")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val title = tagged("crosspost.title=")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val kind = tagged("crosspost.kind=")
        ?.let { encoded ->
            PostKind.entries.firstOrNull { value -> value.name == encoded }
        }
        ?: return null
    val nsfw = tagged("crosspost.nsfw=")
        ?.toBooleanStrictOrNull()
        ?: return null
    val spoiler = tagged("crosspost.spoiler=")
        ?.toBooleanStrictOrNull()
        ?: return null

    return runCatching {
        ComposerCrosspostSource(
            fullname = fullname,
            community = community,
            author = author,
            title = title,
            kind = kind,
            nsfw = nsfw,
            spoiler = spoiler,
        )
    }.getOrNull()
}

private fun decodeComposerCommunity(
    encoded: String?,
    catalog: SubshellProcessRestoreCatalog,
): String {
    val normalized = encoded
        ?.trim()
        ?.removePrefix("/r/")
        ?.removePrefix("r/")
        .orEmpty()
    if (
        normalized.isBlank() ||
        normalized.length > 100 ||
        normalized.any { character ->
            !character.isLetterOrDigit() && character != '_' && character != '-'
        }
    ) {
        return ""
    }
    return catalog.canonicalCommunity(normalized) ?: normalized
}

private fun encodeCommentComposerRecord(
    visible: Boolean,
    draft: CommentComposerDraft,
): String = snapshotRecord(
    "COMMENT_COMPOSER",
    visible.toString(),
    draft.target.postId,
    draft.target.parentCommentId,
    draft.target.parentFullname,
    draft.target.parentAuthor,
    draft.body,
    draft.inputMode.name,
    draft.media?.uri,
    draft.media?.mimeType,
    draft.media?.kind?.name,
    draft.media?.sourceLinkUri,
    // UIPOST02: appended; an older snapshot has no field 11 (account unknown).
    draft.accountStableId,
)

// RECREATE01_MESSAGE_DRAFTS_SURVIVE_RECREATION_V1 (#241): in key order, while
// the drafts fit the shared budget; a draft past it is left out whole.
private fun encodeMessageDraftRecords(drafts: Map<String, String>): List<String> {
    var remaining = SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_DRAFT_CHARS
    return drafts.toSortedMap().mapNotNull { (key, text) ->
        if (text.isEmpty() || key.length + text.length > remaining) return@mapNotNull null
        remaining -= key.length + text.length
        snapshotRecord("MESSAGE_DRAFT", key, text)
    }
}

private fun decodeMessageDrafts(records: List<SnapshotRecord>): Map<String, String> {
    var remaining = SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_DRAFT_CHARS
    return buildMap {
        records.filter { record -> record.kind == "MESSAGE_DRAFT" }.forEach { record ->
            val key = record.fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@forEach
            val text = record.fields.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return@forEach
            if (key.length + text.length > remaining) return@forEach
            remaining -= key.length + text.length
            put(key, text)
        }
    }
}

// RECREATE02_MESSAGE_ATTACHMENT_SURVIVES_RECREATION_V1 (#241): fields as the
// comment composer's media (uri, mime type, kind, source link). After a process
// death the picker's read grant may be gone; sending then fails like a comment
// reply's restored image does, and the reader can remove it.
private fun encodeMessageMediaRecords(media: Map<String, ComposerMediaAttachment>): List<String> =
    media.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_MEDIA).map { (key, attachment) ->
        snapshotRecord(
            "MESSAGE_MEDIA",
            key,
            attachment.uri,
            attachment.mimeType,
            attachment.kind.name,
            attachment.sourceLinkUri,
        )
    }

private fun decodeMessageMedia(records: List<SnapshotRecord>): Map<String, ComposerMediaAttachment> =
    records.asSequence()
        .filter { record -> record.kind == "MESSAGE_MEDIA" }
        .mapNotNull { record ->
            val key = record.fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val uri = record.fields.getOrNull(1)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            key to ComposerMediaAttachment(
                uri = uri,
                mimeType = record.fields.getOrNull(2)?.takeIf(String::isNotBlank) ?: "image/*",
                kind = record.fields.getOrNull(3).enumOrDefault(ComposerMediaKind.IMAGE),
                sourceLinkUri = record.fields.getOrNull(4)?.takeIf(String::isNotBlank),
            )
        }
        .take(SUBSHELL_PROCESS_SNAPSHOT_MAX_MESSAGE_MEDIA)
        .toMap()

private fun encodeSettledOverrideRecords(overrides: SettledAccountOverrides): List<String> = buildList {
    add(snapshotRecord("SETTLED_ACCOUNT", overrides.accountStableId))
    overrides.votes.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
        .forEach { (target, direction) -> add(snapshotRecord("SETTLED_VOTE", target, direction.toString())) }
    overrides.saves.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
        .forEach { (target, saved) -> add(snapshotRecord("SETTLED_SAVE", target, saved.toString())) }
    overrides.hidden.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
        .forEach { (target, hidden) -> add(snapshotRecord("SETTLED_HIDDEN", target, hidden.toString())) }
    overrides.follows.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
        .forEach { (target, followed) -> add(snapshotRecord("SETTLED_FOLLOW", target, followed.toString())) }
    overrides.blocks.toSortedMap().entries.take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
        .forEach { (username, blocked) -> add(snapshotRecord("SETTLED_BLOCK", username, blocked.toString())) }
}

private fun decodeSettledOverrides(records: List<SnapshotRecord>): SettledAccountOverrides? {
    val account = records.lastOrNull { record -> record.kind == "SETTLED_ACCOUNT" }
        ?.fields
        ?.getOrNull(0)
        ?.takeIf(::isRestorableItemId)
        ?: return null
    fun <T> targets(kind: String, value: (String) -> T?): Map<String, T> =
        records.asSequence()
            .filter { record -> record.kind == kind }
            .mapNotNull { record ->
                val target = record.fields.getOrNull(0)?.takeIf(::isRestorableItemId) ?: return@mapNotNull null
                val parsed = record.fields.getOrNull(1)?.let(value) ?: return@mapNotNull null
                target to parsed
            }
            .take(SUBSHELL_PROCESS_SNAPSHOT_MAX_SETTLED_OVERRIDES)
            .toMap()
    return SettledAccountOverrides(
        accountStableId = account,
        votes = targets("SETTLED_VOTE") { field -> field.toIntOrNull()?.takeIf { it in -1..1 } },
        saves = targets("SETTLED_SAVE", String::toBooleanStrictOrNull),
        hidden = targets("SETTLED_HIDDEN", String::toBooleanStrictOrNull),
        follows = targets("SETTLED_FOLLOW", String::toBooleanStrictOrNull),
        blocks = targets("SETTLED_BLOCK", String::toBooleanStrictOrNull),
    )
}

private fun decodeCommentComposer(
    records: List<SnapshotRecord>,
    catalog: SubshellProcessRestoreCatalog,
    routeStack: SubshellRouteStack,
): ProcessCommentComposerState {
    val fields = records.lastOrNull { record -> record.kind == "COMMENT_COMPOSER" }
        ?.fields
        ?: return ProcessCommentComposerState(visible = false, draft = null)
    val postId = fields.getOrNull(1)
        ?.takeIf(::isRestorableItemId)
        ?: return ProcessCommentComposerState(visible = false, draft = null)
    val target = restoredCommentReplyTarget(
        postId = postId,
        parentCommentId = fields.getOrNull(2),
        parentFullname = fields.getOrNull(3),
        parentAuthor = fields.getOrNull(4),
    ) ?: return ProcessCommentComposerState(visible = false, draft = null)
    val mediaUri = fields.getOrNull(7)?.takeIf(String::isNotBlank)
    val draft = CommentComposerDraft(
        target = target,
        body = fields.getOrNull(5).orEmpty(),
        inputMode = fields.getOrNull(6).enumOrDefault(ComposerInputMode.PLAIN),
        media = mediaUri?.let { uri ->
            ComposerMediaAttachment(
                uri = uri,
                mimeType = fields.getOrNull(8)?.takeIf(String::isNotBlank) ?: "image/*",
                kind = fields.getOrNull(9).enumOrDefault(ComposerMediaKind.IMAGE),
                sourceLinkUri = fields.getOrNull(10)?.takeIf(String::isNotBlank),
            )
        },
        accountStableId = fields.getOrNull(11)?.takeIf(String::isNotBlank),
    )
    val currentPostId = (routeStack.current as? SubshellRoute.Post)
        ?.postRoute
        ?.postId
    return ProcessCommentComposerState(
        visible = fields.getOrNull(0)?.toBooleanStrictOrNull() == true &&
            currentPostId == target.postId,
        draft = draft,
    )
}

private fun decodeMediaState(
    records: List<SnapshotRecord>,
    catalog: SubshellProcessRestoreCatalog,
): ProcessMediaState {
    val fields = records.lastOrNull { record -> record.kind == "MEDIA_STATE" }
        ?.fields
        ?: return ProcessMediaState(
            feedVideoMuted = true,
            backgroundResumeMediaId = null,
            backgroundResumeWasManual = false,
        )
    val mediaId = fields.getOrNull(1)
        ?.takeIf(catalog::containsMediaPost)
    return ProcessMediaState(
        feedVideoMuted = fields.getOrNull(0)?.toBooleanStrictOrNull() ?: true,
        backgroundResumeMediaId = mediaId,
        backgroundResumeWasManual = mediaId != null &&
            (fields.getOrNull(2)?.toBooleanStrictOrNull() ?: false),
    )
}

private fun List<SnapshotRecord>.lastField(kind: String, index: Int): String? =
    lastOrNull { record -> record.kind == kind }
        ?.fields
        ?.getOrNull(index)

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { candidate -> candidate.name == name } }
        ?: default

private fun String?.toSnapshotInt(
    default: Int,
    max: Int = SUBSHELL_PROCESS_SNAPSHOT_MAX_INDEX,
): Int = this
    ?.toIntOrNull()
    ?.coerceIn(0, max)
    ?: default
