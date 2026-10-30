// Wraps OSRM's route API and turns its response into the shape the client
// uses everywhere. OSRM returns [lng, lat] pairs; the app uses {lat, lng}, so
// the swap happens once, here, and nowhere else.

// Left or right only, for wording where "slight" or "sharp" reads badly
// ("Keep left", "Take the exit on the right").
function sideOf(modifier) {
  if (/left/.test(modifier)) return 'left';
  if (/right/.test(modifier)) return 'right';
  return '';
}

function tidy(text) {
  return text.replace(/\s+/g, ' ').trim();
}

// Covers every OSRM manoeuvre type. Anything not named here still reads as
// "Continue on", which is only right when the driver really does carry on.
function instructionFor(step) {
  const m = step.maneuver || {};
  const modifier = m.modifier || '';
  // Unnamed roads often still have a number (ref), such as a state route.
  // A bare number reads as "onto 1", so it is spoken as "route 1".
  const ref = step.ref ? (/^\d+$/.test(step.ref) ? `route ${step.ref}` : step.ref) : '';
  const roadName = step.name || ref;
  const onto = roadName ? ` onto ${roadName}` : '';
  const road = roadName || 'the road';
  const side = sideOf(modifier);

  if (modifier === 'uturn' && m.type !== 'depart' && m.type !== 'arrive') {
    return `Make a U-turn${onto}`;
  }
  switch (m.type) {
    case 'depart':
      return `Head ${modifier || 'on'} ${road}`;
    case 'arrive':
      return 'You have arrived';
    case 'turn':
    case 'end of road':
      return modifier === 'straight' ? `Go straight${onto}` : tidy(`Turn ${modifier}${onto}`);
    case 'fork':
      return `Keep ${side || 'straight'}${onto}`;
    case 'merge':
      return tidy(`Merge ${side}${onto}`);
    case 'on ramp':
      return `Take the ramp${side ? ` on the ${side}` : ''}${onto}`;
    case 'off ramp':
      return `Take the exit${side ? ` on the ${side}` : ''}${onto}`;
    case 'roundabout':
    case 'rotary':
      return m.exit ? `At the roundabout, take exit ${m.exit}${onto}` : `Take the roundabout${onto}`;
    case 'roundabout turn':
      return tidy(`At the roundabout, turn ${modifier}${onto}`);
    case 'exit roundabout':
    case 'exit rotary':
      return `Exit the roundabout${onto}`;
    case 'new name':
      return roadName ? `Continue onto ${roadName}` : 'Continue';
    default:
      return `Continue on ${road}`;
  }
}

function parseOsrmResponse(json) {
  if (!json || json.code !== 'Ok' || !Array.isArray(json.routes) || json.routes.length === 0) {
    throw new Error(`no route found (${json && json.code ? json.code : 'unknown'})`);
  }
  const route = json.routes[0];
  const steps = route.legs.flatMap((leg) => leg.steps).map((step) => ({
    instruction: instructionFor(step),
    distanceM: step.distance,
    // Time for this step's stretch of road, used for a per-road-speed ETA.
    durationS: step.duration,
    maneuver: step.maneuver.type,
    location: { lat: step.maneuver.location[1], lng: step.maneuver.location[0] },
  }));
  return {
    distanceM: route.distance,
    durationS: route.duration,
    geometry: route.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
    steps,
  };
}

function createRouter(options) {
  const fetchImpl = options.fetchImpl;
  const baseUrl = options.baseUrl;
  // The public OSRM server refuses requests with no User-Agent or Android's
  // default one (403), so the app identifies itself, as it does to Nominatim.
  const headers = options.userAgent ? { 'User-Agent': options.userAgent } : {};

  async function getRoute({ from, to }) {
    if (from.lat === to.lat && from.lng === to.lng) {
      throw new Error('start and destination are the same');
    }
    const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`;
    const url = `${baseUrl}/route/v1/driving/${coords}?overview=full&geometries=geojson&steps=true`;
    const res = await fetchImpl(url, { headers });
    if (!res.ok) throw new Error(`routing service returned ${res.status}`);
    return parseOsrmResponse(await res.json());
  }

  return { getRoute };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { createRouter, parseOsrmResponse, instructionFor };
}
