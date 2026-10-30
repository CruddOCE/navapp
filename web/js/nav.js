// Navigator: follows a route from server /api/route and reports where the
// device is on it. It holds no timers and makes no network calls. map.js
// feeds it GPS fixes and decides what to do with the answer (speak, reroute).
// Kept free of browser APIs so the same file runs in Node tests.
//
// Progress is measured along the route line, not by circles around each turn.
// A GPS stall or a dropped fix can jump the device past a turn without any fix
// landing near it, and a circles-only check would then leave the prompt stuck
// on a turn already behind the car.

const ARRIVE_RADIUS_M = 25;     // a step counts as passed inside this radius
const OFF_ROUTE_M = 40;         // more than this from the line is "off route"
const OFF_ROUTE_FIXES = 3;      // ...for this many fixes in a row
const BACKTRACK_SEGMENTS = 2;   // slack behind progress when matching a fix to the line

function distanceM(a, b) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// Closest point to pos on the segment a-b ([lat, lng] pairs). Returns the
// distance in metres and t, how far along the segment it is (0 to 1). Over a
// single route segment (a few hundred metres at most) a local flat-earth
// projection is accurate to well under a metre.
function projectOnSegment(pos, a, b) {
  const metresPerDegLat = 111195;
  const metresPerDegLng = metresPerDegLat * Math.cos((pos.lat * Math.PI) / 180);
  const ax = (a[1] - pos.lng) * metresPerDegLng;
  const ay = (a[0] - pos.lat) * metresPerDegLat;
  const bx = (b[1] - pos.lng) * metresPerDegLng;
  const by = (b[0] - pos.lat) * metresPerDegLat;
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  const t = lenSq === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lenSq));
  const px = ax + t * dx;
  const py = ay + t * dy;
  return { d: Math.sqrt(px * px + py * py), t };
}

// Distance from a point to the nearest segment of the route line. Vertex
// distance alone would call a fix off route when it sits midway between two
// vertices.
function distanceToRouteM(pos, geometry) {
  let best = Infinity;
  for (let i = 0; i < geometry.length - 1; i += 1) {
    const d = projectOnSegment(pos, geometry[i], geometry[i + 1]).d;
    if (d < best) best = d;
  }
  return best;
}

