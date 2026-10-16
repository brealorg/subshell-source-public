# FOLLOWPOST01A1B — live post-follow runtime and UI

## Product surface

Authenticated Reddit posts expose `Follow post` / `Unfollow post` in the
existing post overflow menu. The stable primary Vote · Comments · Save · Share
row is not expanded.

## State semantics

Reddit listing/domain models currently provide no qualified authoritative
post-follow field. Therefore an untouched post starts with followed state
`unknown`, not `false`.

Unknown is presented as `Follow post` because `/api/follow_post` takes an
absolute desired boolean state. That presentation is not a claim that Reddit
confirmed the post is not followed.

A successful mutation acknowledgement establishes account-session-local
knowledge for that `t3_*` target. The state is not persisted and is cleared
when the active account changes.

The UI is optimistic. One remote request is active at a time; another click on
the same target records the latest desired state and dispatches a follow-up
only when it differs from the acknowledged state. Failure rolls back to the
previous known state, including `unknown`.

## Authorization

Post following owns persisted authorization owner `post_follow` and capability
`POST_FOLLOW`. Callback and stored-resume routing are deterministic and
separate from post save.

The required `subscribe` scope already belongs to the daily-driver account
grant.

## Root ABI

A1B does not add a `SubshellApp` source parameter. `MainActivity` provides one
`PostFollowRuntimeEntryPoints` value through `LocalPostFollowRuntime`. This
keeps the existing `SubshellApp` source-parameter budget at or below 219.

## Runtime acceptance

`FOLLOWPOST01A1B_RUNTIME_ACCEPTED_V1`

DEV2 runtime acceptance was completed on 2026-09-18 against an ordinary
accessible Reddit post with an authenticated account.

The initial unknown local state presented `Follow post`. An explicit manual
`Follow post` action completed successfully, the same post remained open, no
error presentation appeared, and reopening the overflow presented
`Unfollow post`.

The explicit `Unfollow post` reversal then completed successfully. Reopening
the overflow presented `Follow post` again, with no observed navigation or
presentation regression.

This proves successful write acknowledgement and account-session-local state
projection for both absolute desired states. It is not claimed as an
authoritative Reddit readback because no separate followed-state read path has
been qualified.
