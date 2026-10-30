# navapp: build plan and status

Debug APK for testing on the owner's phone over Wi-Fi. Not a release build. The API address is baked in at build time (NAV_API_BASE).

**Source of truth:** the roadmap board, https://github.com/users/CruddOCE/projects/1. Each feature is one card with a Stage, an area and its open bugs. Change a card's Stage on the board; this file is a snapshot of the board and is updated with it.

## Goal

A Waze-style navigation app for personal use first, Android first (Capacitor). Public release comes later and is out of scope for this plan.

## Features by stage

### Done (16)

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
| Turn instructions and banner | Navigation | Top banner shows next instruction and distance. Verified on phone. | None open. |
| ETA bar and End button | Navigation | ETA and remaining distance at bottom; End clears the route. Verified on phone. | None open. |
| Location permission | Location | Location permission requested and granted on phone. | None open. |
| Android app shell | Android | Capacitor Android app, installed over Wi-Fi debugging. Verified on phone. | None open. |
| Phone-to-server access (CORS) | Android | Allows only the Android app origin to call the server. Verified on phone. | None open. |
| Release v0.01 and changelog | Platform | Published as a pre-release with the debug APK and CHANGELOG.md. | None open. |

### Awaiting testing (15)

| Feature | Area | Notes | Open bugs |
|---|---|---|---|
| Step tracking | Navigation | Tracks which step is next. Unit tested with a recorded GPS trace; not yet tested on a drive. | Unverified on the road. |
| Off-route detection | Navigation | Off route after 3 bad fixes more than 40 m away. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Reroute | Navigation | Recalculates from current position, at most every 10 s. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Arrival detection | Navigation | Stops navigation within 25 m of the destination. Unit tested; not yet tested on a drive. | Unverified on the road. |
| Voice prompts | Navigation | Speaks at 400 m, 100 m and 20 m, once each. Unit tested; not yet heard on the phone. | Speech not yet confirmed on the phone. |
| GPS watchdog | Location | Restarts the GPS watch after 20 s with no fix. Fixes arrive after restart; steady fixes outdoors not yet confirmed. | Indoors the phone gets one fix and then stalls. |
| Report type picker | Reports | Five report types (police, crash, hazard, jam, closed). Built; not yet tested on phone. | Unverified on phone. |
| Report pins on map | Reports | Coloured pins for each report. Server tested; phone not yet confirmed. | Unverified on phone. |
| Report card (still there / gone) | Reports | Tapping a pin opens the vote card. Built; not yet tested on phone. | Unverified on phone. |
| Posting with rate limit | Reports | Posting, limited to 5 per device every 10 minutes. Server tested (40 tests); not yet tested on phone. | Unverified on phone. |
| Voting, one vote per device | Reports | Still there / gone, one vote per device. Server tested; not yet tested on phone. | Device IDs are chosen by the phone and can be faked (see Device-ID protection). |
| Hiding and 30-minute expiry | Reports | Reports hidden after two dismissals and removed after 30 minutes. Server tested; not yet tested on phone. | Unverified on phone. |
| Live updates over WebSocket | Reports | New and changed reports pushed to open phones. Server tested; two-phone test not done. | Unverified across two phones. |
| Android Auto car screen | Android | Navigation template screen; opens on Desktop Head Unit with no crash. Route on car not yet tested. | Car service allows every host in every build (see the host allowlist card). |
| Route state from phone to car | Android | Next instruction and distance sent to car during navigation. Not yet tested with a live route. | Unverified with a live route. |

### Built (1)

| Feature | Area | Notes | Open bugs |
|---|---|---|---|
| Report button | Reports | Orange ! button on the map. Visible on phone. | Tapping ! then tapping outside it crashes the app. Not yet reproduced. |

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
