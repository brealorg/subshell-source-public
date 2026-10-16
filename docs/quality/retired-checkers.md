# Retired checkers

Checkers removed from `tools/` with the owner's approval. A checker is
retired only when no gate reaches it and its guarantee is superseded by a
later slice's checker or a behaviour test; the list here is the record of
what was removed and why, so a red line in an old report can be traced.

## 2026-09-21 — nine orphan predecessor-literal checkers

All nine were orphans in `reports/2026-09-20-checker-inventory.tsv`
(`reachable=False`, `rc=1`): no gate ran them, and each had been red since
a later slice replaced the implementation its literals described.

| Checker | Red on | Superseded by |
|---|---|---|
| `check-diag01a2-contract.sh` | support link `?title=…&body=…` (now issue-template fields `template`, `version`, …) | `check-diag01a3-contract.sh`, `DiagnosticReport*Test` |
| `check-inbox01a1-contract.sh` | cache DB `version = 5` (now 6), `writeEnabled = state.localMutationsAllowed` (now `activeAccountIsAuthenticated` in `ConversationRouteHost`), tab unread-count literals | inbox01b*/inbox02*, messages02* checkers; `check-reddit01d3b` reads its one literal from source |
| `check-inbox01a11-contract.sh` | `item.conversation.ifEmpty` (now `authoritativeMessages`), ungrouped `items(messages…)`, `rawId.removePrefix("t1_")` | messages02c*/02d* conversation checkers |
| `check-inbox01a15-contract.sh` | `conversationParticipantLabel(message.sender)` (messages are grouped: `group.sender`) | messages02* |
| `check-write02c-contract.sh` | dev `versionCode`/`versionName` of its own slice build | `check-source-contract` WRITE02 blocks |
| `check-write02d-contract.sh` | dev `versionCode = 161` / `0.76.4-write02d-…` | `check-source-contract` WRITE02D block, `check-write02d2` |
| `check-write02d1-contract.sh` | dev version pin | `check-source-contract` |
| `check-write02e-contract.sh` | dev version pin | `check-source-contract` |
| `check-write02f2-contract.sh` | dev `versionCode = 168` / `0.76.11-write02f2-…` | `check-source-contract` WRITE02F2 block |

The slice-zero manifests under `docs/slicezero/` still list them; those
files are the 2026-09-19 snapshot and are not rewritten.

## 2026-09-21 — thirteen more orphans (full sequential sweep of all checkers)

A sweep of every checker from clean `main` (`46048da`, one at a time)
found 16 red orphans besides the seven chain checkers that only exceed
a 120 s single-run timeout. Three were made successor-aware instead
(`inbox01b21`: loader field renamed; `polls01a1`: crosspost-parent
`poll_data` split over lines, post-codec width guard now owned by the
newer codec checkers; `egg01`: Help & about is a `SecondaryDestination`
route since S5). The rest are retired here; all were orphans with `rc=1`
in `reports/2026-09-20-checker-inventory.tsv`.

| Checker | Red on | Superseded by |
|---|---|---|
| `check-diag01a1-contract.sh` | "support destinations must remain unconfigured" (`DiagnosticSupportConfiguration()` defaults, legacy section set) | `check-diag01a3-contract.sh` (configured support route), `Diag01A*` policy tests |
| `check-inbox01b1-contract.sh` | documented read endpoints, optimistic read commit/rollback, Room v5 reconciliation literals | `check-inbox01b11/b12/b22/b23/b231` |
| `check-inbox01b13-contract.sh` | `"Activity" to tabUnreadCounts.activity` (counts now come from `inboxTabUnreadCounts(...)`) | `check-inbox01b22`, `check-inbox01b23*` |
| `check-settings02b-contract.sh` | restore-preview confirmation copy of the first backup slice | `check-backup04a4`, `Backup02A3RestorePreviewTest`, `Backup03C2FavoritesRestorePreviewTest` |
| `check-settings03a-contract.sh` | `legacySections = setOf("Reddit connection", "Prototype states")` (task pages re-cut since) | `Settings03NavigationPolicyTest`, `check-settings03b/c/g` |
| `check-settings03d-contract.sh` | `PreviewContextLabel("Feed")`/`("Viewer")` literals | `Settings03DMediaPreviewPolicyTest` |
| `check-settings03e-contract.sh` | `visible = feedsPreviewNsfwPostVisible(showNsfwPosts)` call shape | `Settings03EFeedsPreviewPolicyTest` |
| `check-settings03f-contract.sh` | `primaryContainer.copy(alpha = 0.20f)` highlight colour | `Settings03FCommentsPreviewPolicyTest` |
| `check-subs03a-contract.sh` | dev `versionName` of its own slice build | `check-subs04a0/a1/b/c*`, `Subs04*` policy tests |
| `check-subs03a2-contract.sh` | dev `versionName` pin | same |
| `check-subs03b0-contract.sh` | dev `versionName` pin | same |
| `check-subs03b1-contract.sh` | dev version pin | same |
| `check-subs04a2-contract.sh` | dev version + marker pin | `check-subs04b`, `check-subs04c*` |

## 2026-10-09 — 24 green orphans whose guarantees live elsewhere (step 3)

Triage: `reports/2026-10-09-orphan-triage.md`. These 24 were among the 60 checkers that
neither `check-all-contracts`/`check-source-contract` nor the release manifest
(`tools/contract02/manifests/beta09.tsv`) reach. All of them were green; none
was red debt. Each one's guarantee is covered by a gated checker or a behaviour
test, or it only pinned text with no behavioural content. The owner approved
the batch and the removal of the six past release manifests only these read
(`beta03`–`beta08.tsv`, `auth07c1-c2.tsv`).

| Checker | Covered by |
|---|---|
| `check-beta03-release-scope-contract.sh` | text only: the beta03 changelog wording and its own manifest |
| `check-beta04`…`beta08-nonnative-suite-contract.sh` (5) | every child is gated and is in `check-beta09-nonnative-suite-contract.sh` |
| `check-beta04`…`beta08-release-scope-contract.sh` (5) | `check-beta09-release-scope-contract.sh`, the gated `check-source-contract.sh` (e.g. the `fun RedditComposerEditor(` guard), and behaviour tests: VideoPlaybackLoopPolicyTest, FeedEntryRefreshGateTest, AppLockControllerTest, Issue34UserTagsTest, ShareIntoSubshellTest, ContentFilter02/03/04 tests, Issue44PostFlairRichTextTest, EditDiscardConfirmationComposeTest |
| `check-contract02c-contract.sh` | auth07c1/auth07c2 are gated through `beta09.tsv` |
| `check-contract02e-contract.sh` | text only: the beta02 release record |
| `check-inbox01a13-contract.sh` | RedditInboxNetworkDataSourceTest (read-only inbox/sent requests, merge), InboxSent01LaterPageTest, DataLayer05InboxSentFailureTest |
| `check-inbox01b11-contract.sh` | RedditInboxReadMutationNetworkDataSourceTest (documented endpoint, 25-id batches, bounds), Issue106TabMarkReadComposeTest, gated messages02a (ISSUE106). Its "non-retrying client" pin matched any of 10 uses in the file and guarded nothing; a real test of the inbox writer's client is part of step 3 |
| `check-messages02d3a-contract.sh` | Messages02D3ARedactionProjectionPolicyTest; reader GET-only in gated messages02c7 (now also refuses `patch`) |
| `check-messages02e1-contract.sh` | Messages02E1ReplyReactionProbePolicyTest (8 tests); GET-only in messages02c7 |
| `check-mute01a31`, `mute01a34`, `mute01a34r1-contract.sh` | text only: Settings preview copy and sizes; scene selection is behaviour-tested |
| `check-reddit01b-contract.sh` | gated source-contract and reddit01c3c1; AuthCapabilityAuthorizationOwnerPolicyTest, Auth10CapabilityScopesTest |
| `check-reddit01c1-contract.sh` | gated source-contract; Reddit01c1OwnerBoundCapabilityPolicyTest, the callback owner-routing test, the runtime-shape test |
| `check-settings03g-contract.sh` | text only: colour and elevation values |
| `check-write02f1-contract.sh` | gated source-contract; PostManageFlowPolicyTest, Composer07FlairPickerTest |

As before, the slice-zero snapshot under `docs/slicezero/` still lists these
checkers and is not rewritten.

## 2026-10-09 — contract02b and contract02f replaced by gated selftests

Both were orphans. `check-contract02b-contract.sh` only looked for three case
names in `tools/contract02/selftest.sh`, and nothing ran that selftest.
`check-contract02f-contract.sh` pinned the release path, which the gated
workflow01, auth05d2 and quality-gates checkers also pin, but nothing tested
`verify-release-qualification.py`. The owner approved the change.
`tools/check-contract02-selftests-contract.sh` now runs under source-contract
on every land. It runs:

- `tools/contract02/selftest.sh`, now with a fourth case: a predecessor listed
  after the row that depends on it stops the runner with
  `PREDECESSOR_MUST_APPEAR_EARLIER`.
- the new `tools/contract02/verify-release-qualification-selftest.sh`: a
  throwaway git source and one good record, then twelve bad records that must
  be refused with their reasons (commit, tree, manifest hash and path, gates,
  blocking failures, unit tests, warnings, a missing key, a record inside the
  source, a dirty source).

`tools/contract02/manifests/contract02b.tsv` only fed the retired checker and
was removed with it.

## 2026-10-10 — the Matrix direct-chat orphans, after behaviour tests (step 3)

All five were orphans. Their transport guarantees had no test: no Matrix
writer or reader was built in a test, because the web token is minted in a
WebView. A seam (`RedditChatWebTokenSource`, STEP3_MATRIX_WEB_TOKEN_SEAM_V1)
lets tests pass a fixed token. `MatrixTestFixture` adds a signed-in account, a
session cache that can be warmed, and a recording OkHttp interceptor. Every
new test was red against a sabotaged implementation before the checkers left
(owner-approved rule).

| Checker | Covered by |
|---|---|
| `check-messages02d1-contract.sh` | `Step3MatrixLiveReaderTest`: every sync asks only for `m.typing`/`m.receipt` ephemerals with presence off, and every request is GET. Projection: `Messages02D1MatrixEphemeralProjectionPolicyTest` |
| `check-messages02d2a-contract.sh` | `Step3MatrixLiveReaderTest` (typing forwarded true, then false); `AuthConnectViewModelDirectChatLiveTest.peerTypingIsClearedWhenTheStreamStopsOrFails`; `Step3DirectChatLiveComposeTest` (the "u/peer is typing…" label, no "Seen"); `Messages02D2APeerTypingUiPolicyTest` |
| `check-messages02d2b-contract.sh` | `Step3MatrixWritersTest` (one PUT to the typing path with the timeout, nothing without a warm room, 404 forgets the room, writes serialised, logs without IDs); `Step3DirectChatTypingGateComposeTest` (editing sends typing, clearing and sending stop it); `Messages02D2BMatrixTypingPolicyTest` |
| `check-messages02d3b-contract.sh` | `Step3MatrixWritersTest` (one PUT `{}` to the redact path, a lost answer is OUTCOME_UNKNOWN with one attempt, nothing without a warm room, logs without IDs); `Step3MatrixLiveReaderTest` (redactions forwarded); `Step3DirectChatLiveComposeTest` (asks first; an unknown outcome offers no second delete); `Messages02D3B*` tests |
| `check-messages02d5a-contract.sh` | `Step3MatrixWritersTest` (whoami, directory search, `type=direct&with_user` lookup, createRoom, send, room remembered; two rooms fail closed with no create; a lost create or send is OUTCOME_UNKNOWN with one attempt; logs without IDs); `Step3NewMessageButtonGateComposeTest` (New message only for a signed-in active account); `Messages02D5A*` tests |

## 2026-10-10 — the moderator-response and modmail-row orphans, after behaviour tests (step 3)

All four were orphans. The behaviour now has tests, and each test was red
against a sabotaged implementation before the checkers were removed.

| Checker | Covered by |
|---|---|
| `check-modresponse01a1-contract.sh` | `Step3RemovalResponseComposeTest.theRemovalMessageTypesKeepRedditsWireValues`; `ModResponse01A1RemovalMessageWriterTest` (order, reporting, transport) |
| `check-modresponse01a2-contract.sh` | `Step3RemovalResponseComposeTest`: in both remove dialogs (ModeratorToolsSheet, InboxModeratorRemovalDialog) an empty message blocks the remove, a written message travels with the request, no message sends none, and a message that was not sent is said after the removal. Also `ModResponse01A2RemovalResponseDraftTest` |
| `check-modresponse01a3-contract.sh` | `Step3RemovalResponseComposeTest` (a saved response fills the draft; `saveAsResponseEnabled`/`saveAsResponse` save under the title and keep the dialog open when the list is full); `ModResponse01A3SavedResponsesTest`; the screen wiring is now structural: `savedResponses` is required, not nullable (STEP3_SAVED_RESPONSES_ARE_WIRED_V1) |
| `check-modmail07a1-contract.sh` | `Step3ModmailDetailComposeTest`: a full-screen conversation has Back and system Back closes it; the two-pane detail (MainRouteHost) has none, and the open row is `selected` (STEP3_MODMAIL_ROW_SAYS_IT_IS_SELECTED_V1, owner-approved) |

`tools/contract02/manifests/beta02-reference.tsv` (the beta02 release record,
no longer read by any checker) was removed with them.

"Save as response" is tested through `saveAsResponseEnabled` and
`saveAsResponse`, not by clicking. A Material AlertDialog that holds an
OutlinedTextField never goes idle under Robolectric here. A probe with a plain
AlertDialog and OutlinedTextField showed this, so it is not Subshell code.

## 2026-10-10 — the mute chain, after behaviour tests (step 3)

`mute01a1`, `mute01a2` and `mute01a3` were orphans. Each re-ran the one before
it, so they leave together; a31/a34/a34r1 were retired on 2026-10-09. Every
new test was red against a sabotaged implementation (two runs, so each test
was red for its own reason).

| Checker | Covered by |
|---|---|
| `check-mute01a1-contract.sh` | `Step3MuteHomeComposeTest`: Home leaves out a muted community's posts, says "Muted communities fill this page", a timed mute ends by itself with no refresh, and the mute log names no post titles. `Step3CommunityMuteSheetComposeTest`: every preset mutes for its own duration from the tap, Indefinitely has no end, and Until… refuses a past time with a toast and accepts a future one. `Step3MuteEntryPointsComposeTest`: the post menu mutes and unmutes. Also `Mute01A1TimedCommunityMutePolicyTest`. No scheduler, no network or Room: gated in `check-boundary-guards-contract.sh` |
| `check-mute01a2-contract.sh` | `Step3MuteCommunityComposeTest`: a muted community says it is hidden and opens its settings. `AppSheetsHostComposeTest`: unmute from the list, "No muted communities", the editor's "Change duration" and "Unmute community". `Step3MuteEntryPointsComposeTest`: the drawer badge opens the settings. `Mute01A2MuteManagementPolicyTest`. Auto-mirrored volume icons: gated tree-wide in `check-boundary-guards-contract.sh` |
| `check-mute01a3-contract.sh` | `Step3MuteCommunityComposeTest`: about-only replaces the posts, show-posts keeps them, and the mode is saved. `Step3MuteEntryPointsComposeTest`: a joined community's mute button asks for the editor, and the Settings choice changes the mode. `Mute01A3DirectViewModePolicyTest`, `Feeds02KeysAndStateComposeTest` |

## 2026-10-10 — the settings orphans, after behaviour tests (step 3)

All four were orphans. Every new test was red against a sabotaged
implementation before they left.

| Checker | Covered by |
|---|---|
| `check-settings02c-contract.sh` | `Step3BackupRetentionTest`: ten changes by default, a stricter limit trims at once, a merge trims its own history unless told not to, and the repository keeps the choice and trims with it. `Step3BackupRetentionComposeTest`: a stricter limit asks first and Cancel keeps the old one; a looser limit applies without asking. `SettingsBackupContractTest`. No process or network in backups: gated in `check-boundary-guards-contract.sh`, widened to the whole backup package |
| `check-settings02d-contract.sh` | `Step3SettingsComposeTest.theSideMenuOffersOnlyLeftAndRight` (only Left and Right are offered, Left is applied, and search for "follow" does not find the side menu); `Settings02DDrawerSidePolicyTest`, `FreshInstallInteractionSideOnboardingTest` |
| `check-settings03b-contract.sh` | `Step3SettingsComposeTest.theNavigationPageHasItsCommunitiesLabelsRowAndSearchFindsIt`; `Settings03BVisualPreviewPolicyTest` (preview geometry). Its pins on colours and sizes were visual only |
| `check-settings03c-contract.sh` | `Step3SettingsComposeTest`: Dynamic colour and Reduce motion toggle the state; the Theme and Code font dialogs change it. `Settings03CAppearancePreviewPolicyTest`, `LowUi8bComposeTest`. Its `git diff` scope fence was meaningless on a clean tree |

## 2026-10-11 — the inbox orphans, after behaviour tests (step 3)

All three were orphans. Every new test was red against a sabotaged
implementation before they left. inbox01b11's pin for the non-retrying client
guarded nothing; the real guard is the existing
`WriteTransportPolicyTest.everyWriterReachesRedditOnlyThroughANonRetryingClient`
(every writer in the wired runtime, walked recursively). A duplicate
`Step3MutationsNeverRetryTest` added on 2026-10-11 was removed again the
same day.

| Checker | Covered by |
|---|---|
| `check-inbox01a12-contract.sh` | `Step3InboxFallbackDetailComposeTest`: an item with nothing to open shows its own detail, is marked read, and is not looked up on Reddit. `Issue124InboxTargetOpenComposeTest`, `RedditInboxNetworkDataSourceTest`, `InboxOpenerCharacterisationTest` |
| `check-inbox01a14-contract.sh` | `Step3InboxFirstPageBalanceTest`: the first page pages sent history (and then the inbox) back so an older reply shows in its thread, and stops at four pages a side. `RedditInboxNetworkDataSourceTest` |
| `check-inbox01b21-contract.sh` | `Step3InboxBackgroundSyncTest`: one periodic work every 15 minutes on a connected network, kept (UPDATE) when scheduled again, and the seen-event history capped at 512 with the newest kept. `Inbox01ReadsAndSyncTest`, `InboxBackgroundSyncPolicyTest`. The rule that the sync never marks anything read is gated in `check-boundary-guards-contract.sh`, because the worker builds its own runtime. The "no notification code in the sync" guard was obsolete (posting is delegated to InboxNotificationCenter; since 2026-10-13 boundary guard BACKGROUND_SYNC_POSTS_ONLY_NEW_EVENTS and `Step3InboxNotificationPostingTest`, inbox01b22 being retired) |

## 2026-10-11 — the update orphans, after behaviour tests (step 3)

Both were orphans, and update01a1b re-ran update01a1a. A small seam made the
release-only parts testable: `UpdateCheckScheduler.schedule` and the
package-replaced receiver's `handle` take the release fence as a parameter
whose default is still `UpdateRuntimePolicy.enabled(APPLICATION_ID, DEBUG)`
(STEP3_UPDATE_RUNTIME_FENCE_IS_A_PARAMETER_V1). Every new test was red
against a sabotaged implementation.

