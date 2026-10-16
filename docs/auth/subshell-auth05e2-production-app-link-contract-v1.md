# subshell AUTH05E2 production App Link contract V1

## Locked public identity

AUTH05E2 binds the signed production package to the published GitHub Pages host:

```text
application_id=io.github.brealorg.subshell
callback_uri=https://brealorg.github.io/oauth/callback
assetlinks_uri=https://brealorg.github.io/.well-known/assetlinks.json
pages_repository=brealorg/brealorg.github.io
pages_commit=3a1d41849a1e69d9e9452d3b3b88c35420f6e586
release_certificate_sha256=52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84
```

The hosted Digital Asset Links statement contains exactly
`delegate_permission/common.handle_all_urls`, the final application ID, and the
owner-managed AUTH05D1/AUTH05D2 release certificate.

## Release-only manifest ownership

`app/src/main/AndroidManifest.xml` remains fail-closed and configuration-gated.
`app/src/release/AndroidManifest.xml` replaces the production callback alias only
for release builds. The release alias is enabled, exported, targets the dedicated
non-exported `AuthCallbackActivity`, and contains exactly one `VIEW` filter with
`DEFAULT`, `BROWSABLE`, `android:autoVerify="true"`, HTTPS, host
`brealorg.github.io`, and exact path `/oauth/callback`.

Debug retains only the isolated `subshell-auth-test://oauth/callback` route. The
production HTTPS alias is disabled in the merged debug manifest. Release does not
contain the debug custom scheme.

## OAuth configuration boundary

The Reddit client identifier remains absent. AUTH05E2 does not begin an OAuth
authorization, exchange a code, refresh a token, revoke a token, or call Reddit.
When a client identifier is supplied in a later phase, AUTH04 validation accepts
only the exact published AUTH05E2 callback URI.

Opening the callback without a pending authorization is a local routing test. The
callback processor rejects it locally and returns to `MainActivity`; it does not
perform a token exchange.

`AuthProductionCallback` is an exported alias whose target is the dedicated
`noHistory` callback activity. The callback can complete before `am start -W`
returns, so Android may report `MainActivity` as the launch result even though the
resolver selected the callback alias. The runtime therefore treats the exact
resolver result as route ownership. An `am start -W` result of the final
`MainActivity` is direct completion proof; when Android reports the transient
callback activity instead, the runtime requires resumed/focused `MainActivity`
evidence before passing.

## Runtime proof

The host runtime:

1. validates the public HTTPS endpoint and exact `assetlinks.json`;
2. reads the owner-managed JKS and two passwords through the established AUTH05D2
   external secret-file boundary;
3. runs the canonical quality gate and builds a release-signed APK;
4. verifies package, certificate, merged release manifest, and debug isolation;
5. runs the AUTH05 production-identity and AUTH05C application-ID host
   regressions against the new build;
6. installs the release package without removing debug or retired prototype
   packages;
7. resets App Link state with `pm set-app-links --package ... 0 all`;
8. invokes `pm verify-app-links --re-verify`;
9. waits for `pm get-app-links` to report `brealorg.github.io: verified`;
10. wakes the test device immediately before routing, resolves and launches the unforced HTTPS `VIEW` intent with explicit `DEFAULT` and `BROWSABLE` categories, requires the resolver to select `AuthProductionCallback`, accepts `am start -W` reporting either the transient `AuthCallbackActivity` or the already-completed final `MainActivity`, treats the final `MainActivity` result as direct completion proof, and otherwise captures complete activity and window dumps without asynchronous or early-closing pipes to prove `MainActivity` through Android 17 top/legacy resumed-activity or focused-window evidence.

The only device network operation introduced by this phase is Android's domain
verification fetch from the published Pages host. There are no GitHub writes,
source publication, Reddit requests, live OAuth credentials, or copied private
key material.
