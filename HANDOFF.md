# navapp handoff

Last updated: 2026-10-30. Read this first when resuming.

## Where it stands

- **Built:** map screen with search and a route, navigator, voice prompts, navigation view, crowd reports (server and client), Capacitor Android shell, Android Auto navigation screen, a location service that keeps GPS running with the screen locked, and direct search and routing from the phone. Debug APK at `dist/navapp-debug.apk`.
- **Works without the PC (2026-10-30):** with the dev server stopped, the phone searched, routed and navigated on its own. Only crowd reports need the PC.
- **Tests:** `npm test` passes 61 tests across `api`, `geocode`, `gps`, `nav` (real 22 km route replays, with GPS gaps), `reports`, `routing`, `server` and `voice`.
- **Confirmed on the phone:** place search (1a), route drawing (1b), report screen tap-out (3a), install and open (4a), phone reaching the PC server (4b). See `navapp/issues/README.md`.
- **Not verified yet:** the road test (2a to 2d), reports on two phones (3b and 3c), the car screen route (4a-1).
- **Slice status:** each slice has a file in `navapp/issues/` with a dated log. The public roadmap board is https://github.com/users/CruddOCE/projects/1, and `BUILD-PLAN.md` is a snapshot of it.
- **Git:** the source is tracked in the `claude` repo on branch `navapp-test-build`. The public repo is CruddOCE/navapp (see the publishing landmine below).

## Decisions made

- **Placeholder name and package id.** Folder `navapp`, package `com.example.navapp`. No name chosen yet.
- **Android first.** Capacitor 8.5.3 (released 2026-10-07, at least two weeks old). Newer 8.6.0 is not used yet. Car App Library 1.7.0.
- **v1 scope:** crowd reports, Android Auto, anonymous device IDs, no accounts.
- **The phone works on its own.** In the Android app, search and routing call Nominatim and OSRM directly through Capacitor's native HTTP (`web/js/api.js`), so they work anywhere with mobile data. Native HTTP is used per request, not switched on for every fetch, so MapLibre's tile loading is untouched. In a desktop browser, the dev server proxies the same calls. `geocode.js` and `routing.js` live in `web/js/` so both places run the same code.
- **Reports still need the PC.** The APK calls the report server at `http://<PC Wi-Fi IP>:8090`, baked in at build time. Away from it, the ! button says "Reports need the home server", and the live channel retries with a gap growing from 5 s to a minute. The manifest allows cleartext HTTP for this; the release build needs an HTTPS API (slice R1).
- **User-Agent.** Both Nominatim and the OSRM demo refuse requests without an identifying User-Agent. OSRM answers 403 to Android's default `Dalvik` agent. The app sends `navapp/0.01 (personal test build; https://github.com/CruddOCE/navapp)`.
- **GPS watch (`web/js/gps.js`).** One watch at a time, asking for a fix every second. A 20 s watchdog is the only thing that restarts it. The plugin timeout is 10 minutes so a tunnel does not end the watch.
- **GPS with the screen locked.** While a route is active, `NavLocationService` runs as a foreground service of type location with a "navapp is navigating" notification. The NavState plugin starts it when navigation starts and stops it when it ends; the car screen's stop also stops it. With it, Android keeps serving the app's while-in-use location in the background (checked with `dumpsys location` on 2026-10-30). No "allow all the time" permission is needed.
- **Navigator depart step.** The depart step is skipped if the first GPS fix is more than 25 m from the start point (the app was opened mid-route).
- **Navigator progress.** Measured along the route line, not by circles around each turn. Each fix is matched to the line searching forward from current progress (2 segments of slack), so a road the route uses twice is matched to the right pass. Progress only moves forward, and only while on the route. A turn is passed once progress is beyond it, or once a fix lands within 25 m of it, so a GPS gap passes every skipped turn at once. Distance to the next turn is along the road while on the route, straight line when off it.
- **Off route.** Measured as distance to the nearest route segment, not the nearest vertex. Off route after 3 fixes in a row, more than 40 m away. Reroute at most every 10 seconds.
- **Voice.** Each manoeuvre is announced once at 400 m, 100 m and 20 m. A jump past several thresholds speaks only the closest one. A turn is identified by its instruction and position, not its step number, because a reroute restarts the numbering.
- **ETA.** Now plus the time still to drive: the unfinished part of the current stretch at that stretch's OSRM duration, plus every later step's duration. Freeway and city stretches keep their own speeds. Not adjusted for live traffic or the driver's actual speed. The bottom bar shows the distance left, scaled to OSRM's total.
- **Instruction wording.** Every OSRM manoeuvre type has its own wording in `web/js/routing.js`. Unnamed roads use their ref, and a bare number is spoken as "route 1".
- **Test port.** Server tests use an OS-picked port, not 8092, because a fixed port collided between tests.
- **Search results.** Each search returns "nearby" (inside 100 km of the device, bounded search) and "further" (everything else, shown under a "Further away" heading). Without a device position, everything is "further".

