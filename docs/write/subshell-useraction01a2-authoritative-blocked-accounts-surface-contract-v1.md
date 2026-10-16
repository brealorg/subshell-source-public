# USERACTION01A2 — authoritative blocked accounts surface

## Product boundary

USERACTION01A2 makes Reddit blocking reversible even when a blocked account can no
longer be reached through normal profile or search surfaces. The authenticated user's
existing **You** destination strip gains **Blocked** between **Hidden** and **Drafts**.
No separate privacy dashboard is introduced.

## Authoritative source

Blocked accounts are read from Reddit's OAuth `GET /prefs/blocked` UserList endpoint.
The response owns username, account fullname, relationship id and block timestamp.
Subshell does not infer blocked accounts from search failures, profile HTTP 403s, local
history, or the process-local USERACTION01A1 optimistic override.

The endpoint is requested with a page size of 100 and its returned `after` cursor is
preserved for additional pages.

## Presentation

The Blocked destination is available only for the authenticated user's own profile.
Each row contains a deterministic profile avatar fallback, `u/username`, the Reddit
block date when supplied by the provider, and a trailing **Unblock** action. A blocked
profile itself does not need to be readable for the row to exist.

Empty, loading and failure states remain inside the same You destination surface.
Pull-to-refresh reloads the authoritative first page. Additional pages append using the
provider cursor.

## Mutation reconciliation

Unblock reuses USERACTION01A1's owner-bound `POST /api/unfriend` `enemy` mutation.
After a successful Block or Unblock mutation, Subshell refreshes `/prefs/blocked` so the
list is reconciled against Reddit rather than retained as a local guess.

## Scope

USERACTION01A2 does not add block actions to comment/post overflow menus and does not
filter blocked authors from feeds/comments. Those remain successor work.
