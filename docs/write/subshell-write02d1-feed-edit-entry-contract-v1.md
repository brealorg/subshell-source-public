# WRITE02D1 feed edit entry

WRITE02D1 exposes the existing WRITE02D self-post editor from the standard PostCard overflow menu.

For an authenticated active account, an owned `PostKind.TEXT` card shows `Edit post` on feed, community, search, library, and user-post surfaces that use the canonical PostCard. Non-owned posts and non-text posts remain unchanged.

When the user selects `Edit post` outside the full post screen, PostCard records a one-shot edit intent, executes the existing navigation-state capture, and opens the post through its normal origin-aware route. PostScreen consumes that intent and enters the existing WRITE02D editor immediately.

The editor remains body-only. WRITE02D1 does not broaden Reddit write scope, edit media posts, or add deletion.

After the editor closes or saves, the normal post route remains active. Back navigation therefore returns through the existing route stack and preserves the originating feed scroll state.
