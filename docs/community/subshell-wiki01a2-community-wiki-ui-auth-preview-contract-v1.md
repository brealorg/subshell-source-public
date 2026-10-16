# WIKI01A2 — community Wiki UI, authorization and navigation preview

## Purpose

WIKI01A2 turns the WIKI01A1 read foundation into a user-visible preview while
preserving the accepted Community About surface.

Authenticated Community About now exposes:

- About
- Rules
- Moderators
- Wiki

The original three destinations retain their existing data, disclosure,
moderator-navigation and return behavior.

## Lazy loading

Opening Community About still loads normal CommunityInfo through the established
A2/A3 path.

Wiki data is separate and is requested only when the user selects the Wiki tab.

The Wiki tab first loads `/wiki/pages`. Selecting one page loads only that page.
Returning from a page uses the already-loaded index rather than forcing a new
network request.

## Incremental authorization

`wikiread` remains an incremental capability and remains absent from
`dailyDriverScopes`.

Authorization is persisted under logical owner `COMMUNITY_WIKI_READ`.

WIKI01A2 adds a normalized, bounded authorization context derived from the
community name. This lets callback and stored-callback resume identify the
community that initiated the grant instead of guessing from transient UI state.

The context contains routing identity only. No Wiki body, page list, write
payload or user secret is persisted in authorization metadata.

After a successful grant the same community Wiki index is refreshed.

## Preserved-scope runtime correction

WIKI01A1 correctly creates capability authorization while preserving previously
granted optional OAuth scopes. Therefore the runtime configuration may be a
superset of `coreReadScopes + wikiread`.

WIKI01A2 corrects the loader configuration check to require
`containsAll(liveCommunityWikiReadScopes)` rather than exact set equality.
The Wiki capability remains least-privilege with respect to newly requested
scope: WIKI01A2 adds only `wikiread`.

## Presentation

The functional preview uses the existing Community About `PrimaryTabRow`,
`ListItem`, Material surfaces and canonical `MarkdownText`.

The Wiki index is emitted as lazy list rows. Page Markdown is rendered inside
Subshell; A2 does not use a WebView for Wiki content.

Typed `UNAVAILABLE` failures render as a Wiki-specific unavailable state.
Authorization-required, loading, empty, HTTP, transport and protocol states are
also explicit.

## Scope boundary

WIKI01A2 adds no:

- Wiki writes or `wikiedit`;
- Room entity or migration;
- preference or backup format;
- Community feed mutation;
- Inbox/DEV1 mutation;
- external page browser as the primary Wiki renderer.

## Pre-finalization visual gate

`MATERIAL_3_EXPRESSIVE_FINALIZATION_REQUIRED`

WIKI01A2 is a **functional presentation preview**, not final visual acceptance.

Before WIKI01 is locked or landed, the Wiki/Community presentation must receive
a dedicated Material 3 Expressive review covering information hierarchy,
containers, navigation/tabs, motion/transitions, loading/empty/error states and
Wiki page reading presentation.

The feature must not move directly from “functional” to “lock it” without this
pass.
