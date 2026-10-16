# ISSUE10 — selectable comment hierarchy presentation

## Scope

GitHub issue #10 concerns the visual hierarchy shown beside nested Reddit
comments.

Two interpretations are intentionally supported.

### Guide lines

`GUIDE_LINES` is the backward-compatible default.

It preserves the existing Subshell branch-rail presentation. Existing users do
not receive a silent visual change when updating.

### Parent connectors

`PARENT_CONNECTORS` is the alternative presentation.

It represents actual parent-to-child relationships rather than only structural
guide rails.

The defining visual invariant is:

> A parent connector leaves near the lower edge of the complete rendered parent
> comment and enters the direct reply near its header. It must not visually
> originate across the parent's username/header.

When a parent has multiple direct replies, the relevant parent lane may continue
through an earlier sibling subtree until the next direct sibling.

## Settings

The preference is persisted as `comment_hierarchy_style`.

Default:

`GUIDE_LINES`

Available values:

- `GUIDE_LINES`
- `PARENT_CONNECTORS`

The setting is exposed under the existing Comments / Comment presentation
settings.

The compact settings-row values are:

- `Guides`
- `Connectors`

The choice dialog retains the full labels:

- `Guide lines`
- `Parent connectors`

## Live preview

There is exactly one Comments Live Preview.

No standalone hierarchy preview is introduced.

The existing preview receives the selected hierarchy style and updates
immediately.

The accepted preview uses one parent comment and one direct reply, matching the
actual Subshell comment presentation closely enough to demonstrate the
relationship rather than an abstract tree.

The preview deliberately includes:

- parent author/header;
- parent body;
- parent actions;
- direct reply author/header;
- direct reply body;
- direct reply actions;
- New state when enabled.

It deliberately excludes an artificial third hierarchy level and the old
`Reply context` demonstration label.

## Runtime acceptance

Accepted on Pixel 6 / Android 17.

Verified:

1. Guide lines remain the default/current presentation.
2. Parent connectors visibly attach from the lower region of the parent comment
   rather than crossing its username.
3. Multiple direct replies remain visually attributable to the same parent.
4. Selecting Parent connectors survives app restart.
5. Collapse/expand removes and restores the relevant connector presentation
   without leaving stale rails.
6. Existing comment collapse, action placement, time and new-comment settings
   continue to function.

## Non-goals

This change does not alter:

- Reddit comment tree data;
- `parentId` identity;
- comment sorting;
- collapse state semantics;
- focused-comment navigation;
- hydration;
- pagination / More replies;
- comment write/edit/delete behavior.

It is a persisted presentation preference over an already-correct comment tree.

## Failure classification

Contract02 predecessor semantics and historical product evidence are separate.

Current candidate:

- Primary: PASS after source/test/build/runtime acceptance.
- Contract02 predecessor: none.
- Release-blocking: NO after accepted runtime qualification.

Historical evidence:

- `0.77.0-beta.1` did not provide the selectable direct parent-connector
  presentation requested by issue #10.
