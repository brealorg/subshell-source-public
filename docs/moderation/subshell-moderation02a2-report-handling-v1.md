# MODERATION02A2 — Report review and reversible Ignore / Unignore

## Scope

This DEV2 slice extends the landed MODERATION02A1 moderation foundation. It
provides report reasons for posts and comments, explicit Ignore / Unignore,
and independent exact-target readback. Approve / Remove / Spam remain separate
content decisions. Ignore never sends any of those actions or a removal reason.

Captured base: `3d01390a1ad8dca4098d900b1cdd1f4cde12e972`.
Lane: `dev2/moderation02a2-report-handling`.

## Report data and unknown states

The existing moderation listing now carries typed report details rather than
reducing `user_reports` and `mod_reports` to an array length. User reasons retain
a count where a valid count was returned; they never acquire a reporter identity.
Moderator reasons retain the moderator name only when Reddit supplies one.

Absent/null/unrecognised report arrays remain unknown, distinct from an explicit
empty array. A missing/invalid `ignore_reports` flag is unknown, never false.
A write requires a known boolean setting and `can_mod_post == true` for the exact
target. A moderator-directory entry alone never authorizes a report write.
String booleans do not become true/false. Missing state or permission disables
Ignore and Unignore; the UI offers a read refresh, not a guessed toggle.

Malformed reason rows cannot make the whole queue disappear: valid rows remain
visible and incomplete rows are explicitly signalled. Display is bounded at 100
rows per report category and 2,000 characters per reason. Counts are bounded at
10,000; omitted/shortened details are identified. Server total count wins when
valid; only complete known tuples support a fallback total. Unknown counts are
not labelled “No reports”.

## Independent exact-target read

The report inspector uses `GET /api/info?id=t1_…|t3_…&raw_json=1`, with a single
fullname. The response must contain exactly that one target, with matching thing
kind and, when known, community. Empty, ambiguous or mismatched results are not
proof of a report setting. Comment fullnames never resolve to the parent post.

Lookup accepts a fullname or a trusted HTTPS Reddit post/comment permalink.
A supplied URL is parsed locally for identity only; its host is never requested.
Unsupported hosts, userinfo, ambiguous IDs, conflicting communities and malformed
links are rejected before a request. Network requests use the configured Reddit
endpoint and the existing owner-bound loader/session.

## A way back after queue removal

Both Inbox → Mod → Queue and community Mod tools expose Reports and Report lookup.
Lookup remains present even when a queue is empty. The root-owned report dialog
is not owned by a queue row, so a queue refresh cannot destroy the selected report
workspace just because the row disappeared.

The canonical post overflow and comment overflow also expose Moderator reports
for moderated communities. Therefore an ignored item can be reopened and
Unignore remains reachable outside the queue, including after process restart
via its link/fullname. Recent exact target references are kept only for the
current account/session (maximum 20). They contain no report text, are cleared on
account change, and do not claim to be a durable audit log.

## Confirmation and write safety

One confirmation is bound to account, community, fullname, action and read
revision. It is consumed before the first suspension. Cancel, Back, lookup,
refresh and authorization are not write triggers.

Report changes run through the same `ModerationActionCoordinator` write mutex as
Approve / Remove / Spam. Extra calls are rejected, not enqueued for later replay.
The shared mutex includes a fresh preflight read: if the ignored state changed,
permission is missing, the account changed or the read fails, no write is sent.
The writer rechecks the actor after authorization and at bearer dispatch.

`POST /api/ignore_reports` and `POST /api/unignore_reports` send only the exact
`id`. They use the existing `modposts` capability; no scope expansion, generic
JSON relaxation, personal reporting or content-removal side effect is added.

Authorizing a report action never automatically resumes a pending write. The
user must refresh reports and confirm a new action. Generic moderation Retry and
the generic queue callback cannot replay/bypass a report write.

## Readback and unknown outcomes

A successful write response is not reported as a verified state by itself.
After success or an unknown write outcome, a fresh `/api/info` read must return
the requested known setting on the exact target with moderator permission.
Only then does the UI say verified. The UI reports what was observed, not that
this client necessarily caused a concurrently observed server setting.

Unknown, unchanged, unavailable or wrong-target readback is shown as not confirmed.
No write is retried. Refresh is read-only; a subsequent deliberate change requires
a new confirmation. Late results for a closed dialog/old account/older request
are discarded. Cancellation releases locks and never means a write is known not
to have reached Reddit.

## Preserved boundaries

No new persistent store, Room migration, generic Post/Comment report-data fields,
background worker, audit log, notification policy or automatic live moderation is
introduced. Report bodies and credentials are never logged. Existing A1 Spam,
Approve, ordinary Remove, removal reasons and mod notes keep their paths.
Lock/Unlock, Distinguish, Ignore-all, bulk moderation, mod log and bans are out of
scope. The existing personal Report action remains separate from moderator
report handling.

## Qualification

`check-moderation02a2-contract.sh` owns direct A2 assertions only. Its caller owns
A1 and the MOD01 predecessor chain. The preview driver runs A2, A1, then MOD01A22
once (which owns its predecessor chain), focused tests, full unit suite/debug
assemble and the canonical `tools/subshell dev2` deployment.

Primary, predecessor, build/install and runtime results are separate. A source
assertion pass is not proof of a live Reddit mutation. Preview creates no
checkpoint, sync, land, push or public release. Final official land is separate
and owns its own contract delta and complete build gates.

## Runtime acceptance

1. In both moderator queue entry points, open reports for a post and comment.
   Check exact target/community, user/moderator reasons, known versus unavailable
   fields, and short-screen scrolling. Cancel/Back never sends a write.
