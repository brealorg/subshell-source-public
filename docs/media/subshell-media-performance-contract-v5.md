# subshell Media Performance Contract v5

## Scope

MEDIA-PERF-01 phase 2.3 defines direction-aware video handoff for mixed-height feeds.
It preserves scroll-time preload, one-player ownership, audio focus, manual pause and the
60/35/1 compatibility model.

## Direction state

The feed records the last meaningful vertical movement as `UP`, `DOWN` or `NONE`.
Playback still changes only after the Compose list reports that drag and fling have ended.
The last direction remains available for the settled arbitration pass.

## Signed surface geometry

Every video surface reports both:

- absolute distance from viewport centre; and
- signed centre offset, where negative means above centre and positive means below centre.

Fraction-only compatibility callers have no signed position and therefore cannot use the
directional handoff path.

## Large-to-small handoff

After downward scrolling, a qualified next video may replace the active video when:

1. the active surface centre is at least `0.10` viewport heights above centre;
2. the next meaningful video in feed order is qualified by normal visibility, centre band,
   or terminal visibility; and
3. the viewport is settled.

The rule is mirrored when scrolling upward. It intentionally overrides the 35 percent
retention floor only for the next meaningful item in the actual travel direction.

## No skipping

The first meaningfully visible video in the travel direction blocks all later videos.
A terminal video therefore cannot produce `F -> H` while G is entering the viewport.
The intended lab order is `F -> G -> H` downward and `H -> G -> F` upward.

## Terminal latch

Once the terminal video owns playback at the bottom, it remains active while direction is
`DOWN` or `NONE`. It may yield only after an actual upward direction change and normal
upward handoff qualification. This prevents G/H ping-pong while parked at the bottom.

## Compatibility

Calls through the fraction-only `decide()` entrypoint retain the established 60/35/1 model.
Manual playback, manual pause suppression, background resume, preload and poster-until-first-
frame behaviour remain unchanged.

## Runtime acceptance

For each natural A-to-H-to-A lab run:

- downward committed order must be B, C, D, E, F, G, H;
- upward committed order must be G, F, E, D, C, B, A;
- no `F -> H` transition is allowed;
- no G/H ping-pong is allowed;
- D -> E, F -> G and G -> H must be `directional_takeover` transitions;
- candidate-to-first-frame remains measured separately from arbitration latency.
