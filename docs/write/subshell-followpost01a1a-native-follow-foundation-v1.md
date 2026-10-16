# FOLLOWPOST01A1A — native post-follow foundation

## Scope

A1A introduces the transport and authorization foundation for Reddit post
follow/unfollow. It does not expose a user-visible Follow action and performs no
live Reddit write during qualification.

The canonical mutation is `POST /api/follow_post` with an absolute boolean
`follow` field and a canonical link fullname in `fullname`. A post-follow target
is always `t3_*`; comment fullnames are rejected.

## Authorization and ownership

Post follow has an explicit `POST_FOLLOW` OAuth capability whose narrow grant is
`coreReadScopes + subscribe`. `subscribe` is already part of the established
daily-driver grant, so A1A does not widen that grant.

The configured writer reuses the existing owner-bound authorization architecture,
fixed authenticated bearer token, typed network failure model and serialized
single-writer behavior. There is no automatic replay or retry.

## State boundary

A1A intentionally does not claim an authoritative server-side followed-state
projection. Current Subshell post/listing models expose no qualified post-follow
state. A later visible slice may retain optimistic or last-known acknowledged
state, but must not describe that state as authoritative unless a Reddit read path
is separately proven.

## Runtime obligation

Before FOLLOWPOST01 can be finalized, DEV2 must prove `/api/follow_post` against
an ordinary accessible post, not only source-level fixtures. That runtime action
changes the Reddit account's post-follow state and therefore requires deliberate
manual authorization.

A1A itself does not deploy, call Reddit, checkpoint, land or push.
