# Changelog

## Unreleased

### Fixed
- The turn prompt no longer sticks on a turn already passed after a GPS gap or stall. Progress is measured along the route line, and every turn behind the car is passed at once.
- The arrival time holds steady. It now comes from the time still to drive, using each road's own speed from OSRM, instead of sliding later with every fix.
- Fork, merge, on and off ramps, end of road, roundabout exits and U-turns each get their own wording, instead of "Continue on". Roundabouts say which exit. Unnamed roads use their route number.
- After a reroute, a new turn gets its full voice prompts, and the same turn is not announced twice.

- GPS no longer stalls. The app asked for a fix only every 30 s (the plugin uses the timeout as the update interval) while a 20 s watchdog restarted the watch, and error retries left old watches running. Now it asks every second, keeps one watch, and only the watchdog restarts it.
- Navigation keeps going with the phone locked or another app in front. It now runs in the Android location service (shown as a "navapp is navigating" notification): turn tracking, voice prompts, the car screen and rerouting. Before, it ran in the app's web page, which Android stops after about a minute in the background, so everything froze.
- The car screen now tells Android Auto when navigation ends, not only when it starts.
- The car screen no longer crashes when a route starts. Android Auto's navigation screen needs at least one button, and it had none; it now has End, which ends the route on the phone too.
- The car map is always on while navapp is open on the car, shows your position at all times (route or not, phone locked or not), and appears within about 1 s: it opens at your last known position, and a route shows before the first GPS fix.
- The car map can be panned and zoomed: Android Auto's Pan button with drag and pinch, zoom in and out buttons, and Recenter. Your position stays on screen while you look around, and the map follows you again after 15 s without a touch.
- The car screen now shows a map behind the turn card: the same map and style as the phone, turned to face the direction of travel, with the route line and a position arrow kept clear of the card.
- The car screen now shows a turn card: an arrow for the kind of turn (left, right, slight, sharp, U-turn, merge, roundabout, arrival and more), the distance and the instruction. Before, every turn was sent as "unknown" with no icon, so nothing showed.
- Starting a route away from a road (a driveway, a car park, indoors) no longer reroutes over and over before the car has moved.
- Messages for the driver, such as "Reports need the home server", now show in the navigation banner while navigating, instead of the hidden status line.
- Routing no longer fails with 403 from the OSRM demo server, which refuses Android's default User-Agent.

### Changed
- The app works without the PC: search and routing go straight from the phone to Nominatim and OSRM over mobile data. Only crowd reports still need the PC's server; away from it, the ! button says "Reports need the home server".
- Android 13 and later asks to allow notifications on first launch, for the navigation notification.
- The screen stays on while a route is active, and goes back to normal when the route ends.
- The map library (MapLibre GL JS 5.24.0) is bundled in the app instead of loaded from a CDN, for both maps.
- In the Android app, voice prompts use Android's text-to-speech and briefly lower other audio, such as music, while they play.
- The bottom bar shows the distance left ("12.1 km left") instead of the total route length.
- Distance to the next turn is measured along the road, not as a straight line.

### Known issues
- The 0.01 report button crash did not reproduce in five tap-out cycles on the phone. The report sheet and the report card still do not close when you tap the map.

## 0.01 (2026-10-29)

First test build. Debug APK for personal testing; not a public release.

### Added
- Map with place search, nearby places first, then places further away.
- Route drawing from the device position, with turn-by-turn navigation: banner, ETA bar, End.
- Spoken prompts at 400 m, 100 m and 20 m. Rerouting when off route.
- Crowd reports: post, confirm, dismiss, live updates over WebSocket.
- Android Auto navigation screen (Car App Library 1.7.0).
- Wi-Fi debug build and install scripts.

### Fixed
- Car screen crash on open (missing navigation callback).
- Location stalls: the GPS watch restarts after 20 s with no fix.

### Changed
- Search moved into a bottom sheet with a recenter button.
- Map restyled toward a pale navigation look.

### Known issues
- Report button crash when tapping out of it (under investigation).
- Device IDs are client-chosen; host check allows all hosts in every build.
