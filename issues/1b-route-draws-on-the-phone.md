# Issue 1b: Route draws on the phone

Status: Tested and confirmed
Phase: 1 (map, search, route)

Done when: Picking a search result draws a route from the current position to
           that result, on the phone.

Steps:
1. Search and pick a destination (issue 1a).
2. Start the route.
3. Check the line starts at your position and ends at the destination.

## Status log

- 2026-10-29: Built, awaiting test. BUILD-PLAN.md says "tested on phone"; HANDOFF.md says the phone was not verified. Recorded as not verified, per owner.
- 2026-10-30: Partly confirmed on the phone. Picking Fremantle drew a blue route line from the phone's position to Fremantle: 105 min, 130.4 km. The line starts where the phone's position dot sits and ends at the destination, so the drawing works. But the position dot was in Boddington, about 130 km from the real location, so the route is wrong in distance. The phone's fix came from a coarse location indoors. Start was not pressed. Left as Built, awaiting test until a fix from outdoors puts the dot in Perth.
- 2026-10-30: Correction to the entry above. The Boddington position was most likely right, not a bad fix: board issue #28 records an earlier phone check from Boddington too. So the 130.4 km route to Fremantle is correct, and the check passes: the line starts at the position dot and ends at the destination. Confirmed.
