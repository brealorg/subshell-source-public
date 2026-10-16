# COMPOSER06A — Reddit image upload contract v1

COMPOSER06A adds the first authenticated native-media write path to the composer. It is deliberately limited to one static image so gallery, GIF, and video can be added as separate follow-up phases without weakening the submit safety boundary.

## Supported path

- One image selected through Android's system photo picker.
- JPEG, PNG, and WebP only.
- Maximum staged size is 20 MiB.
- Optional Markdown body text is preserved with the image post.
- Existing flair, NSFW, spoiler, send-replies, community requirements, and preflight validation remain part of the same pinned request identity.
- Gallery, GIF, and video remain explicitly unavailable for authenticated Reddit posting in this phase.

## Reddit transport

The implementation follows the same public-OAuth-compatible media sequence used by current PRAW:

1. Stage the selected content URI into a bounded private cache file.
2. Request an upload lease with `POST /api/media/asset.json` using `filepath` and `mimetype`.
3. Upload the file with multipart form data to the HTTPS S3 action returned by Reddit, preserving every lease field and adding the file last.
4. Construct the uploaded-media URL from the lease action and returned `key`. A gallery-style `asset_id` is not required for this single-image link upload path.
5. Submit through `POST /api/submit` with `kind=image`, `resubmit=true`, `validate_on_submit=true`, the uploaded media URL, title, optional body text, flair, NSFW, spoiler, and reply preference.
6. Treat `websocket_url` from the submit response as the authoritative completion channel for native media creation, even if provisional post identity fields are also present.
7. Resolve only Reddit-owned HTTPS post redirects from the WebSocket success message.

No private Reddit GraphQL endpoint, browser cookie, or first-party session is used.

## Failure and duplicate-safety policy

- Unsupported, unreadable, empty, or oversized local media is routed to the composer MEDIA field before Reddit submission.
- S3 lease and upload failures never expose provider response bodies to UI state.
- A structured Reddit media validation error is routed back to the media card with an actionable message.
- A WebSocket `failed` result is treated as a media validation failure.
- A WebSocket timeout or transport loss after `/api/submit` is `OUTCOME_UNKNOWN`. The client must not automatically repeat the post submission because Reddit may already have created it.
- Network clients used for media write operations disable automatic connection retries.

## UI contract

- A valid single static image passes authenticated composer validation.
- While the write coordinator owns the media submission, the primary action reads `Uploading…`.
- A MEDIA validation error scrolls the media card into view, gives it Material error treatment, exposes error semantics, and opens the media chooser so the user can replace the image.
- A successful native image submission opens the created post with the local URI retained for immediate rendering and the leased Reddit upload URL retained as a network fallback until canonical listing refresh replaces it.
- While the media write is active, the composer reports `Uploading image to Reddit…` rather than presenting the transfer as a completed post submission.

## Success gate

COMPOSER06A is accepted only when source contracts, unit tests, debug assembly, DEV2 installation, and an authenticated device upload of one static image all succeed.
