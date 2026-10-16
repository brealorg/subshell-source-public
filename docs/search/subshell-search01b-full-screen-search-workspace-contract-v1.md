# SEARCH01B1 — full-screen search workspace

## Product boundary

SEARCH01B1 replaces the former page-like search presentation with a full-screen search workspace. The canonical SEARCH01A1 session, Reddit post transport, community autocomplete, local ranking, cursor paging, route restore, process restore, and account-scoped device-local history remain the source of truth.

The search field owns the workspace top surface. The Back affordance is integrated into the field, the default global context is not repeated as a dominant control, and filter chips appear only when the current origin exposes more than one meaningful area or scope.

The former provider explanation, oversized result cards, always-visible `All Reddit` chip, and modal scope sheet are retired.

## Zero-query hub

The zero-query hub is intentionally dense and useful before the first keystroke:

- recent searches are compact horizontally scrollable chips;
- recent communities are compact one-line rows;
- additional locally available communities may appear as compact suggestions;
- empty-state guidance is bounded and does not impersonate remote recommendations.

SEARCH01B1 does not add a recommendation backend. Suggested rows are derived only from the existing local community catalog and are explicitly separate from future ranked or personalized discovery.

## Query workspace

While the user edits a draft, SEARCH01A1 continues to own draft and submitted identities separately. SEARCH01B1 presents:

- a compact direct-target action for supported `r/`, `u/`, thing-ID, and Reddit URL targets;
- a compact explicit search action for the current draft;
- live community autocomplete;
- local people suggestions when that provider is eligible;
- the previously submitted post result set under an explicit result heading.

A changed draft does not mutate, refresh, or append the submitted result set until the user submits again. The Reddit cursor remains bound to the submitted query.

## Internal Back ladder

Back navigation inside Search follows this deterministic ladder:

`draft → submitted results → hub → origin`

1. If the draft differs from a non-empty submitted query, Back restores the submitted query in the field.
2. If a draft or submitted result session is active, Back clears the session and returns to the zero-query hub.
3. Only Back from the zero-query hub leaves Search and restores the exact route origin.

Search → post → Back remains unchanged: it returns directly to the submitted Search result set and its retained scroll identity before the internal Search ladder applies.

The explicit `closeSearch()` API remains a hard route close for predecessor callers and tests. System Back and the Search workspace Back affordance use `handleSearchBack()`.

## Material baseline

The interaction model follows Android Material 3 search guidance: Search is an expanded, full-screen mode on compact devices and remains width-bounded on larger displays. SEARCH01B1 uses APIs already present in the project Compose BOM rather than adding a new alpha dependency solely for the state-based `AppBarWithSearch` family.

The workspace uses a filled Material text field, a single sticky top surface, compact filter chips, bounded content width, and compact suggestion rows. A future dependency update may replace the internal top-surface implementation with the corresponding state-based Material 3 search components without changing SEARCH01A1 session ownership.

## Deferred work

AppSearch remains deferred to SEARCH01D. No local full-text index, schema migration, or AppSearch dependency is introduced here.

Live Reddit community and user providers remain SEARCH01C work. SEARCH01B1 only reuses the existing community autocomplete and current repository-bound user catalog.

Google/web remains a separate provider boundary. It is not blended into Reddit or local results, and no external API key, server dependency, analytics event, or query telemetry is introduced.

## Privacy and compatibility

Search history remains account-scoped, device-local, clearable, excluded from portable backup, and never emitted as raw query logging by SEARCH01B1.

No Room schema migration is required. Existing process snapshots and route tokens remain compatible through SEARCH01A1. No main, DEV1, remote, or stable-package mutation is part of the implementation package.