| Checker | Covered by |
|---|---|
| `check-update01a1a-contract.sh` | `Step3UpdateRuntimeTest`: an own "App updates" channel whatever the inbox setting; a 12-hour periodic check on a connected network, only in the release runtime; "What's new" left pending only by the package-replaced action. Manifest boundary: `Update01A1PolicyTest`, `Update04ManifestTrustAndHealthTest`. No self-installer or Play Core: gated in `check-boundary-guards-contract.sh` |
| `check-update01a1b-contract.sh` | `Step3UpdatePresentationComposeTest`: a background-found update is offered on Home, waits away from Home, and never comes before the backup onboarding; an explicit open shows it anywhere, once; the debug preview does nothing outside debug; Help shows the changelog inline. `Update02CheckAndPresentationTest`, `Update01A1BChangelogPolicyTest`, `Update05ChangelogLoadTest` |

## 2026-10-11 — the issue16–23 orphans, after behaviour tests (step 3)

All five were orphans with no predecessors. Every new test was red against a
sabotaged implementation before they left. The comment row's avatar gate
moved into `commentRowAvatarUri` (PostScreen.kt), with the same marker, so
it can be tested without pixels. What stays source-read on purpose: no custom
pointer engine in SwipeActionSurface and the inline settings editor's shape
(`Issue16SwipeActionsPolicyTest`, `Issue16SwipeActionsSettingsPresentationPolicyTest`),
and the M3 chrome details of the comment image viewer (visual).

| Checker | Covered by |
|---|---|
| `check-issue16-swipe-actions-contract.sh` | `Step3SwipeActionsComposeTest`: a swipe past the threshold commits once and the row springs back (a second swipe commits again); a disabled surface commits nothing; on a post card the mapped vote runs only while swipe actions are on. `Issue16SwipeActionsPolicyTest`, `Issue39SwipeLabelComposeTest`, `PostSaveStateCharacterisationTest`, `SettingsStateCharacterisationTest` |
| `check-issue17-feed-refresh-contract.sh` | `Step3FeedEntryRefreshComposeTest`: Home and a community ask Reddit once on first entry even with cached posts, and not again on re-entry. `FeedEntryRefreshGateTest`, `PostPagingStateCharacterisationTest`, `Feeds02KeysAndStateComposeTest` |
| `check-issue19-inline-comment-fullscreen-contract.sh` | `Step3CommentImageFullscreenComposeTest`: tapping an image in a thread comment opens the CommentMedia route with its uri, and closing returns to the same post. `Issue19InlineCommentFullscreenPolicyTest`, `MediaViewerActionsComposeTest`, `PredictiveMediaBackComposeTest` |
| `check-issue22-comment-avatars-contract.sh` | `Step3CommentAvatarsTest`: on by default, also for settings saved before the key existed; the row shows a known avatar only while the setting is on; `Comment` and `Post` carry no author avatar or author id (so the cache codec cannot store them). `Issue22CommentAuthorFullnameProjectionTest`, `Issue22AuthorAvatarBatchLookupTest`, `Issue22AuthorAvatarStateTest` |
| `check-issue23-drawer-dismiss-contract.sh` | `Step3DrawerDismissComposeTest`: with swipe actions on, a drawer opened through its button closes on a scrim tap and on Back. `Issue23DrawerGesturePolicyTest` |

## 2026-10-11 — the next01a5/a6 and subs04a0/a1 orphans, after behaviour tests (step 3)

All four were orphans with no predecessors. Every new test was red against a
sabotaged implementation before they left. Their negated boundaries were
already gated in `check-boundary-guards-contract.sh` (route-aware
Subscriptions open, no static discovery topics, MainActivity membership
wiring). Markers stay with their code; `NEXT01A6_SHARED_PUBLIC_USER_PROFILE_HOST_V1`
is still a file locator for gated next01a4b/a4c and profile01a2/a4b.

| Checker | Covered by |
|---|---|
| `check-next01a5-contract.sh` | `Step3SubscriptionsCommunityWorkspaceComposeTest`: expanded split shows "Select a community"; a tapped community opens beside the list without a back button, its row says it is selected, and a second tap replaces it at the same route depth; on a phone it opens full screen with Back. `Next01A5SubscriptionsCommunityPolicyTest`, `RouteReplaceCharacterisationTest` |
| `check-next01a6-contract.sh` | `Step3PublicUserWorkspaceComposeTest`: another user's post opens beside the profile and the next replaces it (same depth, only for that user); your own profile is not claimed; both profile hosts ask for the next posts when the list ends on screen. `Next01A6PublicUserContentWorkspacePolicyTest`, `SubshellAppUserProfileWiringComposeTest`, `ContextRouteHostsComposeTest` |
| `check-subs04a0-contract.sh` | `Step3CommunityMembershipHeaderComposeTest`: Join sends JOIN, Joined sends LEAVE, and a change on its way says Joining… / Leaving… and cannot be pressed. `CommunityMembershipUiPolicyTest`, `CommunityMembershipCoordinatorTest`, `DataLayer09MembershipOutcomeTest`, `Issue97MembershipSyncRetryComposeTest` |
| `check-subs04a1-contract.sh` | `Step3CommunityDiscoveryComposeTest`: discovery starts on Reddit's popular communities; typing shows the "Press search for all communities" hint and no sort chips; a submitted search asks for best match, then offers Best match / Most active, which asks again by activity. `Search02A12SearchDiscoveryPolicyTest` |

## 2026-10-11 — the last step 3 orphans: write02f, polls01a1, diag01a3, egg01

All four were orphans with no predecessors. Every new test was red against a
sabotaged implementation before they left. The negated boundaries that no
behaviour test can show were already gated in `check-boundary-guards-contract.sh`
(no poll write transport; the help-screen terminal has no network, Reddit,
process or file access). Two older rows above (diag01a1, diag01a2) name
diag01a3 among their covers; diag01a3 never pinned support links, and those
rows rest on the Diag01A* and DiagnosticReport* tests listed beside it.
Given up on purpose: the report sheet's `sheetGesturesEnabled = false` /
`dragHandle = null` (visual and device-checked) and the doc wording pins.

| Checker | Covered by |
|---|---|
| `check-write02f-contract.sh` | `Step3PostManageScopesTest`: flair needs `flair`, NSFW and spoiler need `modposts`, reply notifications need `edit`; without it nothing is sent and the result asks to authorize. `Step3PostManageOwnerAndStateTest`: someone else's post is refused (NOT_OWNER) before Reddit is asked, for load and change; a confirmed change shows on that post in every loaded feed and nowhere else. `Step3PostManageScreenComposeTest`: Post flair, NSFW, Spoiler, Reply notifications, each switch sending the opposite of the current value. `Mod01A2ModerationAuthorizationPolicyTest`, `RedditPostManageNetworkPolicyTest`, `WriteTransportPolicyTest`, `OwnContentActionPolicyTest`, `PostManageFlowPolicyTest` |
| `check-polls01a1-contract.sh` | `Step3ReadOnlyPollComposeTest`: a signed-in vote changes nothing locally; the poll opens in the default browser's own tab (Custom Tabs session, may not hand the page to another app), and a synthetic id opens nothing; the hand-off is remembered once and can be cleared; a signed-in poll offers "Open on Reddit to vote" and an option opens it there; a closed poll offers "Open on Reddit". `Polls01A1LiveReadOnlyPollProjectionTest`, `Polls01A1PollCachePolicyTest`, `Polls01A1PollLinkPolicyTest`, `Polls01A1ExternalPollReboundPolicyTest` |
| `check-diag01a3-contract.sh` | `Step3ReportAProblemComposeTest`: "Report a problem" records the user incident before the report, the report preview carries it, nothing on the screen or sheet takes text, and the report text is not in a sideways scroll. `Diag01A3UserIncidentPolicyTest` |
| `check-egg01-contract.sh` | `Step3EasterEggComposeTest`: a long upward pull held from the bottom of Help & about reveals the cursor, a short one does not; the terminal answers `help` sent from the keyboard and Back returns to Help & about. `Egg01TerminalPolicyTest`, `SecondaryAndCommunityRouteHostsComposeTest` |

## 2026-10-11 — gated: the reddit01c owner-bound authorization family (step 3)

The first gated checkers retired (owner: "do it" — go on with step 3 into the
gated checkers, same rule as the orphans). All five ran only from the
`check-source-contract.sh` loop after the main checks; that loop no longer
lists them (`reddit01c3c1` stays). None ran a predecessor; reddit01c4 read
reddit01c3b's text, so they leave together. Their build markers in
`app/build.gradle.kts` stay (source-contract's text-post block still accepts
the reddit01c2 marker in one branch of an `or`). Every new test except one
was red against a sabotaged implementation; `withNothingWaitingACallbackSendsNothing`
has nothing to sabotage (no change is waiting).

| Checker | Covered by |
|---|---|
| `check-reddit01c2-contract.sh` | `Step3CapabilityAuthorizationWiringTest`: the real runtime's comment-write, text-post, comment edit/delete and post edit/delete capabilities authorize as their own owner for their own scopes (literal scope names). `AuthConnectViewModelCallbackOwnerRoutingTest`, `AuthConnectViewModelCallbackRoutingTest`, `Reddit01c2StandardWriteOwnerPolicyTest`, `H3PostDeleteOutcomeComposeTest` |
| `check-reddit01c3a-contract.sh` | `Step3CapabilityAuthorizationWiringTest` (composer flair, composer requirements). `Reddit01c3a*` policy tests, `AuthConnectViewModelCallbackOwnerRoutingTest` |
| `check-reddit01c3b-contract.sh` | `Step3OwnerBoundAdaptersTest`: the inbox and custom-feed adapters authorize as their own owner, ask the earlier capability for the grant (and, for custom feeds, the session and ensuring it), and hand on the upgraded session. `Step3CapabilityAuthorizationWiringTest` (inbox, custom feeds, membership, favorites). `Reddit01c3bSpecializedAuthorizationOwnerPolicyTest`, `AuthConnectViewModelCallbackReadFailureTest` |
| `check-reddit01c3c2-contract.sh` | `Step3CapabilityAuthorizationWiringTest` (Manage post: owner `post_manage` with contexts edit / flair / moderation). `Step3PostManagePersistedRouteTest`: after process death the waiting change is sent once only for the same context and account; another context or account sends nothing and says so; an unknown context is ignored. `Reddit01c3c2PostManageAuthorizationContextPolicyTest` |
| `check-reddit01c4-contract.sh` | Already covered: `AuthConnectViewModelCallbackRoutingTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, `AuthConnectViewModelCallbackReadFailureTest`, `Reddit01c4LegacyFallbackFencingPolicyTest`. Its one negated guard (the ViewModel must not use the runtime's inbox predecessor) is structural: the runtime has no such field |

The same wiring test also pins, for the first time, the owner and scopes of
votes, saves, follows, content safety, modmail, mod log, moderator users and
team, moderation actions and removal reasons (which also ask for
`modcontributors`, MOD01A21).

## 2026-10-11 — gated: moderation02a5a6 and moderation02a5a7 (step 3)

Both ran from `check-source-contract.sh` (`run_required_contract`); a5a7 read
a5a6's text, so they leave together. Every new test was red against a
sabotaged implementation (one control first hung on a deadlocked writer; the
concurrency test now has a timeout so a missing guard fails instead).
The activity wiring cannot be shown by a JVM test and every
`ModeratorTeamRuntimeEntryPoints` field has a default, so it moved into
`check-boundary-guards-contract.sh` as `ACTIVITY_WIRES_MODERATOR_TEAM`
(like the membership wiring). Given up on purpose: layout/copy pins and the
"no ModalBottomSheet" shape pin.

| Checker | Covered by |
|---|---|
| `check-moderation02a5a6-contract.sh` | `Step3ModeratorTeamWritersTest`: the permission writer sends one change at a time, only for the account active before and after authorization (nothing goes out otherwise), and asks to authorize when the grant is missing. `Step3ModeratorTeamComposeTest`: only another moderator with known permissions can be edited; an acknowledged change reloads from Reddit and says so; Back closes the editor but not while it sends. `ModeratorToolsRootEntryPointsComposeTest.leavingModeratorsDismissesTheTeamEditors` now covers all three editors. `Moderation02A5A6ModeratorPermissionNetworkTest`, `Moderation02A5A6ModeratorPermissionCoordinatorTest`, `Moderation02A5A6PresentationPolicyTest`, `Moderation02A5A5AuthorizationPolicyTest`, `WriteTransportPolicyTest`, `WriteOut01Test`, `AuthConnectViewModelCallbackOwnerRoutingTest` |
| `check-moderation02a5a7-contract.sh` | `Step3ModeratorTeamWritersTest`: the removal writer, same rules, and Reddit's answer read as 401/403 → authorization, 5xx or a lost answer → unknown outcome, other 4xx → HTTP. `Step3ModeratorTeamComposeTest`: an acknowledged removal reloads and keeps the moderator listed until then; removal does not depend on the order Reddit lists moderators. `Moderation02A5A7ModeratorRemovalNetworkTest`, `Moderation02A5A7ModeratorRemovalCoordinatorTest`, `Moderation02A5A7PresentationPolicyTest`, `Issue53ModeratorTeamUsernameCaseComposeTest`, `Issue86ModeratorEditorBackComposeTest`, `LowUi6aComposeTest` |

## 2026-10-11 — gated: profile01a4b (step 3)

Ran from the `check-source-contract.sh` profile/auth loop and as a child of
`check-error01a5-contract.sh`; both lists dropped it. It re-ran
profile01a4a, which stays gated on its own. auth07a/b1/b2/c1/c2 and
profile01a4a read only its build marker (`SUBSHELL_PROFILE01A4B_…_V1` in
`app/build.gradle.kts`), which stays. The DEAD02 negations (the removed
profile comments flow) are pure text and leave with it. Every new test was
red against a sabotaged implementation. The Saved/Upvoted test went red only
when both screen filters were removed (at the Upvoted step); with the Saved
filter alone removed the Saved tab still listed only the saved post, so the
local repository seems to filter too (not traced further).

| Checker | Covered by |
|---|---|
| `check-profile01a4b-contract.sh` | `Step3ProfileCommentTargetTest`: a tapped comment's post is read from Reddit with the account's authorization every time, never from or into the cache; a missing authorization asks for it without reading. `Step3ProfileCommentsComposeTest`: tapping a signed-in profile comment resolves its post and opens it at that comment; a local profile says "No local comments" and its Saved / Upvoted tabs list only saved / upvoted posts. `UserProfileContentComposeTest`: both profile hosts hand a tapped comment to the resolver. `Profile01a4bProfileCommentsUiPolicyTest`, `Profile01a4aProfileCommentsPolicyTest`, `AuthApplicationRuntimeFactoryTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, `Dead02ProfileHistoryAuthorizationTest`, `Issue215NavigationTargetCharacterisationTest`, `LowUx3ComposeTest` |

## 2026-10-12 — gated: messages02c4r2 and messages02c5 (step 3)

Both ran from `check-source-contract.sh` (`run_required_contract`).
`check-messages02c5r2-contract.sh` required and re-ran messages02c5, and
`check-messages02c4r3-contract.sh` required messages02c4r2's file; both were
changed to stop (their own pins and the build markers stay). messages02c5
re-ran messages02c4r3r1 and messages02c4r2 re-ran messages02c4r1, which stay
gated on their own. Every new test was red against a sabotaged
implementation. Three rules a JVM test cannot express moved into
`check-boundary-guards-contract.sh`: comment writes use only the OAuth API
(no chat token, cookie or compose endpoint; the message side stays in
messages02c), the message reply writer has no wait/retry loop, and a pasted
image is copied into the app before the comment composer attaches it (the
paste arrives through a content receiver).

| Checker | Covered by |
|---|---|
| `check-messages02c4r2-contract.sh` | `Step3MediaReplyPipelineTest`: a comment and a message-thread reply with an image lease a slot, upload, convert and send one `richtext_json` that refers to the asset id, with no markdown text and no upload URL. `Step3MediaReplyRefreshComposeTest`: an accepted reply is marked when it carried an image, and the conversation then reads the thread again. `RedditRichTextMediaPolicyTest`, `MediaRichText01ConvertedTextTest`, `CommentWriteFlowPolicyTest`, `RedditCommentSubmitPolicyTest`, `RedditMessageReplyNetworkPolicyTest`, `RedditCommentRichMediaProjectionPolicyTest`, `Messages02CComposerPolicyTest`, `Low2MediaStagingTest`, `WriteTransportPolicyTest`, `DataLayer07ReplyOutcomeUnknownTest` |
| `check-messages02c5-contract.sh` | `Step3MediaReplyPipelineTest`: a Direct Chat image goes to Matrix and never to `/api/comment`; without a Matrix writer it is unavailable. `MessageReplyFlowPolicyTest`, `Messages02CComposerPolicyTest`, `Issue55ConversationMediaKeyedComposeTest`, `Composer10CommentMediaRulesTest`, gated messages02c / c3 / c5r2 / c5r3 |

## 2026-10-12 — gated: inbox02a (step 3)

