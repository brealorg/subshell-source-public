# COMMENTSAVE01A1 — comment-capable Reddit save foundation

## Goal
Extend the existing HOMEACTION01 owner-bound Reddit Save/Unsave transport so it
can carry canonical Reddit comment fullnames (`t1_*`) as well as post fullnames
(`t3_*`), without creating a parallel comment-save writer, new OAuth capability,
or Room migration.

## Architecture decision
Reddit exposes one `/api/save` and `/api/unsave` mutation for both posts and
comments. The existing HOMEACTION01 transport already owns the correct `save`
OAuth scope, authorization owner, non-retrying write client, coordinator,
retry/error state, and account pinning. COMMENTSAVE01 therefore reuses that
pipeline and only widens its fullname boundary.

The legacy `normalizePostSaveFullname()` entry point remains for predecessor
compatibility. Raw IDs still default to `t3_*`; canonical `t1_*` fullnames are
preserved. New comment callers use `normalizeCommentSaveFullname()` so a post
fullname cannot accidentally enter the comment path.

## Server saved-state projection
Authenticated Reddit comment JSON now projects the optional `saved` boolean into
`Comment.saved`. Missing/legacy values default to `false`.

## Cache compatibility
The comment cache payload gains one trailing boolean field. Existing 13/14/15/
17/19/20-field payloads remain readable; the new payload contains 21 fields.
No Room schema change is required because this is an existing opaque comment
payload codec.

## Explicitly deferred to A2
- Comment-thread Save / Unsave UI entry.
- Optimistic comment saved-state override and rollback in `SubshellState`.
- Comment-specific error copy.
- Saved-tab reconciliation after mutation.

## Safety boundaries
- No OAuth scope expansion: existing daily-driver `save` grant is reused.
- No new authorization owner.
- No new network endpoint.
- No Room migration.
- No DEV1 mutation.

## v2 test-fixture correction

The authenticated comment projection test must model Reddit's canonical
`/comments` response shape: the first listing contains the post and the second
listing contains comments. `toComments()` intentionally consumes listing index
1. This is a test-fixture correction only; production mapping behavior is
unchanged.
