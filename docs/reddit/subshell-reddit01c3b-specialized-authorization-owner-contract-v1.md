# REDDIT01C3B specialized authorization owner contract v1

REDDIT01C3B migrates inbox-read and custom-feed-write authorization onto persisted logical authorization ownership.

`INBOX_READ` retains the existing inbox scope, session, loader, and mutation behavior.

`CUSTOM_FEED_WRITE` retains the existing custom-feed scope, authorized-session lookup, read behavior, create behavior, update behavior, and delete behavior.

Owner-bound specialized adapters delegate existing session and scope helpers while routing begin, pending, callback, cancellation, and completion through `AuthCapabilityAuthorizer`.

The generic successful authorization result carries the already-upgraded stored OAuth session so specialized adapters do not need a second authorization or token exchange.

New inbox and custom-feed callbacks are routed through the common persisted-owner router.

The predecessor configured inbox and custom-feed capabilities remain available only for ownerless predecessor callback and resume records. Legacy fallback is gated on `authorizationOwner == null` and cannot pre-empt a persisted logical owner.

The previous inbox and custom-feed callback front-runners are retired.

REDDIT01C3B does not broaden or otherwise change Reddit OAuth scopes.

Post-management authorization remains deferred to REDDIT01C3C.
