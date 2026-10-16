# REDDIT01C3C1 authorization context foundation v1

REDDIT01C3C1 adds an optional persisted authorization context beside the existing logical authorization owner.

`authorizationOwnerKey` identifies the logical consumer.

`authorizationContextKey` identifies an optional authorization sub-context inside that consumer.

Both pending authorization and stored callback records default the context to null, preserving predecessor records.

OAuth callback processing copies the context from pending authorization into the stored callback.

OAuth session completion validates provider, client, scope, account, logical owner, and authorization context before exchanging or accepting the callback.

Authorization context keys are normalized, bounded, and restricted to lowercase URL-safe identifier characters used by Subshell routing.

A context cannot be created by `OAuthSessionManager` without an authorization owner.

`AuthContextualCapabilityAuthorizer` extends the existing generic authorizer without changing the predecessor `AuthCapabilityAuthorizer` API. Existing consumers continue using owner-only authorization with a null context.

The authorization resolver exposes a typed owner-plus-context route and fails closed when pending and callback routes disagree.

REDDIT01C3C1 does not persist `PostManageMutationRequest` or any destructive write payload. The context is authorization routing metadata only.

No Reddit OAuth scope is added, removed, or broadened by REDDIT01C3C1.

POST_MANAGE consumer migration remains deferred to REDDIT01C3C2.
