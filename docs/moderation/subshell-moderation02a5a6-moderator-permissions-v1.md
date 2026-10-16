# MODERATION02A5A6 — Edit existing moderator permissions

## Product boundary

A5A6 adds a separate **Edit permissions** workflow to Mod Tools → Moderators for
existing moderators. A5A5 remains the invitation workflow, and A5A7 remains the
future remove-moderator slice.

The moderator rows remain Reddit-reported read-only projections. Editing opens a
separate editor inside the existing single Mod Tools sheet. No moderator row is
optimistically rewritten after a write; Subshell refreshes the authoritative
moderator list after Reddit acknowledges the change.

## Reddit boundary

Permission changes use Reddit's existing moderator relationship endpoint:

- `POST /r/{subreddit}/api/setpermissions`
- OAuth scope `modothers`
- `api_type=json`
- `name={username}`
- `type=moderator`
- `permissions=+all` or a sorted explicit grant set such as
  `+access,+mail,+posts`

A5A6 reuses the A5A5 `MODERATION_TEAM` capability and its incremental
`modothers` authorization. It does not add `modothers` to daily-driver scopes and
does not create another OAuth owner.

Reddit remains authoritative for moderator hierarchy. Subshell never offers
self-editing in A5A6. Even when the active account is explicitly reported with
`all`, Reddit may refuse a target that the actor is not allowed to demote or
modify.

## Permission-data safety

The A5A4 parser intentionally keeps unknown server permission codes visible and
cannot distinguish an omitted `mod_permissions` field from an empty permission
list. A5A6 therefore refuses to open a write editor when a target has missing
permission details or any permission code outside Subshell's typed permission
domain.

This prevents an absolute `setpermissions` write from silently erasing a
permission that Subshell cannot represent. Unknown permission labels remain
visible on the read-only row; they are never inferred, normalized into another
permission, or written back blindly.

A5A6 requires either **All permissions** or at least one explicit supported
permission. A no-permission mutation is not exposed in this slice.

## Authorization and write safety

The permission writer uses the same owner-bound `modothers` capability as
moderator invitations and the non-retrying write call factory. Account identity
is checked before and after authorization, and the target username is pinned in
coordinator state.

The shared `MODERATION_TEAM` callback route is explicitly arbitrated: the A5A5
invitation coordinator claims a callback only while it owns a pending invitation;
the A5A6 permission coordinator claims it only while it owns a pending permission
request. Opening one editor dismisses the other before authorization begins.

Authorization completion is non-replaying. After an OAuth upgrade, the user must
review the target and permissions and press **Save permissions** again. A timeout
or transport failure after dispatch is outcome-unknown and is never retried
automatically.

## Diagnostics

Reddit `json.errors` tuples are converted to the shared `FailureDetails`
boundary. Provider code, bounded field context and sanitized provider message may
reach UI state; raw response bodies, exception messages, credentials and
unsanitized provider text do not.

## Presentation

The Back chain remains:

`Edit permissions → Moderators → Mod Tools → subreddit`.

The editor seeds its checkboxes only from a complete supported permission set.
`All permissions` is explicit. A confirmation dialog names the target and final
permission set before the one write is dispatched.

The moderator information card is successor-aware in A5A6. It is titled
**Moderator permissions** and explains that permissions are reported by Reddit,
that eligible moderators can be edited individually, and that invitations remain
a separate workflow. The former **Permissions · Read-only** copy is retired because
it became inaccurate once A5A6 introduced permission editing.

## Deferred

A5A6 does not remove moderators, edit outstanding moderator invitations, accept
invitations, reorder moderators, infer moderator seniority, or expose
no-permission/leave-team mutations. Moderator removal remains A5A7.

## Verification

Primary tests cover the canonical `setpermissions` form, sorted explicit grants,
sanitized Reddit API errors, account/target fences, self-edit rejection,
non-replaying authorization, outcome-unknown behavior, shared-owner callback
arbitration, safe handling of missing/unknown permission data, and single-sheet
Back ownership.

Predecessor execution remains delegated to the global contract gate. Runtime
acceptance requires a safe lower-ranked moderator account whose permissions may
be changed and restored.

Subshell never offers self-editing of moderator permissions.

### JVM signature safety

Preview qualification exposed a JVM class-format limit in the large top-level
`SubshellApp` composable after A5A6 added six more standalone state/callback
parameters. The symptom was `ClassFormatError: Too many arguments in method
signature in class file app/subshell/prototype/ui/SubshellAppKt`.

The runtime seam now carries the A5A5 invitation bindings and A5A6 permission
bindings through one typed `ModeratorTeamRuntimeEntryPoints` parameter. This
replaces twelve top-level moderator-team parameters with one and does not change
Reddit transport, authorization, state ownership, confirmation, or Back
semantics. A5A6 contract and presentation tests lock this boundary against
future signature re-expansion.
