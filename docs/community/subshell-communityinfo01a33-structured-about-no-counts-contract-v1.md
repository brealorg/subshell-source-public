# COMMUNITYINFO01A3.3 — structured About without tab counts

## Runtime correction

A3.2 established the accepted Material information architecture and moderator identity
presentation. Runtime review accepted Rules and Moderators, but exposed two remaining
product issues:

1. `Rules 8` / `Moderators 18` adds no useful decision information to the tabs.
2. About must remain, but clipping arbitrary raw sidebar Markdown to eight rendered
   lines and appending `Show more` produces broken headings, half sentences and a poor
   information hierarchy.

## Final A3.3 surface

The sheet retains all three destinations:

- About
- Rules
- Moderators

No tab includes an item count.

## Structured About behavior

About no longer truncates raw Markdown by character/newline thresholds or an arbitrary
rendered line count.

Instead it has two deliberate layers:

1. The community's compact public description is immediately visible in a Material
   surface.
2. When Reddit publishes richer sidebar Markdown, a collapsed `Community details`
   ListItem is shown with a chevron. Expanding it reveals the full original Markdown,
   including links and resources, without rewriting or summarizing moderator content.

This keeps About useful without turning its default state into a wall of text or
cutting semantic Markdown structures in half.

## Preserved behavior

A3.3 does not change the accepted A3.2 behavior for:

- rule accordion rows
- moderator avatars/snoovatars and identity hydration
- bounded visible-row moderator profile hydration
- moderator profile navigation and return to the selected sheet tab
- community feed position after sheet dismissal
- lazy community-info loading
- fail-soft section errors and retry

## Scope boundary

No OAuth scope change, Room migration, wiki implementation, user-flair mutation,
Search behavior change or DEV1 mutation is introduced.
