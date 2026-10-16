package app.subshell.prototype.ui

internal sealed interface SubshellRoute {
    data class Main(
        val destination: MainDestination,
        val source: FeedSource,
        val sort: FeedSort,
    ) : SubshellRoute

    data class Community(
        val destination: MainDestination,
        val communityName: String,
        val sort: CommunitySort,
    ) : SubshellRoute

    data class Search(
        val destination: MainDestination,
        val originCommunity: String?,
        val query: String,
        val submittedQuery: String = query,
        val scope: SearchScope,
        val area: SearchArea,
    ) : SubshellRoute

    data class Post(
        val postRoute: FeedPostRoute,
    ) : SubshellRoute

    data class User(
        val destination: MainDestination,
        val username: String,
    ) : SubshellRoute

    data class Conversation(
        val destination: MainDestination,
        val inboxItemId: String,
    ) : SubshellRoute

    data class Secondary(
        val destination: MainDestination,
        val secondaryDestination: SecondaryDestination,
    ) : SubshellRoute

    data class Media(
        val postId: String,
        val index: Int,
    ) : SubshellRoute

    // ISSUE19_INLINE_COMMENT_IMAGE_FULLSCREEN_V1
    data class CommentMedia(
        val commentId: String,
        val uri: String,
    ) : SubshellRoute

    data object PostComposer : SubshellRoute
}

@ConsistentCopyVisibility
internal data class SubshellRouteStack private constructor(
    val entries: List<SubshellRoute>,
) {
    init {
        require(entries.isNotEmpty()) { "A navigation route stack requires a root route" }
        require(entries.first() is SubshellRoute.Main) { "The root route must be a main destination" }
        require((entries.first() as SubshellRoute.Main).destination == MainDestination.HOME) {
            "The canonical root route must be Home"
        }
    }

    val current: SubshellRoute
        get() = entries.last()

    val surface: SubshellRoute
        get() = entries.last { route ->
            route !is SubshellRoute.Media &&
                route !is SubshellRoute.CommentMedia &&
                route !is SubshellRoute.PostComposer
        }

    val canPop: Boolean
        get() = entries.size > 1

    val depth: Int
        get() = entries.size

    fun push(route: SubshellRoute): SubshellRouteStack =
        SubshellRouteStack(entries + route)

    fun replaceCurrent(route: SubshellRoute): SubshellRouteStack =
        SubshellRouteStack(entries.dropLast(1) + route)

    fun pop(): SubshellRouteStack =
        if (canPop) SubshellRouteStack(entries.dropLast(1)) else this

    companion object {
        fun forDestination(
            destination: MainDestination,
            source: FeedSource,
            sort: FeedSort,
        ): SubshellRouteStack {
            val root = SubshellRoute.Main(
                destination = MainDestination.HOME,
                source = source,
                sort = sort,
            )
            return if (destination == MainDestination.HOME) {
                SubshellRouteStack(listOf(root))
            } else {
                SubshellRouteStack(
                    listOf(
                        root,
                        SubshellRoute.Main(
                            destination = destination,
                            source = source,
                            sort = sort,
                        ),
                    ),
                )
            }
        }

        fun restored(
            destination: MainDestination,
            source: FeedSource,
            sort: FeedSort,
            selectedCommunity: String?,
            communitySort: CommunitySort,
            postRoute: FeedPostRoute?,
            mediaPostId: String?,
            mediaIndex: Int,
        ): SubshellRouteStack {
            var stack = postRoute?.origin?.let(::fromPostOrigin)
                ?: forDestination(destination, source, sort).let { base ->
                    selectedCommunity?.let { communityName ->
                        base.push(
                            SubshellRoute.Community(
                                destination = destination,
                                communityName = communityName,
                                sort = communitySort,
                            ),
                        )
                    } ?: base
                }

            if (postRoute != null) {
                stack = stack.push(SubshellRoute.Post(postRoute))
            }
            if (mediaPostId != null) {
                stack = stack.push(
                    SubshellRoute.Media(
                        postId = mediaPostId,
                        index = mediaIndex.coerceAtLeast(0),
                    ),
                )
            }
            return stack
        }

        private fun fromPostOrigin(origin: FeedPostOrigin): SubshellRouteStack = when (origin) {
            is FeedPostOrigin.MainFeed -> forDestination(
                destination = origin.destination,
                source = origin.source,
                sort = origin.sort,
            )
            is FeedPostOrigin.Community -> forDestination(
                destination = origin.destination,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            ).push(
                SubshellRoute.Community(
                    destination = origin.destination,
                    communityName = origin.communityName,
                    sort = origin.sort,
                ),
            )
            is FeedPostOrigin.Search -> {
                var stack = forDestination(
                    destination = origin.destination,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                )
                origin.originCommunity?.let { communityName ->
                    stack = stack.push(
                        SubshellRoute.Community(
                            destination = origin.destination,
                            communityName = communityName,
                            sort = CommunitySort.HOT,
                        ),
                    )
                }
                stack.push(
                    SubshellRoute.Search(
                        destination = origin.destination,
                        originCommunity = origin.originCommunity,
                        query = origin.query,
                        submittedQuery = origin.query,
                        scope = origin.scope,
                        area = origin.area,
                    ),
                )
            }
            is FeedPostOrigin.User -> forDestination(
                destination = origin.destination,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            ).push(
                SubshellRoute.User(
                    destination = origin.destination,
                    username = origin.username,
                ),
            )
            FeedPostOrigin.Inbox -> forDestination(
                destination = MainDestination.INBOX,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            )
            is FeedPostOrigin.Secondary -> forDestination(
                destination = origin.destination,
                source = FeedSource.HOME,
                sort = FeedSort.BEST,
            ).push(
                SubshellRoute.Secondary(
                    destination = origin.destination,
                    secondaryDestination = origin.secondaryDestination,
                ),
            )
        }
    }
}

