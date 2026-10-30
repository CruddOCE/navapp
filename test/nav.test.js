const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { createNavigator, distanceM, distanceToRouteM } = require('../web/js/nav');

const trace = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'fixtures', 'gps-trace.json'), 'utf8')
);
// A real 22 km OSRM route with 17 steps, for replaying whole drives.
const fremantle = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'fixtures', 'route-perth-fremantle.json'), 'utf8')
).route;

// Fixes along the route line every spacingM metres, each tagged with how far
// along the line it is. The last fix is the route's end point.
function drive(route, spacingM) {
  const g = route.geometry;
  const fixes = [];
  let along = 0;
  let next = 0;
  for (let i = 0; i < g.length - 1; i += 1) {
    const a = { lat: g[i][0], lng: g[i][1] };
    const b = { lat: g[i + 1][0], lng: g[i + 1][1] };
    const seg = distanceM(a, b);
    while (next <= along + seg) {
      const t = seg === 0 ? 0 : (next - along) / seg;
      fixes.push({ lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t, alongM: next });
      next += spacingM;
    }
    along += seg;
  }
  const end = g[g.length - 1];
  fixes.push({ lat: end[0], lng: end[1], alongM: along });
  return fixes;
}

// How far along the line each step's manoeuvre sits (its nearest vertex,
// searched forward from the previous step), worked out independently of nav.js.
function stepsAlong(route) {
  const g = route.geometry;
  const cum = [0];
  for (let i = 1; i < g.length; i += 1) {
    cum.push(cum[i - 1] + distanceM({ lat: g[i - 1][0], lng: g[i - 1][1] }, { lat: g[i][0], lng: g[i][1] }));
  }
  let from = 0;
  return route.steps.map((s) => {
    let best = from;
    for (let i = from; i < g.length; i += 1) {
      if (distanceM(s.location, { lat: g[i][0], lng: g[i][1] }) < distanceM(s.location, { lat: g[best][0], lng: g[best][1] })) best = i;
    }
    from = best;
    return cum[best];
  });
}

// The step a driver at alongM should be heading for: the first one still
// ahead by more than the 25 m arrival radius.
function expectedStep(stepAt, alongM) {
  for (let s = 0; s < stepAt.length - 1; s += 1) {
    if (stepAt[s] > alongM + 25) return s;
  }
  return stepAt.length - 1;
}

