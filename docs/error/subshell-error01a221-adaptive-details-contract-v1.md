# ERROR01A2.2.1 — Adaptive error details contract v1

ERROR01A2.2.1 refines the viewport Error Tray after runtime validation showed that a bottom sheet containing only `Failure = Network`, `Network = Timeout`, and `Retryable = Yes` repeated information already visible in the tray.

## Adaptive Details rule

`Details` must be hidden for simple transport failures when the tray already names the network reason. DNS, connection, timeout, TLS, and generic I/O transport failures do not gain a Details action merely because retryability is known.

`Details` remains available when structured metadata can materially expand the explanation: HTTP status, protocol failure detail, provider code/message, or retry-after metadata. The host also refuses to open a Details sheet when the current request does not qualify.

## Details sheet content

The sheet is a technical expansion, not a second copy of the error card. It uses the generic heading `Error details`, does not repeat the tray title or explanatory body, and does not add a duplicate `Content = Saved content remains visible` row. Structured safe metadata and the feature-owned retry action remain available.

## Diagnostics successor

DIAG01 may later make Details useful for more failures by adding diagnostic event metadata and report actions. ERROR01A2.2.1 does not fabricate extra rows or expose raw exception text, tokens, OAuth state/codes, cookies, callback payloads, or sensitive URLs.
