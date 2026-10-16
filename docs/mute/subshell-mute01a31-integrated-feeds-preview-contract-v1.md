# MUTE01A3.1 — integrated Feeds live preview

MUTE01A3.1 changes preview ownership only.

The `Show posts` / `About only` controls remain in the Filtering section of
Feeds & content. The standalone preview previously rendered directly under
those controls is retired.

`SettingsScreen` now passes the selected muted-community direct-view mode into
the existing `FeedsSettingsPreview`, implemented in
`Settings03FeedsPreview.kt`.

That existing top `Live preview` surface renders the same AnimatedContent mute
demonstration alongside the preview's existing feed-state content.

No mute filtering, expiry, account scoping, Communities entry point,
About-only community gating, direct-post deeplink behavior, Reddit transport,
Room schema, or background scheduling changes are made.