module.exports = [
  {
    name: 'distanceM is about 111 m per 0.001 deg of latitude',
    fn: async () => {
      const d = distanceM({ lat: -31.95, lng: 115.86 }, { lat: -31.951, lng: 115.86 });
      assert.ok(Math.abs(d - 111.2) < 0.5, `got ${d}`);
    },
  },
  {
    name: 'a fix midway between two route vertices is on route (segment distance, not vertex)',
    fn: async () => {
      const d = distanceToRouteM({ lat: -31.9505, lng: 115.86 }, trace.route.geometry);
      assert.ok(d < 1, `got ${d}`);
    },
  },
  {
    name: 'replaying the recorded trace: step advance, off-route after 3 fixes, arrival',
    fn: async () => {
      const nav = createNavigator(trace.route);
      const out = trace.fixes.map((f) => nav.update(f));

      // Fix 1 is 55 m past the start point, so the depart step is skipped.
      // The prompt is now the turn, about 500 m ahead.
      assert.strictEqual(out[0].stepIndex, 1);
      assert.strictEqual(out[0].instruction, 'Turn right onto Mill St');
      assert.ok(out[0].distanceToNextM > 400, `got ${out[0].distanceToNextM}`);

      // Fix 4 is inside 25 m of the turn: prompt moves to the arrival step.
      assert.strictEqual(out[3].stepIndex, 2);
      assert.strictEqual(out[3].instruction, 'You have arrived');

      // Fixes 5 and 6 are off route; fix 7 is the third in a row.
      assert.strictEqual(out[4].offRoute, false);
      assert.strictEqual(out[5].offRoute, false);
      assert.strictEqual(out[6].offRoute, true);

      // Fix 8 is back on the line at the destination.
      assert.strictEqual(out[7].offRoute, false);
      assert.strictEqual(out[7].arrived, true);
      assert.strictEqual(out[7].distanceToNextM, 0);
    },
  },
  {
    name: 'a single off-route fix does not set offRoute',
    fn: async () => {
      const nav = createNavigator(trace.route);
      const r = nav.update({ lat: -31.9540, lng: 115.8615 });
      assert.strictEqual(r.offRoute, false);
    },
  },
  {
    name: 'real 22 km route at 15 m spacing: every turn in order, then arrival',
    fn: async () => {
      const nav = createNavigator(fremantle);
      const seen = [];
      let out;
      for (const f of drive(fremantle, 15)) {
        out = nav.update(f);
        if (seen[seen.length - 1] !== out.stepIndex) seen.push(out.stepIndex);
        assert.strictEqual(out.offRoute, false);
      }
      // The first fix is at the start, so depart and the first turn's run-up
      // are passed at once; from there every step shows in turn.
      const firstShown = seen[0];
      const expected = [];
      for (let s = firstShown; s < fremantle.steps.length; s += 1) expected.push(s);
      assert.deepStrictEqual(seen, expected);
      assert.strictEqual(out.arrived, true);
    },
  },
  {
    name: 'fixes 300 m apart (GPS gaps) never leave the prompt on a passed turn',
    fn: async () => {
      const nav = createNavigator(fremantle);
      const stepAt = stepsAlong(fremantle);
      let prev = 0;
      let out;
      for (const f of drive(fremantle, 300)) {
        out = nav.update(f);
        assert.ok(out.stepIndex >= prev, `went back from ${prev} to ${out.stepIndex}`);
        prev = out.stepIndex;
        if (!out.arrived) {
          assert.strictEqual(
            out.stepIndex,
            expectedStep(stepAt, f.alongM),
            `at ${Math.round(f.alongM)} m the prompt is "${out.instruction}"`
          );
        }
      }
      assert.strictEqual(out.arrived, true);
    },
  },
  {
    name: 'a fix 150 m past a turn, with none near it, moves the prompt to the next turn',
    fn: async () => {
      const nav = createNavigator(fremantle);
      const stepAt = stepsAlong(fremantle);
      const fixes = drive(fremantle, 5);
      const at = (m) => fixes.find((f) => f.alongM >= m);
      // Step 3 is the right turn onto Wellington Street. Step 2 is only 108 m
      // before it, so the approach fix sits 80 m short of the turn.
      assert.strictEqual(nav.update(at(stepAt[3] - 80)).stepIndex, 3);
      const r = nav.update(at(stepAt[3] + 150));
      assert.strictEqual(r.stepIndex, 4, `prompt still "${r.instruction}"`);
      assert.strictEqual(r.instruction, 'Turn left onto Elder Street');
    },
  },
  {
    name: 'remaining distance and time only go down, from the full route to zero',
    fn: async () => {
      const nav = createNavigator(fremantle);
      const fixes = drive(fremantle, 50);
      const first = nav.update(fixes[0]);
      assert.ok(Math.abs(first.remainingM - fremantle.distanceM) < 50, `start ${first.remainingM}`);
      assert.ok(Math.abs(first.remainingS - fremantle.durationS) < 30, `start ${first.remainingS}`);
      let lastM = first.remainingM;
      let lastS = first.remainingS;
      let out;
      for (const f of fixes.slice(1)) {
        out = nav.update(f);
        assert.ok(out.remainingM <= lastM + 0.001, `distance went up at ${f.alongM}`);
        assert.ok(out.remainingS <= lastS + 0.001, `time went up at ${f.alongM}`);
        lastM = out.remainingM;
        lastS = out.remainingS;
      }
      assert.strictEqual(out.remainingM, 0);
      assert.strictEqual(out.remainingS, 0);
    },
  },
  {
    name: 'halfway along, the time left is near the route time still to drive, not the whole trip',
    fn: async () => {
      const nav = createNavigator(fremantle);
      const fixes = drive(fremantle, 50);
      let out;
      for (const f of fixes.slice(0, Math.floor(fixes.length / 2))) out = nav.update(f);
      // Halfway by distance; the freeway is faster, so time left is well under the full trip.
      assert.ok(out.remainingS < fremantle.durationS * 0.75, `got ${out.remainingS} of ${fremantle.durationS}`);
      assert.ok(out.remainingS > 0);
    },
  },
  {
    name: 'distance to the next turn is measured along the road, never less than straight line',
    fn: async () => {
      const nav = createNavigator(fremantle);
      let longer = 0;
      for (const f of drive(fremantle, 50)) {
        const out = nav.update(f);
        if (out.arrived) break;
        const straight = distanceM(f, out.stepLocation);
        assert.ok(out.distanceToNextM >= straight - 2, `along ${out.distanceToNextM} < straight ${straight}`);
        if (out.distanceToNextM > straight * 1.05 + 5) longer += 1;
      }
      assert.ok(longer > 0, 'no curved stretch measured longer than the straight line');
    },
  },
  {
    name: 'route with no steps is rejected',
    fn: async () => {
      assert.throws(() => createNavigator({ steps: [], geometry: [] }), /needs a route with steps/);
    },
  },
];
