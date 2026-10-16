# MESSAGES02C — typed message composer

MESSAGES02C replaces the legacy local-only conversation input with a Material 3 composer owned by the authenticated Inbox surface.

Private-message threads use Reddit's legacy `t4_` reply contract through `POST /api/comment`, using the already-granted Inbox `privatemessages` capability and the shared non-retrying write transport. Successful API acknowledgement clears only the matching per-thread draft and refreshes the authoritative Inbox conversation. Timeout, I/O, malformed-response, and other ambiguous outcomes are never retried automatically; the draft remains and the UI asks the user to refresh before attempting another write.

Modern Reddit Direct Chat is a separate Matrix service. The public third-party OAuth grant used by Subshell does not expose supported Matrix send credentials. MESSAGES02C therefore provides draft composition in Direct Chat but deliberately disables remote send instead of emulating reddit.com cookies or first-party web-session JWTs.

Draft text is account/thread scoped in the application state and survives ordinary navigation away from and back to a conversation during the process lifetime. The composer is multiline, bounded to 10,000 characters, uses IME bottom inset handling, and exposes `Message` for Direct Chat and `Reply` for private-message threads. Attachments, Matrix write auth, destructive message actions, and a Room schema migration are outside this phase.
