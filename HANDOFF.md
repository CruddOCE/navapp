# navapp handoff

Last updated: 2026-10-31. Read this first when resuming.

## Where it stands

- **Built:** map screen with search and a route, navigation view, crowd reports (server and client), Capacitor Android shell, Android Auto navigation screen, direct search and routing from the phone, and native navigation: in the Android app, `NavLocationService` runs the turns, voice, car screen and rerouting, so it keeps going with the phone locked. Debug APK at `dist/navapp-debug.apk`.
- **Works without the PC (2026-10-30):** with the dev server stopped, the phone searched, routed and navigated on its own. Only crowd reports need the PC.
- **Native navigation checked on the phone (2026-10-31):** with navapp behind the home screen for 3 minutes, the engine handled fixes the whole time (4 to 22, about 6 a minute indoors), spoke its prompt through text-to-speech and rerouted once. On return the page showed the same turn, distance and ETA as the engine at once. Not yet checked with the screen actually locked, on a drive, or on the Desktop Head Unit (it connected but got no video focus).
- **Tests:** `npm test` passes 65 tests across `api`, `geocode`, `gps`, `nav`, `nav-expected`, `reports`, `routing`, `server` and `voice`. `npm run android:test` runs the Java tests: the parity test replays 602 fixes from `test/fixtures/nav-expected.json` and the instruction wording cases. Breaking the Java arrival radius or off-route limit on purpose makes the parity test fail.
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
- **Why navigation is native.** Measured on 2026-10-30, app in the background: native code received fixes steadily for 2 minutes, but the page received them only for the first 60 s; after that a plugin call and an 8 s timer in the page both waited about 150 s, until the app came back. The renderer stayed at foreground priority and Play services kept serving the 1 s request. So the WebView stops running a page that has been hidden for a while, and Android has no supported switch to stop that.
- **Native navigation (`NavLocationService`, `nav/`).** While a route is active the service runs as a foreground service of type location with a "navapp is navigating" notification. It gets fused location every 1 s from `LocationManager`, runs `Navigator` and `VoicePrompter` (Java copies of `nav.js` and `voice.js`), speaks through `AndroidSpeaker` (text-to-speech, ducking other audio), updates `NavState` for the car screen, and reroutes through `OsrmClient` (at most every 10 s, off the main thread). On arrival it ends the route itself after 4 s, since the page may not be running. No "allow all the time" permission is needed.
- **Page and service (`NavStatePlugin`).** At Start, map.js calls `startRoute({ route, destination })`; at End, `stopRoute()`. The service sends `navState` on each fix, `rerouted` with the new route, `message` and `ended`. While the page is hidden the plugin holds back `navState` and `message` (they would only pile up and replay in a burst) and sends the latest state once when the page is shown again; `rerouted` and `ended` are kept until delivered. In a desktop browser, map.js runs `nav.js` and `voice.js` itself instead.
- **Keeping the two navigators the same.** `scripts/make-nav-expectations.js` replays 5 drives through `nav.js` and `voice.js` into `test/fixtures/nav-expected.json`. `NavigatorParityTest` replays the same file through the Java copies and must match every fix. `InstructionsTest` holds the same wording cases as `routing.test.js`. Android's `org.json` is only a stub in JVM tests, so JSON lives in `RouteJson` (app only) and the tests read fixtures with Gson (test dependency only).
- **Screen on.** The plugin still keeps the screen on while a route is active, for a phone in a cradle. Navigation no longer depends on it.
- **Car screen (`car/NavScreen.java`).** While navigating it shows Android Auto's navigation template: a turn card (arrow, distance, instruction) and an End button that appears when the screen is touched. The template will not build without that action strip, and the host shows no turn card for an "unknown" turn without an icon, so every step gets a real turn type from `nav/TurnTypes.java` and an arrow from `res/drawable/turn_*.xml`. Each route step carries OSRM's `modifier`, `exit` and `drivingSide` for this. Behind the card, `car/CarMapRenderer.java` draws the map: the host's Surface goes to a VirtualDisplay, and a Presentation on it holds a WebView running `web/car-map.html` (loaded from `file:///android_asset/public/`). That page is the phone's MapLibre map and style, heading-up, with the route line and an arrow placed 72% of the way down the area the host leaves uncovered (`carPadding` in `web/js/car-map.js`, tested in `test/car-map.test.js`). NavState carries the route line and latest position for it. The car screen always uses the navigation template, so the surface (and the map) is there from the moment navapp opens on the car; before a route it shows "No route yet" over the map, with a "Start on phone" action. The page opens at your last known position (`car-map.html#lat,lng,heading`); outside a route the renderer keeps its own 1 s location watch, during one the service feeds it. Starting a route runs the first navigation update from a recent last-known fix, so the car shows the route within about 1 s. Map buttons: Android Auto's Pan, zoom in and out, and Recenter while not following (`car/FollowState.java`: a touch stops following, Recenter or 15 s without a touch starts it). While not following, `keepInside` in car-map.js nudges the view so your position stays in the uncovered area. Measured on the DHU 2026-10-31: map drawn 1 s after reopening navapp, route 1 s after Start.
- **Traffic side.** OSRM's public server reports `driving_side: right` even in Australia (checked 2026-10-31), so the service takes the side from the phone's network country (`TelephonyManager`), with a list of left-hand-traffic countries in `TurnTypes`, and only falls back to OSRM's value with no network. It decides which way roundabouts and U-turns point.
- **Starting off the road.** OSRM starts the route on the nearest road, so a car in a car park, a driveway or indoors can begin more than 40 m from the line. Until the first fix on the route, only moving 40 m further away than the starting distance counts as off route, so the app does not reroute before it has moved.
- **Driver messages while navigating.** The status line is hidden during navigation, so `showMessage` puts messages (reports offline, report posted, reroute results) in the banner for 4 s before the next instruction takes it back.
- **Navigator depart step.** The depart step is skipped if the first GPS fix is more than 25 m from the start point (the app was opened mid-route).
- **Navigator progress.** Measured along the route line, not by circles around each turn. Each fix is matched to the line searching forward from current progress (2 segments of slack), so a road the route uses twice is matched to the right pass. Progress only moves forward, and only while on the route. A turn is passed once progress is beyond it, or once a fix lands within 25 m of it, so a GPS gap passes every skipped turn at once. Distance to the next turn is along the road while on the route, straight line when off it.
- **Off route.** Measured as distance to the nearest route segment, not the nearest vertex. Off route after 3 fixes in a row, more than 40 m away. Reroute at most every 10 seconds.
- **Voice.** Each manoeuvre is announced once at 400 m, 100 m and 20 m. A jump past several thresholds speaks only the closest one. A turn is identified by its instruction and position, not its step number, because a reroute restarts the numbering.
- **ETA.** Now plus the time still to drive: the unfinished part of the current stretch at that stretch's OSRM duration, plus every later step's duration. Freeway and city stretches keep their own speeds. Not adjusted for live traffic or the driver's actual speed. The bottom bar shows the distance left, scaled to OSRM's total.
- **Instruction wording.** Every OSRM manoeuvre type has its own wording in `web/js/routing.js`. Unnamed roads use their ref, and a bare number is spoken as "route 1".
- **Test port.** Server tests use an OS-picked port, not 8092, because a fixed port collided between tests.
- **Search results.** Each search returns "nearby" (inside 100 km of the device, bounded search) and "further" (everything else, shown under a "Further away" heading). Without a device position, everything is "further".