2. On a target where changing report handling is intentional, confirm Ignore
   once. Require readback confirmation and recheck with Refresh reports. Reopen
   the same target outside the queue via post/comment overflow or Report lookup.
   Confirm Unignore and require a new readback. No disposable spam or report
   content is created by any supplied script.
3. Check that ordinary Approve/Remove/Spam and personal Report remain distinct.
   Account change closes old-account details. Unknown permissions/settings must
   stay disabled, and failed/unknown writes must not offer an automatic Retry.

## API references

Reddit's API documentation lists `POST /api/ignore_reports` and
`POST /api/unignore_reports` under `modposts`, and `GET /api/info` under `read`:
https://www.reddit.com/dev/api/#POST_api_ignore_reports
https://www.reddit.com/dev/api/#POST_api_unignore_reports
https://www.reddit.com/dev/api/#GET_api_info

Wire fields are handled explicitly as optional. Their availability on the user's
actual Reddit account is a runtime check, not assumed from the API documentation.


## V3: discoverable report lookup and loaded queue

Runtime feedback distinguished a usability defect from a loading failure: v2
rendered only `recentTargets` in its lookup panel. Those entries were populated
by successful exact-target reads, so a first opening could be blank even with
three items already visible in the moderation queue. The queue entry and row
entry were also plain TextButtons that looked like section headings.

V3 makes these changes in presentation only:

- `Find reports` is a FilledTonalButton with a Search icon in both queue entry
  points. Each queue row has a separate outlined `View reports` button with the
  existing AutoMirrored FactCheck icon. Approve/Remove/Spam are unchanged.
- The root dialog receives the already loaded `RedditModerationUiState`. It
  offers its same-account, moderated-community queue items without requiring
  prior visits. They are labelled `In the loaded queue`, not global search
  results. Loading, failed, unloaded and partially loaded queues stay distinct.
- `Recently opened · this session` is separate and excludes items already shown
  in the loaded queue. It remains session-only; this patch neither populates nor
  persists it. A recently opened target can still be selected after it leaves
  the queue. Account ownership and moderation membership filter both sections.
- Link/ID lookup remains available for any exact target outside the loaded queue.
  It does not query the user-supplied host or search Reddit report text.
- Selecting a target still runs the existing exact-target read. Queue data is
  never copied into the authoritative inspector item, used to mint a write
  confirmation, or treated as verified ignore state.
- Once a target is selected, its details replace the lookup form. `Choose another
  item` opens the chooser; `Refresh reports` performs the existing read action.
  List position is keyed by account and target fullname so a newly chosen target
  does not inherit a deep scroll offset from the chooser or another target.
- `1 report` and `1 report returned` use the singular form; unknown counts remain
  unavailable, never silently zero.

Coordinator, writer, network, account permissions, confirmation identity,
preflight and readback code are byte-identical to v2. No new dependency, warning
suppression, persisted state, background read or automated moderation write is
introduced.

### V3 runtime acceptance

Open a queue with post/comment items, then open `Find reports` *before* viewing
any row's reports in that app session. Those loaded items must be present
immediately. Open one and verify exact community/fullname and report reasons.
The outlined row action should reach the same details. Check a first-entry empty
history, queue failure/unloaded messaging where applicable, large text and
landscape. After an intentional Ignore operation, reopen the target via the
chooser's recently opened section or an exact link/ID and explicitly Unignore.
V2 screenshots establish read/display paths only; they do not establish that an
Ignore/Unignore operation succeeded. No checkpoint/land is authorized by this
preview package.


## V4 — Expressive review presentation

This revision changes presentation only. The v3 report coordinator, exact-target
reads, permission checks, write lock, pending-confirmation domain policy, writer,
readback, authorization, queue projection and lookup policy are unchanged.

Both queue entry points use the same tonal item header with human-readable
Post/Comment and report-count labels. View reports remains explicit. The
Approve/Remove/Spam callbacks and Spam confirmation are retained. Their action
rows wrap instead of clipping at large font scales or narrow widths. Spam has an
error-color icon and label; color is never the only action identifier. Buttons
keep at least 48 dp height, without shrinking label typography to fit.

The chooser begins with the same-account loaded queue. Session recents appear
only when nonempty; a collapsed Open by link or ID section remains available
below them. This is not an all-Reddit report search. No network activity is
introduced by rendering the chooser or expanding its input.

The detail header contains compact, labelled-for-accessibility Back/choose,
Refresh and Close icon buttons. Content summary takes precedence over technical
ID. Active, ignored and unknown state each have explicit text and an icon;
unknown/partial report details remain visible and cannot enable a write.
Report reasons are grouped in tonal surfaces. Known-empty sections use compact
copy. Readback/error notices remain visible with a polite live region.

Ignore/Unignore opens the existing revision/owner/target-bound confirmation.
That dialog shows the summary, community and exact ID, future-report effect,
reversal route and absence of content/author action. The same pending value is
consumed before onConfirm; opening a panel, chooser or confirmation never sends
an Ignore/Unignore request. Refresh never repeats a write.

The report panel wraps short content and caps long content against available
window height. Only its body scrolls; controls stay in the header. Theme tokens,
existing Material components and the existing Compose animation dependency are
used; there is no dependency, global theme, network or persistence change.

V4 runtime acceptance is separate from earlier V2 build qualification. Check
light/dark themes, portrait/landscape, long reasons, large text and link input
with the keyboard. First verify picker -> target -> Cancel/Back without writes.
Then on a suitable test post/comment verify explicit Ignore -> fresh read ->
reopen outside queue -> explicit Unignore -> fresh read. Screenshots of an open
confirmation or Reports active alone do not establish a successful write round.
