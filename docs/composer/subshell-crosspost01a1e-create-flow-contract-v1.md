# CROSSPOST01A1E — source-bound native crosspost creation

## Scope

A1E exposes the already qualified A1A submit, A1B reconciliation, A1C draft/destination
policy and A1D process restoration through a dedicated composer surface. The shared
post-card overflow exposes **Crosspost** only for an authenticated account and a
canonical, non-deleted post source. Post detail reuses that card. Comments do not
acquire this action. The selected post's own fullname is pinned; a nested crosspost
is not silently flattened to its parent.

## User flow

1. Crosspost opens the existing composer route with a locked source card, source
   title and flags, source owner account, and no preselected destination.
2. Reuse the existing community search/picker and rich post-flair picker. Changing
   destination clears the previous flair through A1C. Source/type/body/link/media
   cannot be changed. Ordinary TEXT/LINK/MEDIA composer behavior is retained.
3. Review is disabled until the destination's account-bound posting policy is loaded
   and A1C validation passes. Refresh/authorization never dispatch a crosspost.
4. Review loads the source from the authenticated `/api/info?id=t3_...&raw_json=1`
   read path, then reloads the destination's current posting requirements and flair.
   It displays source, destination, posting account, title, flair and flags.
5. Only the confirmation dialog's explicit **Publish** calls the publish callback.
   This consumes a single-use, 60-second, in-memory review token and rechecks BOTH
   source and destination immediately before delegating to the existing writer.
6. Success opens the new crosspost wrapper rather than the source. A late success
   clears only exactly matching saved drafts and does not close a newer composer
   or steal navigation after Close. OUTCOME_UNKNOWN continues through A1B's read-only
   reconciliation and retains durable duplicate protection; no automatic resubmit.

## Evidence and fail-closed rules

The source response must contain exactly one `t3` object with matching `name` AND
`id`, known NSFW/spoiler flags, a live author, and `is_crosspostable == true`.
Missing eligibility, a removal category, identity mismatch, unavailable response or
a changed original title/author/community blocks publication. Newly stricter source
flags require the user to enable them; the pinned source is never silently replaced.
This is a conservative client gate, not proof of a successful live Reddit write.
Endpoint availability and field coverage still require runtime validation.

A1C's destination rules remain in force: same owner/community, positively confirmed
crosspost allowance, known ban/permission state, known requirements and required
flair. Unsupported nonempty domain restrictions/body requirements do not downgrade
the request to TEXT/LINK. Unavailable policy is not permission.

Review identity includes all meaningful draft fields, source, owner and draft ID;
only creation/update timestamps are ignored. Edits, account changes, disposal or
ON_STOP invalidate review. Review/publish races and delayed responses use monotonically
increasing revisions. Confirm rechecks the account after each suspended read. A review
is not persisted to draft storage, portable backup or process snapshots. Reopened or
restored source-bound drafts must be reviewed again.

Authorization uses the existing callbacks/scopes and A1A's no-auto-replay behavior.
No new OAuth scope, independent submit endpoint, retry mechanism, telemetry, cache
format or network credential storage is introduced. No tokens/raw responses are logged.

## Persistence and ordinary composer compatibility

The same native draft store and A1D process source capsule remain authoritative.
Crosspost drafts reopen only for their authenticated owner. The A1C portable export
exclusion remains explicit; this slice does not claim portable crosspost backup.
The ordinary V4 composer is still used for TEXT/LINK/MEDIA. Its generic writer guard
continues rejecting CROSSPOST; only the source-aware route can publish a crosspost.
A1C's historical blanket UI guard is superseded by owner/source-validated reopening.
All other A1A/A1B/A1C/A1D obligations remain covered.

## Qualification and runtime obligations

40 new JVM test methods cover review identity, source response projection, single-use
confirm, account/draft changes, stale/ambiguous/unavailable evidence, cancellation,
source/destination rechecks and state entry/reopen/completion. The current source
contract and all five predecessor contracts must pass, together with a fresh full
unit run (at least 2072 methods, no failures/errors/skips, all required class/method
names present). Static source assertions are wiring checks, not UI tests.

The patch runner does not build an installable APK, deploy, commit, land, push or call
Reddit. Canonical DEV2 build/install/launch is a separate command after qualification.
Manual runtime must verify entry, long lists/keyboard, required flair, save/reopen,
account changes, process restore, review cancellation and a deliberately authorized
Publish. A live Publish really creates a post. Do not test uncertain outcomes by
blind resubmission. No release approval is implied by static qualification.
