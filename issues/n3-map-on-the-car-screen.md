# Issue N3: Map on the car screen

Status: Built, awaiting test
Phase: Android Auto (board card "Car screen map")

Done when: On the car screen during a route, the map shows the roads, the
           route line and the position arrow, turned to the direction of travel,
           and keeps moving with the phone locked.

Steps:
1. Connect the Desktop Head Unit (see HANDOFF) and start a route on the phone.
2. Check the map, route line and arrow behind the turn card, clear of it.
3. Lock the phone for 2 minutes and check the map keeps moving.
4. On a drive, check the arrow stays on the road and the map turns with it.

## Status log

- 2026-10-31: Built, awaiting test. On the Desktop Head Unit with the phone unlocked, the car map showed the phone's map style around the phone's position, heading-up, with the arrow pointing up and the blue route line ahead, clear of the turn card. It takes several seconds to load the first time. The adb connection dropped before the locked-phone check.
