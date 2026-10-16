# MODERATION02A5A7 — Remove existing moderator

## Product boundary

A5A7 adds a destructive **Remove moderator** workflow to Mod Tools → Moderators.
It removes an existing non-self moderator relationship. A5A5 remains responsible
for invitations and A5A6 remains responsible for editing permissions.

The action is attached to the target moderator card and opens a child view inside
the existing Mod Tools sheet. The child view names the target, explains the
consequence, and requires a separate destructive confirmation before any write is
dispatched.

A5A7 deliberately does not expose self-removal. Leaving a moderator team is a
different user intent and is outside this slice.

## Reddit boundary

Moderator removal uses Reddit's existing subreddit relationship endpoint:

- `POST /r/{subreddit}/api/unfriend`
- OAuth scope `modothers`
- `name={username}`
- `type=moderator`

The removal request intentionally uses the minimal relationship form (`name` + `type`)
without an `api_type=json` field. The endpoint is treated as a status-only mutation. A5A7 reuses the existing
`MODERATION_TEAM` capability and the A5A5/A5A6 incremental `modothers`
authorization. It does not add a new OAuth scope, add `modothers` to daily-driver
scopes, or create another authorization owner.

## Removal eligibility and hierarchy

Subshell offers **Remove moderator** only when the active account is present in
the Reddit-reported moderator list with explicit `all` permissions and the target
is another moderator. The current account never receives the removal action.

Unlike A5A6 permission editing, target permission details do not gate removal.
Missing or future/unknown target permission codes are not rewritten by this
operation; the complete moderator relationship is removed instead.

Subshell does not infer moderator seniority from list order or permissions.
Reddit remains authoritative for whether the actor may remove the selected
target. A server refusal is presented as an authorization/hierarchy failure.

## Authorization and write safety

The removal writer is owner-bound to the existing `MODERATION_TEAM` capability
and uses the non-retrying write call factory. Account identity and target identity
are pinned before the mutation.

The shared `MODERATION_TEAM` callback route is explicitly arbitrated across
invitation, permission editing, and removal coordinators. A coordinator claims a
callback only while it owns an in-memory pending authorization request. Opening
one moderator-team operation dismisses the other two.

Authorization completion is non-replaying. After an OAuth upgrade, Subshell
returns to the removal child view and requires the user to press **Remove
moderator** and confirm the destructive action again.

A transport failure after dispatch is outcome-unknown. HTTP 5xx responses are
also outcome-unknown because Reddit may fail after receiving a destructive
request. Subshell does not automatically retry either case. The user is told to
refresh the moderator list before deciding whether another attempt is appropriate.

## Authoritative reconciliation

A successful Reddit acknowledgement does not optimistically delete the moderator
row. Subshell closes the child view and force-refreshes the Reddit moderator list.
The row disappears only when the authoritative readback no longer contains that
moderator.

A bounded success notice records which username Reddit acknowledged as removed.
No local moderator-order, permission, or hierarchy projection is mutated.

## Presentation

The Back chain remains:

`Remove moderator → Moderators → Mod Tools → subreddit`.

The destructive confirmation states that moderator access will be revoked and
that re-adding the account requires a new invitation. The UI also states that
Reddit decides whether removal is allowed by moderator hierarchy.

The existing A5A6 `ModeratorTeamRuntimeEntryPoints` bundle carries A5A7 state and
callbacks. No new top-level `SubshellApp` parameter is introduced, preserving the
JVM signature-safety boundary established by A5A6.

## Verification

Primary tests cover the canonical `unfriend` moderator form, canonical target
validation, self-removal rejection, explicit-full-permission actor gating,
unknown/missing target permission tolerance, one-write acknowledgement,
non-replaying authorization, outcome-unknown behavior, shared-owner callback
arbitration, authoritative refresh after acknowledgement, single-sheet Back
ownership, and continued use of the moderator-team runtime bundle.

Predecessor execution remains delegated to the global contract gate. Runtime
acceptance should use a disposable lower-ranked moderator account that can be
re-invited after the removal test.
