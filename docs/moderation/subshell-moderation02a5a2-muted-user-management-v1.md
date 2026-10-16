# MODERATION02A5A2 — muted user management

A5A2 extends the existing Mod Tools → User management R4 single-sheet workspace
with a second Banned / Muted segmented surface.

Reddit contract:

- `GET /r/{subreddit}/about/muted`
- mute: `POST /r/{subreddit}/api/friend` with `type=muted`
- unmute: `POST /r/{subreddit}/api/unfriend` with `type=muted`
- OAuth scope: `modcontributors`
- optional moderator note: maximum 300 characters

Muting affects modmail. The UI does not promise a permanent duration; Reddit
owns mute lifetime semantics. A5A2 does not send ban duration, ban reason, or
user-facing ban-message fields.

Safety:

- account and subreddit are pinned
- self-targeting is blocked
- mute/unmute shares the A5A1 mutation mutex
- fresh preflight read before one non-retrying write
- authoritative muted relationship readback after the write
- ambiguous transport outcome is not blindly retried
- authorization completion reloads only; it never replays a write

Deferred:

- approved-user / contributor management
- moderator invites, removal, or permission editing
- wiki relationship management
- custom mute-duration scheduling
- modmail-conversation-specific temporary mute controls
