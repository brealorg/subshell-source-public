# WRITE01-A3A — typed comment draft state

WRITE01-A3A moves comment-composer identity and draft ownership out of transient
composable state and into `SubshellState`.

## Contract

- A reply target retains the stable post ID and, when present, the exact comment
  ID and author.
- Reddit-compatible targets expose a validated `t3_` post fullname or `t1_`
  comment fullname. Local fixture IDs remain valid draft targets without being
  misrepresented as remote Reddit fullnames.
- Body, Plain/Markdown mode, image/GIF attachment metadata, visibility and
  target survive configuration and process-state restoration.
- Closing the editor preserves the draft.
- Discarding is explicit.
- An unsent draft cannot be silently retargeted to another comment.
- Local fixture publishing retains the existing local-only mutation boundary.
- The app UI remains English.

## Boundary

This checkpoint does not invoke `AuthenticatedCommentSubmitter`, request comment
write authorization, contact Reddit, write Room after a remote submission, or
create a remote comment. Those operations remain assigned to WRITE01-A3B and
the explicit controlled live proof remains assigned to WRITE01-A3C.
