# SEARCH02A1.1 — Personalized zero-query communities contract v1

SEARCH02A1.1 replaces the authenticated Search hub's arbitrary catalog-prefix community suggestions with account-relevant signals.

## Product policy

- `Recent communities` remains a separate, higher-context surface and is excluded from `Suggested communities`.
- Authenticated `Suggested communities` accepts only personal signals: favorites first, then interaction-index evidence, then joined communities.
- A generic catalog row without any personal signal is not eligible on an authenticated account. If no personal community signal exists, the section is absent rather than showing arbitrary alphabetical communities.
- Unauthenticated/local-fixture Search may retain a bounded generic fallback.
- Community identity is normalized case-insensitively, including optional `r/` and `/r/` prefixes, so `r/Achievements` and `r/achievements` are one suggestion.
- At most four zero-query community suggestions are rendered.
- The feature reuses SEARCH02A1 interaction metadata and existing favorites/joined state. It adds no Reddit endpoint, no Room migration, no cached Reddit result page, and no remote telemetry.

## Ranking

For authenticated Search the deterministic signal tiers are:

1. favorite
2. interaction index
3. joined

Within a tier, interaction boost is descending and community name is the deterministic final tie-breaker.

## Regression boundary

SEARCH01 search submission, provider dispatch, People autocomplete, Posts paging, exact Back/restore semantics, and SEARCH02A1 account-scoped persistence remain unchanged.
