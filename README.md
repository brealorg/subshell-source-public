# subshell — source

This is the source code of **subshell**, an independent Android client for
Reddit (Kotlin, Jetpack Compose, Material 3, Media3).

- Download the app, read the changelog and get started:
  <https://brealorg.github.io/subshell/>
- Releases, bug reports and feature requests:
  <https://github.com/brealorg/subshell>
- Security issues: <https://github.com/brealorg/subshell/security/advisories/new>

## This repository is read-only

The source is published so anyone can read, audit, build and fork it.
subshell is developed by one person, and **pull requests are not accepted**.
Please report bugs and ideas as issues in
[brealorg/subshell](https://github.com/brealorg/subshell/issues), not here.

## Build

Requirements:

- JDK 17 or newer
- Android SDK with platform 37 (`compileSdk = 37`, `minSdk = 26`)
- Network access to Google Maven and Maven Central for the first build

```bash
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. It installs as
`io.github.brealorg.subshell.debug`, beside the released app.

Once the dependencies are cached, builds can run offline:

```bash
./gradlew --offline --no-daemon --no-configuration-cache :app:testDebugUnitTest
```

Release builds need the owner's signing key, which is not part of this
repository. A release APK built without it is not a subshell release and
cannot update one.

## Signing in to Reddit

No OAuth client identifier is bundled in the source or in any release.
Each reader supplies their own Reddit Client ID in the app; it is stored
encrypted on the device and never leaves it except in requests to Reddit.
The app's onboarding explains how to get one, and so does the
[get-started guide](https://brealorg.github.io/subshell/get-started/).

## Layout

| Path | What it is |
|---|---|
| `app/src/main` | The app. Kotlin namespace `app.subshell.prototype` (historical name). |
| `app/src/test` | Unit, Robolectric and Compose tests — the main safety net. |
| `app/src/androidTest` | Instrumented tests for a device or emulator. |
| `benchmark/` | Feed scrolling benchmarks (Macrobenchmark). |
| `docs/` | Design contracts written while building each part. |
| `tools/` | Contract checkers (`check-*.sh`) and release tooling. |
| `third_party/` | Licences for bundled fonts and store badges. |

The checkers under `tools/` guard boundaries no test expresses (for example,
that the UI never reaches the network layer). Run them through the runner:

```bash
tools/run-checkers.sh --out /tmp/subshell-checkers/progress.txt check-source-contract.sh
```

## Licence

Copyright (C) 2026 brealorg

subshell is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option)
any later version. See [`LICENSE`](LICENSE).

Bundled third-party material keeps its own licence: the code fonts are under
the SIL Open Font License 1.1 (`third_party/fonts/`), and the store badges
follow their owners' brand guidelines (`third_party/branding/`). "Reddit" is
a trademark of Reddit, Inc.; subshell is not affiliated with or endorsed by
Reddit.
