# Subshell composer rewrite v3

This document is the source contract for the rewritten Create Post flow.

## Main flow

- The top app bar contains Back, a visible `Discard` action, and the primary `Post` action.
- Opening Create starts a new draft. Back archives non-empty work. Drafts are reopened or deleted from `Library → Drafts`.
- The primary post types are `Text`, `Link`, and `Media`. Poll creation is absent from the composer domain, UI, validation, persistence, and tests.
- Media offers Photos and Video. One photo is an image post, multiple photos/GIFs are a gallery, and GIF is detected from the selected media. A gallery cannot mix video with photos.

## Community rules and flair

For connected Reddit accounts the composer loads:

- `/r/{community}/about` for submission type, media permissions, and required flair;
- `/api/v1/{community}/post_requirements` for title and body rules;
- `/r/{community}/api/link_flair_v2` for selectable and editable post flair.

Changing community clears the selected flair. Reopening a draft reloads policy, and a template that is no longer available is removed. A required flair blocks Post until selected. Submission sends `flair_id` and `flair_text`.

## Editor

- `Post title` is an outlined Material field. Its counter appears only near the active limit.
- `Post body` shows `Required` only when the community requires it; there is no static `Optional` label.
- Native selection handles long-press selection. The formatting row applies Reddit Markdown to the active selection.
- Compact layouts switch between Write and Preview. Layouts at least 840 dp wide show source and preview together.
- Media preview remains above the single continuous body. Gallery items can be reordered or removed.

## Options and submission safety

NSFW, spoiler, and reply notifications are stored with the draft, included in the deterministic request identity, and sent to `/api/submit`. Link posts use `kind=link`; text posts use `kind=self`. The pinned account and full request key prevent an edited or account-switched draft from being cleared by an earlier success.

Connected-account media upload deliberately fails closed until the Reddit asset-upload sequence has its own verified transport. Media selection, persistent URI handling, gallery/video draft storage, preview, local publishing, and validation are implemented; the UI does not pretend an unsupported remote upload succeeded.
