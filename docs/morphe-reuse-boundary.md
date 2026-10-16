# Morphe knowledge reuse boundary

`subshell` lives beside the Morphe/Boost work so it can benefit from the product
and runtime knowledge accumulated there. It does not compile against Morphe and
does not contain Boost code, resources, bytecode, package names, or decompiled UI.

## Reused as documented contracts

| Area | Contract carried into `subshell` |
|---|---|
| Navigation | Four stable top-level destinations; post detail is a separate context; back returns to the caller instead of exiting unexpectedly. |
| Feed | Dense posts, configurable-style compact actions, explicit saved state, and visible sort/feed scope. |
| Comments | Coloured depth rails, collapsible subtrees, compact reply/vote actions, and readable metadata hierarchy. |
| Media | Media is a typed post capability rather than a hard-coded screen assumption. Version 0.1 draws local artwork only. |
| Settings | Theme controls are discoverable under You; the orange identity remains the default while dynamic colour is opt-in. |
| Safety | Separate package, no normal Boost overwrite, no secrets, no account data, no network permission, and build-before-install discipline. |
| Validation | Static source boundary, deterministic fixtures, unit contracts, navigation smoke test, then device runtime evidence before any distribution claim. |

## Not reused

- Boost Java/Kotlin, resources, APK contents, smali, reflection hooks, or internal class names.
- Reddit client identifiers, OAuth configuration, endpoints, API models, or user data.
- Morphe extension binaries, patches, manager metadata, or release machinery.
- Boost branding, iconography, screenshots, or proprietary media.

## Future extraction rule

A utility may move from Morphe into `subshell` only when all of the following are
true:

1. Morphe owns the implementation or explicit permission covers the reuse.
2. The utility has no Boost class, resource, package, bytecode, or runtime dependency.
3. Its licence and attribution requirements are documented before copying code.
4. It enters through a focused `core` abstraction with tests, not as a dependency
   on the patch project.

Until those gates are met, knowledge is translated into a clean-room product
contract and implemented independently in `subshell`.
