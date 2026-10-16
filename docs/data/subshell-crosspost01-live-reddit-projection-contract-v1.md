# CROSSPOST01 — live Reddit crosspost product contract

## Product ownership

The Reddit wrapper remains the canonical outer `Post`.

Its community, author, title, score, comment count, vote/save state, overflow actions
and discussion ownership are never replaced by source-post values.

`crosspost_parent_list` supplies a bounded embedded source preview.

## Rich source card

A usable source preview carries the original post identity and presentation data that
Reddit already included with the wrapper:

- fullname/id and permalink
- source community, author and relative age
- source title and Markdown body
- source flair
- source score and comment count
- normal Reddit media classification and media fields
- spoiler/NSFW state
- external target when the source is itself a link

The source body is bounded in the feed presentation. The outer wrapper remains visually
and behaviorally distinct from the embedded source card.

## Community identity and header density

The embedded source header reuses Subshell's existing community identity pipeline.
It requests the source community identity lazily, renders the cached Reddit community
icon when available, and falls back to the standard `CommunityAvatar` letter identity.

The source community row follows the same compact hierarchy used by ordinary post
cards: community identity first, author and age as secondary metadata. This is
presentation only; the source header does not introduce a new navigation owner.

Source Markdown body preview is capped at four lines in feed/card presentation.

## Media reuse

CROSSPOST01 does not create a second image/video/gallery implementation.

The source parent is normalized through the same Reddit post media projection already
used for normal posts. The embedded card then reuses the existing `PostMedia` pipeline
for image, GIF, video, gallery and link presentation.

This means crossposted media inherits the same loading, sensitive-media, playback and
media-viewer behavior as ordinary posts without duplicating transport logic.

## Nesting

The embedded source projection intentionally has no nested `crosspost_parent_list`.
If Reddit supplies a crosspost whose source was itself a crosspost, Subshell presents
one source layer rather than recursively nesting cards.

## Cache compatibility

The historical four-field `CrosspostPreview` cache payload remains readable.

New writes append richer source fields after the original community/author/title/score
prefix. Existing cached crossposts therefore continue to decode with safe defaults.

## Navigation boundary

The source fullname and permalink are retained now, but the whole embedded source card
does not introduce a new navigation implementation in CROSSPOST01.

Canonical source-post routing belongs to DEEPLINK01 so external Reddit URLs, internal
Reddit links and crosspost-source navigation can share one target resolver.
