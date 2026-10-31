# Issue 4a-1: Route shows on the car screen

Status: Built, awaiting test
Phase: 4a (Android Auto)

Done when: A route started on the phone draws on the Desktop Head Unit.

Steps:
1. Start the DHU and connect the phone.
2. Start a route on the phone.
3. Check the route draws on the DHU screen.

## Status log

- 2026-10-29: Built, awaiting test. Car screen opens on the DHU with no crash; route on the car not confirmed.
- 2026-10-31: Partly confirmed on the Desktop Head Unit. A route started on the phone shows on the car as a turn card: the right-turn arrow, the distance (185 m) and "Turn right onto Crossman Road", updated on each fix. End on the car ends the route on the phone. First run crashed ("Action strip for this template must be set") and showed no card (turn sent as unknown with no icon); both fixed. The route line itself does not draw yet: there is no map on the car screen, so the area behind the card is black. Stays Built, awaiting test until the map is drawn.
- 2026-10-31: The route line now draws on the car: the map behind the turn card shows it with the position arrow (slice N3). Still awaiting the locked-phone check and a drive.