## In progress at the stop

Nothing is half-edited. The dev server on port 8090 was stopped on purpose for the standalone test; start it again with `npm start` for reports. A test route was left running on the phone when the owner picked the phone up; ending it in the app also stops the notification.

## Next steps (in order)

1. **Road test, away from home, phone not connected to anything:** issues 2a to 2d, with a passenger operating the phone. Check the screen-locked case and the notification. Save the GPS trace as a fixture for `test/nav.test.js` (issue 2e).
2. **Issue 4a-1:** confirm a route shows on the Desktop Head Unit.
3. **Issues 3b and 3c:** confirm reports and votes on two phones.
4. **Report sheet and card:** they do not close when the map is tapped (board #41 and #44).
5. **Issues 3d and 4a-2:** need the owner's approval before starting.
6. The remaining audit items (search reply race, rate limiter race, report expiry, `navigationEnded`, error page) are listed in the 2026-10-30 audit and not yet planned.

## Open questions and assumptions

- **Name, icon and package id:** placeholders.
- **Public routing and search:** demo services, development only. Self-host OSRM, Nominatim and a tile host before release.
- **Privacy, moderation and store listing:** deferred to public release.
- **Hosting the report server** so reports work away from home: release slice R1, not started.

## Landmines

- **The GPS plugin uses the timeout as the update interval** when no `interval` is passed (`GeolocationPlugin.kt`, line 340 in `@capacitor/geolocation` 8.2.4). Always pass `interval`. Before 2026-10-30 the app asked for a fix every 30 s while the watchdog restarted the watch every 20 s, so fixes stalled.
- `NAV_API_BASE` is baked into the APK and is now only used for reports. A changed PC IP means rebuilding the APK for reports to work.
- `npm run android:build` writes `web/js/config.js` while it runs and restores it after. If the build is interrupted and the file looks wrong, reset it to `window.NAV_API_BASE = '';`.
- Gradle needs the socket redirect (`.jdk-sock/`) because Java sockets fail under `AppData` on this PC. The build script handles it. Running Gradle directly will fail.
- `package.json` version is `0.01`. Confirm with the owner before changing it.
- The public OSRM demo and Nominatim have usage limits. The geocoder cache and one-request-per-second limit protect them, but heavy testing will get blocked.
- **Notification permission:** Android 13 and later asks on first launch. If refused, GPS still works but the navigation notification is hidden.
- **Publishing to CruddOCE/navapp:** the public repo's `main` has commits made there (the board link, BUILD-PLAN snapshots), so a fresh `git subtree split` no longer lines up. Publish by applying the `navapp/` commits as patches (`git format-patch --relative=navapp`) onto a worktree of the public `main`, then push. The privacy rule from the first publish still applies: no LAN addresses or the owner's name in docs.
- `test/fixtures/osrm-route.json` is hand-written in OSRM's format. `test/fixtures/route-perth-fremantle.json` is a real route captured through `parseOsrmResponse` on 2026-10-30, so it holds the parsed shape, not raw OSRM. If the parser's output changes (new step fields, new wording), recapture it, or the replay tests run on stale data.
