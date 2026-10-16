# PROFILE01A4B live profile comments UI contract v1

PROFILE01A4B connects the authenticated profile Comments tab to the live Reddit profile-comments foundation introduced by PROFILE01A4A.

Authenticated profile comments are scoped by both active account stable ID and normalized Reddit username. The screen presents cached comments first when available, performs a Reddit refresh, supports pull-to-refresh, and requests additional pages from the opaque Reddit `after` cursor as the list approaches the end.

PROFILE01A4B does not replace local profile fixtures for local accounts. The existing local Activity tab remains Saved and Upvoted and is not reinterpreted as Reddit overview history.

The standard daily-driver Reddit account grant already includes `history`. Those accounts load profile comments without an authorization prompt. An older authenticated account that does not contain `history` is not upgraded merely by viewing a profile. The loader reports `AuthorizationRequired`, and only the explicit `Enable comments` action starts OAuth authorization.

The explicit profile-history authorization uses the persisted `PROFILE_HISTORY` authorization owner. The target profile username is encoded into the authorization context key using a reversible lowercase hexadecimal representation, allowing callback and stored-callback resume to recover the exact profile after activity or process recreation. The context contains no OAuth token, Client ID, password, or other secret.

Profile-history authorization remains bound to the active authenticated account and the existing `PROFILE_HISTORY` runtime selection. It does not widen `CORE_READ` and does not add any OAuth scope beyond the already-defined narrow `history` profile capability.

Tapping a live profile comment resolves its parent `t3` post through the existing Reddit `/api/info` read transport under the profile-history session. A cached parent post is reused when available. Once resolved, navigation uses the existing `openFromUser(post, username, comment.id)` path so the post screen receives the exact focused comment target.

PROFILE01A4B adds no Reddit write endpoint and does not alter comment submission, comment editing or deletion, post submission or management, subscriptions, custom feeds, Inbox, moderation, account switching, or composer behavior.
