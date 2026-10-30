# navapp (placeholder name)

Progress by slice (built, awaiting testing, bugs, planned): https://github.com/users/CruddOCE/projects/1

A Waze-style navigation app. Current build: map, place search, a drawn route, turn-by-turn guidance with voice prompts, and an Android debug build for testing on a phone. Crowd reports, Android Auto and public-release work come in later phases (see `HANDOFF.md`).

## Run it in a browser

Needs Node 22.5 or newer. Tested on Node 24.

```bash
npm install
npm start
```

Open http://localhost:8090/. Allow location when asked. Search works without it.

## Test it

```bash
npm test
```

Tests are hand-rolled (`test/run.js`) and use recorded fixtures, so they need no network. The server tests use an OS-picked port, so they never collide with the dev server.

## Build the Android test app

The installed app does not need the PC, adb or anything else to navigate. Map, place search, routing and GPS all work on their own with mobile data. Only crowd reports need the PC, because the PC runs the shared report server for now.

For reports to work, three things must be true:

1. **The server runs on this PC** (`npm start`) and stays running while you test.
2. **The phone is on the same Wi-Fi network** as this PC. Find this PC's Wi-Fi address with `ipconfig` (the "Wireless LAN adapter Wi-Fi" IPv4 line).
3. **Windows Firewall allows port 8090** on the private network. The first run usually shows a prompt; allow it. If the phone can't reach the server, check this first.

Then build with that address:

```bash
NAV_API_BASE=http://<PC-IP>:8090 npm run android:build
```

Replace `<PC-IP>` with your PC's Wi-Fi address. The build writes it into `web/js/config.js`, bundles the APK, then restores the file. The APK lands at `dist/navapp-debug.apk`.

The address is only used for crowd reports. It is baked into the APK at build time, so the phone keeps using whatever address the APK was built with, even after the PC's address changes. Tapping the ! button then says "Reports need the home server".

When the PC's Wi-Fi address changes:

1. Find the new address with `ipconfig`. Use the "Wireless LAN adapter Wi-Fi" IPv4 line.
2. Rebuild the APK with the new address:

   ```bash
   NAV_API_BASE=http://<new-PC-IP>:8090 npm run android:build
   ```

3. Install it on the phone (wireless debugging must be connected first, see above):

   ```bash
   npm run android:install
   ```

The build script writes the address into `web/js/config.js` only while it runs, then restores the file. Nothing in the repo keeps the address.

### Install over Wi-Fi debugging

USB debugging doesn't work on this PC, so use wireless debugging:

1. On the phone, open **Settings > Developer options > Wireless debugging** and turn it on.
2. Choose **Pair device with pairing code**. Note the pairing IP:port and the six-digit code.
3. On this PC, run `adb pair <ip:port>` with the pairing values, then enter the code.
4. Note the **IP address and port** shown on the main Wireless debugging screen, and run `adb connect <ip:port>`.
5. Install and launch:

```bash
npm run android:install
```

`adb` is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`. If it isn't on PATH, run it with the full path.

### Using it away from home

Once installed, the app needs nothing from the PC to navigate:

- **Search and routing** go straight from the phone to Nominatim and OSRM over mobile data, using Capacitor's native HTTP so the app can send its identifying User-Agent (both services refuse requests without one).
- **GPS keeps running with the screen locked** while a route is active. Starting a route starts a location service, shown as a "navapp is navigating" notification, and ending the route stops it. On first launch Android asks to allow notifications; allow it so the notification shows. GPS still works if you refuse.
- **Crowd reports** need the report server on the PC. Away from it, the ! button says "Reports need the home server" instead of opening the report sheet.
- **The map** still needs internet: MapLibre loads from a CDN and the map tiles come from OpenFreeMap.

## Configuration (env vars, server side)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8090` | Dev server port |
| `NOMINATIM_URL` | `https://nominatim.openstreetmap.org` | Place search. Public service, for development only |
| `OSRM_URL` | `https://router.project-osrm.org` | Routing. Public demo server, for development only |
| `USER_AGENT` | `navapp/dev (placeholder contact)` | Sent to Nominatim and OSRM, which both refuse requests without an identifying User-Agent |
Place search needs no setting. It returns places within 100 km of the device as "Nearby", and everything else as "Further away", using the device position the app sends.

Build-side: `NAV_API_BASE` (see above).

## Layout

- `server/index.js`: Express app. Serves `web/`, plus `/api/geocode`, `/api/route` and `/api/reports`. A desktop browser uses it for search and routing; the Android app only uses it for reports.
- `web/js/geocode.js`: place search against Nominatim, with a cache and a one-request-per-second limit. Runs on the server (the browser's proxy) and inside the Android app.
- `web/js/routing.js`: OSRM wrapper. Converts OSRM's `[lng, lat]` into `{lat, lng}`, keeps each step's duration for the ETA, and builds the instruction text for every OSRM manoeuvre type. Runs on the server and inside the Android app.
- `web/js/api.js`: search and routing for the map. Direct to Nominatim and OSRM through native HTTP in the Android app, through the dev server in a browser.
- `web/js/gps.js`: the Android GPS watch and its 20 s watchdog. Asks for a fix every second.
- `web/js/nav.js`: navigator. Measures progress along the route line, so a GPS gap cannot leave the prompt on a passed turn. Reports the current step, distance to the next turn along the road, distance and time left, and off-route state. No network, no timers.
- `web/js/voice.js`: spoken prompts at 400 m, 100 m and 20 m, once each per manoeuvre. A manoeuvre is known by its instruction and position, so prompts survive a reroute.
- `web/js/map.js`: map screen, search, GPS and the navigation view. Reroutes at most once every 10 seconds.
- `web/js/config.js`: API base. Empty in the repo; the Android build sets it.
- `android/`: Capacitor Android project. The location and notification permissions are in its manifest, and cleartext HTTP is allowed so the phone can reach the PC's report server. `NavLocationService.java` is the location service that keeps GPS running while a route is active; `car/` is the Android Auto screen.
- `scripts/build-android.js`: builds the APK and installs it.
- `test/`: `run.js` runner, `*.test.js` suites, `fixtures/` for recorded responses and a GPS trace.

## Notes

- The Android debug build allows plain HTTP to the report server, for testing on home Wi-Fi only. The release build needs an HTTPS API.
- The map tiles come from `tile.openstreetmap.org`, which is for light development use. Switch to a tile host with its own terms before release.
- Capacitor is pinned to 8.5.3, which was released 2026-10-07. Newer versions are not used until they are at least two weeks old.
- The app name and package id (`com.example.navapp`) are placeholders.