function createNavigator(route) {
  if (!route || !Array.isArray(route.steps) || route.steps.length === 0) {
    throw new Error('navigator needs a route with steps');
  }
  const geometry = Array.isArray(route.geometry) ? route.geometry : [];
  const steps = route.steps;
  const last = steps.length - 1;

  // Distance along the line from the start to each vertex.
  const cumM = [0];
  for (let i = 1; i < geometry.length; i += 1) {
    const a = { lat: geometry[i - 1][0], lng: geometry[i - 1][1] };
    const b = { lat: geometry[i][0], lng: geometry[i][1] };
    cumM.push(cumM[i - 1] + distanceM(a, b));
  }
  const lineM = cumM[cumM.length - 1];
  // Without a usable line, fall back to the circles-only rules.
  const hasLine = geometry.length >= 2 && lineM > 0;

  // Where each manoeuvre sits along the line. The search for each one starts
  // at the previous one, so a road the route uses twice maps to the right pass.
  const stepAtM = [];
  let from = 0;
  for (const step of steps) {
    let best = from;
    let bestD = Infinity;
    for (let i = from; i < geometry.length; i += 1) {
      const d = distanceM(step.location, { lat: geometry[i][0], lng: geometry[i][1] });
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    stepAtM.push(hasLine ? cumM[best] : 0);
    from = best;
  }

  const haveStepTimes = steps.every((s) => Number.isFinite(s.durationS));

  let stepIndex = 0;
  let missedFixes = 0;
  let arrived = false;
  let firstFix = true;
  let progressSeg = 0;
  let progressM = 0;
  // OSRM starts the route on the nearest road, so a car in a car park, a
  // driveway or a house can begin more than OFF_ROUTE_M from the line. Until
  // the first fix on the route, only moving further away from it counts as
  // off route; otherwise the app reroutes over and over before it has moved.
  let joined = false;
  let startOffsetM = null;

  // Matches a fix to the line, searching forward from current progress so a
  // later pass along the same road is never mistaken for the current one.
  // Takes the first stretch of segments within OFF_ROUTE_M and the closest
  // point inside that stretch.
  function locate(pos) {
    let found = null;
    for (let i = Math.max(0, progressSeg - BACKTRACK_SEGMENTS); i < geometry.length - 1; i += 1) {
      const p = projectOnSegment(pos, geometry[i], geometry[i + 1]);
      if (p.d <= OFF_ROUTE_M) {
        if (!found || p.d < found.d) found = { seg: i, d: p.d, alongM: cumM[i] + p.t * (cumM[i + 1] - cumM[i]) };
      } else if (found) {
        break;
      }
    }
    return found;
  }

  // OSRM's distance and the line's own length differ slightly, so the
  // remaining distance is scaled to match the total the app showed at the start.
  function remainingMetres() {
    if (arrived) return 0;
    if (!hasLine) return route.distanceM;
    return Math.max(0, lineM - progressM) * (route.distanceM / lineM);
  }

  // Each OSRM step covers the road from its manoeuvre to the next one, with
  // its own duration, so freeway and city stretches keep their own speeds.
  // The car is on the stretch of step stepIndex - 1, heading for stepIndex.
  function remainingSeconds(remainingM) {
    if (arrived) return 0;
    if (!haveStepTimes || !hasLine || stepIndex === 0) {
      return route.distanceM > 0 ? route.durationS * (remainingM / route.distanceM) : route.durationS;
    }
    const prev = stepIndex - 1;
    const stretchM = stepAtM[stepIndex] - stepAtM[prev];
    const leftOnStretch = stretchM > 0 ? Math.max(0, Math.min(1, (stepAtM[stepIndex] - progressM) / stretchM)) : 0;
    let s = leftOnStretch * steps[prev].durationS;
    for (let j = stepIndex; j <= last; j += 1) s += steps[j].durationS;
    return s;
  }

  function update(pos) {
    const fromLineM = distanceToRouteM(pos, geometry);
    const onRoute = fromLineM <= OFF_ROUTE_M;
    if (onRoute) joined = true;
    if (startOffsetM === null) startOffsetM = fromLineM;
    const away = joined ? !onRoute : fromLineM > startOffsetM + OFF_ROUTE_M;
    missedFixes = away ? missedFixes + 1 : 0;

    // Progress only moves forward, and only while on the route.
    if (onRoute && hasLine) {
      const at = locate(pos);
      if (at && at.alongM > progressM) {
        progressM = at.alongM;
        progressSeg = at.seg;
      }
    }

    // The depart step is only worth announcing from the start point. If the
    // first fix is already away from it (app opened mid-route), skip it.
    if (firstFix && steps[0].maneuver === 'depart' && last > 0) {
      if (distanceM(pos, steps[0].location) > ARRIVE_RADIUS_M) stepIndex = 1;
    }
    firstFix = false;

    // A step is passed once progress is beyond it, or once a fix lands inside
    // its radius. Every step passed since the last fix is skipped in one go.
    while (
      stepIndex < last &&
      ((hasLine && stepAtM[stepIndex] < progressM) || distanceM(pos, steps[stepIndex].location) <= ARRIVE_RADIUS_M)
    ) {
      stepIndex += 1;
    }
    if (stepIndex === last && distanceM(pos, steps[last].location) <= ARRIVE_RADIUS_M) {
      arrived = true;
    }

    // Along the road while on the route; straight line when off it.
    let distanceToNextM = 0;
    if (!arrived) {
      distanceToNextM = onRoute && hasLine
        ? Math.max(0, stepAtM[stepIndex] - progressM)
        : distanceM(pos, steps[stepIndex].location);
    }
    const remainingM = remainingMetres();

    return {
      stepIndex,
      instruction: steps[stepIndex].instruction,
      stepLocation: steps[stepIndex].location,
      distanceToNextM,
      remainingM,
      remainingS: remainingSeconds(remainingM),
      offRoute: missedFixes >= OFF_ROUTE_FIXES,
      arrived,
    };
  }

  return { update };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { createNavigator, distanceM, distanceToRouteM };
}
