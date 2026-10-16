# NEXT01A5 — Subscriptions → Community adaptive list/detail

## Product contract

NEXT01A5 extends the existing adaptive list/detail model to the
Subscriptions/Communities destination.

When all of the following are true:

- the window is expanded,
- Tablet layout mode is Split,
- Community detail originated from the SUBS destination,

the Communities surface remains visible as the left list pane and the
canonical CommunityScreen is rendered as the right detail pane.

The empty SUBS workspace shows `Select a community`.

## Selection semantics

The first Community selection from Main/SUBS keeps the existing push-route
semantics so Android Back can return to the SUBS workspace.

Further selections while a SUBS-origin Community is already open use
`routeStack.replaceCurrent(...)`. Selecting r/foo, then r/bar, then r/baz
therefore keeps one Community detail route instead of creating a growing
Community back stack.

The Main/SUBS split host and the SUBS-origin Community split host share the
`subscriptions-community-workspace` SaveableStateProvider key so the left
Communities pane can preserve its section/list state across first open and
Back.

The selected Community receives tonal selected-container presentation in
the existing Community row/discovery surfaces.

## Compact behavior

Compact windows and Tablet layout mode Single continue to use the existing
full-screen Communities → Community navigation path. No replacement semantics
are introduced there.

## Functional boundary

NEXT01A5 changes presentation and navigation selection semantics only.

It does not change:

- Reddit transport,
- OAuth/authentication,
- subscription membership write semantics,
- Community feed transport or paging,
- Room/database schema,
- persistence formats,
- Community About/Wiki/moderation functionality,
- post navigation inside CommunityScreen.


## Split detail Back affordance

The SUBS-origin Community detail keeps the canonical Community context header,
including community label, Search, moderator tools when applicable, About and
overflow actions.

Only the redundant visual Back button is suppressed in the expanded Split
detail pane. Android/system Back remains authoritative and returns from the
single Community detail route to the Subscriptions workspace.

Compact/full-screen Community presentation retains its existing visual Back
button.
