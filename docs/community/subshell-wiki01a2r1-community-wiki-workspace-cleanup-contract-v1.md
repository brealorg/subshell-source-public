# WIKI01A2R1 — Community Wiki workspace and Markdown cleanup

## Runtime motivation

Runtime screenshots of the first WIKI01A2 preview proved that incremental
`wikiread` authorization, Wiki index loading, page loading, back navigation,
links and native code blocks work.

They also exposed three concrete presentation defects before visual
finalization:

1. a fourth equal-width `Wiki` tab clipped `Moderators`;
2. raw `config/*` and automoderator pages dominated the normal Wiki index;
3. Reddit ATX headings such as `#Rules` and `##Behavior:` were rendered with
   their literal hash markers.

## Information architecture

WIKI01A2R1 restores the three accepted Community Info tabs:

- About
- Rules
- Moderators

Wiki is an entry from About labelled `Wiki` with supporting copy
`Community documentation`.

Opening it switches the existing modal into a dedicated hierarchical Wiki
workspace. The first level offers `Back to Community info`; a selected page
offers `Back to Wiki`. Android back follows the same hierarchy before the modal
is dismissed.

This keeps Wiki's index → page → back hierarchy out of the equal-width tab
row while preserving the accepted CommunityInfo behavior and return state.

## Index projection

The API result is not discarded. It is projected into two groups:

- normal community pages, with `index` first;
- configuration/moderation pages.

`config/*`, `automoderator-schedule`, `automoderator/*` and `mod/*` are
collapsed behind `Configuration pages`. They remain readable on demand.

The redundant `Wiki page` supporting label is removed from every row.

## Canonical Markdown headings

Heading support is added to the shared `MarkdownText` pipeline, not to a
Wiki-only renderer.

Paragraph blocks are post-processed into semantic heading blocks for ATX
lines with one through six leading `#` characters. Reddit forms without a
separating space (`#Rules`) are accepted because they occur in live Wiki
content.

Existing code, quote, spoiler, inline formatting and link blocks continue
through their existing canonical paths. Code blocks retain their existing
Copy control and configured code font.

## Boundary

WIKI01A2R1 does not add Wiki writes, Room migration, backup/prefs mutation,
daily-driver OAuth scope widening, or DEV1 mutation.

`wikiread` remains incremental-only.

## Pre-finalization gate

`MATERIAL_3_EXPRESSIVE_FINALIZATION_REQUIRED`

WIKI01A2R1 is still a functional runtime preview. It does not satisfy the
dedicated Material 3 Expressive pass required before WIKI01 can be locked or
landed.
