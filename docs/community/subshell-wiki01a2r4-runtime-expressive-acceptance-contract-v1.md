# WIKI01A2R4 — runtime and Material 3 Expressive acceptance

## Purpose

This slice records final acceptance of the WIKI01A2 presentation and navigation
work before checkpoint.

No product behavior is added by R4.

## Functional runtime acceptance

Manual DEV2 runtime verification established the complete Wiki path:

- Community info exposes Wiki as a separate documentation destination.
- `wikiread` remains incremental-only.
- Wiki index loads community pages.
- Configuration pages remain available but collapsed by default.
- Wiki pages render natively through the canonical Markdown pipeline.
- ATX headings render as headings.
- Horizontal rules render as dividers.
- Existing code blocks and Copy behavior remain available.

The corrected Android Back hierarchy was manually verified:

1. Wiki page → Wiki index
2. Wiki index → Community info
3. Community info → dismiss the modal sheet

The visible Wiki header Back action shares the same navigation ladder.

## Material 3 Expressive acceptance

WIKI01A2 predecessor contracts deliberately required a dedicated Material 3
Expressive pass before lock or land.

WIKI01A2R2 supplied that pass:

- dedicated Wiki workspace identity;
- shaped tonal destinations;
- grouped Wiki page surfaces;
- Material icon identity and directional affordances;
- coherent type hierarchy;
- explicit authorization/loading/error surfaces;
- collapsed advanced configuration content.

R3 corrected system Back ownership without changing the accepted visual
direction.

`MATERIAL_3_EXPRESSIVE_FINALIZATION_SATISFIED`

No additional visual polish is required for WIKI01 before checkpoint.

## Boundary

The accepted feature remains read-only.

No:

- `wikiedit` or Wiki write;
- Room migration;
- backup or preference format change;
- daily-driver `wikiread` grant;
- DEV1 mutation;
- remote operation.

The historical predecessor marker
`MATERIAL_3_EXPRESSIVE_FINALIZATION_REQUIRED` remains in predecessor
documentation as the requirement that R2/R3/R4 have now satisfied.
