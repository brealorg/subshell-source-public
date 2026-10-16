# subshell COMPOSER-04 fixed media placement contract v1

COMPOSER-04 removes the abandoned arbitrary inline-position model from the local
post and comment composers.

## Placement

Media is rendered above one continuous text body in the editor and in Markdown
preview. A draft supports at most one image or GIF. Existing remove and replace
actions remain unchanged.

The model no longer stores a text insertion offset, does not split the draft
into before/after text fields, and does not move media as the cursor moves.

## Restore compatibility

Process snapshot version `v1` remains readable. New snapshots do not write
`mediaOffset`. The decoder continues to recognize and discard a legacy
`mediaOffset=` field so previously saved drafts restore with their attachment
fixed above the unchanged body text.

## Preserved behavior

- Plain and Markdown modes remain available.
- Markdown source and preview remain available.
- Image/GIF picker, pasted media, remove and replace remain available.
- Post drafts continue to survive close and process recreation.
- Local post and comment publishing remains unchanged.
- Authenticated Reddit accounts remain read-only.
