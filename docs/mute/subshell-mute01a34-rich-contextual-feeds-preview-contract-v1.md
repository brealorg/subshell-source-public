# MUTE01A3.4 — rich contextual Feeds preview

The contextual preview model remains: the last supported setting changed
chooses the preview scene.

A3.4 replaces A3.3's undersized 72 dp status-like preview with one fixed
176 dp demonstration workspace, matching the visual role of the existing
Navigation & gestures and Appearance previews.

The preview header remains stable:
- `Live preview`
- `Feed and community behaviour.`
- a compact state pill such as `Unread on`, `NSFW hidden`, or `About only`.

Scenes:
- unread marker: a mini Home feed with two posts and a visible/hidden unread dot;
- NSFW visibility: a mini Home feed showing an NSFW card or an explanatory
  `NSFW post hidden` replacement;
- muted-community direct visit: a mini community page with top bar, community
  identity, member count, Joined state, Muted status, and either posts or
  `Posts hidden while muted`.

The workspace keeps the same height while scenes crossfade.

A3.3's presentation-specific test/contract/doc are retired because the 72 dp
scene was rejected during visual review.

No mute filtering, expiry, persistence, community-page behavior, Communities
entry point, direct-post deeplink behavior, Reddit transport, Room schema, or
background scheduling changes are made.
