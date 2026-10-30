# Issue 4a: App installs and opens on the phone

Status: Tested and confirmed
Phase: 4 (Capacitor shell)

Done when: The debug APK installs over Wi-Fi debugging and opens to the map.

Steps:
1. Build: NAV_API_BASE=http://<PC Wi-Fi IP>:8090 npm run android:build
2. Pair and connect the phone over Wi-Fi debugging.
3. Run npm run android:install.
4. Open the app and check the map shows.

## Status log

- 2026-10-29: Built, awaiting test. BUILD-PLAN.md records the install; open-to-map on the phone is not separately recorded.
- 2026-10-30: Confirmed. `npm run android:install` installed the rebuilt APK over Wi-Fi adb (package updated 15:24) and launched it. Earlier the same day the app opened, showed the map and position dot, and ran a search and a route on the phone.
