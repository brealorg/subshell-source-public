# MESSAGES02C6 — Matrix session/cache hardening

Runtime-proven Direct Chat Matrix media remains unchanged at the protocol boundary. This slice
reduces per-send bootstrap cost and makes stale-session handling explicit.

- First-party `/svc/shreddit/token` JWT is cached **in memory only** until five minutes before its
  server-provided expiry.
- Cache reuse requires the same hashed `reddit_session` cookie value; a web-session switch cannot
  reuse the prior account JWT.
- Matrix `/account/whoami` identity and exact two-party room resolution are cached per token/account.
- Direct-room entries have a 30-minute TTL and are additionally bounded by JWT expiry.
- 401/403 invalidates only the token that failed, including its identity/room cache.
- A stale-room 404 clears only that participant room mapping. There is **no automatic resend**.
- The accepted-event `/sync` readback remains. Warm sends avoid the separate room-discovery `/sync`.
- No JWT, room ID, or Matrix bearer is persisted to disk by these caches.
