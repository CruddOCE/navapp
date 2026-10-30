# Changelog

## Unreleased

### Fixed
- The turn prompt no longer sticks on a turn already passed after a GPS gap or stall. Progress is measured along the route line, and every turn behind the car is passed at once.
- The arrival time holds steady. It now comes from the time still to drive, using each road's own speed from OSRM, instead of sliding later with every fix.
- Fork, merge, on and off ramps, end of road, roundabout exits and U-turns each get their own wording, instead of "Continue on". Roundabouts say which exit. Unnamed roads use their route number.
- After a reroute, a new turn gets its full voice prompts, and the same turn is not announced twice.

- GPS no longer stalls. The app asked for a fix only every 30 s (the plugin uses the timeout as the update interval) while a 20 s watchdog restarted the watch, and error retries left old watches running. Now it asks every second, keeps one watch, and only the watchdog restarts it.
- GPS keeps working with the screen locked or another app in front while a route is active, through a location service with a "navapp is navigating" notification.
- Routing no longer fails with 403 from the OSRM demo server, which refuses Android's default User-Agent.

### Changed
- The app works without the PC: search and routing go straight from the phone to Nominatim and OSRM over mobile data. Only crowd reports still need the PC's server; away from it, the ! button says "Reports need the home server".
- Android 13 and later asks to allow notifications on first launch, for the navigation notification.
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
