# COMPOSER05 Reddit post flair contract v1

COMPOSER05 makes Reddit post flair a verified composer capability rather than a passive draft field.

## Policy and authorization

- Connected composers load community submission policy from Reddit's canonical OAuth `/r/subreddit/about` route and the canonical post-requirements endpoint.
- Flair-capable communities load selectable templates from the community link-flair endpoint.
- The optional OAuth `flair` grant is requested only when templates are available or required and the current token does not already grant it.
- The flair grant remains a strict superset of the read and submit grants, and stored sessions remain valid when their token grants a superset of the runtime configuration scopes.

## Draft behavior

- Changing community clears the selected flair.
- Opening a different draft reloads community policy even when the community name is unchanged.
- A selected template is reconciled against the refreshed template list. Missing templates are removed.
- Custom text survives refresh only while both the stored selection and refreshed template remain editable and the text stays within the supported limit.
- Required flair blocks the actual submission until a valid template is selected.
- The Post action remains available once policy is ready so validation can explain the missing requirement instead of silently disabling the action.
- Submitting without required flair scrolls to the flair row, marks it as an error, shows `Post flair is required`, and opens the flair picker automatically.

## Submission

- Text and link submissions send `flair_id` and normalized `flair_text` through the pinned request identity and `/api/submit`.
- The created post receives the submitted flair label for immediate post-screen and feed rendering.

## Verification

- Unit tests lock the canonical about route, OAuth scope-superset session matching, the remaining policy request paths, flair DTO mapping, template reconciliation, stale-template removal, editability changes, and text bounds.
- The source contract locks the OAuth capability, policy reload, picker UI, actionable required-flair validation, and submit fields.
