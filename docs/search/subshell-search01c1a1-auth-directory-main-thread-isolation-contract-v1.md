# SEARCH01C1A1 — auth directory main-thread isolation contract v1

## Incident

A runtime ANR on Pixel 6 / Android 17 reported `Input dispatching timed out` while the
Compose main thread was blocked for more than six seconds in
`AndroidKeystoreAesGcmCipher.decrypt()` while
`rememberSubshellState()` called `AccountSessionDirectory.snapshot()` during
recomposition.

The observed stack was:

`SubshellApp -> rememberSubshellState -> AuthAccountSessionCoordinator.snapshot ->
registry -> EncryptedAuthSessionStore.accountRegistry -> Android Keystore -> Binder`.

## Contract

SEARCH01C1A1 makes the account-session directory a memory-backed UI boundary.

- `AuthAccountSessionCoordinator.snapshot()` is storage-free and returns only the
  current in-memory snapshot.
- Coordinator construction does not read or decrypt the account registry.
- Initial registry/session hydration is dispatched through the coordinator's IO
  storage dispatcher.
- Hydration publishes the directory revision after the memory snapshot is ready,
  allowing existing Compose revision observation to synchronize the UI.
- The AUTH06C compatibility-cache migration runs from the completed async hydration,
  not from a synchronous factory-time `snapshot()` read.
- Account selection updates the in-memory active account synchronously, while registry
  persistence is dispatched off the UI thread. A persistence failure rehydrates from
  storage and republishes the authoritative directory.
- Access-token refresh, capability-session replacement, logout storage work, and final
  account-registration persistence use the IO storage dispatcher.
- Existing AUTH08A2 account-health/reconnect semantics and SEARCH01C1 federation
  semantics remain predecessors and must continue to pass.

## Explicit boundary

This contract addresses the demonstrated account-directory/session-snapshot ANR path.
It does not claim that every historical auth-storage call outside the coordinator has
already been converted to an asynchronous API.

## Runtime regression proof

`Auth02AccountSessionInstrumentedTest.coldDirectorySnapshotDoesNotDecryptBeforeAsyncHydration` uses an instrumented counting cipher to prove that coordinator construction and a cold `snapshot()` perform zero decrypt operations; explicit asynchronous `hydrateDirectory()` must then perform the decrypt. This guards the exact UI/main-thread boundary that caused the observed ANR.
