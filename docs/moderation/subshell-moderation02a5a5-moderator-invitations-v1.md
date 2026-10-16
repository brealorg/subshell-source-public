# MODERATION02A5A5 — Moderator invitations

## Product boundary

A5A5 adds an explicit **Invite moderator** workflow to the existing Mod Tools → Moderators
workspace. A5A4 remains the authoritative presentation of the currently returned moderator
team and its reported permission codes. A pending invitation is not inserted into that active
moderator list.

The editor reuses Subshell's existing Reddit People search and avatar projection. The active
moderator must be explicitly reported with the `all` permission before Subshell enables the
invite entry point. Missing permission details are not promoted to full permission.

## Reddit boundary

A moderator invitation is sent with the classic Reddit relationship endpoint:

- `POST /r/{subreddit}/api/friend`
- `api_type=json`
- `name={username}`
- `type=moderator_invite`
- `permissions=+all` or a sorted comma-separated grant list such as `+access,+posts` from `access,config,flair,mail,posts,wiki`

The invitation does not make the target account a moderator immediately. The target becomes a
moderator only after accepting Reddit's invitation. A5A5 therefore refreshes the active
moderator overview after an acknowledged request but never treats absence from that list as a
failed invitation and never projects the target into the list optimistically.

## Authorization and write safety

A5A5 introduces a dedicated persisted authorization owner `moderation_team` and the incremental
OAuth scope `modothers`. The existing `moderation_users` / `modcontributors` owner remains
unchanged, and `modothers` is not added to daily-driver scopes.

The invite writer uses the non-retrying write call factory. Account identity is checked before
and after authorization. Only one invitation write may be in progress. A timeout or transport
failure after dispatch is treated as outcome-unknown and is never replayed automatically.

Authorization completion is deliberately non-replaying: if the first explicit Invite action
requires `modothers`, Subshell upgrades authorization and returns to the editor. The user must
review the target and permissions and press **Invite moderator** again. An OAuth callback can
never become a delayed invitation write.

## Reddit API error diagnostics

Reddit may return HTTP success with validation tuples in `json.errors`. A5A5 reads the first
bounded tuple with the existing shared Reddit validation parser, then converts its provider
code/message and field context into the shared `FailureDetails` boundary before it crosses into
authenticated/coordinator/UI state. The field is retained as bounded provider-code context
(`field=<name>`); the provider message is carried only as `SafeFailureText`. UI technical detail
is rendered through `FailureDetails.toSafeTechnicalDetail()` rather than a parallel provider
message field.

This diagnostic presentation data does not trigger an automatic retry and does not change the
invitation request. Raw response bodies, raw exception messages, credentials, and unsanitized
provider text are never placed in UI state or logs.

## Presentation

The workflow remains inside the existing single physical Mod Tools sheet:

`Invite editor → Moderators → Mod Tools → subreddit`.

Permissions in the moderator list remain read-only reported data. The invitation permission
selector is a separate write input. It defaults to no permissions selected; `All permissions`
is never selected implicitly.

## Deferred

A5A5 does not edit permissions of existing moderators, remove moderators, accept invitations,
reorder moderators, or expose a pending-invitations management surface. Those operations remain
separate successor slices.

## Runtime acceptance

Runtime must verify People search and avatars, explicit permission selection, Back ownership,
incremental `modothers` authorization when required, no automatic write after the OAuth callback,
and the acknowledged invitation copy. A safe test/alternate Reddit account should be used for
the real invitation write.
