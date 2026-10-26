# SECAUDIT04 — Gradle dependency verification

`gradle/verification-metadata.xml` and `gradle/verification-keyring.keys` make
Gradle verify every plugin and library file the build uses (security audit
2026-10-22, #4; SECAUDIT04_DEPENDENCIES_ARE_VERIFIED_V1). Gradle checks each
file it takes from `~/.gradle/caches`, so every build (lane, land, release
qualification) stops with "Dependency verification failed" when a file's
checksum or signature does not match or a file is not listed. Before it, a
file poisoned during an approved online fetch would have been trusted by every
later `--offline` build.

## What is checked

- Every jar/aar/plugin file **and** its `.pom`/`.module` metadata
  (`verify-metadata=true`).
- PGP signatures (`verify-signatures=true`): a file signed by one of the
  trusted keys passes by its signature; the keys are in the armored keyring
  (`verification-keyring.keys`), so offline builds need no key server.
- Files that are not signed (most of Google's Maven: androidx, AGP) or whose
  key no key server had (14 `ignored-key` entries) pass only by their pinned
  SHA-256.

The file was written on 2026-10-23 (owner approved one online window: the
missing `.pom`/`.module` files, `.asc` signatures and public keys; no new
library or version) from the cache as it was. It proves the files never change
from here on and that signed ones come from their publisher's key; it does not
prove an unsigned file was clean when first fetched.

Why not offline: Gradle's verification reads the raw `.pom`/`.module` files,
not its parsed copy, and 575 of 1 231 cached components had only their jar.
Any build with a verification file then failed with "No cached version".

## After an approved dependency change

A changed version or a new library stops the build until the file knows it.
In the same lane and slice as the version change, during the approved online
fetch:

```text
./gradlew --write-verification-metadata pgp,sha256 --export-keys \
  :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest \
  :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest \
  :app:assembleBenchmark :benchmark:assemble \
  --no-daemon --no-configuration-cache
```

Then run the same command once more with `--offline`: the first pass misses
some metadata files of the build script's own classpath (seen 2026-10-23: the
second pass added only `.pom`/`.module` entries). Gradle adds new entries to
the existing file. Review the diff: only the changed modules may move, and a
new `trusted-key` must belong to the library's publisher. Commit both files
with the version change.

Never regenerate the file to get past a checksum or signature failure on an
unchanged version: that is what it is there to catch (rule 5). Find the cause
first.

Release builds (`:app:assembleRelease`) resolve the same modules as the R8
benchmark build, which the list above covers.

## After an empty Gradle cache

On 2026-10-24 `~/.gradle/caches` was gone (cause unknown) and an approved
online fetch refilled it. Every build then stopped on metadata files the
file had never listed: a version that only loses a conflict still has its
`.pom` read (Media3 1.11.1 names `compose-bom:2026.05.00`; the build uses
2026.09.00), and lint's own tooling resolves on its own configurations. The
old cache had hidden them. They were found with a dry run that writes
`gradle/verification-metadata.dryrun.xml` and leaves the real file alone:

```text
./gradlew --write-verification-metadata pgp,sha256 --dry-run \
  <the task list above> --offline --no-daemon --no-configuration-cache
```

Only `.pom`/`.module` entries were added (16, VERIFYBOM01), no jar/aar and no
key trust. Run the dry run after any cache loss and read its diff before
taking an entry; move the dryrun file out of the repo afterwards.
