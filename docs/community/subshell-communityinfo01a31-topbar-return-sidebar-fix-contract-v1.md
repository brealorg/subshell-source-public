# COMMUNITYINFO01A3.1 — top-bar About, profile return and sidebar correction

## Runtime evidence

COMMUNITYINFO01A3 was exercised on real `r/norge` and `r/android` data.

Three product findings came from that runtime pass:

1. The full-width `About community` row competes visually with the actual community
   header and feed controls.
2. Opening a moderator profile deliberately cleared the About-sheet flag, so Back
   returned only to the subreddit rather than the context the user had opened.
3. Rules and moderators loaded, while the About/sidebar section consistently failed.

The third finding exposed an A1 transport mistake: `/r/{subreddit}/sidebar` is not the
canonical Reddit Data API resource used for the legacy/sidebar description. The raw
Markdown is carried as `data.description` on `/r/{subreddit}/about`.

## Product correction

A3.1 moves About into the existing Community top action row.

The visual order is:

- subreddit title
- Search
- Info / About

The Info action is therefore the right-most top action and the large body button is
retired. Loading remains lazy; opening the icon is still what triggers A2.

## Navigation correction

Moderator rows continue to use the existing `SubshellState.openUser()` route.

A3.1 no longer clears `aboutSheetVisible` before opening that route. The Community
screen already owns the flag with `rememberSaveable` inside its route-specific
`SaveableStateProvider`, so returning from the moderator profile restores the About
sheet instead of dropping the user one level deeper than expected.

Explicit sheet dismissal still clears the flag.

## Sidebar/about correction

The A1 `SIDEBAR` semantic section now requests:

`GET /r/{subreddit}/about`

and projects:

`data.description`

Rules and moderators remain:

- `GET /r/{subreddit}/about/rules`
- `GET /r/{subreddit}/about/moderators`

The shared identity path still owns compact `public_description`; Community Info owns
the richer raw Markdown sidecar.

## Scope boundary

A3.1 adds no OAuth scope, Room schema, wiki, user-flair mutation, Search behavior or
DEV1 mutation.

Wiki remains deferred behind the separate `wikiread` decision.
