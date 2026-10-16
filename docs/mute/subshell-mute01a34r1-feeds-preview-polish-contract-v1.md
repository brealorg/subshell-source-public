# MUTE01A3.4R1 — Feeds preview polish

This is a polish-only successor to the accepted MUTE01A3.4 rich contextual
Feeds preview.

Changes:
- shorten the Live preview subtitle from
  `Feed, filters and community behaviour.` to
  `Feed and community behaviour.`;
- in the muted-community `Show posts` scene, render exactly one complete
  preview post;
- remove the second `Another community post` preview so the mini-community
  viewport finishes cleanly rather than suggesting clipped continuation.

Explicitly preserved:
- one rich contextual preview scene;
- last-changed-supported-setting focus;
- Crossfade scene transition;
- fixed 176 dp workspace;
- state pill;
- mini Home feed for unread;
- mini Home feed for NSFW;
- mini direct-community scene for mute;
- `About only` presentation and copy.

No mute filtering, expiry, persistence, community-page behavior, Communities
entry point, direct-post deeplink behavior, Reddit transport, Room schema, or
background scheduling changes are made.
