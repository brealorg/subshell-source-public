# subshell MEDIA-HAND-01 interaction-side media controls contract v1

MEDIA-HAND-01 applies the persisted Interaction side to secondary media actions
without mirroring media content or semantic navigation.

## Follow Interaction side

- Inline video sound and full-screen controls move as one top action cluster.
- Full-screen is the outermost thumb action on either interaction edge.
- Sound is immediately inward from full-screen; Playback options is inward from sound when present.
- Full-screen video Playback options, sound, and full-screen exit use the same cluster.
- Automatic video quality remains available from Playback options; the inline quality badge is intentionally omitted.
- Inline gallery full-screen moves to the interaction side.
- The gallery counter moves to the opposite corner to avoid overlap.
- The expanded full-screen touch target remains 64 × 56 dp with a 48 dp visual control.
- Safe-drawing insets, control auto-hide, click behavior, player ownership, and playback state are unchanged.

## Fixed semantics

Semantic Back and modal navigation stay at the logical start/end positions.
The image/GIF/gallery viewer Back action, video Back action, playback-panel Back,
playback-panel Close, timeline, time labels, centered play/pause/retry/replay,
other media badges, gallery paging, subtitles, and content layout do not follow Interaction side.
