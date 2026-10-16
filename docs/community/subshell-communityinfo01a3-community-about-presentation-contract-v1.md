# COMMUNITYINFO01A3 — Community About presentation

## Purpose

COMMUNITYINFO01A3 turns the A1/A2 data path into a user-facing community context
surface without restructuring the existing feed route.

## Product shape

The accepted community header keeps its existing banner, icon, membership, favorite
and compact public description.

A3 adds one full-width `About community` affordance below that description. Opening
it presents a Material bottom sheet with:

- full Reddit sidebar Markdown;
- ordered subreddit rules;
- moderator list with profile navigation;
- loading state;
- top-level read failure presentation;
- partial-section degradation and retry.

The feed remains the primary Community route and does not reload or reset when the
About sheet opens or closes.

## Loading policy

Community Info is lazy. A3 does not add three Reddit requests to every community
visit.

The A2 loader is called only while the About sheet is open. It uses the active account
stable ID and normalized community name, and ViewModel A2 remains the latest-request
owner.

Retry forces an A2 refresh.

## Failure policy

A2's section-level fail-soft result is honored:

- one failed endpoint does not remove successful content;
- unavailable sections are labeled individually;
- a retry control is offered for missing sections;
- a total failure uses the shared Reddit read error-presentation family.

## Navigation

Moderator rows route through the existing `SubshellState.openUser()` path. A3 does not
invent a parallel user-profile destination.

## Explicit non-goals

A3 does not add:

- wiki or `wikiread`;
- user flair management;
- Room persistence;
- OAuth grants;
- community membership behavior;
- new community feed state;
- Search behavior.

Wiki and user flair remain COMMUNITYINFO01 successors after the base About surface is
runtime-accepted.
