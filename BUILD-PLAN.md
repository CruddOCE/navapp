# navapp: build plan and status

Debug APK for testing on the owner's phone over Wi-Fi. Not a release build. The API address is baked in at build time (NAV_API_BASE).

**Source of truth:** the roadmap board, https://github.com/users/CruddOCE/projects/1. Each feature is one card with a Stage, an area and its open bugs. Change a card's Stage on the board; this file is a snapshot of the board and is updated with it.

## Goal

A Waze-style navigation app for personal use first, Android first (Capacitor). Public release comes later and is out of scope for this plan.

## Features by stage

### Done (14)

| Feature | Area | Notes | Open bugs |
|---|---|---|---|
| Map display (pale navigation style) | Map | Map drawn with MapLibre and the pale OpenFreeMap-based style. Verified on phone. | None open. |
| Position dot | Map | Blue dot at rest. Verified on phone. | None open. |
| Recenter button | Map | Round recenter button. Verified on phone. | None open. |
| Heading-up map and navigation arrow | Map | Arrow while navigating; map turns to direction of travel. Arrow seen on phone. | None open. |
| Place search | Search | Search box sends text to the server. Verified on phone. | None open. |
| Nearby and further-away results | Search | Results inside 100 km listed first under Nearby, then Further away. Verified on phone. | None open. |
| Bottom search sheet | Search | White sheet with grab handle and search bar, as in the mockup. Verified on phone. | None open. |
| Route request and drawing | Route | Route from device position to chosen place, drawn on map. Verified on phone (Boddington, 20 min, 12.6 km). | None open. |
| Route summary (time and distance) | Route | Time and distance shown under the search. Verified on phone. | None open. |
| Start button | Route | Start begins navigation. Verified on phone. | None open. |
| Location permission | Location | Location permission requested and granted on phone. | None open. |
| Android app shell | Android | Capacitor Android app, installed over Wi-Fi debugging. Verified on phone. | None open. |
| Phone-to-server access (CORS) | Android | Allows only the Android app origin to call the server. Verified on phone. | None open. |
| Release v0.01 and changelog | Platform | Published as a pre-release with the debug APK and CHANGELOG.md. | None open. |

### Awaiting testing (19)

