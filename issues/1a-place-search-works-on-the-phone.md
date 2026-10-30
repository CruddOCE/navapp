# Issue 1a: Place search works on the phone

Status: Tested and confirmed
Phase: 1 (map, search, route)

Done when: Searching a place with location on shows the nearby results first,
           on the phone.

Steps:
1. Install the debug APK (see issue 4a).
2. Turn location on and open search.
3. Search a place you know is nearby.
4. Check the nearby section appears above the further-away section.

## Status log

- 2026-10-29: Built, awaiting test. BUILD-PLAN.md says "tested on phone"; HANDOFF.md says the phone was not verified. Recorded as not verified, per owner.
- 2026-10-30: Confirmed on the phone (Android 16, Wi-Fi adb). Typed "Fremantle" into the search box. The result list showed a NEARBY heading above the results, with "Fremantle, Western Australia, 6160, Australia" under it. The phone's own location was known at the time, so the nearby group was filled.
