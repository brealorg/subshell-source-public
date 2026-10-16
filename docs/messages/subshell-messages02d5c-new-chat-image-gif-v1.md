# MESSAGES02D5C — initial Direct Chat image/GIF

Successor scope for the runtime-proven D5 new-Direct-Chat checkpoint.

## Product boundary

The first Direct Chat event may now be exactly one of:

- text; or
- one image/GIF.

Mixed text + media is deliberately rejected because the existing Direct Chat Matrix
media path does not support captions. Video remains out of scope.

## Reuse

D5C reuses the already-proven C5/C6 Direct Chat media stack:

- the existing Android image/GIF picker and `ComposerMediaAttachment`;
- `ComposerInlineMedia` and the existing single-media replacement dialog;
- `RedditChatMatrixMediaWriter`;
- the first-party Reddit Web Matrix token;
- the shared verified Matrix identity and participant-to-room cache;
- Matrix media upload followed by `m.room.message` / `m.image`;
- non-retrying write behavior and `OUTCOME_UNKNOWN` for an ambiguous send.

No second Direct Chat media transport is introduced.

## New-room safety

Media-only first messages are staged locally before any Direct Chat network mutation,
and therefore before `createRoom`. Unsupported, unreadable, empty, or oversized media
fails before a new remote room/invite can be created.

The staged file is then handed to the existing Matrix media writer after the room has
been found or created and cached. The create and send write boundaries are not blindly
retried.

## GIF staging

Direct Chat media preparation uses the existing GIF-capable `stageGallery()` boundary.
It accepts the existing JPEG/PNG/WebP formats plus `image/gif`; video is still excluded.

## UI

The accepted fullscreen Material 3 Expressive `New message` route is preserved. After
recipient selection it gains Image and GIF actions, the existing local media preview,
and the existing single-media replacement dialog.

Text and media remain mutually exclusive for the first event.

## Fast-loop gates

Run the D5A transport-preservation contract plus the D5C contract and targeted D5A/D5C
tests. Do not run the global source contract.

D5B/D5B2 predecessor contracts intentionally retain their historical text-only first-send
scope and are not successor fast-loop gates after D5C.

<!-- SUBSHELL_MESSAGES02D5C_RUNTIME_PROOF_20260829 -->
## Runtime verification

On 2026-08-29, DEV1 runtime verification proved the initial-media Direct Chat path.

A new-chat media send produced the full internal chain:

- `stage=directory result=accepted`
- `stage=room result=created`
- `subshell-matrix-media: result=accepted`
- `stage=media result=accepted`

The accepted Matrix media trace included `identity_cache=true` and `room_cache=true` during
the upload/send phase after the room had been created and cached. The runtime-proven DEV1
APK SHA256 was
`c9dee7a8a09fbef84f382dff33d75ccc038165591b089d87e842ecd0a7367723`.

Both image and GIF behavior were manually verified. This closes D5C for the supported
first-event media scope: text XOR one image/GIF, with no video and no mixed text+media.
