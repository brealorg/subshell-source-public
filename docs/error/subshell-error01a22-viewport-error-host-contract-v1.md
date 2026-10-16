# ERROR01A2.2 — Viewport error host contract v1

ERROR01A2.2 changes degraded read-error delivery without changing the typed failure model established by ERROR01A1/A2.

## Viewport delivery rule

A degraded read failure with usable content must not live inside a scroll list. It is reported to one root `ViewportErrorHostState` and rendered through the Scaffold snackbar-host slot as a persistent Error Tray. The tray remains visible until the failure clears, the user dismisses it, or navigation disposes that scoped reporter. It never scrolls content to the top.

Automatic/prefetched append is treated as degraded background work when existing posts remain usable. An append failure therefore uses the same viewport host instead of leaving a failure card below the last loaded item. Retry repeats the failed append operation rather than refreshing from the beginning.

The tray provides a short summary, an optional feature-owned `Retry`, and an explicit dismiss control. `Details` is adaptive: it is shown only when structured failure metadata adds useful information beyond the tray summary. It is exposed as a polite accessibility live region so newly delivered failures are announced without forcing focus or changing scroll position. When available, `Details` opens a Material bottom sheet over the current screen and renders only structured/sanitized `FailureDetails` metadata. DIAG01 may later extend this sheet with diagnostics/report actions; ERROR01A2.2 does not create a dead diagnostics affordance.

## Presentation endpoint matrix

| Surface | Degraded/cached read failure | No usable content | Append/more failure |
|---|---|---|---|
| Home feed | Error Tray | Blocking ErrorNotice in content | Error Tray |
| Community feed | Error Tray | Blocking ErrorNotice in content | Error Tray |
| Search posts | Error Tray | Blocking ErrorNotice in content | Error Tray |
| Post comments | Error Tray | Blocking comment ErrorNotice | More-node/local |
| Inbox Activity/Messages | Error Tray | Blocking ErrorNotice | Existing paging/local |
| Subscriptions/Favorites/Recent | Error Tray | Blocking subscriptions ErrorNotice | N/A |
| Discover full/default results | Error Tray | Blocking ErrorNotice | N/A |
| Discover typeahead/fallback | Contextual inline notice | Contextual inline notice | N/A |
| You/user profile Posts | Error Tray | Blocking ErrorNotice in content | Error Tray |
| You/user profile Comments | Error Tray | Blocking profile-comment ErrorNotice | Existing paging/local |

Explicitly deferred:
- Composer field validation stays inline; submit/upload/network errors migrate in ERROR01A4.
- Media and backup migrate in ERROR01A3.
- Write/mutation failures migrate in ERROR01A4.
- AUTH08 account recovery remains its dedicated account sheet.

## Refresh behavior

Post comments retain capture-safe pull-to-refresh and the post-level overflow action `Refresh comments`. Individual comment overflow menus do not own whole-thread refresh.

## Safety

The Error Tray and Details sheet may present HTTP status, retry delay, transport/protocol/local categories and already-sanitized provider metadata. They must not introduce raw exception text, tokens, OAuth state/codes, cookies, callback payloads, or sensitive URLs.
