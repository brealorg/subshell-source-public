# IMAGE-01 Reddit-shaped sensitive media lab

The DEV2 sensitive-media lab is a deterministic integration surface, not a replacement for a later
live Reddit smoke test.

## Input boundary

The debug fixture provider begins as raw Reddit Listing JSON containing the provider fields
`over_18`, `spoiler`, `post_hint`, `is_video`, `secure_media.reddit_video`, `is_gallery`,
`gallery_data`, and `media_metadata`. URL tokens are expanded only inside the debug source set.

The JSON must pass through the production serialization and mapping path:

```text
Reddit Listing JSON
→ RedditListingDto
→ toNetworkPage()
→ Post
```

No test post may set `Post.nsfw`, `Post.spoiler`, or `Post.kind` directly.

## Deterministic media

After mapping, remote URLs are replaced with bundled media assets while the mapped identity,
sensitivity flags, and media kind remain unchanged. This keeps visual tests deterministic and avoids
depending on Reddit availability or incidental NSFW content.

Mapped Reddit gallery entries retain `MediaFormat.IMAGE` and therefore use static bundled image
assets. Animated GIF fixtures must not be assigned to those entries because `GalleryTile` renders
image-format assets through `StaticMedia` and Compose `painterResource`.

## UI boundary

A neutral `data` bridge owns the production Reddit mapper call. UI code consumes only `List<Post>` and
never imports `data.network`. The lab renders those posts with the production `PostCard` and `PostMedia`
composables and the active `SubshellState`, exercising NSFW visibility, all three preview modes, reveal
reset, and the sensitive autoplay gate.

## Exposure

The Settings entry is visible only when both conditions are true:

- `BuildConfig.DEBUG`
- `BuildConfig.DEBUG_SLOT == "dev2"`

DEV1 and release builds do not expose the lab.
