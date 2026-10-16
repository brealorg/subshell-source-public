# MESSAGES02C6R4 — Direct Chat tail follow

Runtime evidence after C6R3 showed that Matrix upload/send latency is now measurable
and the warm session cache works, but the second GIF can be appended below the current
viewport. A later authoritative projection replaces the confirmed Matrix echo with a
different item key, which can also leave the replacement below the viewport.

C6R4 keeps the just-sent tail visible at both boundaries:

1. server-confirmed local Matrix echo insertion;
2. authoritative Reddit/Matrix reconciliation before reply state reset.

The helper aligns the measured item bottom with the LazyColumn viewport and re-checks
for two frames to absorb the GIF's aspect-ratio layout change. It does not add any
network call or write retry.
