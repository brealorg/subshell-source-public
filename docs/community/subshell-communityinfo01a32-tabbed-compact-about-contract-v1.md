# COMMUNITYINFO01A3.2 — tabbed compact Community About

## Runtime problem

The live A3.1 surface proved the data path and navigation, but also exposed a
presentation problem: a subreddit with a large legacy sidebar, ten rules and twenty
moderators becomes one extremely long document.

A3.2 changes information architecture rather than summarizing or rewriting Reddit
content.

## Material information architecture

The sheet keeps one title and introduces a sticky Material `PrimaryTabRow`:

- About
- Rules `<count>`
- Moderators `<count>`

Only the selected section is rendered. The tab row remains visible while that section
scrolls.

The redundant subtitle `Community information from Reddit` is retired.

## About

The raw Reddit sidebar/about Markdown remains authoritative.

By default, the About card renders an eight-line Markdown preview with ellipsis.
Long content exposes an explicit `Show more` / `Show less` control. Subshell does not
algorithmically rewrite moderator-authored text.

## Rules

Rules are compact `ListItem` rows.

By default only rule number and short name are visible. A rule with a description has
an expand affordance. Expanding one row reveals the original Markdown description;
the state model keeps one expanded rule key at a time.

## Moderators

Moderator rows use the same visual identity language as People results:

- circular remote avatar / snoovatar with the existing letter fallback
- `u/username` primary label
- public profile description as secondary label when available
- only non-`all` moderator permissions as compact metadata

The moderator relationship endpoint does not include profile images. A3.2 therefore
uses the existing `SubshellState.userFor()` cache and `ensureUserIdentity()` pipeline.

Identity hydration is initiated inside each composed lazy moderator row, so only rows
that become visible request missing user identity. Existing cached identity appears
immediately. A small semaphore caps identity hydration at two concurrent requests. This
avoids firing one eager request for every moderator when the sheet opens and avoids a
visible-row request burst.

## Navigation state

Selected tab, About expansion and expanded rule are `rememberSaveable` state scoped to
the community. Together with A3.1's preserved sheet intent, moderator -> profile ->
Back returns to the About sheet and the previously selected Moderators tab.

## Boundaries

A3.2 adds no:

- OAuth scope
- Room migration
- community-info persistence
- wiki support
- user-flair mutation
- Search behavior change
- DEV1 mutation

Wiki and user flair remain separate COMMUNITYINFO01 successors.
