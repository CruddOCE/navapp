# Issue 2c: Each manoeuvre is spoken once at 400 m, 100 m and 20 m

Status: Built, awaiting test
Phase: 2 (navigator)

Done when: On the drive from issue 2b, every manoeuvre is announced at 400 m,
           100 m and 20 m, and each announcement is spoken once.

Steps:
1. Run the drive from issue 2b with voice on.
2. Log each prompt and its distance.
3. Check no prompt repeats and none is missed.

## Status log

- 2026-10-29: Built, awaiting test.
- 2026-10-30: Voice now tracks each turn by its instruction and position, not its step number, so after a reroute a new turn gets its full prompts and the same turn is not repeated. Distance to the turn is now measured along the road. Every OSRM manoeuvre type now has its own wording (fork, merge, ramps, end of road, roundabout exit number) instead of "Continue on". Road test still to do.
