# Issue 2a: Location recovers after it stalls indoors

Status: Built, awaiting test
Phase: 2 (navigator)

Done when: After the GPS goes quiet indoors, the position updates again within
           about 20 seconds of walking outside.

Steps:
1. Start a route indoors so the GPS goes quiet.
2. Walk outside.
3. Check the position updates again, and note how long it took.
4. If it does not recover, record the time it took and the phone model.

Note: do this first in Phase 2. The navigator depends on the position updating.

## Status log

- 2026-10-29: Built, awaiting test.
- 2026-10-30: Navigator fixed so a fix jump after a stall no longer leaves the prompt on a turn already passed: progress is now measured along the route line and every turn behind the car is passed in one update. Covered by the 300 m gap replay in test/nav.test.js. Still needs the indoor stall check on the phone.
- 2026-10-30: Root cause of the stalls found and fixed. The Geolocation plugin uses the timeout as the update interval when none is given, so the app asked for a fix every 30 s while the 20 s watchdog restarted the watch before each one arrived; error retries also left old watches running (4 restarting together every 35 s in logcat). Now one watch asks every second and only the watchdog restarts it (web/js/gps.js, test/gps.test.js). On the phone after the fix: Android registered the request at 1 s instead of 30 s, and no restarts in 40 s. A location service now keeps GPS running in the background while a route is active (checked with dumpsys location). Still needs the outdoor and screen-locked check on a drive.