Ran from `check-source-contract.sh` (`run_required_contract`);
`check-messages02a-contract.sh` required its file and no longer does. The
`versionCode = 234` pin was pure text. Its one meaningful negated guard (a
notification must not resolve by the inbox item's id) is now a behaviour test.
Every new test was red against a sabotaged implementation (three control runs).

| Checker | Covered by |
|---|---|
| `check-inbox02a-contract.sh` | `Step3InboxNotificationTargetComposeTest`: a reply notification looks its post up by the post's id, opens it at the exact comment and leaves the read mark to the thread; a post reply without a comment target is marked read once its post is open. `Step3InboxActivityComposeTest`: rows say "Replied to your comment" / "post"; a long press starts a selection; dismissing part says "1 dismissed" (Undo), dismissing all says "Activity cleared"; dismissals and the clear are kept per account across a restart (real SharedPreferences). `RedditInboxNetworkDataSourceTest`, `Inbox02AActivityPolicyTest`, `Inbox02ACacheCompatibilityTest`, `InboxOpenerCharacterisationTest`, `InboxActivityStateCharacterisationTest`, `Issue109FocusedLandingComposeTest`, `AppTopBarComposeTest`, `Issue106TabMarkReadComposeTest` |

## 2026-10-12 — gated: deeplink01 (step 3)

Ran from `check-source-contract.sh` (`run_required_contract`, and listed among
its required files); both lines are gone (its doc stays in that list). Every
new test was red against a sabotaged implementation. MainActivity taking a
forwarded link in on a cold start and through `onNewIntent` moved into
`check-boundary-guards-contract.sh` (`ACTIVITY_TAKES_IN_REDDIT_LINKS`): tests
that started the whole activity passed alone but ran the unit-test JVM out of
memory in the full suite. Opening a held link is covered through SubshellApp
by Issue41/LowUi2. Not covered: the device preferences' backup exclusion stays
source-read (Backup03 tests).

| Checker | Covered by |
|---|---|
| `check-deeplink01-contract.sh` | `Step3RedditLinkDeliveryTest`: every Reddit host (and redd.it) for r/, comments/ and user/ paths resolves to `ExternalLaunchActivity` and nothing else, other hosts and paths to nothing. `Step3RedditLinkSurfacesComposeTest`: a crosspost's source opens in Subshell and Back returns; a signed-in reader is asked once ("Set as default" → settings, "Not now" → dismiss) and not when Subshell already handles the links. `RedditTargetResolverTest`, `RedditUrl01ResolverTest`, `Issue215NavigationTargetCharacterisationTest`, `Issue41RedditLinkNoticeComposeTest`, `LowUi2MainThreadComposeTest`, `Issue70SelectedPostAccountTest`, `RedditUrl04AppLinkTest`, `Issue46ShortUsernameTest`, `RedditLinkHandlingPolicyTest`, `HeldDecisionsTest` |

## 2026-10-12 — gated: vote01 (step 3)

Ran from `check-source-contract.sh` (required file and `run_required_contract`)
and was re-run by `check-scroll01-contract.sh`; all three stopped. Its own
predecessors (error01a5, auth07b1, protoclean01a3) stay gated on their own.
Every new test was red against a sabotaged implementation; the retry control
needed both guards removed (the coordinator drops a non-retryable vote and
also refuses to retry it).

| Checker | Covered by |
|---|---|
| `check-vote01-contract.sh` | `Step3VoteComposeTest`: a signed-in upvote on a post card sends one request for that post, shows the new score at once and holds only the vote buttons (Save stays usable); removing a vote counts from Reddit's own vote; a comment's upvote targets the comment; Compact mode shows "score · time" in the comment header; Settings changes vote controls and colours. `Step3PostVoteViewModelTest`: the ViewModel sends a vote to the writer once and reports the outcome; Retry resends only after a retryable failure. `Step3PostVoteTransportTest`: a lost connection is sent once and is a retryable transport failure. `Vote01PostVotePolicyTest`, `PostVoteStateCharacterisationTest`, `Vote02SettlementsTest`, `Low1AuthorizationMappingTest`, `Auth17SessionBoundBearerTest`, `WriteTransportPolicyTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, `Issue218ContrastTest`, `RuntimeErrorReportsComposeTest`, `SettingsStateCharacterisationTest` |

## 2026-10-12 — gated: homeaction01 (step 3)

Ran from `check-source-contract.sh` (required file and `run_required_contract`)
and was re-run by commentsave01a1, a2, a3 and a31; all six places stopped.
It re-ran scroll01, which source-contract also runs directly. Its MEDIA file
sets (dev1's VIDEO00 arrays) leave with it; its two video-adjacent pins (the
post-media long-press sheet and the image viewer's aspect callback) are now
behaviour tests on image posts and the gallery, no video file. Every new test
was red against a sabotaged implementation; the gallery test went red when
the double-tap zoom was removed (the pager lock alone is a second layer: a
zoomed page takes the swipe for panning anyway).

| Checker | Covered by |
|---|---|
| `check-homeaction01-contract.sh` | `Step3PostActionsComposeTest`: a post card shares its Reddit link through the system share sheet and copies it from the overflow, which has no Share or Download of its own; Home's Comments button opens the post at its comments; the post screen scrolls to them once; a long press on a post image offers Share media, Share post link and Download. `Step3GalleryZoomComposeTest`: a double-tapped gallery page keeps the swipe for panning, zooming out pages again. `Step3PostSaveViewModelTest`: the ViewModel sends a save to the writer once and reports it. `HomeAction01PostActionsPolicyTest`, `PostSaveStateCharacterisationTest`, `FeedScrollAnchorPolicyTest`, `SubshellCacheCodecPolicyTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, `AuthApplicationRuntimeFactoryTest`, `WriteTransportPolicyTest`, `RuntimeErrorReportsComposeTest`, `CommentMediaActionsComposeTest`, `MediaViewerGesturePolicyTest`, `Media02ImageMemoryAndCancelTest` |

## 2026-10-12 — gated: error01a3 (step 3)

Ran from `check-source-contract.sh` (required file and `run_required_contract`)
and was re-run by `check-error01a4-contract.sh`; `check-error01a5-contract.sh`
read its text. All four places stopped; its build marker stays (other
checkers read it). Its video pins (the player's ERROR → Retry →
`coordinator.retry`, no "Retry video" text) stay gated in dev1's
media-audio-r27 and issue11-video-error. A seam makes the image loader's
network a parameter (`RemoteImageLoader.load(uri, callFactory)`, default the
shared media read client; STEP3_IMAGE_LOADER_CALL_FACTORY_IS_A_PARAMETER_V1).
The rule that a backup failure keeps no exception text moved into
`check-boundary-guards-contract.sh` (`BACKUP_FAILURES_KEEP_NO_EXCEPTION_TEXT`,
control red). Every new test was red against a sabotaged implementation,
except `mainActivityIsAFragmentActivity` (structural: the activity's own code
needs the FragmentActivity APIs) and the empty-body case, which is also
caught by the body read (the content-type control made that test red).
Given up on purpose: the backup screen's tray wiring and the doc/version pins.

| Checker | Covered by |
|---|---|
| `check-error01a3-contract.sh` | `Step3ImageLoaderFailuresTest`: an image that cannot be shown fails with a typed reason — HTTP status (retryable only for server answers), wrong content type, empty body, unreachable host (DNS), redirect to an untrusted host (security). `Step3BackupAndMediaFailureKindsTest`: IO / bad input / unusable storage keep their kind and survive being stored; video codes read as "Media code" (4003 "Decoding failed", 2002); MainActivity is a FragmentActivity. `Step3ImageFailureComposeTest`: a failed image says so with Retry, a compact one shows only "Image unavailable", a failed avatar falls back to the letter. `Error01a3MediaBackupErrorPolicyTest`, `LowMediaVideoTest`, gated media-audio-r27 / issue11-video-error |

## 2026-10-12 — gated and release tooling: auth08a1 and quality01d-usektx (step 3)

The owner approved touching release tooling for these two. auth08a1 ran as a
child of `check-error01a5-contract.sh` and in the beta09 non-native suite;
error01a1, error01a4, error01a5 and auth08a2 read its text (successor gates).
quality01d-usektx ran from `check-all-contracts.sh` (listed in
`check-quality-gates-contract.sh`) and in the beta09 non-native suite. Both
leave `check-beta09-nonnative-suite-contract.sh` and the required list in
`check-beta09-release-scope-contract.sh`; the beta09 manifest itself never
listed them. Every new test was red against a sabotaged implementation.
quality01d-usektx's core-ktx version, edit counts and PASS labels were pure
text; its subtitle `toUri()` pins stay in source-contract QUALITY01E.

| Checker | Covered by |
|---|---|
| `check-auth08a1-contract.sh` | `AuthConnectViewModelLogoutTest.loggingOutTheActiveAccountDeletesOnlyItsSession` (the active account's session and registry entry go, another account's stay). `Step3AccountSettingsComposeTest`: logout only after the reader confirms (Cancel sends nothing) and the question closes when done; "Add Reddit account" starts a fresh sign-in; after a logout the app takes the account list from the directory. `AuthConnectViewModelLogoutTest`, `LowUx1ComposeTest`, `AccountSwitcherSheetComposeTest`, `Auth07b1AccountSessionPresentationPolicyTest`, `Profile01a3AccountSwitcherPolicyTest`, `Auth19RegistrationKeepsLaterChangesTest` |
| `check-quality01d-usektx-contract.sh` | `Step3BackupRestoreCommitComposeTest`: a restore is one synchronous commit to the real settings file, readable at once; a failed commit stops the restore and the running settings stay. `Issue65PostActionsOffMainComposeTest`, `Backup02A2TransactionalRestoreTest`, source-contract QUALITY01E |

## 2026-10-12 — gated: moderation02a2, 02a3, 02a5a5 and communityinfo01a2 (step 3)

All four ran from `check-source-contract.sh`. moderation02a3 also ran as a
child of `check-moderation02a4-contract.sh`; communityinfo01a2 as a child of
communityinfo01a3/a31/a32, and communityinfo01a33 read its gate line. Those
blocks are gone (successor comments in place). The source markers stay
(`MODERATION02A5A5_INVITE_EDITOR_V1` splits moderation02a5a4 and
moderation02a5-final-ux-material). Two boundaries no JVM test can express
moved into `check-boundary-guards-contract.sh`: `ACTIVITY_WIRES_MODERATOR_TEAM`
now also requires the invite writer and its authorization, and
`MODERATOR_REPORTS_ARE_NOT_LOGGED_OR_PERSISTED` (report dialog, coordinator,
parser: no log, saved state, preferences, files or database); both controls
red. Every new test was red against a sabotaged implementation (12 controls).
Given up on purpose: test-name, doc-title, colour/icon and wording pins, and
the "no CommunityInfo table" Room pin (the cache has no such table).

| Checker | Covered by |
|---|---|
| `check-moderation02a2-contract.sh` | `Step3ModerationWriterAccountRecheckTest` (ignoring reports after an account switch during authorization sends nothing). `Step3ModeratorReportsComposeTest`: ignoring reports waits for the reader to confirm the pinned item (Cancel sends nothing, the confirmation carries target, action and revision); an unknown report setting offers no action; shortened details are said; the loaded queue comes before recent items; the row button and the overflow item open their own item. `ModeratorToolsDialogsComposeTest` (the root host shows the dialog once, only for the active account). `Moderation02A2ReportParserTest`, `Moderation02A2ReportNetworkTest`, `Moderation02A2ReportPolicyTest`, `Moderation02A2ReportsCoordinatorTest`, `Moderation02A2SharedWriteGateTest`, `Moderation02A2ReportLookupPolicyTest` |
| `check-moderation02a3-contract.sh` | `Step3ModerationWriterAccountRecheckTest` (locking after an account switch sends nothing). `Moderation02A3ContentControlsCoordinatorTest.accountChangedDiscardsALateReadAndCannotWrite`. `Step3ModeratorContentControlsComposeTest`: locking waits for the confirm, which names the item, its community and how to undo it; a running write disables the confirm; an unknown distinguish state and another author's content offer no distinguish; the row button and the overflow item open their own item. `ModeratorToolsDialogsComposeTest`. `Moderation02A3ContentControlPolicyTest`, `…NetworkTest`, `…ContentStateParserTest`, `…SharedWriteGateTest`, `Moderation02A1CoordinatorTest` |
| `check-moderation02a5a5-contract.sh` | `Step3ModeratorTeamWritersTest` (invitation writer: another active account before or after authorization sends nothing, a second invitation while one runs is refused, a missing grant asks to authorize, Reddit's refusal carries its reason). `Step3ModeratorInviteComposeTest`: only a moderator with All permissions may invite; search shows at most six people, never the reader or a current moderator; at least one permission is needed; the invitation goes out only after "Invite u/x?" → Send invitation (Cancel sends nothing); Back closes the editor; an acknowledged invitation reloads from Reddit and does not add the invitee; a refusal shows Reddit's reason. `Moderation02A5A5ModeratorInviteNetworkTest`, `Moderation02A5A5ModeratorInviteCoordinatorTest`, `Moderation02A5A5AuthorizationPolicyTest`, `ModWrite01OutcomeUnknownTest`, `WriteTransportPolicyTest`, boundary-guards `ACTIVITY_WIRES_MODERATOR_TEAM` |
| `check-communityinfo01a2-contract.sh` | `Step3CommunityInfoCoordinatorTest`: one request per account and community (a second open does not read again unless forced; a refresh keeps what is shown); an answer for a community no longer asked for is dropped even when its read ignores cancellation; missing loader, thrown read and bad name each have their own failure; a cancelled read is not folded into a failure; a missing section is named. `Step3CommunityInfoLoaderTest`: only the active signed-in account, with its own token, under the read grant. `CommunityInfo01A2AuthenticatedLoaderPolicyTest`, `DataLayer08OwnerBoundTokenTest`, `Wiki01A1AuthorizationPolicyTest`, `AuthApplicationRuntimeFactoryTest` |

## 2026-10-12 — gated and release tooling: error01a1, 01a2, 01a22, 01a221, 01a4, 01a5 and auth08a2 (step 3)

The six error checkers ran from `check-source-contract.sh` (required files and
`run_required_contract`) and ran or read each other in a chain; auth08a2 ran
as their child and in the beta09 non-native suite, and is gone from that
suite and from beta09 release-scope's required list (owner approved, like
auth08a1). safety01a2 no longer re-runs error01a4; search01c1a1 no longer
reads auth08a2's result line. The build markers stay (source-contract reads
them in OR branches); the `docs/error/` contract docs stay. Two rules moved
into `check-boundary-guards-contract.sh`: `NO_RAW_EXCEPTION_TEXT_IN_APP`
(tree-wide, error01a5) and `SOURCE_CONTRACT_PROPAGATES_CHILD_FAILURES`
(checker tooling: no bare `bash tools/check-…` child in source-contract; now
for every checker, not only reddit01/profile01/auth07); both controls red.
Given up on purpose: version chains, test-name and doc pins, predecessor
helper names that must stay deleted, colour/icon text.

