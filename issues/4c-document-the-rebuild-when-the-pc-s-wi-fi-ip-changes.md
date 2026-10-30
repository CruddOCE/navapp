# Issue 4c: Document the rebuild when the PC's Wi-Fi IP changes

Status: Tested and confirmed
Phase: 4 (Capacitor shell)

Done when: The README says what to run when the PC's Wi-Fi IP changes.

Steps:
1. Write the rebuild steps in README.md with the NAV_API_BASE command.
2. Note that the address is baked into the APK at build time.

## Status log

- 2026-10-29: To come.
- 2026-10-30: README "Build the Android test app" now has the rebuild steps (ipconfig, NAV_API_BASE build, install) and says the address is baked in at build time. Checked against scripts/build-android.js: the build reads NAV_API_BASE and restores config.js. The current APK bakes in 192.168.1.221, which matches this PC's Wi-Fi address.
