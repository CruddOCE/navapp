# Issue 2b: Banner and ETA hold up over a full drive

Status: Built, awaiting test
Phase: 2 (navigator)

Done when: On a real drive, the banner shows the next instruction and distance,
           the ETA updates, and End returns to the map, for the whole route.

Steps:
1. A passenger operates the phone while you drive a route.
2. Note each banner change against the real turn.
3. Check the ETA and the End button.

## Status log

- 2026-10-29: Built, awaiting test. Navigation screen tested on phone; the full road drive is not done.
- 2026-10-30: Two fixes before the road test. The ETA now comes from the time still to drive (per-step OSRM durations), so it holds steady instead of sliding later. The bottom line now counts down the distance left ("12.1 km left") instead of showing the total route length. Also the prompt can no longer stick on a passed turn after a GPS gap. Replayed on a real 22 km route in test/nav.test.js; road test still to do.