| Checker | Covered by |
|---|---|
| `check-error01a1-contract.sh` | `Step3SafeFailureTextTest` (provider text bounded at 240/80, never over 512; client_secret, authorization_code and cookie values redacted; ErrorPresentation/ErrorAction hold no function), `Step3ErrorTrayComposeTest.anErrorNoticesRetrySaysRetryToItsOwner`. `Error01a1ErrorPresentationPolicyTest`, `Error01a5FinalErrorAuditPolicyTest`, `LowUi7aSemanticsComposeTest`, `Issue222FeedbackTest` |
| `check-error01a2-contract.sh` | `Step3ReadFailureDetailsTest` (subscriptions and comments keep 429 + wait, DNS, malformed JSON; a failed session refresh keeps its status). `Step3ReadErrorSurfacesComposeTest` (Home refresh and next-page failures and a community's go to the tray under their scope with their details, never into the list; Retry repeats the same operation). `Step3PostActionsComposeTest` (signed in: "Refresh comments" in the overflow asks again; local account: absent). `Error01a2CoreRedditReadErrorPolicyTest`, `Feeds01CustomFeedPagingComposeTest` |
| `check-error01a22-contract.sh` | `Step3ErrorTrayComposeTest`: title, Details, Retry and "Dismiss error" (Retry once, Dismiss clears); still shown after 10 minutes; a screen that leaves takes its error with it and brings it back on return. `Error01a22ViewportErrorHostPolicyTest`, `Issue88ErrorTrayScopesTest`, `Issue222FeedbackTest`, `RuntimeErrorReportsComposeTest` |
| `check-error01a221-contract.sh` | `Step3ErrorTrayComposeTest` (a simple timeout has no Details; the sheet says "Error details" without repeating the tray's title or message), `Step3SafeFailureTextTest` (retryability alone is not a detail). `Error01a22ViewportErrorHostPolicyTest` |
| `check-error01a4-contract.sh` | `Step3WriteFailureDetailsTest` (a comment Reddit did not take: DNS → network kind, HTML → response kind, refusal keeps its code, 429 keeps status and wait), `Step3CreatePostCheckAgainComposeTest` ("Post may have been published"; Check again reads back, never sends), `Issue96aCustomFeedCancelComposeTest` (after an unknown create outcome Create stays off; after a refusal it is offered). `Error01a4RedditWriteErrorPolicyTest`, `LowCrash01Test`, `WriteOut01Test`, H1/H2/H3 outcome tests, `Composer01CommentOutcomeReadBackTest`, `Composer02PostSubmitAttemptTest` |
| `check-error01a5-contract.sh` | `Step3RepositoryFailureCauseTest` (a cached answer for posts, one post, a post for an account and comments keeps Reddit's failure details, offline or error; a user's identity says network or storage), `LowUx2ComposeTest.aFailedPageSaysWhyInTheFooter`. Boundary guards `NO_RAW_EXCEPTION_TEXT_IN_APP`, `SOURCE_CONTRACT_PROPAGATES_CHILD_FAILURES`. `Error01a5FinalErrorAuditPolicyTest` |
| `check-auth08a2-contract.sh` | `Step3SessionRecoveryTest` (a 401 for a session that cannot refresh ends only that session, marks the account for a new sign-in and moves the directory revision; a 401 for a token nobody holds changes nothing; signing in again as another Reddit account stores nothing), `AppSheetsHostComposeTest.aDirectoryRevisionThatMarksTheActiveSessionStaleOpensTheSwitcher`. `Auth11KeepRefreshTokenTest`, `Auth12OkStatusInvalidGrantTest`, `Auth17SessionBoundBearerTest`, `LowNetTest`, `AccountSwitcherSheetComposeTest`, `AuthConnectViewModelLogoutTest` |

## 2026-10-12 — gated: profile01a5, profile01a6a2, profile01a6a6 and profile01a2 (step 3)

All three ran from `check-source-contract.sh`. profile01a6a2 also ran
profile01a5; profile01a6a3 required and ran profile01a6a2; profile01a6a7 and
library02a1 required and ran profile01a6a6; useraction01a2 read two lines of
profile01a6a6's text. Those blocks are gone (successor comments). The
repro scripts and source markers stay (profile01a6a5/a6a7 read them). Two
boundaries moved into `check-boundary-guards-contract.sh`:
`PRESENTATION_HAS_NO_NETWORK` and `NO_RAW_WEB_ENDPOINTS_IN_PRESENTATION_SOURCE`
(controls red), and from profile01a2 `ACTIVITY_WIRES_USER_SUBMITTED_FEED`.
profile01a2 ran in source-contract's profile loop and from profile01a4a and
next01a4a (which also read two of its markers); those blocks are gone.
Given up on purpose: version chains, removed-code names, wording pins
("Saved in Drafts", Library copy), icon choices, the code-font family pin,
the debug ADB launch extras and the Room no-migration fence (a working-tree
fence on SubshellCacheDatabase.kt; protoclean01a3 stays gated).

| Checker | Covered by |
|---|---|
| `check-profile01a5-contract.sh` | `Step3FocusedCommentCacheTest` (a comment link's focused load is never stored as the whole discussion; a refresh keeps the opened comment when Reddit's page lacks it and stores both). `Step3OwnProfileComposeTest` (a comment row names its community, whose identity is asked for, and renders Markdown; Back returns to the opened comment). `Step3DeepRepliesComposeTest` ("↳ replying to u/…" past the indentation cap; no "Continue thread" row or "N more" count). Boundary guards above. `Profile01a5*PolicyTest` (cache codec, transport, flattened viewport, target-first navigation, full thread, hydration merge, presentation), `Issue109FocusedLandingComposeTest`, `LowUx4ComposeTest`, `Issue94FailingNodesComposeTest`, `Issue110RepliesRetryComposeTest`, `AuthConnectViewModelCommentLoadingTest`, `LowUi8cComposeTest`, `DebugFocusedCommentLaunchPolicyTest` |
| `check-profile01a6a2-contract.sh` | `Step3ProfileCommentsCacheTest` (what Reddit returned is there on the next open under the history key; comments from the earlier flow are found and moved). `Step3AccountHistoryCommentsCoordinatorTest` (stored copy first, then Reddit's answer and cursor; the next page asks with that cursor; no valid name reads nothing). `Profile01a6AccountHistoryQueryIdentityTest`, `Profile01a6AccountHistoryNetworkPolicyTest`, `Profile01a6a2CommentsSharedHistoryPolicyTest`, `AuthApplicationRuntimeFactoryTest`, `Issue58ProfileHistoryReentryComposeTest`, `Step3ProfileCommentTargetTest` |
| `check-profile01a6a6-contract.sh` | `Step3OwnProfileComposeTest.theStripNamesTheOpenDestinationAndHiddenAndDownvotesReadTheirOwnHistory` (the open destination shows its label; others are labelled, selectable icons; Hidden and Downvotes ask for their own history). `Profile01a6AccountHistoryUiPolicyTest`, `Profile01a2LiveUserPostsUiPolicyTest`, `Profile01a6a3LibraryShortcutPolicyTest`, `UserProfileDraftsWorkspaceComposeTest`, `Issue117SavedNeverBlankComposeTest`, `SampleDataContractTest` |
| `check-profile01a2-contract.sh` | dev1's `ProfileMediaAutoplayParityComposeTest` (profile media autoplays like the feed; owner-approved ownership fix), `Step3OwnProfileComposeTest.aSignedInProfileNeverShowsTheLocalSampleComments`, boundary guard `ACTIVITY_WIRES_USER_SUBMITTED_FEED`. `AuthConnectViewModelUserFeedTest`, `SubshellAppUserProfileWiringComposeTest`, `Step3PublicUserWorkspaceComposeTest`, `Profile01a1UserSubmittedFeedPolicyTest`, `Profile01a2LiveUserPostsUiPolicyTest`, `FeedScrollAnchorPolicyTest`, `LowUi8cComposeTest` |

## 2026-10-12 — gated: messages02c3, messages02c5r2, messages02a, reddit01d3b, search01c1a1 and search01c2 (step 3)

All six ran from `check-source-contract.sh` (search01c1a1/c2 also in its
required-files list; reddit01d3b in its profile loop). Dependents no longer
run or read them: messages02c4 (c3), messages02c5r3 (c5r2), messages02b (02a),
communityinfo01a3/a31/a32 (search01c2), and dev1's reddit01d4 no longer
greps reddit01d3b's text (dev1 told). Build markers and `docs/search/`
contract docs stay. One boundary moved into `check-boundary-guards-contract.sh`:
`CONVERSATION_COMPOSER_TAKES_PASTED_MEDIA` (control red). reddit01d3b was
pure text (naming and "read-only" terminology). Given up on purpose: version
chains, removed-code names, the message row's visual parity (VISUAL01 shows
it) and the people-search UI's log tag text.

| Checker | Covered by |
|---|---|
| `check-messages02c3-contract.sh` | `Step3ConversationComposerComposeTest` (GIF asks the system picker for image/gif and attaches it; the dock offers its ten Markdown actions; a message shows its GIF and formatted text). Boundary guard above. `Step3MediaReplyPipelineTest`, `Issue55ConversationMediaKeyedComposeTest`, `Issue99ReplyReadBackTest`, `RedditMessageReplyNetworkPolicyTest`, `PostComposerContractTest`, `Messages02C5R2MarkdownRendererPolicyTest`, `WriteTransportPolicyTest`, boundary-guards `MESSAGE_REPLY_WRITER_HAS_NO_RETRY_LOOP` |
| `check-messages02c5r2-contract.sh` | `MessageReplyFlowPolicyTest` (direct chat media via Matrix), `Step3MediaReplyPipelineTest`, `Messages02CComposerPolicyTest`, `Messages02C5R2MarkdownRendererPolicyTest`, `RedditCommentRichMediaProjectionPolicyTest`, boundary guard `CONVERSATION_COMPOSER_TAKES_PASTED_MEDIA` |
| `check-messages02a-contract.sh` | `Step3InboxMessagesComposeTest` (Today/Yesterday/Earlier in order; a row shows sender, "Direct chat", preview and age with no Dismiss; only real senders' avatars are asked for). `Inbox02AActivityPolicyTest`, `Issue106TabMarkReadComposeTest`, `Messages02B1ChatFidelityPolicyTest`, gated inboxm3a1 |
| `check-reddit01d3b-contract.sh` | Pure text (runtime/ViewModel naming, "read-only" terminology, doc sentences). Registration with the bootstrapper: `Auth19RegistrationKeepsLaterChangesTest` |
| `check-search01c1a1-contract.sh` | `Step3AccountDirectoryOffMainTest` (creating the directory and its snapshot touch no storage; loading, switching, reading a token and logging out do their storage work off the main thread; a switch shows at once and is stored afterwards; a token asked for early loads the directory first). `Auth09RegistryReadFailureTest`, `LaunchAsync01StoreWorkOffMainTest`, `Step3AccountSettingsComposeTest` |
| `check-search01c2-contract.sh` | `Step3PeopleSearchCoordinatorTest` (a name search asks for people only, not twice; a newer search drops the older late answer; no loader says so; the query is never logged, only its length). `Step3PeopleSearchLoaderTest` (only the active signed-in account, its own token and read grant; nothing stored). `Step3SearchResultsComposeTest` (communities → people → posts, three and two previewed; a person opens their profile without per-row lookups; Search needs two characters, no Clear). `RedditPeopleSearchNetworkDataSourceTest`, `RedditPeopleSearchLoaderTest`, `Search01C2LivePeoplePolicyTest`, `Issue57SearchResubmitComposeTest`, `LowUi8bComposeTest` |

## 2026-10-12 — gated: commentstability01/01a2/01a3, commentuser01, commentsave01a1/a2/a3/a31 (step 3, batch 3)

All eight ran from `check-source-contract.sh`; safety01a1/a2/a3 also re-ran
commentsave01a31 (those blocks are gone). commentstability01/01a2 were
already covered by behaviour tests; commentsave01a3/a31 pinned icons and
colours only. The source-reading `CommentSave01A2LiveUiPolicyTest` is deleted
with them (owner: tests that only read source and pin retired checkers go).
Given up on purpose: version chains, removed-code names, icons, colours.

| Checker | Covered by |
|---|---|
| `check-commentstability01-contract.sh` | `Issue110RepliesRetryComposeTest`, `CommentStability01VisibleHydrationCommitPolicyTest` |
| `check-commentstability01a2-contract.sh` | the same two |
| `check-commentstability01a3-contract.sh` | `Step3HydrationWaitsForScrollComposeTest` (replies that arrive mid-drag appear only after the finger lifts), `CommentStability01VisibleHydrationCommitPolicyTest.automaticHydrationStillDefersDuringActiveUserScroll` |
| `check-commentuser01-contract.sh` | `Step3CommentRowComposeTest` (author tap opens the profile and records comment activity; a deleted author shows "Account unavailable" and opens nothing; the thread button collapses and expands; a body tap collapses only in Comment mode). `CommentUser01PolicyTest` |
| `check-commentsave01a1-contract.sh` | `Step3CommentSavedCacheTest` (saved survives the cache, also in a 21-field payload; a 20-field payload reads as not saved and keeps its vote). `CommentSave01A1SaveFoundationPolicyTest` |
| `check-commentsave01a2-contract.sh` | `Step3CommentRowComposeTest` (Save/Unsave comment from the menu, Reddit's saved state shown, rollback after a failure, one save at a time, no Save without a Reddit account), `Step3CommentSavedCacheTest` (comment vs post wording of a failed save). `PostSaveStateCharacterisationTest` |
| `check-commentsave01a3-contract.sh` | Pure text (icon consistency); labels by `Step3CommentRowComposeTest` |
| `check-commentsave01a31-contract.sh` | Pure text (overflow icon system) |

## 2026-10-12 — gated: wiki01a1, wiki01a2 and wiki01a2r1-r4 (step 3, batch 3)

All six ran from `check-source-contract.sh` and from each other (r4 → r3 → r2
→ r1 → a2 → a1; r2 switched on r3's file). Owner decisions: the sheet's two
decisions moved unchanged into `communityAboutSheetMayMoveTo` and `wikiBackStep`
(CommunityAboutSheet.kt, STEP3_WIKI_SHEET_DECISIONS_ARE_FUNCTIONS_V1) so a test
can call them; the source-reading `Wiki01A2PresentationPolicyTest` and
`Wiki01A2R3BackOwnershipPolicyTest` are deleted with the checkers. Three
boundaries moved into `check-boundary-guards-contract.sh`:
`COMMUNITY_WIKI_IS_READ_ONLY`, `COMMUNITY_WIKI_IS_NOT_PERSISTED` and
`ACTIVITY_WIRES_COMMUNITY_WIKI`. Given up on purpose: version chains, the Room
`version = 6` pin, doc sentences, icons, shapes, and the window-Back wiring
inside the sheet (Robolectric cannot drive the sheet window; the decision it
calls is tested).

| Checker | Covered by |
|---|---|
| `check-wiki01a1-contract.sh` | `Step3CommunityWikiLoaderTest` (active signed-in account only, its own token; raw Markdown pages and the page list from Reddit's wiki answers with raw_json=1; 403/404 unavailable). `Step3CapabilityAuthorizationWiringTest.theWikiAsksToReadOnlyAndNothingAsksToEditAWiki`. `Wiki01A1*` tests, `AuthApplicationRuntimeFactoryTest`, boundary guards above |
| `check-wiki01a2-contract.sh` | `Step3CommunityWikiLoaderTest` (a configuration may carry more scopes; a missing grant asks to authorize and sends nothing), `Step3CommunityWikiCoordinatorTest` (no re-read unless forced, stale answer dropped, authorization required shown), `Step3CommunityWikiWorkspaceComposeTest` (three tabs; the wiki opens into its workspace and reads the page list only then; Enable Wiki access). `Wiki01A2AuthorizationContextPolicyTest`, `Low9AuthorizationFlowTest`, `AuthKey01OversizedContextKeyTest`, `AuthConnectViewModelCallbackOwnerRoutingTest` |
| `check-wiki01a2r1-contract.sh` | `Step3CommunityWikiWorkspaceComposeTest` (configuration pages folded until Show; a page shows its Markdown as text), `Step3WikiSheetAndMarkdownTest` (headings). `Wiki01A2R1WikiUxPolicyTest` |
| `check-wiki01a2r2-contract.sh` | `Step3CommunityWikiWorkspaceComposeTest` (back arrow page → wiki → community info), `Step3WikiSheetAndMarkdownTest` (rules, preview) |
| `check-wiki01a2r3-contract.sh` | `Step3WikiSheetAndMarkdownTest` (the sheet refuses to hide while the wiki is open; Back steps page → wiki → info → close) |
| `check-wiki01a2r4-contract.sh` | Repeated the others' pins and doc text; covered by the tests above |

## 2026-10-12 — gated: moderation02a1, 02a4, 02a5a1-a5a4 and 02a5-final-ux-material (step 3, batch 3)

All seven ran from `check-source-contract.sh`; 02a5a3 → a5a2 → a5a1 → 02a4 ran
each other, so they retire together. Nine tests that only read source as
text are deleted with them (owner rule): Moderation02A1SpamPresentation-,
02A5A1Presentation-, 02A5A1R3Navigation-, 02A5A1R4SingleSheetWorkspace-,
02A5A2Presentation-, 02A5A3Presentation-, 02A5A3Authorization-,
02A5A4Presentation- and 02A5FinalUxMaterialPolicyTest;
`Moderation02A4ModLogPresentationPolicyTest` keeps only its behaviour test.
One boundary moved into `check-boundary-guards-contract.sh`:
`ACTIVITY_WIRES_MODERATOR_TOOLS`. The ui/ network-import ban these checkers
repeated is source-contract NET01/NET02. Given up on purpose: version chains,
segmented-button and colour choices, "no second sheet" design pins,
removed-code names, the 10-page preflight cap.

| Checker | Covered by |
|---|---|
| `check-moderation02a1-contract.sh` | `Step3ModeratorSpamComposeTest` (Spam asks first and sends one SPAM for that item; not offered while another action runs; the question goes when item or account changes and is not restored; the inbox queue card offers Approve, Remove, Spam). `Moderation02A1SpamPolicyTest`, `Moderation02A1CoordinatorTest` |
| `check-moderation02a4-contract.sh` | `Step3ModerationLogTest` (read for its account with its token by GET only; 401 or a missing grant asks to authorize; 5xx retryable; the authorization is a fresh sign-in for the same active account only), `Step3ModeratorToolsWorkspaceComposeTest` (Mod log button only where the moderator may read). `Moderation02A4ModLog*` tests, `Step3CapabilityAuthorizationWiringTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, boundary guard above |
| `check-moderation02a5a1-contract.sh` | `Step3ModeratorUserWritersTest` (active account before and after authorization, one POST with type=banned, missing grant asks; field limits), `Step3ModeratorUserManagementCoordinatorTest` (one change at a time; authorization reloads and never resends; another account changes nothing), `Step3ModeratorUserManagementComposeTest` (ban only by Confirm ban; Unban asks; search at most six, never the reader; Back closes the editor). `Moderation02A5A1*` network/coordinator/authorization tests, `ModUsers01UserFilterTest`, `WriteTransportPolicyTest` |
| `check-moderation02a5a2-contract.sh` | the same three (mute: type=muted, Confirm mute, Unmute asks, note limit). `Moderation02A5A2*` network/coordinator/authorization tests, `Issue87FailurePerListTest` |
| `check-moderation02a5a3-contract.sh` | the same three (approval: type=contributor, Confirm approve, Unapprove asks). `Moderation02A5A3*` network/coordinator tests |
| `check-moderation02a5a4-contract.sh` | `Step3ModeratorTeamOverviewComposeTest` (reads once when it may, not when it may not; at most three identities at a time; local search keeps order; "Permission details not provided"; back to mod tools), `Step3ModeratorToolsWorkspaceComposeTest` (Moderators opens the view for the canonical community; its back returns to the root). `Moderation02A5A4ModeratorTeamPolicyTest`, `Step3ModeratorTeamComposeTest`, `ModeratorToolsRootEntryPointsComposeTest` |
| `check-moderation02a5-final-ux-material-contract.sh` | `Step3ModeratorUserManagementComposeTest` (header back and refresh; editors' back arrow), `Step3ModeratorInviteComposeTest` (an exact name the search does not find can still be invited; the reader's own name and a current moderator say why not). `LowUi6aComposeTest` |

## 2026-10-12 — gated: search01a1, 01b, 01c1, 02a1, 02a11 and 02a12 (step 3, batch 3)

All six ran only from `check-source-contract.sh` (01b/01c1 also in its
required-files list). The source-reading `Search02A12SearchDiscoveryPolicyTest`
is deleted with them (owner approved). One boundary moved into
`check-boundary-guards-contract.sh`: `SEARCH_PRESENTATION_HAS_NO_NETWORK`
(SearchScreen, SearchFoundation, SubshellState, SubshellApp; control red).
The "no SampleData in ui/" rule stays in source-contract DATA01. Given up on
purpose: version chains, search02a12's dead v3/v5 branches, the trending
URL/TTL constants (network-side), suggested-community ordering by favourite
and joined state (seeding not reachable without the preference stores; the
state-level ranking keeps `Search02A11ZeroQueryCommunitiesPolicyTest`), the
chrome Back button of the search route, and the CURRENT_COMMUNITY scope
normalisation on restore.

| Checker | Covered by |
|---|---|
| `check-search01a1-contract.sh` | `Step3SearchWorkspaceComposeTest` (typing asks Reddit for communities once the reader pauses; NSFW communities hidden with NSFW off; "Open r/x" opens the community and records the search; a submitted ALL search survives process restore). `Search01FoundationPolicyTest`, `Search01StateIntegrationPolicyTest`, `SearchHistoryStateCharacterisationTest` |
| `check-search01b-contract.sh` | `Step3SearchWorkspaceComposeTest` (filters only for a submitted search; a recent search and a trending topic submit). `Search01BWorkspacePolicyTest`, `Issue57SearchResubmitComposeTest`, `LowUi8bComposeTest` |
| `check-search01c1-contract.sh` | `Step3SearchWorkspaceComposeTest` (a submitted search asks for the best match and shows its communities; editing afterwards asks only for suggestions). `Search01CFederatedProvidersPolicyTest`, `Issue111And112ComposeTest` |
| `check-search02a1-contract.sh` | `Step3SearchWorkspaceComposeTest` (the newest four people come back on the empty page; Clear forgets people and keeps communities, also in the account's stored index). `Search02A1*`, `SearchHistoryStateCharacterisationTest` |
| `check-search02a11-contract.sh` | `Search02A11ZeroQueryCommunitiesPolicyTest`, `Step3SearchWorkspaceComposeTest` (the empty search page) |
| `check-search02a12-contract.sh` | `Step3SearchWorkspaceComposeTest` ("Trending now" with its Google Trends label; a topic submits). Boundary guard above |

## 2026-10-12 — gated: communityinfo01a1, 01a3 and 01a31-a35 (step 3, batch 4)

All seven ran only from `check-source-contract.sh` and read or ran each other
(a3 → a1; a31/a32 → a1, a3, a31; a33-a35 grep each other's run lines), so they
retire together. The six source-reading policy tests that only pinned them are
deleted with them (`CommunityInfo01A3PresentationPolicyTest`,
`CommunityInfo01A31RuntimePolishPolicyTest`, which also read the a3 checker,
and `CommunityInfo01A32`-`A35`). Two boundaries moved into
`check-boundary-guards-contract.sh`: `COMMUNITY_INFO_IS_NOT_PERSISTED` (nothing
under data/cache names CommunityInfo) and `ACTIVITY_WIRES_COMMUNITY_INFO`
(MainActivity hands the state and loader to SubshellApp); controls red.
Already covered elsewhere and dropped here: presentation network isolation
(source-contract NET01/NET02), the Room `version = 6` pin (DATA03,
protoclean01a3), the daily-driver grant without `wikiread`
(`Auth07b1DailyDriverAccountPolicyTest`). Given up as layout/design: the About
button's place after Search, no OutlinedButton anywhere on the community
screen, icons, colours, shapes, the sticky tab row and the accent helper's
package.

| Checker | Covered by |
|---|---|
| `check-communityinfo01a1-contract.sh` | `CommunityInfo01A1NetworkPolicyTest` (canonical read paths with raw_json; rules and moderators deduplicated; `projectionsAreBounded`: 100 000 sidebar characters, 100 rules, 500 moderators), `Step3CommunityInfoLoaderTest`, `Step3CommunityInfoCoordinatorTest` |
| `check-communityinfo01a3-contract.sh` | `Step3CommunityAboutEntryComposeTest` (read only once About is opened; Retry reads with force; a local account reads nothing and sees the community's description), `Step3CommunityAboutRouteComposeTest` (SubshellApp hands the state and loader to the community), `Step3CommunityAboutSheetComposeTest` (a missing section and a failed read offer Retry; refresh vs load titles) |
| `check-communityinfo01a31-contract.sh` | `Step3CommunityAboutRouteComposeTest` (a moderator's profile returns on Back to the community with the sheet open on Moderators), `Step3CommunityAboutEntryComposeTest` (the open sheet keeps its tab and open rule across state restoration) |
| `check-communityinfo01a32-contract.sh` | `Step3CommunityAboutSheetComposeTest` (one rule open at a time, a rule without description does not open; moderator identity asked at most two at a time, description or "Moderator"; "all" left out, at most three permissions; another account's or community's answer not shown) |
| `check-communityinfo01a33-contract.sh` | `Step3CommunityAboutSheetComposeTest` (tabs without counts; summary at once, details behind the row, details equal to the summary not repeated) |
| `check-communityinfo01a34-contract.sh`, `check-communityinfo01a35-contract.sh` | `Step3CommunityAboutSheetComposeTest` (the Community details row: Show/Hide copy and label, expand/collapse description, details appear and go) |

## 2026-10-13 — gated: messages02b, 02b1, 02b2, 02b21, 02b22, 02c, 02c1, 02c2, 02c4 and 02c4r1 (step 3, batch 4)

All ten ran only from `check-source-contract.sh` and form one predecessor
chain (02c2 → 02c1 → 02c → 02b22/02b21/02b2/02b1/02b, plus 02c4r1 → 02c4;
02b22, 02c and 02c1 also grep markers inside predecessor checker files), so
they retire together. None of the tests they named read source; all stay.
Three boundaries moved into `check-boundary-guards-contract.sh`:
`MESSAGE_REPLIES_USE_ONLY_THE_OAUTH_API` (no chat token, cookie, Matrix host
or legacy compose endpoint in the reply flow, data source, writer and
coordinator; replaces the "gated by messages02c" note),
`ACTIVITY_WIRES_MESSAGE_REPLY` (MainActivity hands the reply state, the send
and its reset to SubshellApp; MessagingEntryPoints' fields have defaults) and
`MESSAGE_REPLY_WRITER_HAS_NO_RETRY_LOOP`, which now also covers
`MessageReplyCoordinator`; controls red. Already covered elsewhere and dropped
here: the ui-side network import ban (source-contract NET01/NET02), the bubble
cache compatibility (`Messages02BCacheCompatibilityTest`), the direct-chat list
label (`Messages02B1ChatFidelityPolicyTest`, `Step3InboxMessagesComposeTest`),
Markdown and GIFs in a message (`Step3ConversationComposerComposeTest`), the
newest-message anchor (`LowUi8cComposeTest`). Given up as layout or device
checks: the conversation's top inset owner (02b22; the route's `RouteTopBar`
draws behind the status bar since #223), the IME re-anchor on return from the
inbox (02c2, 02c4), PostScreen's list padding under the composer, the
detailed failure variant of a conversation image (no call-factory seam in
`RemoteImage`), icons and colours. The build markers stay
(source-contract COMPOSER_HAND01 reads the C4R1 marker).

| Checker | Covered by |
|---|---|
| `check-messages02b-contract.sh` | `Step3ConversationScreenComposeTest` (a direct chat's header names the partner and "Direct chat", never "[direct chat room]"; a thread shows its subject and u/sender; one sender within five minutes on one day is one group), `Step3ConversationRouteWiringComposeTest` (the partner's identity is asked, a community's is not), `RedditInboxNetworkDataSourceTest` (every message in a thread keeps its own time), `Messages02BCacheCompatibilityTest`, `Messages02BConversationDetailPolicyTest` |
| `check-messages02b1-contract.sh` | `Step3MatrixImageRedirectLoaderTest` (only Matrix media may answer as octet-stream), `Messages02B1RemoteImagePolicyTest`, `Messages02B1ChatFidelityPolicyTest`, `Step3ConversationComposerComposeTest` |
| `check-messages02b2-contract.sh` | `Step3MatrixImageRedirectLoaderTest` (one trusted hop on the same host or to Reddit's image CDN with a fresh plain GET, no Authorization or Cookie; a second redirect, an http target or an access token refused), `Media06MediaReadTimeoutTest.mediaReadsFollowNoRedirectOnTheirOwn` |
| `check-messages02b21-contract.sh` | `Messages02B21*` and `Messages02C4Preview*` policy tests (unchanged) |
| `check-messages02b22-contract.sh` | Given up (top inset; see above) |
| `check-messages02c-contract.sh` | `Step3MessageReplyWriterTest` (one POST to api/comment with the bearer token and no cookie; missing grant, another account or a missing authorization sends nothing; a lost answer may have been sent and is never retried; an unreachable host is retryable), `Step3MessageReplyCoordinatorTest` (an accepted reply is sent once and reads the inbox again; a lost answer is not sent again), `Step3ConversationScreenComposeTest` (typed reply sent once; a reply on its way cannot be sent again; without a target or write access nothing can be sent; a lost answer offers Refresh and Checked), `Step3ConversationRouteWiringComposeTest` (typed request for the active account; a local account gets no composer; a lost authorization opens the inbox sign-in). Boundary guards above |
| `check-messages02c1-contract.sh` | `Step3ConversationScreenComposeTest` (a direct chat says Message and sends it; leaving it after a sent reply refreshes the inbox, a thread or a quiet chat does not), `Step3ConversationRouteWiringComposeTest` (a direct-chat message is a DIRECT_CHAT request; leaving after a sent message reloads the inbox) |
| `check-messages02c2-contract.sh` | `Step3ConversationScreenComposeTest` (a sent reply shows at once and only once when the server copy arrives); IME re-anchor given up |
| `check-messages02c4-contract.sh`, `check-messages02c4r1-contract.sh` | `Step3ReplyComposerEditorComposeTest` (formatting actions at once, Image/GIF open their pickers, Ctrl+B / Ctrl+Z / Ctrl+Shift+Z, a locked reply ignores the shortcuts), `Step3PostCommentComposerComposeTest` (Add a comment opens the composer inside the post with "Commenting on r/x · title"; a reply names the person answered) |

## 2026-10-13 — gated: crosspost01, crosspost01a1a-a1e and crosspost01a2 (step 3, batch 4)

None of the seven runs another; all ran from `check-source-contract.sh`, and
six of them (a1a-a1e, a2) were also children of the beta09 non-native suite
and listed in the beta09 release scope. **Owner approved** editing both
release checkers (as for auth08a1/auth08a2): `check-beta09-nonnative-suite-contract.sh`
and `check-beta09-release-scope-contract.sh` no longer list them. None of the
crosspost tests reads source; all stay. One boundary moved into
`check-boundary-guards-contract.sh`: `ACTIVITY_WIRES_CROSSPOST_CREATE`
(MainActivity hands the review state, Review, Publish and dropping a review
to SubshellApp; every field of PostComposerWriteRuntimeEntryPoints has a
default); control red. Given up: the "no logging" half of the source read
(no guard covers logging), exact `@Test` counts and test-name pins, the
SubshellApp parameter budget (followpost01a1b keeps its own copy), the
`ignoreUnknownKeys` test-hygiene pin, data-class defaults, icons, layout,
and the source panel asking for its community's identity (no observable
requester). The `CROSSPOST01*` markers stay.

| Checker | Covered by |
|---|---|
| `check-crosspost01-contract.sh` | `Crosspost01ProjectionTest`, `Issue207RedGifsTest`, `RedditListingCrosspostPollToleranceTest` (live JSON), `Step3CrosspostCacheHistoryTest` (the first 4-field form and the 21-23-field forms still read; any other length is refused), `Step3CrosspostPanelComposeTest` (source card: r/community, u/author, age, Markdown body, singular/plural counts; a source without a Reddit address cannot be opened), `Step3RedditLinkSurfacesComposeTest`, `Issue84And83SensitiveMediaComposeTest` |
| `check-crosspost01a1a-create-foundation-contract.sh` | `Step3CrosspostSubmitterTest` (one native submit naming its source, no url/text; an answer naming the source itself is not a success), `Crosspost01A1AWriteIdentityPolicyTest`, `Crosspost01A1ANativeSubmitPolicyTest`, `Crosspost01A1AAuthenticatedRequestPolicyTest`, `Crosspost01A1ACoordinatorPolicyTest`, `Crosspost01A1AAttemptStorePolicyTest` |
| `check-crosspost01a1b-source-reconciliation-contract.sh` | `Step3CrosspostSubmitterTest` (the read-back only reads and finds the wrapper by its parent; a post without the parent is not taken; another username reads nothing), `Crosspost01A1BReconciliation*` policy tests |
| `check-crosspost01a1c-draft-destination-contract.sh` | `Step3CrosspostStateTest` (source and owner cannot change, no body, a title edit is taken; the ordinary composer never sends a crosspost draft), `Step3CrosspostReadLoadersTest` (the destination's `crosspostable`, else `allow_post_crossposts`, for the owner, GET only), `Crosspost01A1C*` policy tests |
| `check-crosspost01a1d-process-restore-contract.sh` | `Crosspost01A1DProcessRestorePolicyTest` (real snapshot codec), `Step3CrosspostStateTest` (an open crosspost comes back with its locked source after process restore) |
| `check-crosspost01a1e-create-flow-contract.sh` | `Step3CrosspostCreateScreenComposeTest` (own editor, source locked, no body; Review waits for loaded rules and only asks; only Publish sends the reviewed revision; Back to editing, a title edit and leaving drop the review; a verifying review cannot publish; a crosspost that may be on Reddit locks the editor and offers Check again; the draft saves itself), `Step3CrosspostEntryComposeTest` (Crosspost in the post menu opens the composer for the active account; none without a Reddit account or for a deleted author), `Step3CrosspostReadLoadersTest` (the source is read fresh by its fullname with the owner's token; not crosspostable or removed = unavailable; without the read grant nothing is asked), `Step3CrosspostViewModelWiringTest` (the ViewModel's review only reads; Publish hands one crosspost to the submitter), `Crosspost01A1ECreateCoordinatorPolicyTest`, `Crosspost01A1EStatePolicyTest`, `Crosspost01A1EReviewPolicyTest`, `Issue103CrosspostReviewGateTest`, `AuthApplicationRuntimeFactoryTest`. Boundary guard above |
| `check-crosspost01a2-post-publish-rebind-contract.sh` | `Step3PostPublishRebindBridgeComposeTest` (the new crosspost opens at once and the review is dropped; Reddit's post is asked before the reset and replaces the open one; Back before the answer wins; without an answer the state is reset after 8 s and the new post stays), `Crosspost01A2PostPublishRebindPolicyTest`, `Composer05PostPublishRebindTest` |

## 2026-10-13 — gated: messages02c4r3, 02c4r3r1, 02c5r3, 02c6, 02c6r2-r5, 02c6r5r1, 02c7, 02d5b, 02d5b2, 02d5c, 02d5d, 02e2a and 02e2a2 (step 3, batch 4)

Sixteen checkers in three chains: c7 → c6r5r1 → c6r5 → c6r4 → c6r3 → c6r2 →
c6 → c5r3, e2a2 → {c6r5, e2a} (e2a had no other runner), c4r3r1 required
c4r3's file, and d5b/d5b2 ran d5c with a fallback that failed once d5c was
gone; they retire together. `check-next01a3b-contract.sh` stops running
messages02c7. d5b, d5b2, d5c and d5d ran only from the beta09 non-native
suite and its release scope; **owner approved** dropping them from both
(as for auth08a1/auth08a2 and crosspost). The rows above for messages02d3a
and messages02e1 named "the gated messages02c7" for the live reader's
GET-only rule; its successor is `Step3MatrixLiveReaderTest` (every request
is a GET). Three source-reading tests that only pinned these checkers are
deleted (`Messages02C6R4TailFollowPolicyTest`,
`Messages02E2A2DualWritePolicyTest`, `Messages02E2A3ReactionLifecyclePolicyTest`).
Boundaries moved into `check-boundary-guards-contract.sh`:
`ACTIVITY_WIRES_DIRECT_CHAT` (MainActivity hands live read, typing, delete,
reactions and the first message to SubshellApp; MessagingEntryPoints'
fields have defaults), `CHAT_WEB_TOKEN_WEBVIEW_IS_LOCKED_DOWN` (**owner
approved**; the chat token WebView reads no files or content URIs, talks
only to reddit.com and stores nothing) and `MESSAGE_REPLY_WRITER_HAS_NO_RETRY_LOOP`,
which now also covers the Matrix media, reaction, redaction and start
writers; controls red. Given up: the WebView mint's single-flight mutex and
`invalidate(expectedToken)` (WebView), the 20 s long-poll literal, log key
names, the four-column picker layout, asset file names, compiler-warning
cleanups, removed-code negations, and the triage's "upload before
createRoom" for a first image (the code stages the file locally first and
uploads after createRoom; the test follows the code).

| Checker | Covered by |
|---|---|
| `check-messages02c4r3-contract.sh` | `Step3MediaCommentUnknownOutcomeTest` (a lost media comment is confirmed by Reddit's own copy; an older or someone else's comment is not taken and nothing is sent again; a failed read-back stays "may have been sent"), `Messages02C4R3CommentMediaReadbackPolicyTest` |
| `check-messages02c4r3r1-contract.sh` | `Step3ConversationReplyErrorComposeTest` (the app's words, not Reddit's raw text; Dismiss, Grant media access, Reconnect; "may have been sent" offers Refresh and Checked and sends nothing; no Refresh without a way to refresh), `Messages02C4R3R1MessageReplyErrorPresentationPolicyTest` |
| `check-messages02c5r3-contract.sh`, `check-messages02c6-contract.sh`, `check-messages02c6r3-contract.sh` | `Step3MatrixMediaWriterTest` (a warm image is one upload and one m.image event without whoami, room lookup or sync; a lost send is read back and never sent again; 401 drops the web token, 404 forgets the cached room; a three-member room or another account's Matrix user is refused before any upload; no participant = nothing sent), `Messages02C5R3MatrixIdentityPolicyTest`, `Messages02C6*CachePolicyTest`, `ChatRead01LostSendReadBackTest`. Boundary guards above |
| `check-messages02c6r2-contract.sh`, `check-messages02c6r5r1-contract.sh` | `Step3DirectChatMediaReplyCoordinatorTest` (an accepted Direct Chat image keeps its Matrix identity and its local picture; text-only replies and private threads have none; a partial success keeps the image and the text), `Step3ConversationTailFollowComposeTest` (Reddit's copy of a sent reply is shown once), `Messages02C6R5OptimisticMediaEchoPolicyTest`, `Messages02CComposerPolicyTest` |
| `check-messages02c6r4-contract.sh` | `Step3ConversationTailFollowComposeTest` (the image being sent shows at the bottom; Reddit's copy shows once before the reply state is cleared; a new live message is followed at the bottom but not when scrolled up) |
| `check-messages02c6r5-contract.sh` | `Step3DirectChatOptimisticMediaComposeTest` (a Direct Chat image leaves the composer and shows in the chat with "Sending…"; a private-thread image stays in the composer with "Uploading and sending…"; after a partial send the image leaves the composer and the text stays), `Step3DirectChatMediaReplyCoordinatorTest` |
| `check-messages02c7-contract.sh` | `Step3MatrixLiveReaderTest` (GET only), `Step3DirectChatLifecycleComposeTest` (an open Direct Chat starts the live read, closing and going to the background stop it, coming back starts it again; a private thread never starts it), `AuthConnectViewModelDirectChatLiveTest` (the live window keeps the newest 100 messages; cursor), `AuthApplicationRuntimeFactoryTest.everyDirectChatPartSharesOneTokenProviderAndOneSessionCache`, `Messages02C7*` policy tests. Boundary guard ACTIVITY_WIRES_DIRECT_CHAT |
| `check-messages02d5b-contract.sh`, `check-messages02d5b2-contract.sh` | `Step3NewMessageSendComposeTest` (a chosen person gets the first message only when Send is tapped; the shared composer with its formatting actions; an unknown start offers Refresh Inbox and blocks Send; a definite failure offers no refresh and typing clears it), `AppFloatingActionButtonComposeTest`, `NewMessageRouteHostComposeTest` |
| `check-messages02d5c-contract.sh` | `Step3NewMessageMediaComposeTest` (a picked GIF is sent alone; text and an image never go together and there is no video; typing with an image turns Send off), `Step3MatrixWritersTest` (a first GIF is staged first and sent as one image in the created room; failed staging creates no room; a lost first image is unknown with one send), `Messages02D5C*` policy tests |
| `check-messages02d5d-contract.sh` | `NewMessageRouteHostComposeTest` (a started chat opens the inbox's Direct Chat item without another refresh; without it the route goes back to the inbox after the refresh settles; while the hand-off is pending Send is off and typing keeps the started chat), `Messages02D5DPostSendHandoffPolicyTest` |
| `check-messages02e2a-contract.sh` | `Step3MatrixReactionReadersWritersTest` (one annotation PUT to the verified room; cold session sends nothing; a lost answer is unknown and not sent again; keys outside Reddit's catalog never sent; logs name no event, room or key; the relations read only GETs the newest twelve targets four at a time and asks nothing cold), `AuthConnectViewModelDirectChatLiveTest` (accepted reaction shown and cached; a second reaction while one runs ignored; stopping during a reaction = may have been sent; a throwing writer = may have been sent; unknown or deleted targets not reacted to; cached reactions show at once and Reddit's relations replace them), `Step3DirectChatReactionComposeTest` (Reddit's picker reacts to a live message; snapshot-only reactions are shown without a way to react; no reaction without write access), `Messages02E2A*` policy tests |
| `check-messages02e2a2-contract.sh` | `Step3MatrixMediaWriterTest` (image first, then text; a refused or lost text after an accepted image keeps the image identity and is sent once), `Step3DirectChatMediaReplyCoordinatorTest`, `Step3DirectChatOptimisticMediaComposeTest` |

## 2026-10-13 — gated: mod01a1, mod01a2, mod01a21, mod01a22, modmail01a1, modmail02, modmail03a1r2, modmail04a1, modmail05 and modmail06 (step 3, batch 5)

One predecessor chain, ten deep (modmail06 → 05 → 04a1 → 03a1r2 → 02 →
01a1 → mod01a22 → a21 → a2 → a1; mod01a1 also needed mod01a2's file), so
all ten retire together. Only mod01a1-a22 and modmail01a1 ran from
`check-source-contract.sh`; modmail02-06 ran only from next01a3c, next01a3d,
notify01a1 and notify01a1r1, which stop running them (owner order: modmail
first, then notify/inbox, then next01). Deleted with them, as source-reading
tests that only pinned these checkers: `Mod01A2ModeratorToolsActionUiPolicyTest`,
`Mod01A21RemovalReasonUiPolicyTest`, `Modmail02WritePolicyTest`,
`Modmail02PresentationPolicyTest`, and eleven source-reading methods in
Mod01A22InboxModTabPolicyTest, Modmail01A1Authorization/InboxPolicyTest,
Modmail04A1ArchivePolicyTest, Modmail05TriagePolicyTest and
Modmail06UnreadNotificationPolicyTest (their code-calling tests stay).
Boundaries moved into `check-boundary-guards-contract.sh`:
`ACTIVITY_WIRES_MODMAIL`, `ACTIVITY_WIRES_MODERATION_QUEUE`,
`MODMAIL_WRITER_HAS_NO_RETRY_LOOP`, and `BACKGROUND_INBOX_SYNC_NEVER_MARKS_READ`
now also refuses modmail read-state writes; controls red. **Owner:** "modmail
has no worker, scheduler or channel of its own" is not kept as a guard.
Already elsewhere: the fresh-session pin (auth07a), presentation network
isolation (source-contract NET01/NET02). Given up: icons, layout, test-name
pins, markers (the MODMAIL markers other checkers read stay).

| Checker | Covered by |
|---|---|
| `check-mod01a1-contract.sh` | `Step3ModerationLoaderTest` (an inactive account reads nothing; the moderator directory follows the cursor for at most ten pages; without mysubreddits nothing is sent; one GET per normalized queue; an exact target is one info read), `Step3CommunityModToolsEntryComposeTest` (a moderated community opens its queue once from the top bar; no entry without the active account's loaded list with this community, or for a local account), `Step3ModerationQueueCoordinatorTest`, `Mod01A1*` tests |
| `check-mod01a2-contract.sh` | `Step3ModeratorQueueSheetComposeTest` (Remove asks first and sends one removal; a comment is named as a comment; a missing or denied grant asks or names the Posts permission), `Moderation02A1CoordinatorTest` (each action is one write that is read back), `ModResponse01A1RemovalMessageWriterTest` |
| `check-mod01a21-contract.sh` | `Step3ModeratorQueueSheetComposeTest` (the chosen reason and mod note travel with the removal; no reason sends none; a missing reasons grant asks and still allows removing; an unconfirmed reason says so), `ModResponse01A1RemovalMessageWriterTest` (a refused reason is a warning and nothing is repeated; 401 asks, 403 refuses; an approval cannot carry a reason), `Mod01A21RemovalReasonNetworkPolicyTest` |
| `check-mod01a22-contract.sh` | `Step3InboxModTabComposeTest` (a moderator gets a Mod tab that reads the whole queue once; none without moderated communities; empty queue; a queue post opens through the resolver; the badge counts queue items and unread modmail), `Step3ModerationQueueCoordinatorTest`, `Step3NewMessageButtonGateComposeTest` (no New message button on the Mod tab), `Mod01A22InboxModTabPolicyTest` |
| `check-modmail01a1-contract.sh` | `Step3ModmailLoaderTest` (each queue asks for its own Reddit state; without mail communities nothing else is asked; a thread is read without marking it read; a missing grant or 401 asks to authorize), `Step3InboxModTabComposeTest` (the Modmail section asks for its grant and reads modmail), `Inbox03AccountLockTest`, `Step3InboxAuthorizationTest`, `Auth10CapabilityScopesTest` (a missing capability scope asks and keeps the session), `AuthApplicationRuntimeFactoryTest` (modmail, moderation and removal reasons use their own grants) |
| `check-modmail02-contract.sh`, `check-modmail03a1r2-contract.sh` | `Step3ModmailWriterTest` (a reply is one POST and an internal note is never author-hidden; unknown outcome never sent again), `Step3ModmailThreadComposeTest` (a reply goes as the mod team by default, or as the moderator; an internal note is for moderators only), `Step3ModmailRouteWiringComposeTest` (send reaches the entry points) |
| `check-modmail04a1-contract.sh` | `Step3ModmailWriterTest` (archive and unarchive are empty-form POSTs), `Step3ModmailCoordinatorTest` (an archived conversation is read back with the chosen queue; a lost answer is not), `Step3ModmailThreadComposeTest` (archive/unarchive offered for outside conversations, not internal ones; an unclear archive offers Refresh), `Step3ModmailRouteWiringComposeTest` |
| `check-modmail05-contract.sh` | `Step3ModmailWriterTest` (Reddit's triage verbs; mute sends its hours), `Step3ModmailCoordinatorTest` (a mute needs an outside participant; a highlight is read back), `Step3ModmailThreadComposeTest` (the triage menu marks read, highlights and mutes, and offers the opposites; the queue field lists Reddit's ten queues), `Step3ModmailRouteWiringComposeTest` (triage from the inbox, beside a message conversation and beside a mod-queue post), `Modmail05TriagePolicyTest` |
| `check-modmail06-contract.sh` | `Step3ModmailLoaderTest` (unread reads and counts), `Step3ModmailWriterTest` (read and unread name the conversation), `Step3ModmailCoordinatorTest` (opening an unread conversation marks it read once; checking the read state rereads thread and queue; account switch sends nothing), `LowInboxTest` (a modmail notification uses the inbox channel and its tap opens the conversation once; inbox and modmail keep separate background histories), `Step3NewMessageButtonGateComposeTest` (the inbox badge adds only this account's unread modmail), `Modmail06UnreadNotificationPolicyTest`, `Issue105ModmailNotificationTargetComposeTest`. Boundary guard BACKGROUND_INBOX_SYNC_NEVER_MARKS_READ |

## 2026-10-13 — gated: notify01a1, 01a1r1, 01a1r2, 01a2, inbox01b12, 01b22, 01b23, 01b231 and inboxm3a1 (step 3, batch 5)

A chain (notify01a2 → a1r2 → a1r1 → a1 → inbox01b22/b23/b231) plus
inboxm3a1, which source-contract and next01a3a-d ran; inbox01b12 had no
runner at all. They retire together; next01a3a-d stop running inboxm3a1 and
notify01a2 (next01a3a also stops listing its file). Deleted as
source-reading tests that only pinned them: `Notify01A1R1CompactSettingsPolicyTest`,
`Notify01A1R2NotificationTypesSubscreenPolicyTest`. Boundaries moved into
`check-boundary-guards-contract.sh`: `BACKGROUND_SYNC_POSTS_ONLY_NEW_EVENTS`
(the worker builds its own runtime), `ACTIVITY_RUNS_NOTIFICATION_READ_RECONCILIATION`,
`ACTIVITY_TAKES_IN_NOTIFICATION_TARGETS`, `ACTIVITY_ASKS_FOR_THE_NOTIFICATION_PERMISSION`;
controls red. **Owner:** given up without a guard — the notification glyph's
vector XML (white, scale 1.5, no tint), inboxm3a1's visual pins (VISUAL01
pictures remain; `Issue225ComponentsComposeTest.theInboxDestinationsAreTabs`
covers the tabs), the "reconciliation starts before setContent" order and the
0x1B22 request-code literal.

| Checker | Covered by |
|---|---|
| `check-notify01a1-contract.sh`, `check-inbox01b22-contract.sh`, `check-inbox01b23-contract.sh`, `check-inbox01b231-contract.sh` | `Step3InboxNotificationPostingTest` (a new reply is one private notification on the inbox channel with the glyph; modmail uses the same channel and glyph, also with hidden content; a tap opens exactly that item once; each type follows its own setting; nothing posted with notifications off, without the permission, for a blocked app or without an account; two accounts never share a notification or its tap; the app asks for the permission), `LowInboxTest`, `Modmail06UnreadNotificationPolicyTest`. Boundary guards above |
| `check-notify01a1r1-contract.sh`, `check-notify01a1r2-contract.sh` | `Step3NotificationSettingsComposeTest` (one notification switch and a types row with its count; the summary says what is on; one switch per type and Back shows the new count; types locked with notifications off; turning on retakes the baseline, creates the channel and schedules the check; turning off clears what is shown; the permission answer counts only for the same account; a local account cannot turn them on; search finds each type) |
| `check-notify01a2-contract.sh` | `Step3NotificationReadReconciliationTest` (an item read on Reddit leaves only that account's shade; a read still on its way keeps the notification; a read modmail conversation leaves its shade; old untracked notifications go at the first reconciliation; the app's reconciliation follows the live states and stops with its scope; turning notifications off removes everything), `AppEffectsBridgeComposeTest` (a notified message opens its conversation once the reload has it) |
| `check-inbox01b12-contract.sh` | `Step3ConversationRouteWiringComposeTest` (the conversation follows Reddit's newer copy; pulling a live conversation reloads the inbox, another account's does not; an unread live conversation is marked read once, a seen one is not) |
| `check-inboxm3a1-contract.sh` | `Issue225ComponentsComposeTest.theInboxDestinationsAreTabs`, `Step3InboxWorkspaceBadgesComposeTest` (each tab carries its unread count capped at 99+; no badge with nothing unread; a moderator's Mod tab splits into Queue and Modmail with Modmail's own count), `Step3InboxMessagesComposeTest`, VISUAL01 |

## 2026-10-13 — gated: profile01a1, 01a3, 01a4a, 01a6, 01a6a3-a5, 01a6a7, 01a7a4, profileuser01, savedcomments01, 01a2, 01a3, useraction01a1, 01a2 and library02a1 (step 3, batch 5)

The profile, saved, Library and block family (triage
`reports/2026-10-13-batch5-drafts/triage5-profile.md`; dev1 took it from
dev2). Chains: library02a1 → useraction01a2 → useraction01a1;
savedcomments01a3 → 01a2 → 01; savedcomments01/01a2 read the profile01a6
checker and useraction01a2 read the profile01a6a3 checker; profile01a6a4/a5
were dead (they exit on the LOWUI4 Library retirement). They retire
together. Outside edits: safety01a1/a2/a3 stop running profile01a6;
source-contract stops running all sixteen (profile01a1/a3/a4a left its
final loop); **owner approved (release tooling)**: check-beta09-nonnative-suite
and check-beta09-release-scope drop profile01a7a4. Deleted as source-reading
tests that only pinned them: `UserAction01A1ProfileUiPolicyTest`,
`UserAction01A2BlockedAccountsTransportPolicyTest`, and the source read in
`UserAction01A1RequestPolicyTest`. Boundaries moved into
`check-boundary-guards-contract.sh`: `ACTIVITY_WIRES_PROFILE_HISTORY`,
`ACTIVITY_WIRES_BLOCKED_ACCOUNTS`, `SAVED_HISTORY_CACHE_IS_DEVICE_LOCAL`
(controls red). Given up as pure text (owner: the listed pins may go): the
profile probe-logging ban (profileuser01's `SubshellProfileSnoop`),
`versionCode` literals, Library icons and tab geometry, the `git diff`
Room-schema guards (the Room version stays pinned by source-contract DATA03
and protoclean01a3), the UI import ban (source-contract NET02).
Build markers stay (source-contract and auth07* read some of them).

The Blocked test found a real bug from #223: a screen that leaves its route
bar out (the own profile; a community beside a post on a wide screen) still
installed the bar's enterAlways scroll connection, and a bar never laid out
has no height limit, so it took every upward drag and the list could not be
scrolled by touch. The connection now scrolls only with a shown bar
(STEP3_NO_BAR_NO_BAR_SCROLL_V1); `Step3RouteBarScrollComposeTest`.

| Checker | Covered by |
|---|---|
| `check-profile01a1-contract.sh` | `Step3UserSubmittedFeedUpdaterTest` (a user's posts are one GET to `/user/<name>/submitted`, newest first; kept under the user's key and the next page follows Reddit's cursor; an account that is not active sends nothing), `Profile01a1UserSubmittedFeedPolicyTest`, `AuthConnectViewModelUserFeedTest`, `SubshellAppUserProfileWiringComposeTest`, boundary guard `ACTIVITY_WIRES_USER_SUBMITTED_FEED` |
| `check-profile01a3-contract.sh` | `Step3AccountSettingsComposeTest.localAccountsAreNotListedAndARowSwitches` (local accounts are not listed; a row switches; a row that needs sign-in resets and reconnects), `Profile01a3AccountSwitcherPolicyTest`, `AccountSwitcherSheetComposeTest` |
| `check-profile01a4a-contract.sh` | `Step3CapabilityAuthorizationWiringTest.profileHistoryAsksCoreReadPlusHistoryForItsUser`, `Profile01a4aProfileCommentsPolicyTest`, `Step3ProfileCommentsCacheTest` |
| `check-profile01a6-contract.sh`, `check-profile01a6a3-contract.sh` | `Step3AccountHistoryUpdaterTest` (Upvoted is one GET with `sort=new`, `type=links`, `limit=25`; the next page follows the cursor and a finished list asks nothing; a missing grant asks to authorize; comments never travel as posts), `Step3AccountHistoryPostsCoordinatorTest` (refresh reaches the updater; the latest request wins; authorization; blank name), `Step3ProfileHistoryAuthorizationCompletionTest` (an upgrade for the same active account reloads both histories; another account or owner reloads nothing), `Step3ProfileLibraryComposeTest`, `SubshellAppUserProfileWiringComposeTest.theOwnProfileHostHandsLibraryRefreshesToTheApp`, `AuthConnectViewModelAccountHistoryTest`, boundary guard `ACTIVITY_WIRES_PROFILE_HISTORY` |
| `check-profile01a6a4-contract.sh`, `check-profile01a6a5-contract.sh` | dead (exit 0 on the LOWUI4 Library retirement before any check) |
| `check-profile01a6a7-contract.sh`, `check-library02a1-contract.sh` | `Step3ProfileLibraryComposeTest` (own profile shows Posts/Comments/Library and the Library children only under Library; Library returns to its last child; another user's profile has no Library; Upvotes refreshes on first entry, not on re-entry, and on a pull) |
| `check-profile01a7a4-contract.sh` | `Step3RemoteHistorySyncComposeTest` (a post-history or feed revision keeps the loaded range instead of starting from the first page; a comment-history revision asks the post pager for nothing), `Profile01A7A4PagingWindowSyncPolicyTest`, `Step3ProfileLibraryComposeTest.upvotesRefreshOnFirstEntryNotOnReentryAndOnPull` |
| `check-profileuser01-contract.sh` | `Step3PublicProfileComposeTest` (an empty list says privacy may be enabled only for the kind with evidence; a profile Reddit cannot find is unavailable without stats or actions; Block asks first and sends one request, your own profile has no Block; a local account follows instead; a post's author row opens the profile with post evidence) |
| `check-savedcomments01-contract.sh`, `check-savedcomments01a2-contract.sh` | `Step3SavedHistoryCoordinatorTest` (cached first, then fresh; append follows the cursor and stops at the end; the latest request wins; blank name; another account's refresh does not keep this account's items), `Step3ProfileHistoryAuthorizationCompletionTest`, boundary guard `SAVED_HISTORY_CACHE_IS_DEVICE_LOCAL` |
| `check-savedcomments01a3-contract.sh` | `Step3ProfileLibraryComposeTest` (saved posts and comments in order; a saved comment opens its post at the comment; Back returns to the saved comment) |
| `check-useraction01a1-contract.sh`, `check-useraction01a2-contract.sh` | `Step3UserBlockWriterTest` (block is one POST to `block_user`, unblock one POST to `unfriend` from the account itself; the account cannot block itself; 403/401/lost connection told apart and never re-sent; the blocked list is Reddit's own page by page; the approved-clients refusal says so), `Step3PublicProfileComposeTest`, `Step3ProfileLibraryComposeTest.blockedListsAccountsUnblocksAndAsksForTheNextPage`, `UserAction01A1RequestPolicyTest`, boundary guard `ACTIVITY_WIRES_BLOCKED_ACCOUNTS` |

## 2026-10-13 — gated: next01a1, 01a2, 01a3a-a3d and 01a4a-a4c (step 3, batch 5)

Two chains (a2 → a1 → a11y01a4; a3d → a3c → a3b → a3a → a11y01a4) plus three
independent checkers, all run only from `check-source-contract.sh`; they
retire together. a11y01a4 stays gated by source-contract. 25 source-reading
methods in the seven Next01A* policy tests are deleted (their 20
code-calling tests stay; Next01A4B/A4C files stay whole). **Owner:** the
"exactly one CreatePostScreen" count is not kept as a guard. Given up: the
`listSubtitle` literals (invisible since #224 hides list headers), system-bar
inset literals (visual), the InboxScreen ×3 and UserProfileScreen ×2 call
counts (behaviour tests cover each host; triage through all three inbox
hosts is in `Step3ModmailRouteWiringComposeTest`). The NEXT01A4C marker stays
(auth07c2, beta09 release manifest, reads it). Finding while writing the
tests: `ScrollPagingLoadGate` asks for the next page only after a real
scroll, so a short list with a cursor that does not fill the screen never
pages (modmail list, blocked accounts); the tests scroll a long list.

| Checker | Covered by |
|---|---|
| `check-next01a1-contract.sh` | `Step3CommunityAndSearchPostWorkspaceComposeTest` (a community post opens beside the community without its top bar and another replaces it; on a phone a tapped post opens full screen with its community origin; the community list keeps its place), `Next01A1CommunityPostTwoPanePolicyTest` |
| `check-next01a2-contract.sh` | `Step3CommunityAndSearchPostWorkspaceComposeTest` (before a post is chosen the search detail names what to pick for the scope; a result opens beside the results without the search field and another replaces it; Back brings the field and placeholder back; phone search origin), `Next01A2SearchPostTwoPanePolicyTest` (+ the search workspace is split only on an expanded window in split mode) |
| `check-next01a3a-contract.sh`, `check-next01a3b-contract.sh` | `Step3InboxWorkspacesComposeTest` (the Messages tab stays chosen beside a conversation and after Back; the chosen tab stays beside an inbox post and Activity comes back after it), `Next01A3A*`/`Next01A3B*` policy tests |
| `check-next01a3c-contract.sh` | `Step3InboxWorkspacesComposeTest` (the Mod workspace's placeholders; the section stays beside a queue post), `LowUi8aRecreationComposeTest` (the Mod section survives recreation and resets on an account switch), `Issue105ModmailNotificationTargetComposeTest` (a modmail notification switches the parent to Modmail) |
| `check-next01a3d-contract.sh` | `Step3InboxWorkspacesComposeTest` (a modmail thread sits beside its list in split and fills the screen in single; on a phone an open thread hides the bottom bar), `Issue108TwoPaneModmailNavigationComposeTest` (two-pane refresh reloads the list, full-screen refresh reopens the loading conversation, the list pages when scrolled to its end, a full-screen conversation pages nothing), `Step3ModmailDetailComposeTest` (no chosen conversation asks for one; a loading one says so), `Step3ModmailRouteWiringComposeTest` |
| `check-next01a4a-contract.sh` | `Step3YouWorkspacesComposeTest` (each You tab says what to pick; a post opens beside your list and the next replaces it; another tab closes the open post; a saved comment opens its post at that comment; single mode keeps the plain profile), `RouteReplaceCharacterisationTest` (a user-content selection keeps the focused comment), `Next01A4AYouContentWorkspacePolicyTest` |
| `check-next01a4b-contract.sh` | `Step3YouWorkspacesComposeTest` (a blocked account opens beside the list; leaving Blocked closes it), `Issue118BlockedEntryRefreshComposeTest` (unblock removes the account at once and asks Reddit once; a row opens the account only where the host offers it; the list pages when scrolled to its end), `Next01A4BBlockedWorkspacePolicyTest` |
| `check-next01a4c-contract.sh` | `Next01A4CDraftsWorkspacePolicyTest`, `Next01A4CDraftsWorkspaceStateTest`, `SubshellAppDraftsWorkspaceComposeTest`, `UserProfileDraftsWorkspaceComposeTest` (unchanged) |

## 2026-10-13 — gated: scroll01, reddit01d1, 01d2a and 01d3a (step 3, batch 6)

All four ran only from `check-source-contract.sh` (scroll01 also in its
required-file list; reddit01d1/d2a/d3a in the `FAIL -eq 0` loop with
reddit01c3c1, reddit01d4 and auth07a–c2, which stay). Nothing else runs or
reads them. scroll01's build-version pin (`versionCode = 234` plus the
retired profile01a6a7 marker) was text only. reddit01d1/d2a/d3a pin names:
file, type and helper names, banned predecessor names, the internal
`"reddit-paging"` list key and doc sentences; the compiler proves the types
exist and the loaders have their own behaviour tests. The REDDIT01D3A build
marker stays (the orphan `check-reddit01a-contract.sh` reads it).

| Checker | Covered by |
|---|---|
| `check-scroll01-contract.sh` | `Step3HomeScrollToTopComposeTest` (choosing Home again brings the feed back to its first post; coming back to Home after a post does not replay it), `UiRequestStateCharacterisationTest`, `FeedScrollAnchorPolicyTest.homeScrollToTopRequestIsConsumedOnceAcrossPostBackReentry` |
| `check-reddit01d1-contract.sh` | `Auth06cLiveReadOnlyPolicyTest` (the core read scopes are exactly identity, mysubreddits, read), `Step3CapabilityAuthorizationWiringTest`; feed helpers: `HomeFeedSelectionTest`, `LiveFeedCorrectnessPolicyTest` |
| `check-reddit01d2a-contract.sh` | the loaders' own tests (comment, subscriptions, discovery, inbox loaders and the Step3* loader tests); names only otherwise |
| `check-reddit01d3a-contract.sh` | `Error01a2CoreRedditReadErrorPolicyTest`, `Step3ReadErrorSurfacesComposeTest`; names only otherwise |

## 2026-10-13 — gated: media-audio-r27, issue11-video-error and issue18-media-navigation (step 3, batch 6)

dev1's media checkers (triage `reports/2026-10-13-batch6-drafts/triage6-media.md`).
check-all-contracts and quality-gates ran all three; protoclean01,
protoclean01a2 and quality01c-autoboxing read r27's text and drop those pins.
**Owner approved (release tooling)**: check-beta09-nonnative-suite drops r27,
check-beta09-release-scope drops r27 and issue11 (its expected manifest too),
`contract02/manifests/beta09.tsv` drops the issue11 row, and the manifests
`issue11-error.tsv` and `issue18.tsv` are retired. Most of r27 pinned build
markers, doc titles and the names of tests that call code (Video01–Video12,
VideoPlayerCharacterisationTest, VideoOwnershipStateTest and others).
Boundaries moved into `check-boundary-guards-contract.sh` (controls red):
`ONE_MEDIA3_PLAYER`, `ACTIVITY_OWNS_ONE_VIDEO_PLAYER`,
`MANIFEST_KEEPS_THE_PLAYER_ACROSS_CONFIGURATION`,
`VIDEO_TRANSPORT_HAS_NO_URL_OR_NETWORK_IDENTITY`,
`RELEASE_MANIFEST_HAS_NO_TEST_TRUST`, `VIDEO_CACHE_STAYS_OUT_OF_BACKUPS`.
**Owner:** the debug video fixtures' integrity (r27 ran ffprobe on them) is
given up without a guard.

| Checker | Covered by |
|---|---|
| `check-issue11-video-error-contract.sh` | `Step3VideoFailureCoordinatorTest` (the real player against a local server: the next source is tried after the first fails and the failure keeps both HTTP statuses; only the owner retries and a retry is a new attempt; attaching the same failed video again keeps the failure and asks nothing), `Step3VideoErrorNoticeComposeTest` (the notice shows the HTTP status, its explanation and Retry; another video's failure is not shown), `Issue11VideoPlaybackFailurePolicyTest`, `Issue11VideoErrorPresentationTest` |
| `check-issue18-media-navigation-contract.sh` | `Step3ReturnedFeedAudioComposeTest` (a queued feed start from an old route or visibility epoch applies nothing; a video handed to the viewer stops being handed off once it is gone; a returning feed pauses the player it does not own, and keeps the one it selects), `Issue18ReturnedFeedAudioPolicyTest` |
| `check-media-audio-r27-contract.sh` | `Step3VideoLoopSettingTest` (looping follows the setting for the current and the next video and reaches the player), `Step3MediaSettingsAndViewerComposeTest` (the full-screen viewer shows bars only by swipe and gives the window back; the media cache and old downloads are cleared only after asking), Video01–Video12 tests, `VideoPlayerCharacterisationTest`, `VideoOwnershipStateTest`, `VideoVisibilityStateTest`; boundary guards above |

## 2026-10-13 — gated: subs04b, 04c, 04c1 and 04c2 (step 3, batch 6)

All four ran only from `check-source-contract.sh` (also its required-file
list, with the subs04c/c1/c2 docs); each re-ran its predecessor (c2 → c1 →
c → b), so they leave together. Nothing else runs them; the SUBSHELL_SUBS04*
build markers stay (source-contract's inline guards read them). Text only:
`versionCode = 234`, call-site counts, the forbidden saved-history names
(they never existed in the app; saved history is guarded by boundary-guards
SAVED_HISTORY_CACHE_IS_DEVICE_LOCAL), card sizes, paddings and the compact
Join style (visual; VISUAL01 captures Communities and the drawer). The
activity wiring of community discovery is boundary-guards
ACTIVITY_WIRES_COMMUNITY_DISCOVERY.

| Checker | Covered by |
|---|---|
| `check-subs04b-contract.sh` | `Step3CommunitiesInlineDiscoveryComposeTest` (discovery sits between the joined title and the list, at most three communities not already joined and no NSFW with NSFW off; the search bar opens Discover with focus, See all without; Join sends one change and waits for the live joined list; SubshellApp hands discovery and membership to the Joined tab), boundary-guards ACTIVITY_WIRES_COMMUNITY_DISCOVERY |
| `check-subs04c-contract.sh` | `Step3CommunitiesInlineDiscoveryComposeTest` (the hidden entry counts hidden communities and opens their list), `Subs04cCommunityNavigationDiscoveryPolicyTest`, `SubsExploreShortcutPolicyTest`, `SettingsAudit01Test` (retired setting keys) |
| `check-subs04c1-contract.sh` | `Step3DrawerCommunitiesComposeTest` (one group open at a time; the custom feeds header is selected while a feed is open and its group closed; another account starts with the groups closed), `DrawerDynamicGroupsPolicyTest` |
| `check-subs04c2-contract.sh` | `Step3DrawerCommunitiesComposeTest` (Feeds, Custom feeds, Communities, You, Inbox, Profile in that order with no Saved or Library item; Full lists communities without a chevron, Current only links to Communities; the theme row switches the theme), `Issue223ChromeComposeTest.drawerDestinationsAreMaterialDrawerItems`, `Subs04c2CommunitiesTerminologyDrawerIaPolicyTest` |

## 2026-10-13 — gated: a11y01a1, 01a11, 01a2, 01a3, 01a4, 01a41 and 01a411 (step 3, batch 6)

Triage `reports/2026-10-13-batch6-drafts/triage6-a11y.md` (dev2); dev1 wrote
the tests. A file-fence chain (a411 → a41 → a4 → a3 → a2 → a11 → a1, the
last three also re-running safety01a32), so all seven retire together;
source-contract stops running them (safety01a32 stays gated directly).
**Owner approved (release tooling)**: check-beta09-nonnative-suite and
check-beta09-release-scope drop a11y01a411. **Owner:** the dead
`ExpandedMainContentMaxWidth` constant is removed; the purely visual pins
(the visible "Subs" text at large text, `maxLines`, the 80 dp rail, the
360 dp list pane, the pill's centred text) are left to VISUAL01. Deleted as
source-reading tests: `A11y01A3ExpandedContextSurfacePolicyTest`,
`A11y01A411StablePreviewFramePolicyTest`, and the source-reading methods of
the A11y01A1/A11/A2/A4/A41 policy tests (their pure methods stay). One test
seam: the bottom bar carries `testTag("app-bottom-bar")`
(STEP3_APP_BOTTOM_BAR_TEST_TAG_V1). No boundary guard: every negated rule was
pure text.

| Checker | Covered by |
|---|---|
| `check-a11y01a1-contract.sh`, `check-a11y01a11-contract.sh` | `Step3A11yLargeTextComposeTest` (the bottom navigation is 64 dp, 88 dp at large text, keeping full names; Home's sort button grows with the text; a post card's vote and comment actions are 48 dp targets), `Step3InboxMessagesComposeTest.aMessageRowSaysWhetherItIsRead`, `Composer04ConfirmReplaceComposeTest.theGalleryButtonsAre48DpTargetsAndReorderMovesTheImage`, `Step3CommentRowComposeTest.theThreadAndOptionsButtonsAre48DpTargets`, `A11y01A1LargeTextAndTouchTargetPolicyTest`, `A11y01A11BottomNavAndCommentTargetPolicyTest`, `AppBottomBarComposeTest` |
| `check-a11y01a2-contract.sh`, `check-a11y01a3-contract.sh`, `check-a11y01a4-contract.sh` | `Step3HomeAndInboxListDetailComposeTest` (a post tapped in the Home list, and a message tapped in the Inbox list, replaces the open detail at the same depth; Back returns to Main), `Step3ContextWidthsComposeTest` (Help at the reading width; community discovery wider but at most 960 dp; a post beside the Home list and the Inbox on its own at the reading width), `A11y01A2ExpandedMainShellPolicyTest`, `A11y01A4ExpandedListDetailPolicyTest`, `Issue224AdaptiveComposeTest`, `ExpandedSurfacesComposeTest`, `RouteReplaceCharacterisationTest` |
| `check-a11y01a41-contract.sh`, `check-a11y01a411-contract.sh` | `Step3TabletModeSettingComposeTest` (a chosen Tablet mode is read back at the next start, unknown or missing starts Split; the row changes the mode and shows it in the one preview), `Step3NavigationPreviewFrameComposeTest` (every preview context keeps the same frame height, title and status width), `A11y01A41TabletModeSettingsPolicyTest`, `SettingsStateCharacterisationTest` |

## 2026-10-14 — gated: safety01a1, 01a2, 01a3, 01a31 and 01a32 (step 3, batch 6)

All five ran from `check-source-contract.sh` and re-ran each other (a32 →
a31 → a3 → a2 → a1; a2 also ran auth07b1), so they leave together.
dev1's a11y01a1/a11/a2, which re-ran safety01a32, were retired first
(`4f69c3f6`). **Owner approved**: auth07b1 decided that the daily grant
carries "report" by the existence of `check-safety01a2-contract.sh`; it now
keys on the SAFETY01A2_CONTENT_SAFETY_REPORT_SCOPE_V1 marker in
RedditOAuth.kt (control red); the authorizer rebind test after an account
switch (test only). Text only: file lists, markers, error titles, the
community-name limit of 1 000, route-table counts. Deleted source-reading
`Safety01A3RealSafetyUiPolicyTest`, `Safety01A32ImmediateHiddenProjectionPolicyTest`
and `Report01A1MaterialPresentationPolicyTest.productStillUsesSafety01WriterBoundary`.
The activity wiring is boundary-guards ACTIVITY_WIRES_CONTENT_SAFETY, the
writer's single send CONTENT_SAFETY_WRITER_HAS_NO_RETRY_LOOP.

| Checker | Covered by |
|---|---|
| `check-safety01a1-contract.sh` | `Safety01A1ContentSafetyRequestPolicyTest` (+ a reason of 100 characters is accepted and 101 refused), `Safety01A2ContentSafetyNetworkPolicyTest` |
| `check-safety01a2-contract.sh` | `Step3ContentSafetyWriterTest` (a hide is one POST and re-reads that account's Hidden history, unhide goes to /api/unhide; a post report is one POST with its community and Reddit's refusal is kept; a comment report hides nothing; a report answered 5xx/408/unreadable or lost is outcome unknown after one request, a lost hide is retryable; a refused hide asks to sign in again), `Safety01A2ContentSafetyCoordinatorPolicyTest` (+ the community name is not kept in the state; retry only after a retryable failure; a change for an account no longer active is never sent), `AuthConnectViewModelContentSafetyTest` (Hide and Report reach the runtime's writer once), `AuthApplicationRuntimeFactoryTest` (+ the writer refreshes Hidden through the runtime's history updater), `Safety01A2AuthorizationPolicyTest`, `Auth07b1DailyDriverAccountPolicyTest`, `AuthConnectViewModelCallbackOwnerRoutingTest`, `WriteTransportPolicyTest`, boundary-guards CONTENT_SAFETY_WRITER_HAS_NO_RETRY_LOOP |
| `check-safety01a3-contract.sh` | `Step3PostActionsComposeTest` (+ Report needs a rule and sends it with the community; a custom reason is capped at 100; Report waits while reasons load; no Report without a Reddit account), `Step3CommentRowComposeTest` (+ Report comment sends a comment rule with the fullname and no community; waits while another safety action sends; absent without a Reddit account), `Step3ProfileLibraryComposeTest` (+ a Hidden post offers Unhide and leaves the list at once; Hide in Upvotes removes the card at once), `AccountPostActionsPersistenceTest` (+ a signed-in hide is never stored locally; stored local hides are not shown while signed in), `Step3ContentSafetyBridgeComposeTest` (actions reach the activity for the active account; authorization asked once per action; a confirmed hide re-reads only the active account's Hidden list), `ContentSafetyStateCharacterisationTest`, `RuntimeErrorReportsComposeTest`, boundary-guards ACTIVITY_WIRES_CONTENT_SAFETY |
| `check-safety01a31-contract.sh` | `Safety01A31ContentSafetyViewportErrorPolicyTest`, `RuntimeErrorReportsComposeTest` (a failed safety action goes through the real viewport error host) |
| `check-safety01a32-contract.sh` | `Step3CapabilityRebindTest` (the active account takes the grant when Reddit says it is that account; another identity is refused and no session changes), `AuthConnectViewModelContentSafetyTest` (a report resumes for the same Reddit account under its new stable id), `Safety01A32ReauthContinuityPolicyTest`, `Step3PostActionsComposeTest` (the open post stays and offers Unhide after Hide) |

## 2026-10-14 — gated: settings02 (step 3, batch 6)

Ran from `check-source-contract.sh` and was a child of the beta09 release
suite. **Owner approved (release tooling)**: check-beta09-nonnative-suite
and check-beta09-release-scope drop it. Text only: the section title style,
padding and divider (visual; VISUAL01 captures Settings), the parse count of
`setting("` lines, the RouteTopBar inset owner (Issue223ChromeComposeTest)
and the names of the three search tests, which stay. theme01 (same triage)
waits for dev1's media-viewer test; its system-bar tests landed with this
slice (`Step3StartupSystemBarsTest`, Issue45BarIconsAfterRotationComposeTest +2).

| Checker | Covered by |
|---|---|
| `check-settings02-contract.sh` | `Step3SettingsComposeTest` (+ a search with no match says so and Clear search empties it; "loop video" shows only Loop videos under its heading), `Settings02SearchIndexPolicyTest` (+ case and device language do not change the result, also under Turkish), `LowUx3Test`, `Settings03NavigationPolicyTest` |

## 2026-10-14 — gated: theme01-system-bar-contrast (step 3, batch 6)

The last of the subs/settings/theme family (triage
`reports/2026-10-13-batch6-drafts/triage6-subs-settings-theme.md`): dev2
landed T a/b (`Step3StartupSystemBarsTest`, +2 in
`Issue45BarIconsAfterRotationComposeTest`) with settings02; dev1 wrote T c and
retires it. source-contract stops running it; **owner approved (release
tooling)**: check-beta09-nonnative-suite and check-beta09-release-scope drop
theme01. Deleted as source-reading tests: the two
`Issue19InlineCommentFullscreenPolicyTest` methods that read the comment and
image viewer source (`Step3CommentImageFullscreenComposeTest` and T c cover
the behaviour). The media viewers' bars have two layers each: the app theme
leaves the bars alone on Media/CommentMedia routes *and* the viewer re-applies
light icons after a rotation; closing gives the icons back through the
viewer's own restore *and* the theme taking over again — the controls break
both (one layer alone stays green).

| Checker | Covered by |
|---|---|
| `check-theme01-system-bar-contrast-contract.sh` | `Step3MediaViewerSystemBarsComposeTest` (an image viewer and a comment image viewer keep light bar icons through a rotation in the light theme, and closing gives the theme its icons back), `Step3StartupSystemBarsTest`, `Issue45BarIconsAfterRotationComposeTest`, `ThemeSystemBarContrastPolicyTest` |

## 2026-10-14 — gated: reddit01c3c1 (step 3, batch 6)

Ran in `check-source-contract.sh`'s `FAIL -eq 0` loop (with reddit01d4 and
auth07a–c2, which stay); nothing else runs or reads it. **Owner approved
(auth storage)**: the tests write and read the pending and callback records
of the real encrypted store, and boundary-guards
AUTH_RECORDS_NEVER_HOLD_A_WRITE_REQUEST keeps post-manage write requests out
of the auth records and the session manager. The
SUBSHELL_REDDIT01C3C1_AUTHORIZATION_CONTEXT_FOUNDATION_V1 build marker stays.

| Checker | Covered by |
|---|---|
| `check-reddit01c3c1-contract.sh` | `Step3AuthorizationContextRecordsTest` (the context travels from the pending record to the stored callback, normalised; only a manager with the same context completes a callback, another gets a configuration mismatch without a token request; a context without an owner is refused), `Reddit01c3c1AuthorizationContextPolicyTest` (+ blank and over-64-character contexts fail closed), `AuthConnectViewModelCallbackOwnerRoutingTest`, `Reddit01c3c2PostManageAuthorizationContextPolicyTest`, `Step3PostManagePersistedRouteTest`, `Reddit01c4LegacyFallbackFencingPolicyTest`, boundary-guards AUTH_RECORDS_NEVER_HOLD_A_WRITE_REQUEST |

## 2026-10-14 — gated: protoclean01, 01a2, 01a3, 01a31 and issue10-comment-hierarchy (step 3, batch 6)

Triage `reports/2026-10-13-batch6-drafts/triage6-protoclean-reddit-misc.md`
(dev2; tests drafted by dev2's forks, compiled and controlled by dev1).
source-contract stops running the four protoclean checkers; **owner
approved**: issue10 leaves check-all-contracts and check-quality-gates, its
manifest `contract02/manifests/issue10.tsv` is deleted, net02 (ran
protoclean01a3) now checks the PROTOCLEAN01A3 build marker plus the version 6
database with `ProtoClean01Migration5To6` registered, and quality01c-resource
(branched on protoclean01a2's label in source-contract) checks the debug-only
fixture layout unconditionally. Deleted as source-reading tests: the four
source-reading methods of `Issue10CommentHierarchyStylePolicyTest`.
Boundaries moved into `check-boundary-guards-contract.sh` (controls red):
`NO_DEBUG_FIXTURES_IN_MAIN`, `SUBSHELL_STATE_IS_BUILT_ONLY_BY_ITS_FACTORY`.
**Owner:** the comment connector drawing is left to VISUAL01; no
MIGRATION_5_TO_6_IS_DATA_ONLY guard (the Room version stays pinned by
source-contract DATA03 and net02). The androidTest of the migration stays.

| Checker | Covered by |
|---|---|
| `check-protoclean01a3-contract.sh` | `ProtoClean01LegacyFixtureMigrationTest` (Robolectric, the real 5 → 6 migration on a seeded database: fixture posts leave their feed and the feed refreshes; a fixture community goes by its payload and the live community of the same name stays; fixture comments and their offline actions go, live ones stay; fixture users and inbox go, the live inbox is not touched), net02 |
| `check-protoclean01-contract.sh`, `check-protoclean01a2-contract.sh`, `check-protoclean01a31-contract.sh` | `ProtoClean01ProductionDefaultsTest` (the app's repository on a fresh install holds no sample content; a restored route never counts a sample post as real), quality01c-resource, boundary guards above |
| `check-issue10-comment-hierarchy-contract.sh` | `Issue10CommentHierarchyStylePolicyTest` (guides stay the default; the state persists the style; the backup key keeps its spelling; connector lanes follow real edges; a collapsed comment's replies have no connectors), `Step3CommentHierarchySettingComposeTest` (the preview shows a parent and one reply; the row opens its dialog and the choice changes the style; search finds the row) |


## 2026-10-14 — gated: reddit01d4, auth07a, auth07b1 and auth07b2 (step 3, batch 7)

All four ran only in `check-source-contract.sh`'s `FAIL -eq 0` loop (auth07c1 and auth07c2
stay there); nothing else runs or reads them. Their version helpers pinned versionCodes
197–201 and the retired PROFILE01A4A/A4B markers (text only). reddit01d4 (dev1's, dev1
agreed) pinned names: its eight-file "read-only" list is terminology, not a write
boundary; the live read-only guarantee is `Auth06cLiveReadOnlyPolicyTest`. auth07a's
activity wiring and the first-feed-sync rule are boundary-guards
ACTIVITY_ASKS_FOR_A_FRESH_SIGN_IN_WINDOW and FIRST_FEED_SYNC_CANNOT_REJECT_THE_ACCOUNT
(a behaviour test on the real store waits for the owner's answer on auth storage tests).
The build markers stay (auth03 and auth06c read AUTH07B1 and AUTH07A1).

| Checker | Covered by |
|---|---|
| `check-reddit01d4-contract.sh` | `Auth06cLiveReadOnlyPolicyTest` (cursor null, limit 25, refresh, user-initiated only); names only otherwise |
| `check-auth07a-contract.sh` | `Step3FreshSignInWindowTest` (the launcher hands the login window the fresh flag; a fresh sign-in clears Reddit's cookies and the cache before the authorization page; a reusable one keeps them), `Auth07aFreshAccountAuthorizationPolicyTest`, `AuthBrowserLaunchDispatchTest`, `Auth19RegistrationKeepsLaterChangesTest` (a first sync that loads nothing still registers the account), boundary-guards ACTIVITY_ASKS_FOR_A_FRESH_SIGN_IN_WINDOW, FIRST_FEED_SYNC_CANNOT_REJECT_THE_ACCOUNT |
| `check-auth07b1-contract.sh` | `Auth07b1DailyDriverAccountPolicyTest`, `Safety01A2AuthorizationPolicyTest`, `Auth07b1AccountSessionPresentationPolicyTest`, `AuthApplicationRuntimeFactoryTest`, `Low4ConnectFlowTest`, `AuthConnectViewModelCallbackOwnerRoutingTest` |
| `check-auth07b2-contract.sh` | `Step3ZeroSubscriptionHomeComposeTest` (an account Reddit says joined nothing sees an empty Home with Browse Popular and Find communities and no feed request; a pending landing says the feed is being prepared; a cached empty list never shows the empty Home), `Auth07b2SubscriptionAwareLandingPolicyTest`, `AccountScopedEffectsComposeTest`, `AppTopBarComposeTest`, `RootBackHandlersComposeTest` |

## 2026-10-14 — gated: followpost01a1a, 01a1b, userstatus01a1, backup04a4, net01, net02 and reddit01a (step 3, batch 7)

Triage `reports/2026-10-14-batch7-drafts/triage7-misc-net.md` (dev2's fork);
dev1 wrote the tests (three forks) and retires them in one slice. **Owner
approved (release tooling)**: check-beta09-nonnative-suite and release-scope
drop followpost01a1a/b; userstatus01a1 and backup04a4 leave the published
`contract02/manifests/beta09.tsv` and the scope's expected manifest, and
`userstatus01a1.tsv` is deleted; net01/net02 leave check-all-contracts and
check-quality-gates, and source-contract keeps their real rules but no longer
reads the checker files or `run-net0x-*.sh` (scripts, docs and androidTests
stay). reddit01a was an orphan nothing ran (retired without a test).
source-contract stops running followpost01a1a/b and userstatus01a1. Deleted as
a source-reading test: `UserStatus01A1PresentationTest`. Boundaries moved into
`check-boundary-guards-contract.sh` (controls red): `ACTIVITY_WIRES_POST_FOLLOW`,
`ACTIVITY_WIRES_ONLINE_VISIBILITY`, `HTTP_CLIENT_HAS_NO_LOGGING_OR_DISK_CACHE`
(owner), `POST_FOLLOWER_HAS_NO_RETRY_LOOP`, and `RELEASE_MANIFEST_HAS_NO_TEST_TRUST`
now also refuses cleartext in the debug configuration and any user CA (from
net01). Given up: net01's dependency-version pins (Gradle owns versions);
net02's "a cancelled write is cancellation" case was not written — the store
always writes on its own thread and a cancelled caller does not interrupt the
write, so no real path throws CancellationException there.

| Checker | Covered by |
|---|---|
| `check-followpost01a1a-native-foundation-contract.sh`, `check-followpost01a1b-live-follow-ui-contract.sh` | `Step3PostFollowerTest` (a follow is one POST with the absolute state as the account; only 401 asks to sign in and a lost answer is never sent again; two follows run one after the other; no session sends nothing), `Step3PostFollowCoordinatorTest` (confirmed follow; resend once after authorization; only a retryable failure is retried; another active account sends nothing; a non-post target is refused), `Step3PostActionsComposeTest` +3 (follow from the overflow then Unfollow; disabled while another follow runs; no Follow without a Reddit account), `Step3PostFollowBridgeComposeTest` 2 |
| `check-userstatus01a1-contract.sh` | `Step3OnlineVisibilitySettingComposeTest` 7 (read when shown and stopped when left; another account's state ignored; unknown value has no switch and offers Retry; a tap only proposes; the confirmation sends only on Show status; Cancel sends nothing; an account that must sign in again gets Sign in again and no confirmation), `Settings02SearchIndexPolicyTest.onlineStatusSearchFindsTheAccountPrivacyRow` |
| `check-backup04a4-contract.sh` | `Step3ModeratorResponsesBackupOptInTest` 3 (off on a fresh install; a setup saved with it off carries no responses; on, they round-trip), `Step3ModeratorResponsesRestoreComposeTest` 2 (a restore replaces the responses only with the opt-in on) |
| `check-net01-contract.sh` | `Step3HttpClientBoundaryTest` 4 (no redirects, connection retry only; User-Agent and Bearer token; an answer over the size limit is BODY_TOO_LARGE whether announced or streamed; cancelling the read cancels the call), source-contract NET01, boundary guards above |
| `check-net02-contract.sh` | `Step3FeedWriteTransactionTest` 5 (a write that fails before commit leaves the feed the reader had; a refresh starts a new generation and a next page keeps the refresh time; a next page never lists a post twice; a refresh leaves no orphan post; a rate-limited read keeps Reddit's wait), source-contract NET02 |
| `check-reddit01a-contract.sh` | orphan (nothing ran it; the REDDIT01D3A build marker it read stays) |

## 2026-10-14 — gated: auth06b and auth06c (step 3, batch 7)

Both ran from `check-all-contracts.sh` and `check-quality-gates-contract.sh`
(its exact list), and source-contract required their files and read their
RESULT lines in its inline AUTH06B/AUTH06C blocks; auth06c also required
auth06b's file, so they leave together (reddit01d4, which read auth06c,
retired first). **Owner approved**: both leave all-contracts and quality-gates
(release tooling); tests drive the real encrypted store, the callback and
the token client without network (auth storage); the login window's
navigation decision became a pure function, `loginNavigationFor`
(STEP3_LOGIN_NAVIGATION_DECISION_IS_A_FUNCTION_V1, behaviour unchanged).
source-contract's inline blocks keep their product pins and negations and
drop the checker, run-script, androidTest and doc pins; the run scripts stay
as files. Text only: version and marker pins, type names, the androidTest and
device run-script pins (not run on land), the Settings copy. The Settings
save/reload (auth06b's opt-in UI) stays pinned by source-contract's inline
block and by auth07c1 until slice C. boundary-guards'
FIRST_FEED_SYNC_CANNOT_REJECT_THE_ACCOUNT (slice A's stand-in) is replaced by
`Step3BootstrapCannotRejectAccountTest`.

| Checker | Covered by |
|---|---|
| `check-auth06b-contract.sh` | `Step3RedReaderCallbackManifestTest` (the RedReader callback is taken only by its alias for the callback activity; a `redreader://` link to another host is not offered to Subshell), `Step3RedReaderLoginNavigationTest` (only the exact callback is the sign-in's answer; the front page leaves the login, HTTPS loads, anything else is blocked), `Step3CompatibilityCallbackModeTest` (a compatibility callback is not completed by the native mode and nothing is exchanged), `Auth06bRedReaderCompatibilityPolicyTest`, `AuthApplicationRuntimeFactoryTest`, `Step3AuthorizationContextRecordsTest`, `Step3FreshSignInWindowTest`; boundary-guards RELEASE_MANIFEST_HAS_ONLY_THE_PRODUCTION_CALLBACK, COMPATIBILITY_LOGIN_WEBVIEW_IS_LOCKED_DOWN, SYSTEM_BACKUP_DENIES_ALL, COMPATIBILITY_CLIENT_ID_IS_NEVER_BUNDLED |
| `check-auth06c-contract.sh` | `Step3BootstrapCannotRejectAccountTest` (a first sync that throws or fails still registers and activates the account with its session; a cancelled one stops the sign-in), `Step3ImageLoaderFailuresTest` (+ an image request carries no Authorization header and the media client keeps no cookies), `Auth06cLiveReadOnlyPolicyTest`, `Reddit01aCoreReadPolicyTest`, `Auth19RegistrationKeepsLaterChangesTest`, `Step3SearchWorkspaceComposeTest`, `Step3CommunitiesInlineDiscoveryComposeTest`; boundary-guards READ_LISTING_SOURCES_HAVE_NO_WRITE_TRANSPORT |

## 2026-10-14 — gated: quality01-p0, 01c-autoboxing, 01c-resource, 01d-compose-hygiene, 01d-final-lint and releasetool01c (step 3, batch 7)

**Owner approved (land and release tooling, 2026-10-14):**
- **5A:** land runs Android Lint for the rules these checkers stood in for. `tools/subshell land` runs
  `:app:lintDebug -Psubshell.landLint=true`.
  - The land profile (`app/build.gradle.kts` STEP3_LAND_LINT_GATE_V1) checks only NewApi,
    UnsafeOptInUsageError, AutoboxingStateCreation, ModifierParameter, LocalContextResourcesRead,
    ConfigurationScreenWidthHeight and MonochromeLauncherIcon, as errors.
  - Today's findings are in `app/lint-land-baseline.xml`, so lint fails on any new site tree-wide, not just
    the pinned ones.
  - Plain `lintDebug` and `gradle check` are unchanged.
- **8:** land also runs the auth policy-matrix tasks (verifyAuth04ConfigurationPolicy,
  verifyAuth05ProductionIdentityPolicy, verifyAuth05cApplicationIdPolicy,
  verifyAuth05d2ReleaseSigningPolicy), which before ran only under `gradle check`. They use fixtures and
  read no signing material.
- **6:** the legacy `tools/release01/publish-github-prerelease.sh` (no release02 driver called it) retires
  with releasetool01c.
  - releasetool02a and the tool manifests releasetool02a–d.tsv drop it as a predecessor.
  - `releasetool01c.tsv` is deleted.
- **14:** check-all-contracts prints a computed `CONTRACT_COUNT` (quality-gates checks the computed echo).
  The pinned 21 was stale.

**Edits outside the lint profile:**
- check-all-contracts and quality-gates drop the five quality checkers.
- boundary-guards:
  - extends NO_DEBUG_FIXTURES_IN_MAIN with the retired prototype clips and the old `ic_subshell` icon;
  - adds MEDIA3_OPT_IN_IS_NEVER_FILE_WIDE and LAUNCHER_ICON_IS_V26_WITH_A_MONOCHROME_LAYER.

**Given up as text:**
- the exact count of five opt-in sites (main has 11);
- lint.xml's exact issue/path list (it is the configuration, still used by `check`);
- the debug fixture count of 26;
- pins on source-contract's text.

**Structural:** AAPT fails on a missing icon layer or retained icon.

**Controls:** one lint run with all seven sabotages: six ids reported, MonochromeLauncherIcon not
(see above). The variable-held form `val context = LocalContext.current; context.resources` is
flagged too (the baseline's three MediaComponents sites are that form). The first baseline run
also found a real bug: `URLDecoder.decode(String, Charset)` is API 33 and threw on Android 8–12,
hidden by `runCatching` (RedditTarget.kt; fixed in the same land, STEP3_URL_DECODE_WORKS_BEFORE_ANDROID_13_V1),
so the baseline holds 28 findings. Lint alone took ~4.7 min in a fresh lane build (part is the
debug compile land does anyway).

| Checker | Covered by |
|---|---|
| `check-quality01-p0-contract.sh` | land lint NewApi (API 28 AnimatedImageDrawable only behind SDK ≥ P / @RequiresApi), UnsafeOptInUsageError (Media3 unstable API opted in), boundary-guards MEDIA3_OPT_IN_IS_NEVER_FILE_WIDE |
| `check-quality01c-autoboxing-contract.sh` | land lint AutoboxingStateCreation (tree-wide; raised to error in the land profile) |
| `check-quality01c-resource-contract.sh` | boundary-guards NO_DEBUG_FIXTURES_IN_MAIN (extended), AAPT for the retained icons |
| `check-quality01d-compose-hygiene-contract.sh` | land lint LocalContextResourcesRead, ModifierParameter, ConfigurationScreenWidthHeight |
| `check-quality01d-final-lint-contract.sh` | boundary-guards LAUNCHER_ICON_IS_V26_WITH_A_MONOCHROME_LAYER (lint's MonochromeLauncherIcon cannot see the release icon: lintDebug checks the debug manifest, which swaps in the lane's own icon — its control stayed green), source-contract's ADAPTIVE_ICON_V26 inline pins |
| `check-releasetool01c-contract.sh` | retired with the legacy publisher it guarded; beta09-release-scope still keeps it out of the release manifest |

## 2026-10-14 — gated: auth07c1 and auth07c2 (step 3, batch 7)

Both ran from `check-source-contract.sh`'s `FAIL -eq 0` loop (the loop is gone with them) and
were primary rows of the beta09 release manifest; auth07c2 also re-ran auth07c1. **Owner
approved**: they leave `tools/contract02/manifests/beta09.tsv` and check-beta09-release-scope's
expected manifest (release tooling, as issue11); the tests drive the real encrypted store (auth
storage, tests only) through a software stand-in for the Android key store
(`harness/FakeAndroidKeyStore.kt`, test only). Given up (owner: to VISUAL01): the onboarding
branding, store badge and authorization-email visuals and the theme/onboarding/composer order;
no guard for a durable onboarding flag (owner: G10 no). Text only otherwise: version markers,
the immutable runtime graph pins, docs. The Settings save/remove dialog is not driven (a text
field in an AlertDialog never goes idle under Robolectric); its reload wiring is boundary-guards
CONFIGURATION_CHANGE_RELOADS_THE_HOST.

| Checker | Covered by |
|---|---|
| `check-auth07c1-contract.sh` | `Step3RedditConfigurationStoreTest` (a saved Client ID is sealed in the keystore envelope in no-backup storage, survives a fresh controller and is removed without trace), `Step3RedditConfigurationSettingsComposeTest` (Settings reads the stored choice and says changes apply by themselves), `Step3RedditOnboardingComposeTest` (a saved Client ID asks the app to reload its auth host), boundary-guards CONFIGURATION_CHANGE_RELOADS_THE_HOST (restart task, no process kill, Settings/route-host/activity wiring) and CLIENT_ID_IS_NEVER_LOGGED |
| `check-auth07c2-contract.sh` | `Step3RedditOnboardingComposeTest` (shown without a Reddit account, not with one; store links open only RedReader's Google Play and F-Droid pages; a pasted Client ID is stored and reloads the host; Advanced shows the fixed redirect URI and user agent; a configured device connects through the app's connect flow), `Auth07c2RedditOnboardingPolicyTest`, `RedditOnboardingConnectPhasePolicyTest`, `Issue111And112ComposeTest`, boundary-guards ONBOARDING_HAS_NO_AUTH_STORAGE_OR_TOKENS |

## 2026-10-14 — gated: auth01, auth02 and auth03 (step 3, batch 7)

All three ran from `check-all-contracts.sh` and were in quality-gates' exact list; source-contract
read their files (required list, inline blocks). **Owner approved**: they leave check-all-contracts
and check-quality-gates (release tooling); source-contract's AUTH01–03 blocks keep their real rules
(build markers, the vault/session/coordinator/flow types, ui and data/cache isolation) and stop
reading the checkers, runtime scripts, docs and androidTest strings; the tests drive the real
encrypted store, the OAuth callbacks and the token client without network (auth storage); boundary-
guards AUTH_VAULT_IS_KEYSTORE_AES_GCM_AND_NOT_BACKED_UP stands in for the Android Keystore,
which Robolectric does not have. Text only: version-code floors, the library version pin, the
`withLock` count, the JUnit/localhost-TLS runtime shapes, doc sentences, the bare word `code` in
MainActivity. The runtime scripts, docs, androidTest files and build markers stay.
auth04/05b/05c/05d2/05e2 stay (owner: keep the release identity checkers).

| Checker | Covered by |
|---|---|
| `check-auth01-contract.sh` | `Step3OAuthCallbackStateTest` (a callback with another state is refused and the sign-in keeps waiting; none waiting is refused; ten minutes and no longer; an accepted callback cannot be used twice), `Step3OAuthTokenEndpointTest` (one form POST with Basic client ID and empty secret; refresh and revoke send only their fields; Reddit's own token and revoke endpoints; over 64 KiB or non-JSON refused; TLS final, lost connection retryable), `Step3AuthVaultFilesTest` (session only in the no-backup folder under an opaque name), `Step3AccountSessionCoordinatorTest` (logout revokes the grant once), `Step3AuthCallbackManifestTest` (the debug callback reaches only the callback activity on its exact path), `AuthPolicyTest`, `Low3LocksPerKeyTest`, `AuthTabCancelledCallbackTest`, `SettingsBackupContractTest`; boundary-guards AUTH_VAULT_IS_KEYSTORE_AES_GCM_AND_NOT_BACKED_UP, SIGN_IN_HAS_NO_WEBVIEW_OUTSIDE_COMPATIBILITY, AUTH_NEVER_LOGS_OR_KEEPS_TOKENS, ACTIVITIES_NEVER_HANDLE_TOKENS, NO_OAUTH_CLIENT_VALUES_IN_SOURCE, AUTH_NEVER_REACHES_BACKUP; source-contract AUTH01 block |
| `check-auth02-contract.sh` | `Step3AccountSessionCoordinatorTest` (provider-qualified stable id; one /api/v1/me read with that sign-in's token, oversized answer refused; a name taken by a local or another signed-in account is refused and nothing kept; a local active account has no bearer; logout leaves the local account active and the other account alone), `Step3AuthVaultFilesTest` (the registry holds no tokens), `AccountSessionIntegrationPolicyTest`, `AuthConnectViewModelLogoutTest`, `ActiveAccountSelectionPersistencePolicyTest`; boundary-guards AUTH_NEVER_LOGS_OR_KEEPS_TOKENS; source-contract AUTH02 block |
| `check-auth03-contract.sh` | `Step3ConnectFlowFailuresTest` (a refused token exchange or failed identity read ends in an error naming the stage and leaves no records or token; a taken name is an account conflict; the same callback after connecting changes nothing; a retry connects with only the account's name), `Auth03ConnectAccountPolicyTest`, `Low4ConnectFlowTest`, `AuthApplicationRuntimeFactoryTest`, `Step3AccountSettingsComposeTest`, `Step3AuthCallbackManifestTest`; boundary-guards ACTIVITIES_NEVER_HANDLE_TOKENS, NO_OAUTH_CLIENT_VALUES_IN_SOURCE; source-contract AUTH03 block |
