# ERROR01A3 — media and backup error contract

ERROR01A3 migrates remote media playback/loading failures and backup/restore failures onto the ERROR01 structured failure foundation.

## Media

Remote image and GIF loading returns typed success/failure results. HTTP status, transport category, response/protocol failure and local decode/security/validation failure remain structured in `FailureDetails`. Full media surfaces show readable inline Material failure UI with Retry instead of a silent black/unavailable placeholder. Tiny decorative/metadata image call sites may intentionally retain icon-only fallback.

Video terminal playback errors preserve the Media3 error code as bounded safe media detail, present a readable playback category, and keep the existing retry operation.

Media failures remain local to the media surface rather than producing one global tray per failing image or video.

## Backup

Backup UI and stored backup status must never surface or persist raw `Throwable.message`. Backup failures map to typed local categories before presentation. Backup/restore screen action failures use the viewport error host so an error cannot be inserted outside the current scroll position. Folder onboarding remains modal and renders the same sanitized structured failure information inside the dialog.

Legacy stored raw backup error strings may only be treated as an unknown local failure and must never be displayed verbatim.

Because the application host is a `FragmentActivity`, Activity Result launchers used by backup import/export, folder selection and media pickers require an explicitly modern Fragment runtime. ERROR01A3 pins Fragment 1.9.0 so the host cannot fall back to the legacy transitive Fragment supplied by Biometric and hit the 16-bit request-code crash path.

## Deferred

Composer submit/write policy migration remains ERROR01A4. Compact composer thumbnails that reuse `RemoteImage` may retain icon-only media fallback until the composer error endpoint matrix is migrated.
