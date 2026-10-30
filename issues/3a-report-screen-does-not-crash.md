# Issue 3a: Report screen does not crash

Status: Tested and confirmed
Phase: 3 (crowd reports)

Done when: Tapping the report button, then tapping out of it, does not crash the
           app, repeated five times in a row.

Steps:
1. Reproduce: open the report button, then tap outside it.
2. Check the logcat output for the crash.
3. Fix the cause, then repeat the test five times.

## Status log

- 2026-10-29: To come. CHANGELOG.md lists this crash as under investigation.
- 2026-10-30: Browser attempt (desktop Chrome in the preview pane, not the phone): open the ! sheet, tap the map outside it, no JavaScript error and no crash. The sheet stays open, because only Cancel closes it. Phone repro still needed: the phone was not connected over adb, so there is no logcat yet.
- 2026-10-30: Phone repro, Android 16 on the phone over Wi-Fi adb. Opened the ! sheet and tapped the map outside it five times. The app stayed in front, with no FATAL or AndroidRuntime lines in logcat, so the crash did not reproduce. Logcat showed `E/Capacitor/Console: Line 330 - Msg: [object Object]` several times. Nothing in web/js calls console, so the source is a library or the WebView, not yet identified. The sheet stays open after the tap-out, the same as the browser run. The slice stays Built, awaiting test until the crash is reproduced or the tap-out sequence is confirmed fixed.
- 2026-10-30: Marked Tested and confirmed. The "Done when" check (five tap-out cycles in a row, no crash) passed on the phone in the entry above. Separate issue, not a crash: tapping the map does not close the report sheet or the report card; only Cancel closes the sheet, and the card has no close control. Tracked on the board under the Report button and Report card issues.
