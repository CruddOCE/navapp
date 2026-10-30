# Issue N2: Navigation keeps working with the phone locked

Status: Built, awaiting test
Phase: Native navigation (board #61)

Done when: On a drive with the phone locked, the voice prompts play, the car
           screen's turn card updates, and the phone shows the right turn as
           soon as it is unlocked.

Steps:
1. Start a route, then lock the phone.
2. Drive past at least three turns; listen for each prompt.
3. With Android Auto connected, watch the turn card change at each turn.
4. Unlock the phone; check the banner and ETA match where you are.

## Status log

- 2026-10-31: Built, awaiting test. With navapp behind the home screen for 3 minutes, the native engine kept handling fixes, spoke its prompt and rerouted once, and the page caught up on return. On the Desktop Head Unit the turn card showed the right-turn arrow and updated each fix. Not yet checked with the screen locked on a drive.
