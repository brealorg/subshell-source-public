# ISSUE16 configurable swipe actions contract v1

## Product boundary

ISSUE16 adds optional horizontal swipe accelerators to rendered posts and comments.

The feature is disabled by default. When enabled, post left/right and comment left/right
actions are configured independently. Posts support None, Upvote, Downvote, Save and Hide.
Comments intentionally start with None, Upvote and Downvote only.

Ordinary vote, save, hide and comment controls remain available. Swipe does not create a
new write authority.

## Mutation ownership

Swipe dispatch reuses the current `SubshellState` mutation boundaries:

- post/comment vote -> `state.vote(...)`
- post save/unsave -> `state.toggleSaved(post)`
- post hide/unhide -> `state.togglePostHidden(post, authoritativeHiddenHint)`

Those functions continue to own account identity, Reddit fullname normalization,
optimistic projection, replacement/toggle semantics, in-flight exclusion and rollback.
No Reddit network writer or coordinator is duplicated by ISSUE16.

## Gesture arbitration

The shared `SwipeActionSurface` observes pointer movement at `PointerEventPass.Final`.

- A gesture beginning inside the 32 dp system-edge guard is never claimed.
- Vertical-dominant movement cancels swipe ownership.
- Movement consumed by a nested child cancels swipe ownership, preserving galleries,
  horizontal code blocks, media controls and other horizontal children.
- A direction configured as None or currently disabled cannot be claimed.
- The commit threshold is 88 dp.
- Crossing the threshold emits one haptic signal per gesture.
- No action runs during drag. Commit is evaluated only after release.
- Release below threshold cancels and returns content to rest.
- Reduce-motion uses immediate reset; otherwise the content animates back.

## Material 3 presentation

The revealed affordance uses Material 3 theme roles and rounded shapes. The label reflects
the mutation that release will perform, including Remove upvote, Remove downvote, Unsave
and Unhide states.

## Settings and backup

The global enable switch and four directional mappings are explicit `SubshellSettings`
keys. They participate in the existing allowlisted settings backup/restore path. Older
backups receive backward-compatible defaults with swipe disabled.


## Runtime UX correction

The first runtime preview proved the gesture and mutation boundaries but failed UX
qualification because raw finger travel was rendered 1:1 as content translation and the
action affordance occupied the full revealed background.

The revised presentation keeps commit semantics on raw drag distance while resisting
visible content movement to 42% with a 72 dp visual cap. The action is presented as a
fixed Material 3 edge pill whose emphasis increases toward threshold. Release returns
with a non-bouncy spring.

Post swipe started as a list/feed-card accelerator with the post-detail `PostCard` opted
out. Since #217 (2026-10-12, owner reversed the opt-out) the post card at the top of the
post screen takes the same configured swipe actions as a feed card; only that card swipes,
not the whole screen, and comment rows keep their own swipe actions. Hide on the open post
toggles the hidden state like the card's Hide/Unhide menu item; the card stays.


## Google Material 3 native rewrite

The custom ISSUE16 pointer-input engine failed runtime UX qualification and is superseded.

The production gesture surface now delegates horizontal dragging, velocity, positional
thresholds, settling and nested draggable arbitration to stable Material 3
`SwipeToDismissBox` / `SwipeToDismissBoxState` from Material3 1.4.0.

The implementation uses:

- `rememberSwipeToDismissBoxState`
- `SwipeToDismissBoxDefaults.positionalThreshold` (Material3 default: 56 dp)
- `gesturesEnabled`
- `enableDismissFromStartToEnd` / `enableDismissFromEndToStart`
- `backgroundContent`
- `onDismiss`
- `SwipeToDismissBoxState.reset()` for ordinary update-like actions

`confirmValueChange` is not used because it is deprecated in Material3 1.4.0.

SUBSHELL owns only action mapping and dispatch. Post/comment vote, save and hide continue
through the existing state mutation boundaries. Physical left/right settings are mapped
to Material's logical StartToEnd/EndToStart values with layout-direction awareness.

The Android system Back edge is not overridden or excluded. ISSUE16 installs no
`systemGestureExclusion` and no custom pointer listener.

Since #217 the post-detail `PostCard` swipes too; comment rows retain their own swipe actions.


## Immediate Save/Unsave reversal

