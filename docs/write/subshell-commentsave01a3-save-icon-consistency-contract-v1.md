# COMMENTSAVE01A3 — comment save icon consistency

## Product intent

COMMENTSAVE01A2 established live Reddit save/unsave for comments. A3 is a presentation-only polish successor: the comment overflow action now uses the same bookmark identity already used by post Save and the Saved destination.

## Contract

- The existing comment overflow remains the owner of `Save comment` / `Unsave comment`.
- Unsaved comments use `Icons.Rounded.BookmarkBorder`.
- Saved comments use `Icons.Rounded.Bookmark` and the active primary tint, matching the existing post-save visual language.
- The menu text remains explicit; the icon supplements rather than replaces the action label.
- No inline comment action-row button is added in A3.
- COMMENTSAVE01A1/A2 transport, optimistic state, rollback, error presentation and authoritative mixed Saved history remain unchanged.
- No OAuth scope or Room schema change is introduced.

## Runtime acceptance

1. An unsaved comment overflow shows the outline bookmark beside `Save comment`.
2. After saving, the same action shows the filled/active bookmark beside `Unsave comment`.
3. Save/unsave behavior and post Save remain unchanged from accepted A2 runtime behavior.
