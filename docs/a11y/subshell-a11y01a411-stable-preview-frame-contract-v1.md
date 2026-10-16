# A11Y01A4.1.1 — stable sticky preview frame

## Runtime finding

A4.1 successfully reuses the existing sticky Navigation preview host for
Tablet mode, but the transition looks like a layout glitch.

The inner Navigation and Nettbrett preview canvases were both 142 dp high.
The visible jump came from the preview **chrome** around them: the header changed
from `Live preview` plus a two-line helper to `Tablet mode` plus a shorter
helper, while the status pill also changed width.

The host therefore changed measured height and text allocation while Crossfade
was switching content.

## Correction

1. The sticky preview remains one component and one card.
2. Its outer chrome structure remains stable across preview contexts:
   the `Live preview` heading, helper-text allocation and status-pill geometry
   remain in the same host even when the helper text describes the active
   preview.
3. Navigation, Tablet mode and the later Community discovery successor use one
   shared 142 dp viewport-height constant.
4. The status pill receives a stable 88 dp width and centered text so
   `At rest`, `Scrolling`, `Split`, `Single` and discovery-layout labels do not
   change header allocation.
5. Crossfade changes only the pixels/content **inside** the same-size viewport.
6. Split / Single behavior, persistence and A4 list/detail logic are unchanged.
7. Community discovery reuses the same preview host; no second preview is
   introduced.

## Runtime acceptance

On Settings → Navigation & gestures:

- switch repeatedly between Split and Single;
- touch another navigation setting to return to the normal preview;
- repeat several times.

The sticky card must keep the same outer bounds throughout. Only the content
inside the preview viewport and the status-pill text should change.


## ISSUE16 successor: shared swipe preview viewport

ISSUE16 extends the existing Navigation & gestures Live preview with Post swipe and
Comment swipe contexts. This does not introduce a second preview frame or a new
viewport size.

The fixed `NavigationSettingsPreviewViewportHeight = 142.dp` invariant remains
authoritative. Navigation, Tablet mode, Community discovery, and the shared swipe
preview all use that same height. Post and Comment swipe contexts intentionally share
one `NavigationSwipePreviewSnapshot`, so the five preview contexts are represented by
four `.height(NavigationSettingsPreviewViewportHeight)` owner call sites.

Crossfade changes only the pixels/content **inside** the same-size viewport. The
ISSUE16 swipe successor likewise changes only content inside that stable viewport.