## In progress at the stop

- Nothing is half-edited.
- **Desktop Head Unit, checked 2026-10-31.** navapp opens on the car, a route shows the turn card with the right arrow, and End on the car ends the route on the phone. How to connect:
  1. On the phone, open Android Auto's settings (`adb shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity`) and keep that screen in front. If another app is in front, the phone's freezer kills Android Auto mid-handshake ("Kill app due to repeated failure to freeze binder") and the DHU sits on "Waiting for phone...".
  2. In its ⋮ menu, make sure the head unit server is running (the menu then offers "Stop head unit server"). A freezer kill turns it off; check with `adb shell "ss -ltn | grep 5277"`.
  3. `adb forward tcp:5277 tcp:5277`, then start `%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe`. The DHU reads console commands (`tap X Y`, `screenshot FILE`) on its standard input, which is the easiest way to drive it from a script.
  4. After reinstalling the APK, reconnect the DHU: Android Auto keeps the old package's resources and fails to load new icons (`Resources$NotFoundException`).
  - Connecting may auto-start music on the phone (Android Auto's "Start music automatically").
- The dev server on port 8090 is running.
- **Reading the engine:** `adb shell dumpsys activity service com.example.navapp/.NavLocationService` prints fixes handled, seconds since the last fix, prompts spoken, the last prompt, reroutes and the latest state. It works while the page is not running.
- **Debugging the live app page:** a debug build allows WebView debugging. `adb forward tcp:9333 localabstract:webview_devtools_remote_<app pid>`, then `http://127.0.0.1:9333/json` lists the page and its DevTools WebSocket, which can run JavaScript in the running app.

## Next steps (in order)

1. **Road test, away from home, phone locked and not connected to anything:** issues 2a to 2d, with a passenger. Listen for the prompts with the phone locked. Save the GPS trace as a fixture for both navigators (issue 2e).
2. **Car screen with the phone locked:** on the DHU, lock the phone mid-route and check the map and turn card keep moving. The map runs in a WebView on a virtual display; whether Android keeps that drawing with the phone locked is not yet checked. If it stops, draw the route line and arrow straight onto the surface with a Canvas instead.
3. **Issues 3b and 3c:** confirm reports and votes on two phones.
4. **Report sheet and card:** they do not close when the map is tapped (board #41 and #44).
5. **Issues 3d and 4a-2:** need the owner's approval before starting.
6. The remaining audit items (search reply race, rate limiter race, report expiry, error page) are listed in the 2026-10-30 audit and not yet planned. `navigationEnded` is fixed.

## Open questions and assumptions

- **Name, icon and package id:** placeholders.
- **Public routing and search:** demo services, development only. Self-host OSRM, Nominatim and a tile host before release.
- **Privacy, moderation and store listing:** deferred to public release.
- **Hosting the report server** so reports work away from home: release slice R1, not started.

## Landmines

- **Two copies of the navigation logic.** `web/js/nav.js`, `voice.js` and the wording in `routing.js` have Java copies in `android/app/src/main/java/com/example/navapp/nav/`. Change both together, run `node scripts/make-nav-expectations.js`, then `npm test` and `npm run android:test`. `npm test` fails while the expectations file is stale, but only `npm run android:test` checks the Java side.
- **The GPS plugin uses the timeout as the update interval** when no `interval` is passed (`GeolocationPlugin.kt`, line 340 in `@capacitor/geolocation` 8.2.4). Always pass `interval`. Before 2026-10-30 the app asked for a fix every 30 s while the watchdog restarted the watch every 20 s, so fixes stalled.
- `NAV_API_BASE` is baked into the APK and is now only used for reports. A changed PC IP means rebuilding the APK for reports to work.
- `npm run android:build` writes `web/js/config.js` while it runs and restores it after. If the build is interrupted and the file looks wrong, reset it to `window.NAV_API_BASE = '';`.
- Gradle needs the socket redirect (`.jdk-sock/`) because Java sockets fail under `AppData` on this PC. The build script handles it. Running Gradle directly will fail.
- `package.json` version is `0.01`. Confirm with the owner before changing it.
- The public OSRM demo and Nominatim have usage limits. The geocoder cache and one-request-per-second limit protect them, but heavy testing will get blocked.
- **Notification permission:** Android 13 and later asks on first launch. If refused, navigation still works but the navigation notification is hidden.
- **MapLibre is bundled from npm** (`maplibre-gl` 5.24.0, dev dependency) into `web/vendor/` by `scripts/copy-vendor.js`; `npm start` and `npm run android:build` run it. Opening `web/index.html` without either leaves the map library missing.
- **The DHU cannot drag or pinch** (its console only has `tap`), so pan and pinch on the car map need a touch screen or a car to test.
- **The browser path still runs the JavaScript navigator and voice.** Changes to the Android path (native events) do not exercise it, so check both after touching `map.js`.
- **Publishing to CruddOCE/navapp:** the public repo's `main` has commits made there (the board link, BUILD-PLAN snapshots), so a fresh `git subtree split` no longer lines up. Publish by applying the `navapp/` commits as patches (`git format-patch --relative=navapp`) onto a worktree of the public `main`, then push. The privacy rule from the first publish still applies: no LAN addresses or the owner's name in docs.
- `test/fixtures/osrm-route.json` is hand-written in OSRM's format. `test/fixtures/route-perth-fremantle.json` is a real route captured through `parseOsrmResponse` on 2026-10-30, so it holds the parsed shape, not raw OSRM. If the parser's output changes (new step fields, new wording), recapture it, or the replay tests run on stale data.
