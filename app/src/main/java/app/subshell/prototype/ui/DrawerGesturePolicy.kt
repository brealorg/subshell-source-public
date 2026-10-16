package app.subshell.prototype.ui

// ISSUE23_DRAWER_DISMISS_OWNERSHIP_V1
/**
 * Whether the navigation drawer may be operated by gestures.
 *
 * Material 3 ties three things to `gesturesEnabled`: the drag gesture, the
 * scrim tap, and nothing else (Back lives in `ModalDrawerSheet(drawerState)`).
 * ISSUE16 gave configurable row swipes ownership of horizontal drag, which
 * must only remove the drawer's drag-to-open: while the drawer is open the
 * content sits under the scrim and cannot be swiped, so scrim tap and
 * drag-to-close keep working. Contextual routes never expose the drawer.
 */
internal fun drawerGesturesEnabled(
    inContext: Boolean,
    swipeActionsEnabled: Boolean,
    drawerOpen: Boolean,
): Boolean = !inContext && (!swipeActionsEnabled || drawerOpen)