| Feature | Area | Notes | Open bugs |
|---|---|---|---|
| Turn instructions and banner | Navigation | Top banner shows the next instruction and the distance to it, measured along the road. Verified on phone before 2026-10-30. Changed 2026-10-30: every OSRM manoeuvre type now has its own wording (fork, merge, ramps, end of road, U-turn, roundabout exit number) instead of "Continue on", and the banner no longer sticks on a passed turn after a GPS gap. Replayed on a real 22 km route in the tests and in a simulated browser drive. Not yet seen on a real drive. | New wording not yet checked on a real drive. |
| Step tracking | Navigation | Tracks which step is next by measuring progress along the route line, so a GPS gap or stall passes every turn behind the car at once (fixed 2026-10-30; before that the prompt could stick on a passed turn). Tested by replaying a real 22 km route at 15 m spacing and with 300 m gaps. Not yet tested on a drive. | Unverified on the road. |
| ETA bar and End button | Navigation | ETA and distance left at the bottom; End clears the route. Changed 2026-10-30: the ETA is now plus the time still to drive (each road's own OSRM duration) instead of now plus the whole trip, which made it slide later on every fix. The bottom line now reads "12.1 km left" instead of the total route length. Checked in a simulated browser drive. Not yet checked on a real drive. | New ETA not yet checked on a real drive. |
| Off-route detection | Navigation | Off route after 3 bad fixes more than 40 m away. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Reroute | Navigation | Recalculates from current position, at most every 10 s. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Arrival detection | Navigation | Stops navigation within 25 m of the destination. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Voice prompts | Navigation | Speaks at 400 m, 100 m and 20 m, once each. Since 2026-10-30 a turn is known by its instruction and position, not its step number, so after a reroute a new turn gets its full prompts and the same turn is not repeated. Unit tested; not yet heard on the phone. | Speech not yet confirmed on the phone. |
| GPS watchdog | Location | Restarts the GPS watch after 20 s with no fix. Root cause of the stalls found 2026-10-30: the plugin used the 30 s timeout as the update interval, so fixes came every 30 s while the watchdog restarted the watch at 20 s, and error retries left old watches running. Now the watch asks for a fix every second, only one runs at a time, and only the watchdog restarts it. On the phone Android now registers the request at 1 s. Since 2026-10-30 the navigator also copes with the position jump after a stall (see #33). | Steady fixes outdoors and with the screen locked not yet confirmed on a drive. |
| Report button | Reports | Orange ! button on the map. Visible on phone. On 2026-10-30, five tap-out cycles on the phone (open the sheet, tap the map) gave no crash and nothing fatal in logcat. | The original crash has not been reproduced. Tapping the map does not close the report sheet; only Cancel does. |
| Report type picker | Reports | Five report types (police, crash, hazard, jam, closed). Built; not yet tested on phone. | Unverified on phone. |
| Report pins on map | Reports | Coloured pins for each report. Server tested; phone not yet confirmed. | Unverified on phone. |
| Report card (still there / gone) | Reports | Tapping a pin opens the vote card. Checked in the browser on 2026-10-30: the card opens and "Still there" updates the count. Not yet tested on the phone. | Tapping the map does not close the card, and it has no close control. Unverified on phone. |
| Posting with rate limit | Reports | Posting, limited to 5 per device every 10 minutes. Server tested (40 tests); not yet tested on phone. | Unverified on phone. |
| Voting, one vote per device | Reports | Still there / gone, one vote per device. Server tested; not yet tested on phone. | Device IDs are chosen by the phone and can be faked (see Device-ID protection). |
| Hiding and 30-minute expiry | Reports | Reports hidden after two dismissals and removed after 30 minutes. Server tested; not yet tested on phone. | Unverified on phone. |
| Live updates over WebSocket | Reports | New and changed reports pushed to open phones. Server tested; two-phone test not done. | Unverified across two phones. |
| Android Auto car screen | Android | Navigation template screen; opens on Desktop Head Unit with no crash. Route on car not yet tested. | Car service allows every host in every build (see the host allowlist card). |
| Route state from phone to car | Android | Next instruction and distance sent to car during navigation. Not yet tested with a live route. | Unverified with a live route. |
| Works without the PC (standalone phone build) | Platform | The installed app navigates without the PC, adb or a debugging session. Search and routing go straight from the phone to Nominatim and OSRM over mobile data, through Capacitor's native HTTP with an identifying User-Agent. A location service keeps GPS running with the screen locked while a route is active. Only crowd reports still need the PC's report server; away from it, the ! button says so. Built and checked on 2026-10-30 with the PC server stopped: search, route and background location all worked on the phone. | Not yet tried on a real drive away from home. |

### Planned (7)

| Feature | Area | Notes | Open bugs |
|---|---|---|---|
| Route alternatives | Route | Show two or three routes with times and let the driver pick one. | Not started. |
| Device-ID protection | Reports | Server-issued signed device tokens and per-IP limits, so one person cannot fake many devices. | Not started; needs approval. |
| Car host allowlist for release | Android | Allow every host only in debuggable builds; release uses an Android Auto host allowlist. | Not started; needs approval. |
| iOS build | Android | Capacitor iOS build. Needs a Mac or cloud macOS builder and an Apple Developer account. | Not started. |
| Live traffic | Platform | Traffic colours from anonymised driver speeds. Needs enough users to be useful. | Not started. |
| Release infrastructure | Platform | HTTPS API, self-hosted OSRM and Nominatim, a tile host with its own terms, release signing. | Not started. |
| Public release items | Platform | Real name and icon, privacy policy, report moderation, store listing, Android Auto review. | Not started. |

## Updating this plan

1. Change the Stage on the board card.
2. Update the matching row here in the same commit.
3. A feature moves to Done only after it is verified on the phone or the car, not just unit tested.

## How to build and install
    cd navapp
    NAV_API_BASE=http://<PC Wi-Fi IP>:8090 npm run android:build
    npm run android:install
The server must be running on the PC (npm start), and the phone must be on the same Wi-Fi.
