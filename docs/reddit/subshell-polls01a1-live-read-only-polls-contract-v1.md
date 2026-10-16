# POLLS01A1 — live Reddit read-only poll rendering contract v1

POLLS01A1 promotes the existing local prototype poll presentation into the authenticated Reddit read path without adding a poll write capability.

## Provider boundary

Authenticated listing DTOs read Reddit `poll_data` from ordinary `t3` listing children and from crosspost parents. The projection consumes only read data already returned with the post:

- `options[].id`
- `options[].text`
- optional `options[].vote_count`
- `total_vote_count`
- `voting_end_timestamp`

`voting_end_timestamp` is normalized defensively from either Unix milliseconds or legacy Unix seconds. Invalid poll options are skipped; fewer than two valid options do not create a native poll projection.

No poll vote endpoint, GraphQL call, Developer Platform mutation, OAuth capability, scope, submit kind, or poll-creation transport is introduced.

## Result visibility

Per-option percentages are rendered only when Reddit supplies a vote count for every valid option. A missing `vote_count` is represented internally as zero for cache compatibility but does **not** make results visible. This prevents Subshell from fabricating a `0%` result surface when Reddit withholds option counts.

The total vote count remains visible when supplied. Poll end state is projected as a compact remaining-time label or `Closed`.

## Presentation

`PostKind.POLL` remains the canonical post kind and uses the existing `PostMedia` slot in every `PostCard` surface, including PostScreen. Authenticated Reddit polls remain read-only inside Subshell:

- options never select or mutate a Reddit vote inside Subshell;
- tapping an option uses the same external vote handoff as `Open on Reddit to vote`;
- the local `Vote` button is not rendered;
- open polls identify themselves as `Read-only Reddit poll`;
- closed polls identify themselves as `Poll closed`;
- a canonical `https://www.reddit.com/poll/<post-id>` target backs `Open on Reddit to vote` for an open poll and `Open on Reddit` for a closed poll;
- the vote handoff uses a package-pinned browser Custom Tab and explicitly sets both Custom Tabs external-handler escape controls to `false`, so the initial navigation and its redirects are requested to remain inside the browser; if no default browser package is available, the normal Android chooser is used as a fallback;
- the exact redundant Reddit poll-link line in `selftext` is removed when a native poll projection is present, while all other post body text is preserved.

The existing local fixture poll remains interactive for local accounts. `SubshellState.votePoll()` stays fenced by `localMutationsAllowed`, so POLLS01A1 cannot turn the prototype mutation store into a live Reddit write path.

Crossposted polls preserve their poll payload through `CrosspostPreview` and reuse the existing embedded-post renderer rather than adding a parallel UI.

## Cache compatibility

The nested poll cache payload appends `resultsVisible` and `isClosed`. The decoder accepts both the legacy three-field poll payload and the new five-field payload. Crosspost cache payloads append the optional poll field and continue to accept the previous 4-field and 21-field forms.

No Room schema or migration is added because the existing post payload already owns nested poll serialization.

## Acceptance

1. A live listing `poll_data` object maps to `PostKind.POLL` with its options and total vote count.
2. Complete provider option counts render percentages; missing counts do not render fake percentages.
3. Future and closed `voting_end_timestamp` values produce stable open/closed presentation.
4. Authenticated poll options dispatch no local mutation; tapping a choice uses the external Reddit vote handoff.
5. An open authenticated poll offers `Open on Reddit to vote` using the canonical Reddit `/poll/<post-id>` page in a package-pinned browser Custom Tab with both external-handler and initial-navigation escape flags explicitly disabled; a chooser is only the no-default-browser fallback.
6. Native poll rendering removes only the redundant provider poll-link line from the selftext body.
7. Poll projections survive the existing post cache and crosspost cache payloads.
8. Local prototype poll voting remains unchanged and device-local.

## Same-app external poll rebound guard

The browser handoff is paired with a narrow same-app rebound guard. Before an
authenticated Reddit poll is sent to the browser, Subshell records only the
post identity and handoff timestamp in application-private preferences.

If Android immediately returns a supported Reddit post/comment deep link for
the same post to that same Subshell package within 60 seconds, the marker is
consumed once, normal deep-link navigation is skipped, the delivered URI is
cleared from the activity intent, and the Subshell task is moved behind the
browser. Different posts, community/user links, stale markers, future
timestamps, and subsequent normal Reddit links are not suppressed.

The marker is deliberately package-private. A production installation writes
and receives the marker in the same application package. Multi-slot development
installations must route the runtime rebound to the slot under test; a DEV2
marker cannot be read by a separately installed stable package.

This guard does not disable Reddit App Links, change supported Reddit target
parsing, add a browser-specific URL, add a poll write, or broaden OAuth.
