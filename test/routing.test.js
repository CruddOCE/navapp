const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { createRouter, parseOsrmResponse, instructionFor } = require('../web/js/routing');

const fixture = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'fixtures', 'osrm-route.json'), 'utf8')
);

function fixtureFetch(body, status = 200, seen = []) {
  return async (url) => {
    seen.push(url);
    return { ok: status === 200, status, json: async () => body };
  };
}

module.exports = [
  {
    name: 'parses distance, duration and swaps OSRM [lng, lat] into {lat, lng} steps',
    fn: async () => {
      const route = parseOsrmResponse(fixture);
      assert.strictEqual(route.distanceM, 1240.5);
      assert.strictEqual(route.durationS, 210.3);
      assert.deepStrictEqual(route.geometry[0], [-31.9505, 115.8575]);
      assert.deepStrictEqual(route.steps[1].location, { lat: -31.951, lng: 115.8601 });
    },
  },
  {
    name: 'builds readable instructions for depart, turn and arrive',
    fn: async () => {
      const steps = parseOsrmResponse(fixture).steps;
      assert.strictEqual(steps[0].instruction, 'Head left Mounts Bay Rd');
      assert.strictEqual(steps[1].instruction, 'Turn right onto Harbour St');
      assert.strictEqual(steps[2].instruction, 'You have arrived');
    },
  },
  {
    name: 'every OSRM manoeuvre type gets its own wording, not "Continue on"',
    fn: async () => {
      const cases = [
        [{ type: 'turn', modifier: 'left' }, 'Turn left onto High St'],
        [{ type: 'turn', modifier: 'straight' }, 'Go straight onto High St'],
        [{ type: 'end of road', modifier: 'right' }, 'Turn right onto High St'],
        [{ type: 'turn', modifier: 'uturn' }, 'Make a U-turn onto High St'],
        [{ type: 'continue', modifier: 'uturn' }, 'Make a U-turn onto High St'],
        [{ type: 'fork', modifier: 'slight left' }, 'Keep left onto High St'],
        [{ type: 'fork', modifier: 'straight' }, 'Keep straight onto High St'],
        [{ type: 'merge', modifier: 'slight right' }, 'Merge right onto High St'],
        [{ type: 'on ramp', modifier: 'right' }, 'Take the ramp on the right onto High St'],
        [{ type: 'off ramp', modifier: 'slight left' }, 'Take the exit on the left onto High St'],
        [{ type: 'roundabout', exit: 3 }, 'At the roundabout, take exit 3 onto High St'],
        [{ type: 'rotary' }, 'Take the roundabout onto High St'],
        [{ type: 'roundabout turn', modifier: 'left' }, 'At the roundabout, turn left onto High St'],
        [{ type: 'exit roundabout' }, 'Exit the roundabout onto High St'],
        [{ type: 'exit rotary' }, 'Exit the roundabout onto High St'],
        [{ type: 'new name' }, 'Continue onto High St'],
        [{ type: 'continue', modifier: 'straight' }, 'Continue on High St'],
        [{ type: 'notification' }, 'Continue on High St'],
      ];
      for (const [maneuver, want] of cases) {
        assert.strictEqual(instructionFor({ name: 'High St', maneuver }), want, JSON.stringify(maneuver));
      }
    },
  },
  {
    name: 'an unnamed road uses its number, and a bare number reads as "route N"',
    fn: async () => {
      assert.strictEqual(
        instructionFor({ name: '', ref: '1', maneuver: { type: 'off ramp', modifier: 'left' } }),
        'Take the exit on the left onto route 1'
      );
      assert.strictEqual(
        instructionFor({ name: '', ref: 'M1', maneuver: { type: 'merge', modifier: 'left' } }),
        'Merge left onto M1'
      );
      assert.strictEqual(instructionFor({ name: '', maneuver: { type: 'new name' } }), 'Continue');
    },
  },
  {
    name: 'each step keeps its OSRM duration for the ETA',
    fn: async () => {
      const steps = parseOsrmResponse(fixture).steps;
      assert.deepStrictEqual(steps.map((s) => s.durationS), [80.1, 130.2, 0]);
    },
  },
  {
    name: 'no route from OSRM throws a clear error, not a crash',
    fn: async () => {
      assert.throws(
        () => parseOsrmResponse({ code: 'NoRoute', routes: [] }),
        /no route found \(NoRoute\)/
      );
    },
  },
  {
    name: 'getRoute builds the OSRM URL with lng,lat order',
    fn: async () => {
      const seen = [];
      const router = createRouter({
        fetchImpl: fixtureFetch(fixture, 200, seen),
        baseUrl: 'https://osrm.test',
      });
      await router.getRoute({
        from: { lat: -31.9505, lng: 115.8575 },
        to: { lat: -31.9535, lng: 115.8602 },
      });
      assert.ok(
        seen[0].startsWith('https://osrm.test/route/v1/driving/115.8575,-31.9505;115.8602,-31.9535?'),
        seen[0]
      );
    },
  },
  {
    name: 'same start and destination is rejected before any upstream call',
    fn: async () => {
      const seen = [];
      const router = createRouter({
        fetchImpl: fixtureFetch(fixture, 200, seen),
        baseUrl: 'https://osrm.test',
      });
      const p = { lat: -31.95, lng: 115.86 };
      await assert.rejects(() => router.getRoute({ from: p, to: p }), /same/);
      assert.strictEqual(seen.length, 0);
    },
  },
  {
    name: 'non-200 from the routing service throws',
    fn: async () => {
      const router = createRouter({
        fetchImpl: fixtureFetch({}, 503),
        baseUrl: 'https://osrm.test',
      });
      await assert.rejects(
        () => router.getRoute({ from: { lat: 1, lng: 1 }, to: { lat: 2, lng: 2 } }),
        /returned 503/
      );
    },
  },
];
