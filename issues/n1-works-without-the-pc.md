# Issue N1: Works without the PC

Status: Built, awaiting test
Phase: Standalone phone build (board #60)

Done when: Away from home and not connected to the PC, adb or anything else,
           the phone searches, routes and navigates a real drive.

Steps:
1. Install the current debug APK, then disconnect from the PC.
2. Away from home Wi-Fi, search a place and start a route over mobile data.
3. Drive it; check search, the route line, the banner and the prompts.
4. Tap the ! button and check it says reports need the home server.

## Status log

- 2026-10-30: Built, awaiting test. With the PC's dev server stopped, search and a route worked on the phone through native HTTP to Nominatim and OSRM. Not yet tried on a drive away from home.
