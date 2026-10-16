# SEARCH01A1 — canonical search session and provider foundation

SEARCH01A1 separates the text currently being edited from the query that owns visible Reddit results. A draft may change without changing the submitted query, paging key, cached page, or exact return route. Submitting normalizes whitespace, advances a monotonic submission revision, and records the query in private account-scoped history.

The canonical query planner reuses the existing `RedditTarget` resolver. Exact `r/`, `u/`, `user/`, `t3_`, `redd.it`, Reddit post, and Reddit comment targets are exposed as direct actions before ordinary provider results. It does not add a second URL parser.

The provider foundation distinguishes direct targets, communities, posts, users, and future local-cache results. SEARCH01A1 connects the main search surface to the existing authenticated community autocomplete transport. Requests are debounced, account-scoped, NSFW-policy-aware, and accepted only when the returned state matches the active query parameters.

Search history is persisted per account stable ID in the existing device-local `subshell_device_preferences` store, which is excluded from Android backup by the DEEPLINK01 boundary. Entries are normalized, de-duplicated case-insensitively, bounded, encoded for robust ordered storage, and cleared explicitly from the active account only. No search query is logged.

Route and process snapshots carry both draft and submitted query identity. Older process snapshots remain compatible by treating their single query field as both draft and submitted data.

SEARCH01A1 intentionally preserves the existing Reddit post search transport, cursor paging, viewport append behavior, error presentation, and post return anchors. AppSearch, live Reddit user search, full federated ranking, and optional web search are successor phases.
