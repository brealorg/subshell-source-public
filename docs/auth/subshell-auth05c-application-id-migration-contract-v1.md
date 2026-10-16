# subshell AUTH05C application ID migration contract V1

## Selected identity

AUTH05C applies the permanent production Android application ID:

```text
io.github.brealorg.subshell
```

The debug and instrumentation identities are deterministic derivatives:

```text
io.github.brealorg.subshell.debug
io.github.brealorg.subshell.debug.test
```

The selected identity is associated with the existing `brealorg` GitHub
organization. It does not claim ownership of a separate DNS domain.

## Namespace separation

Kotlin and generated-code namespace migration is explicitly deferred:

```text
app.subshell.prototype
```

All manifest component class names are fully qualified with that namespace.
The application ID therefore changes without moving source directories,
package declarations, Room schema names, test class names, or generated `R`
and `BuildConfig` namespaces.

## Runtime identity

Network user-agent construction uses `BuildConfig.APPLICATION_ID`. Debug
traffic therefore identifies the debug package and a future production build
identifies the production package without hardcoded prototype identity.

All device/runtime scripts target the new debug and instrumentation package
IDs. Test class names remain in the unchanged Kotlin namespace.

## Coexistence and data isolation

The previous `app.subshell.prototype` installation is not removed or modified.
Android treats the AUTH05C debug application as a separate package with a
separate UID, application data directory, no-backup storage, preferences,
Room databases, and Android Keystore namespace.

No automatic migration from the retired prototype package is attempted.

## Release-signing successor

AUTH05C did not itself configure release signing. AUTH05D1 subsequently created
the permanent owner-managed app-signing key outside source control, and AUTH05D2
now wires release signing through external JKS and temporary password-file
inputs. The application ID and namespace decisions made by AUTH05C remain
unchanged.

Production App Links remain undeployed. No Reddit client ID, approval reference,
callback URI, live authorization, token exchange, or remote write is introduced.
