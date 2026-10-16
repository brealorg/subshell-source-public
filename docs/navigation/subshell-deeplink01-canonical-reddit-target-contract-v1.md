# DEEPLINK01 — canonical Reddit target and Android VIEW contract

## Canonical target boundary

DEEPLINK01 keeps one pure Kotlin/JVM resolver for Reddit resource identities.
The resolver accepts supported Reddit web URLs, Reddit permalinks/paths and post
fullnames, and returns typed targets for posts, exact comments, communities and
users. External web URLs and ambiguous Reddit path shapes are rejected.

Post/comment URL parsing only accepts canonical `/comments/...` and
`/r/<community>/comments/...` shapes. Community and user routes require exact
resource paths, so share-token, subreddit settings and user-history paths are not
silently reclassified as another target.

## Internal crosspost handoff

CROSSPOST01 retains the source fullname/permalink and resolves it through this same
`RedditTarget` boundary. Source identity/title/stats open the original source post,
while source Markdown links and media preserve their own interaction ownership.
The wrapper stays below the source route, so Back returns to the crosspost wrapper.

## Android ACTION_VIEW delivery

DEEPLINK01 v2 declares HTTP/HTTPS VIEW intent filters for the supported Reddit host
variants and `redd.it`. MainActivity uses `singleTop`, consumes supported VIEW data
on both cold `onCreate` and warm `onNewIntent`, and turns the URI into a one-shot
`RedditIntentLaunch`. Compose resolves that launch with the same `RedditTarget`
parser; MainActivity does not contain a second Reddit path parser.

The filters intentionally do not request verification for Reddit domains. Subshell
does not control Reddit's Digital Asset Links configuration, so Android/user domain
association remains the system-owned policy. Explicit-component ADB tests bypass
that default-app policy and validate Subshell's cold/warm delivery deterministically.

## Navigation and exact-post resolution

Community and user targets dispatch directly to the existing canonical routes.
Post and exact-comment targets first reuse any matching post already present in the
current Subshell paging/repository state. If the post is not local and an
authenticated account is active, the existing exact Reddit `/api/info` resolution
path is reused through a navigation-only ViewModel callback. This avoids inventing a
second post-fetch protocol while keeping Inbox UI state out of the navigation
result callback.

Exact comment targets open the resolved post with the comment ID preserved, so the
existing focused-comment transport/viewport machinery owns hydration and landing.

## Warm/cold ownership

Cold VIEW delivery is consumed after Compose content is installed. Warm VIEW
delivery is sent to the existing top MainActivity through `singleTop` and
`onNewIntent`; `setIntent(intent)` remains in the warm path. Each accepted URI gets a
monotonic request ID and is consumed once after successful local routing or one
remote resolution attempt. If an authenticated account is still being restored,
the request remains pending until account state is ready.


## Android link opt-in onboarding

DEEPLINK01 v3 treats Android's domain association state as system-owned. On
Android 12+ Subshell reads `DomainVerificationManager` for the exact seven
canonical hosts already declared by DEEPLINK01: `reddit.com`, `www.reddit.com`,
`old.reddit.com`, `new.reddit.com`, `np.reddit.com`, `m.reddit.com`, and
`redd.it`. User-selected and verified host states both count as enabled.

An authenticated installation that has never seen the link-handling prompt gets
one contextual onboarding step when the supported Reddit hosts are not fully
enabled. New users reach the same step immediately after the existing Reddit
account onboarding; existing authenticated users see it once after upgrading.
"Set as default" opens Android's package-specific **Open by default** page.
The onboarding copy explains that Android owns the association, then gives the
user the concrete system-side steps: enable **Open supported links** and select
the Reddit addresses Subshell should handle. It also notes that an existing
Reddit app may already own some selections and that Subshell refreshes the live
state when the user returns. The step is vertically scrollable so the additional
guidance remains usable on smaller displays and with larger text scales.
"Not now" records a device-local one-shot acknowledgement and does not nag on
later launches. If all supported hosts are enabled, the prompt is considered
complete automatically.

The prompt acknowledgement lives in the dedicated
`subshell_device_preferences` SharedPreferences file. That file is excluded from
cloud backup and device transfer, so this Android-installation choice is not
treated as account or portable Subshell configuration.

Settings exposes **Media & links → Open Reddit links** whenever the platform
supports domain-selection state. The row reports Enabled, Partial, or Off from
the live Android state and opens the same system page. MainActivity refreshes the
state in `onResume`, so returning from system Settings updates both onboarding
and Settings without maintaining a second source of truth.

Subshell never programmatically selects Reddit domains and never claims domain
verification it does not own.
