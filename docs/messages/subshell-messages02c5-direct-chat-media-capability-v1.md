# MESSAGES02C5 — Direct Chat media capability

## Runtime boundary

- OAuth `/api/comment` with a canonical `t4` parent is retained for text replies.
- Native RTJSON image/GIF publication to a Direct Chat `t4` target was rejected by Reddit at runtime.
- Inbound Direct Chat images/GIFs remain readable and animated.
- Native image/GIF comments remain supported and are not changed by this slice.

## Product policy

Direct Chat is explicitly text-only until Reddit exposes a supported authenticated media-send transport for third-party clients.

- Image and GIF actions are hidden in Direct Chat.
- IME/Gboard rich image content is not advertised or accepted in Direct Chat.
- A Direct Chat media request is rejected before upload or network write as a defense-in-depth boundary.
- The shared post-style Markdown editor and formatting dock remain available.
- Legacy private-message compatibility keeps its previous behavior; this slice is scoped to Direct Chat.

No Matrix token, Reddit web cookie, browser-derived session, or `/api/compose` workaround is introduced.
