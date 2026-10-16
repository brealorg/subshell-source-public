# NEXT01A3B — Inbox Messages canonical adaptive workspace

## Purpose

NEXT01A3B follows the accepted NEXT01A3A Activity workspace.

A3A established controlled, account-saveable Inbox primary-tab ownership and
one shared main Inbox host. A3B reuses that foundation for Messages.

## Messages main route

On an expanded window with Tablet mode `Split`, selecting Messages immediately
shows a list/detail workspace.

The left pane remains the production Inbox Messages list.

The right pane initially displays `Select a conversation`.

## Conversation detail

Selecting a private-message or Direct Chat conversation continues to use the
existing A11Y01A4 Conversation detail route.

The Messages list remains in the left pane while Conversation/Direct Chat is
rendered on the right.

Selecting another conversation replaces the current detail through the existing
Inbox detail/navigation model; NEXT01A3B adds no new route type.

Back from Conversation returns to the Messages workspace and its empty detail
placeholder.

## Preserved architecture

NEXT01A3B reuses the single shared main `InboxScreen` host introduced by A3A.

The established three static application Inbox call sites remain intact:

1. Inbox-origin Post detail
2. Conversation detail
3. shared main Inbox host

Modmail, moderation and notification callback wiring is not duplicated.

## Unchanged

NEXT01A3B does not change:

- Activity behavior accepted in A3A;
- Mod Queue;
- Modmail;
- Conversation rendering;
- Direct Chat rendering, long-polling, writes or media;
- Inbox transport;
- read-state semantics;
- routing model;
- persistent data;
- OAuth;
- Compact layout;
- Tablet mode `Single`.

## Runtime acceptance

With Tablet mode `Split`:

1. Open Inbox and select Messages.
2. Messages must immediately remain left with `Select a conversation` right.
3. Open a Direct Chat.
4. Direct Chat appears right while Messages remains left.
5. Select another conversation from the left pane.
6. Only detail changes.
7. Back returns to Messages plus `Select a conversation`.
8. Switch Messages → Activity; accepted A3A Activity workspace remains intact.
9. Switch Activity → Messages; Messages workspace returns.
10. Mod remains unchanged.
11. Tablet mode `Single` retains the previous full-surface Messages behavior.

Final tablet visual QA remains deferred.
