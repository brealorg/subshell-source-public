# SAFETY01A1 — typed Reddit content-safety request boundary

## Product boundary

SAFETY01 replaces Subshell's local-only post Hide action and preview-only Report action with authoritative Reddit operations.

A1 is intentionally transport-adjacent but does **not** execute a write yet. It establishes one typed request boundary for the three Reddit safety operations needed by later phases:

- post Hide: `POST /api/hide`;
- post Unhide: `POST /api/unhide`;
- content Report: `POST /api/report`.

Reddit Hide/Unhide is post/link-only (`t3`). Report accepts posts (`t3`) and comments (`t1`). Direct comment Hide/Unhide is not part of this contract.

## Existing Subshell ownership preserved

The existing You → Hidden history remains the authoritative Reddit account-history listing (`/user/{username}/hidden`). A1 does not introduce a parallel Hidden history engine, Room table, cache namespace, or local mutation replay.

The current account-local `hiddenPostIds` behavior is not removed in A1. Its authenticated runtime ownership will be reconciled in a later SAFETY01 phase after the owner-bound writer exists.

## Request semantics

`RedditPostVisibilityRequest` requires an account ID, canonical `t3_` fullname, and desired hidden state. The request builder maps it to `/api/hide` or `/api/unhide` with the `id` form field.

`RedditContentReportRequest` requires an account ID, explicit canonical `t1_` or `t3_` fullname, and a non-empty report reason. Optional Reddit report metadata is bounded at the API limits:

- reason / rule_reason / other_reason / site_reason: 100 characters;
- additional_info / custom_text: 2000 characters;
- sr_name: 1000 characters.

The report builder sends `api_type=json`. The deployed target field is isolated as `id`, matching current PRAW's live `/api/report` request shape even though Reddit's legacy documentation currently labels the corresponding target field `thing_id`. Keeping that choice in one builder lets a future Reddit correction stay local to the network boundary.

## OAuth and execution are deferred to A2

All three operations are documented under Reddit OAuth scope `report`. The current Subshell daily-driver scope set does not contain `report`.

A1 therefore makes **no OAuth scope mutation** and creates no authorizer, callback route, writer, or network execution. SAFETY01A2 will add a dedicated owner-bound content-safety capability, non-retrying writer, and authoritative Hidden readback/reconciliation. Historical auth gates must become successor-aware in that phase rather than being silently widened here.

## Failure and reconciliation policy reserved for A2

Hide/Unhide must not infer success merely from whether a refreshed feed visually filters the post. The write path will use Subshell's non-retrying mutation transport and reconcile against the existing authoritative `/user/{username}/hidden` history.

Report is also a write and must surface structured Reddit/API failures. Reddit documents that reporting posts/comments implicitly hides the reported thing; the later runtime phase must reconcile that side effect with Hidden history rather than introducing a second local truth.

## A1 exclusions

- no UI mutation;
- no OAuth scope mutation;
- no Room schema migration;
- no write execution;
- no new Hidden history engine;
- no direct comment Hide/Unhide;
- no remote operations, checkpoint, or landing.
