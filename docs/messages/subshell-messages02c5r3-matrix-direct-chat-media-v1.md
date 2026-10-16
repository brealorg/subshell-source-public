# MESSAGES02C5R3 — Matrix Direct Chat media

Direct Chat outbound image/GIF no longer uses Reddit `/api/comment` richtext against a `t4` target.
That compatibility path was runtime-rejected by Reddit.

C5R3 uses the first-party Reddit Chat path proven from Reddit Web and an isolated capability probe:

1. Reuse the logged-in reddit.com WebView session and mint the first-party JWT with
   `POST /svc/shreddit/token` inside a reddit.com-pinned WebView.
2. Verify the JWT against `matrix.redditspace.com/_matrix/client/v3/account/whoami` and require the
   Matrix user ID to match the active authenticated Reddit account ID.
3. Resolve the two-party Matrix room by exact participant identity from `/sync` member state, with a
   bounded `/members` fallback. Ambiguous or missing rooms fail closed.
4. Stage JPEG/PNG/WebP/GIF locally and upload once to `/_matrix/media/v3/upload`.
5. Send exactly one `m.room.message` event with `msgtype=m.image`, an `mxc://reddit.com/...` URI and
   width/height/mimetype/size metadata.
6. Never blindly retry a room-message write after an unknown transport outcome.
7. Preserve the existing `t4 /api/comment` compatibility writer for text replies and the existing
   Reddit richtext path for legacy private-message media.

Initial C5R3 intentionally requires Direct Chat media to be sent without accompanying text. Text and
media can be sent separately until Reddit Web caption semantics are captured and proven.
