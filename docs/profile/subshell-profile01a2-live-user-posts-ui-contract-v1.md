# PROFILE01A2 live user posts UI contract V1

PROFILE01A2 connects the PROFILE01A1 Reddit user-submitted listing foundation to `UserProfileScreen` for authenticated account sessions.

Authenticated `Posts` uses `redditUserSubmittedPostFeedQueryKey(accountStableId, username)` and the existing shared `AuthenticatedRedditFeedUpdater`, `RedditFeedRequestCoordinator`, Room post query cache, viewport auto-append governor, pull-to-refresh surface, and `RedditPagingControl`.

Both the main `You` destination and contextual user routes receive the same Reddit feed state and user-submitted refresh/append callbacks. The user target remains `AuthenticatedRedditFeedTarget.user(username)`, which uses Reddit `new` ordering and no listing time period.

Profile ownership is resolved from `activeAccountUsername`, not the repository fixture `localUsername`. Authenticated profiles intentionally expose only `Posts` and `Comments`; the local `Activity` tab is not projected into an authenticated Reddit account.

Authenticated profile comments are not implemented by PROFILE01A2. The `Comments` tab therefore exposes an explicit unavailable state instead of leaking local profile-comment fixtures.

Local fixture accounts preserve their existing Posts, Comments, Activity, saved, upvoted, paging, scroll-anchor, and post-origin behavior.

PROFILE01A2 does not add OAuth scopes, authorization owners, network endpoints, loaders, Room tables, migrations, cache keys outside the PROFILE01A1 user-submitted key, or write behavior.

## PROFILE01A2 runtime feed parity

The authenticated profile uses the same feed-media ownership model as Home and Community: video viewport tracking, terminal-video context, GIF/gallery viewport autoplay selection, and cleanup when the profile surface leaves composition.

Profile post navigation captures the stable `FeedScrollAnchor` before route transition so deep-list return resolves by stable post item key instead of relying on disposal timing.

User-submitted scrolling remains seamless through the shared near-end `RefreshableLazyColumn` paging gate. No feed exposes a manual `Load 25 more` fallback; the viewport-fill governor continues by itself (FEEDS01).

The main `You` destination owns a full stable viewport and receives `mainContentPadding` like the other main surfaces. Scaffold chrome is therefore translated without leaving reserved top or bottom viewport gaps.
