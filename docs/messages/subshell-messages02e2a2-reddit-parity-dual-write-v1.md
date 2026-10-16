# MESSAGES02E2A2 — Reddit-parity Direct Chat dual write

`MESSAGES02E2A2` promotes mixed Direct Chat text + image/GIF from the old fail-closed
`MEDIA_CAPTION_UNSUPPORTED` behavior to the wire protocol observed from Reddit Web.

A single user Send is represented by three ordered network actions:

1. upload the selected media;
2. send the existing `m.room.message` / `m.image` event with `body = "Image"`, `url`, and
   the existing `info` object;
3. only after the image event returns HTTP success with an `event_id`, send a separate
   `m.room.message` / `m.text` event whose `body` is the composer text.

The several-second gap seen in the captured Reddit Web HAR is not treated as protocol behavior.
Subshell inserts no arbitrary delay between the confirmed image write and the text write.

## Failure boundary

The first confirmed `m.image` cannot be rolled back merely because the second write fails.
Therefore:

- failure before image acceptance preserves the original text + selected media draft;
- image accepted + text accepted completes normally and clears both;
- image accepted + definite text failure clears only the media selection, keeps the text draft,
  and presents **Media sent, text not sent**;
- image accepted + uncertain text outcome clears only the media selection, keeps the text draft,
  blocks another send until authoritative refresh/check, and presents
  **Media sent; text may have been sent**;
- no failed or uncertain Matrix write is automatically retried.

The media payload is intentionally unchanged from the previously proven Direct Chat media path.
New-message first-event media remains its existing text-XOR-media scope; E2A2 changes existing
Direct Chat reply composition only.

## Hygiene bundled with E2A2

The eight compiler warnings caused by safe-calling OkHttp's non-null `ResponseBody` are removed
without changing network semantics. `body?.string().orEmpty()` becomes `body.string()` only at the
eight compiler-proven sites.

<!-- SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1 -->
