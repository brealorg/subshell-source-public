# COMPOSER05B — field validation routing

## Scope

Composer validation must explain why Reddit rejected a submission and route the user to the field that can resolve the problem.

## Policy pre-validation

- `GET /api/v1/{subreddit}/post_requirements` is the primary source for community content controls.
- `is_flair_required` is authoritative when post requirements are available.
- Body minimum and maximum lengths from post requirements participate in local validation.
- The older subreddit `about` metadata remains a fallback when post requirements are unavailable.

## Structured validation

- Local composer validation returns a typed field plus a user-facing message instead of inferring the field from message text.
- Supported destinations are community, title, flair, body, link, media, and general.
- Required flair produces `Post flair is required` before a network submission is started.

## Reddit fallback validation

- Sanitized Reddit submit error codes and fields may cross the network boundary; raw provider messages do not.
- `SUBMIT_VALIDATION_FLAIR_REQUIRED` maps to the flair field and `Post flair is required`.
- Other known submit-validation codes map to title, body, or link where possible.
- Unknown future `SUBMIT_VALIDATION_*` codes still map to the reported field with a safe generic requirement message.
- A field-level validation failure suppresses the generic `Reddit rejected the post` status.

## UX routing

- Flair validation clears text focus, brings the flair control into view, marks it with the Material error role, announces the error through accessibility semantics, and opens the flair picker.
- Link validation brings the URL field into view and focuses it.
- Title validation focuses the title field.
- Body validation brings the editor into view and focuses it.
- Media validation opens the media chooser.
- Community validation opens the community picker.
- A corrected field may be submitted again from a failed validation state without dismissing a generic error first.

All user-facing app strings remain English.
