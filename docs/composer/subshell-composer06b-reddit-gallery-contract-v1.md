# COMPOSER06B — Reddit gallery contract v1

COMPOSER06B extends the authenticated native-media path from one static image to an ordered Reddit gallery while preserving COMPOSER05C preflight, pinned account identity, and duplicate-safe write semantics.

## Supported path

- Two through twenty gallery items selected through Android's system photo picker.
- JPEG, PNG, WebP, and GIF are accepted as gallery items.
- Each staged item is limited to 20 MiB.
- Gallery order is the composer order and is part of the pinned request identity.
- Each item can carry an optional caption up to 180 characters and an optional absolute HTTP/HTTPS outbound URL.
- Optional Markdown body text remains part of the Reddit gallery post.
- Flair, NSFW, spoiler, send-replies, community requirements, and COMPOSER05C preflight remain part of the same request.
- Single-image COMPOSER06A behavior is unchanged. Single GIF and video upload remain separate future phases.

## Community capability

- The Reddit community policy loader reads `allow_galleries` from the subreddit about payload.
- MEDIA remains available when the community allows a single image, a gallery, or video.
- A loaded policy with galleries disabled fails locally with `This community doesn't allow gallery posts` before any media lease or upload begins.
- When the provider omits `allow_galleries`, the policy falls back to the existing image capability instead of falsely disabling an otherwise valid gallery path.

## Gallery transport

The gallery transport follows Reddit's media-asset lease model used by current public OAuth clients:

1. Validate the complete gallery and stage every local item before any network upload begins.
2. Request `POST /api/media/asset.json` for each staged item with its file name and MIME type.
3. Require the sanitized `asset.asset_id` returned by Reddit for each gallery item.
4. Upload each staged file to the HTTPS S3 lease action with every lease field preserved.
5. Preserve the resulting asset IDs in composer order and construct ordered items containing `media_id`, `caption`, and `outbound_url`.
6. Submit the gallery as JSON to `POST /api/submit_gallery_post` with `api_type=json`, `show_error_list=true`, `validate_on_submit=true`, title, optional body, flair, NSFW, spoiler, and reply preference.
7. Accept only a Reddit-owned HTTPS result URL whose canonical comments path yields the created post identity.

No browser cookie, private first-party session, or automatic post retry is introduced.

## Failure and duplicate-safety policy

- Unsupported, unreadable, empty, or oversized local items are rejected before the first media lease request and identify the failing item number.
- Missing or malformed gallery `asset_id` values fail closed.
- Lease and binary-upload failures occur before gallery submission and may be retried according to their existing transport classification.
- Once `/api/submit_gallery_post` has been sent, timeout, IO loss, 5xx response ambiguity, malformed response, or other indeterminate completion is `OUTCOME_UNKNOWN`; the client must not automatically repeat the post.
- Structured gallery/image/media validation errors route to the composer MEDIA field.
- Local staged files are deleted after success or failure.

## Composer UI and persistence

- The system picker accepts multiple images and the composer displays them as a paged gallery preview.
- The active item can be moved earlier or later without reselecting media.
- The active gallery item exposes an optional caption and a separate `+ Add link` control for an optional outbound link.
- Captions and outbound URLs survive draft storage and process-state restoration.
- Request identity changes when gallery order, caption, outbound URL, URI, or MIME type changes.
- While the coordinator owns the write, the status reads `Uploading gallery to Reddit…`.
- A successful submission opens an immediate `PostKind.GALLERY` using the ordered local URIs and Reddit upload URLs while canonical feed refresh remains the long-term source of truth.

## Success gate

COMPOSER06B is accepted only when source contracts, unit tests, debug assembly, DEV2 installation, and an authenticated device post of an ordered multi-image gallery all succeed. The device proof must confirm selection, reorder/preview, preflight, upload, created-post navigation, and gallery rendering after returning to the community feed.
