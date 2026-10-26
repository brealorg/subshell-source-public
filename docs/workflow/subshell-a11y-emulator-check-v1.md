# A11Y01 — accessibility check at release (non-blocking)

`tools/a11y-emulator-check.sh` runs Google's Accessibility Test Framework
(ATF) over the app's main surfaces for the exact source of a release
(A11Y01_RELEASE_ACCESSIBILITY_CHECK_V1, #246). The owner decided on
2026-10-25 that it **reports and never stops a release**: a finding is read
in the release report and the owner decides whether it holds the release.

## Why the emulator

ATF does not evaluate Compose content under Robolectric (it reports
"Composable content is not evaluated in this environment"), so the checks
live in `Issue246AccessibilityChecksInstrumentedTest` (androidTest) and run on
`subshell-api36`. The test opens 13 surfaces from a freshly composed
`SubshellApp` with the built-in sample data and fails a surface on any ATF
error; warnings are logged under `SubshellA11y`. A control test proves the
checks read Compose content (an unlabelled 20 dp control must be reported).
Signed-in-only UI is not covered.

## What the script does

```text
tools/a11y-emulator-check.sh --source <qualified source> --commit <SOURCE_COMMIT> --out <dir>
```

1. Unpacks `git archive <commit>` under `--out` and builds the debug and
   androidTest APKs there, offline. The qualified source is only read: Stage C
   requires it clean, and `~/Downloads/subshell-*` is read-only.
2. Boots the AVD as `tools/emulator-smoke.sh` does (`-memory 4096`, read-only,
   no window) on port 5590; every adb call names the emulator serial, so the
   phone is never used (a `connected*` Gradle task would use it too).
3. Installs both APKs, runs the test class with `am instrument`, stops the
   emulator.
4. Writes `<out>/a11y-result.env` and prints one of
   - `A11Y_RESULT=PASS` — no surface had an error;
   - `A11Y_RESULT=FINDINGS` — the tests ran and at least one failed
     (`instrument.txt` lists the errors per surface);
   - `A11Y_RESULT=FAILED` — it could not run (build, boot, install).

   It exits 0 for all three (2 only for usage errors).

About five minutes. Checked on 2026-10-25: PASS on `main` `f7b5354d`
(14 tests); FINDINGS on a planted failing test.

## In the release recipe

From the next release on (`worklog/beta16/`), `3-smoke.sh` runs it after the
emulator smoke and before the phone smoke:

```bash
bash "$SRC/tools/a11y-emulator-check.sh" --source "$SRC" \
  --commit "$SOURCE_COMMIT" --out "$OUT/a11y-check" || true
cat "$OUT/a11y-check/a11y-result.env" 2>/dev/null || echo "A11Y_RESULT=FAILED (no result file)"
```

The release report records the `A11Y_RESULT` line. The recipe for 0.85
(`worklog/beta15/`) is left as it ran.
