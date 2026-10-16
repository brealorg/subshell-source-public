# subshell Feed/Post Logic V2A route-origin contract v1

## Scope

FEEDPOST-V2A replaces implicit post origin inference with a typed `FeedPostRoute` and
`FeedPostOrigin` contract. Reddit networking, repository integration, visual redesign,
and per-feed scroll anchors remain out of scope.

## Canonical origins

A post route records exactly one origin:

- main feed (`MainDestination`, `FeedSource`, `FeedSort`),
- community (`MainDestination`, community name, `CommunitySort`),
- contextual search (destination, community, query, scope, area),
- user profile,
- inbox,
- secondary destination such as Library.

Home, Community, Search, User profile, Library, You, and Inbox use explicit origin APIs.
Bare `state.open(post)` calls are absent from screen code.

## Route transition boundary

Opening a post clears incompatible overlay fields and makes `FeedPostRoute` the only
post-return contract. Closing the post restores the typed origin deterministically.
Existing media presentation handoff remains unchanged.

Post → User → Back and Post → Community → Back restore the same post id, focused
comment, and visit id before the following Back restores the original feed surface.

## Process restoration

The typed origin is encoded as a versioned URL-safe Base64 token and mirrored through
`rememberSaveable` together with post id, focused comment id, media route, and visit id.
Unknown or malformed tokens are rejected and fall back to the existing root route.

## Deferred V2 phases

- FEEDPOST-V2B: replace the remaining non-post nullable navigation fields with a typed stack.
- FEEDPOST-V2C: `FeedKey` and independent scroll anchor per Home source/sort/community/search.
- FEEDPOST-V2D: exact item/offset return and post/comment scroll restoration.
- FEEDPOST-V2E: complete process-death stack restoration.
