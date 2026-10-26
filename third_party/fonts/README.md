# Code fonts

subshell bundles Latin regular subsets of JetBrains Mono and Iosevka, plus a
static Recursive Mono Casual instance for the code-font setting. The Recursive
instance uses `MONO=1`, `CASL=1`, `wght=400`, `slnt=0`, and `CRSV=0.5`.

All three fonts are distributed under the SIL Open Font License 1.1. The
corresponding license text and copyright notice are stored beside this file.

## Provenance

The upstream versions are read from each file's own `name` table (record 5);
the subsetting command was not recorded when the files were added (July 2026),
so the SHA-256 is that of the bundled file, not of an upstream download
(security audit 2026-10-22, #30).

| File in `app/src/main/res/font/` | Font, version | Upstream | SHA-256 of the bundled file |
|---|---|---|---|
| `jetbrains_mono_regular.ttf` | JetBrains Mono Regular 2.211 | https://github.com/JetBrains/JetBrainsMono | `51dc310d670a0438510cf8fc18dd23609a41671364369b81a855e190df7e640f` |
| `iosevka_compact_regular.ttf` | Iosevka Regular 22.1.2 (ttfautohint 1.8.4) | https://github.com/be5invis/Iosevka | `919bc634a20ff581d7f36709162678ed2973e8f2c2a6a8e38bbaca0f8f87236d` |
| `recursive_mono_casual_regular.ttf` | Recursive 1.085 (static instance above) | https://github.com/arrowtype/recursive | `7dac158f736f1c97cebc40c889ec20bbd55cc93099b83998ba55ef6768375735` |

A replaced font updates its row in the same commit.
