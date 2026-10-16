# COMMUNITYINFO01A35 — existing UI details affordance

## Intent
Keep the accepted A3.4 product shape (`About | Rules | Moderators`), but make the
About-tab `Community details` expansion control read like the app's existing
list/card language instead of a nearly invisible dropdown.

## Required behavior
- About keeps the public description visible immediately.
- Additional sidebar/about markdown remains behind a single `Community details`
  disclosure row.
- The disclosure row uses explicit `Show` / `Hide` action text alongside the
  chevron icon.
- The row keeps the leading info icon and still expands inline inside the same
  card with a divider.
- Rules and Moderators behavior is unchanged.

## Source markers
`CommunityAboutSheet.kt` must contain:
- `COMMUNITYINFO01A35_EXISTING_UI_DETAILS_AFFORDANCE_V1`
- `COMMUNITYINFO01A35_EXISTING_UI_DETAILS_DISCLOSURE_V1`
- `Show links, resources and additional information.`
- `Hide links, resources and additional information.`
- `if (detailsExpanded) "Hide" else "Show"`

## Regression expectations
- A1/A2/A3/A31/A32/A33/A34 contracts remain wired.
- No new auth scope.
- No Room migration.
- No wiki or user-flair scope.