internal fun SubshellRoute.destinationOrNull(): MainDestination? = when (this) {
    is SubshellRoute.Main -> destination
    is SubshellRoute.Community -> destination
    is SubshellRoute.Search -> destination
    is SubshellRoute.Post -> postRoute.origin.destination
    is SubshellRoute.User -> destination
    is SubshellRoute.Conversation -> destination
    is SubshellRoute.Secondary -> destination
    is SubshellRoute.Media,
    is SubshellRoute.CommentMedia,
    SubshellRoute.PostComposer,
    -> null
}

internal fun SubshellRoute.composerCommunityOrNull(): String? = when (this) {
    is SubshellRoute.Community -> communityName
    is SubshellRoute.Search -> originCommunity
    is SubshellRoute.Post -> when (val origin = postRoute.origin) {
        is FeedPostOrigin.Community -> origin.communityName
        is FeedPostOrigin.Search -> origin.originCommunity
        else -> null
    }
    else -> null
}

internal val FeedPostOrigin.destination: MainDestination
    get() = when (this) {
        is FeedPostOrigin.MainFeed -> destination
        is FeedPostOrigin.Community -> destination
        is FeedPostOrigin.Search -> destination
        is FeedPostOrigin.User -> destination
        FeedPostOrigin.Inbox -> MainDestination.INBOX
        is FeedPostOrigin.Secondary -> destination
    }

internal val SubshellRoute.kindName: String
    get() = when (this) {
        is SubshellRoute.Main -> "Main"
        is SubshellRoute.Community -> "Community"
        is SubshellRoute.Search -> "Search"
        is SubshellRoute.Post -> "Post"
        is SubshellRoute.User -> "User"
        is SubshellRoute.Conversation -> "Conversation"
        is SubshellRoute.Secondary -> "Secondary"
        is SubshellRoute.Media -> "Media"
        is SubshellRoute.CommentMedia -> "CommentMedia"
        SubshellRoute.PostComposer -> "PostComposer"
    }
