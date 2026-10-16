# NEXT01A3A — Inbox Activity canonical adaptive workspace

## Purpose

NEXT01A3A is the first Inbox successor after A11Y01A4.

A11Y01A4 already supports Inbox-owned Post and Conversation detail while
retaining Inbox on the left. NEXT01A3A adds the missing canonical workspace
entry for Activity.

## Order

Inbox is implemented in this order:

1. Activity
2. Messages
3. Mod Queue
4. Modmail

NEXT01A3A intentionally covers only item 1.

## Activity main route

On an expanded window with Tablet mode `Split`, Inbox Activity opens directly
as a two-pane workspace.

The left pane is the production Inbox Activity surface.

The right pane initially displays `Select activity`.

## Detail selection

Activity retains the existing A11Y01A4 detail engine.

An Activity item may resolve to:

- an Inbox-owned Post/comment detail; or
- a fallback Conversation detail.

Once a detail is open, another Activity selection replaces the current detail
through the existing A4 replacement path rather than pushing additional detail
routes.

## Primary-tab ownership

Before NEXT01A3A, `InboxScreen` owned its primary tab only through local
`rememberSaveable` state while `SubshellApp` merely observed it.

NEXT01A3A adds an optional controlled primary-tab input.

The existing local state remains the default for legacy/uncontrolled callers.
SubshellApp uses the controlled mode so Activity identity survives transitions
between the main Inbox workspace and the existing A4 Post/Conversation detail
hosts.

The hoisted owner remains `rememberSaveable` and is keyed by active account,
preserving the previous account-scoped UI-state continuity rather than
downgrading the selected Inbox tab to transient composition state.

The main Inbox route uses one shared `InboxScreen` host for both its Activity
split presentation and its ordinary full-surface presentation. This preserves
the established three static Inbox application call sites and avoids
duplicating Modmail, moderation, notification, and account-pinned wiring.

## Deferred behavior

Messages is not promoted to split-from-entry in NEXT01A3A.

Mod is not promoted to split-from-entry in NEXT01A3A.

Modmail retains its existing full-thread behavior and the existing
`modmailThreadOpen` shell exception.

## Unchanged systems

NEXT01A3A does not change:

- Reddit Inbox transport;
- Inbox target resolution;
- Inbox read-state semantics;
- Conversation routing;
- Direct Chat transport or writes;
- moderation queue routing;
- Modmail state or routing;
- OAuth;
- Room;
- persistent account/session data;
- Compact layout;
- Tablet mode `Single`.

## Runtime acceptance

Use DEV1 with Tablet mode `Split`.

1. Open Inbox while Activity is selected.
2. Inbox immediately shows Activity left and `Select activity` right.
3. Open Activity targeting a Post/comment.
4. Post/comments appear right and Activity remains left.
5. Open another Activity item; only detail changes.
6. Back returns to Activity workspace with placeholder.
7. Open an Activity item that uses fallback Conversation detail if available.
8. Conversation appears right and Activity remains left.
9. Switch to Messages; NEXT01A3A must retain the existing Messages entry
   presentation rather than claiming A3B behavior.
10. Switch back to Activity; split workspace returns.
11. Verify Tablet mode `Single` retains prior behavior.

Messages entry split is NEXT01A3B.
