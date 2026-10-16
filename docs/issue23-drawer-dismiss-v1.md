# Issue #23 — drawer dismiss with swipe actions enabled

With configurable swipe actions on, the navigation drawer did not close on
Back or on a tap outside it; only selecting a destination closed it.

## Root cause

ISSUE16 set `ModalNavigationDrawer(gesturesEnabled = !inContext &&
!state.swipeActionsEnabled)` so row swipes own horizontal drag. In Material 3
that flag also gates the scrim tap (`onDismissRequest` checks
`gesturesEnabled` before `close()`, and the scrim still consumes the tap).
Back was never handled for the drawer: the app used the `ModalDrawerSheet`
overload without `drawerState`, and only the `drawerState` overload installs
the (predictive) back handler.

## Fix

- `drawerGesturesEnabled(inContext, swipeActionsEnabled, drawerOpen)`
  (`ui/DrawerGesturePolicy.kt`): `!inContext && (!swipeActionsEnabled ||
  drawerOpen)`. A closed drawer gives up drag-to-open while row swipes are on;
  an open drawer covers the rows, so scrim tap and drag-to-close stay enabled.
- `ModalDrawerSheet(drawerState = drawerState, …)` so Material 3 owns Back and
  predictive back for the open drawer, independent of the swipe setting.

## Runtime evidence

```text
tag=subshell-issue23
drawer current=Open target=Closed swipe=true inContext=false gesturesEnabled=true
```

Device evidence 2026-09-20 (Pixel 6, dev2): the scrim is the strip beside the
sheet (`[882,0]–[1080,2400]`, sheet 336 dp); a tap on it closes the drawer,
Back closes it. Two Material 3 properties remain as-is: the scrim only takes
taps once `drawerState.isOpen` (a tap during the opening animation reaches
the content), and Back during the closing animation falls through to the
activity (on Main with nothing to pop, Android exits).

## Device check

Swipe actions on: open the drawer → Back closes it; tap outside closes it;
drag closes it; a closed drawer cannot be dragged open; row swipes still work.
Swipe actions off: unchanged (drag-to-open works).

## Failure classification

- Primary: the arbitration function or the `drawerState` sheet overload is lost.
- Predecessor: ISSUE16 swipe contracts red while issue #23 assertions pass.
- Release-blocking: only when a release being prepared cannot ship because of it.