Runtime probing on the native Material 3 preview proved that the apparent post-save
"timer" was not `SwipeToDismissBox` settling. The shared authenticated Save pipeline
held one global target in-flight until Reddit returned `SUCCEEDED`/`FAILED`; the first
measured gate was 322 ms and the next dispatch occurred 24 ms after success.

ISSUE16 therefore preserves one remote Save/Unsave request at a time but allows the
same canonical target to be reversed immediately:

- the UI applies the latest desired saved state optimistically;
- while that same `t1_`/`t3_` target is in-flight, newer intent replaces one queued
  desired state instead of dispatching a concurrent network write;
- after `SUCCEEDED`, one follow-up write is dispatched only when the queued desired
  state still differs from the acknowledged server state;
- if the queued intent has converged with the acknowledgement, no redundant request
  is sent;
- `FAILED` rolls back through the existing rollback boundary, clears queued intent,
  and never performs an automatic retry;
- a different target remains disabled while the single shared PostSave writer is
  in-flight, preserving the existing single-request coordinator ownership;
- ordinary post Save controls and swipe Save share the same target-aware enable rule.

This is a serialization policy change only. OAuth ownership, canonical fullname
normalization, request account pinning, transport retry semantics, and Material 3 swipe
physics remain unchanged.


## Runtime callback lifecycle and drawer arbitration

A correlated Pixel runtime probe after the same-target Save queue change captured a
deterministic Material 3 callback loop:

- 543 `onDismiss` callbacks;
- 542 reset starts;
- 541 reset cancellations;
- 0 completed resets;
- 6 drawer states that were Open or targeted Open during the same horizontal gesture run.

The shared Save queue was not the cause of this second failure. Material 3's current
`SwipeToDismissBox` implementation invokes `onDismiss` from a `LaunchedEffect` keyed by
both `state.settledValue` and the `onDismiss` callback. SUBSHELL previously supplied an
inline callback that changed identity when optimistic Save/Unsave state recomposed the
row. While the row remained settled at a dismiss anchor, each callback identity change
restarted Material 3's effect, invoked the action again, and started a new reset that
cancelled the preceding anchored-drag mutation.

The correction keeps the `onDismiss` function identity stable with `remember` and reads
the latest action, layout direction, and reduce-motion values through
`rememberUpdatedState`. The Material 3 state machine and its native reset animation stay
authoritative; no custom pointer or settling physics is reintroduced.

The navigation drawer is a second horizontal drag owner on the root surface. When
configurable swipe actions are enabled, drawer drag gestures are therefore disabled.
The drawer remains accessible through its explicit drawer control. When swipe actions
are disabled, the pre-existing drawer gesture remains available.

The same-target Save last-intent queue, Reddit writer, OAuth/account pinning, settings,
and ordinary controls are unchanged by this lifecycle/arbitration repair.


## Accepted settings presentation: inline expandable swipe actions

The accepted ISSUE16 settings presentation remains within the existing
**Navigation & gestures** destination and reuses its existing sticky **Live preview**.

Presentation rules:

- exactly one persistent preview owner exists: `NavigationSettingsPreview`;
- Posts and Comments are compact summary rows inside one Swipe actions card;
- exactly one of Posts or Comments may be expanded inline at a time;
- expanding Posts derives the shared preview context as `POST_SWIPE`;
- expanding Comments derives the shared preview context as `COMMENT_SWIPE`;
- collapsing the swipe section restores the non-swipe navigation preview context;
- no swipe preview state is persisted as a sticky last-selected preview mode;
- the shared preview uses the production `SwipeActionSurface`, but binds both preview
  callbacks to no-op lambdas so it cannot write to Reddit;
- the shared preview status badges use singular `Post` and `Comment`;
- expanded swipe rows expose `Swipe left` and `Swipe right` as ordinary settings rows;
- each action picker uses one Material 3 single-choice radio dialog with
  `selectableGroup`, `Role.RadioButton`, and `RadioButton`;
- no FilterChip action strip, no horizontal choice scroller, no modal bottom sheet, and
  no second preview are used;
- the Side menu section is visually separated from Swipe actions.

This presentation does not alter the ISSUE16 production gesture engine, Save last-intent
serialization, vote/save/hide mutation boundaries, account pinning, drawer arbitration,
or persisted swipe setting keys.
