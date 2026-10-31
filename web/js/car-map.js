// The map on the Android Auto car screen, behind the host's turn card. Native
// code (car/CarMapRenderer.java) calls window.carMap with the route line, each
// position, the part of the screen the host leaves uncovered, and the driver's
// pan and zoom. The map is always there while navapp is open on the car, and
// your position is always on screen: when following, the map is heading-up
// with the car low in the uncovered area; when you look around, panning and
// zooming stop before your position would leave it.
//
// The page opens at your position (car-map.html#lat,lng,heading), so its first
// frame is already in the right place.

const CAR_ZOOM = 16.5;
const CAR_PITCH = 40;
const MIN_ZOOM = 12;
const MAX_ZOOM = 19;
// Where the car sits in the uncovered area, from its top (0) to its bottom (1).
const CAR_HEIGHT_IN_AREA = 0.72;
// A position further than this from the view jumps there instead of animating.
const JUMP_IF_FURTHER_M = 500;
// How far inside the uncovered area your position is kept while you look around.
const KEEP_IN_MARGIN_PX = 28;

// MapLibre padding that puts the map's centre (the car) at the middle of the
// uncovered area across, and CAR_HEIGHT_IN_AREA of the way down it. area and
// the screen size are in CSS pixels.
function carPadding(area, width, height) {
  const bottom = Math.max(0, height - area.bottom);
  const targetY = area.top + (area.bottom - area.top) * CAR_HEIGHT_IN_AREA;
  // The centre sits halfway between the top and bottom padding edges.
  const top = Math.max(0, 2 * targetY - height + bottom);
  return {
    top,
    bottom,
    left: Math.max(0, area.left),
    right: Math.max(0, width - area.right),
  };
}

// The host reports the uncovered area in surface pixels; the page works in CSS
// pixels, which differ by the device pixel ratio.
function toCssArea(px, ratio) {
  const r = ratio > 0 ? ratio : 1;
  return { left: px.left / r, top: px.top / r, right: px.right / r, bottom: px.bottom / r };
}

// "#lat,lng,heading" from the page address, or null.
function startFromHash(hash) {
  const parts = String(hash || '').replace(/^#/, '').split(',').map(Number);
  if (parts.length < 2 || !Number.isFinite(parts[0]) || !Number.isFinite(parts[1])) return null;
  return { lat: parts[0], lng: parts[1], heading: Number.isFinite(parts[2]) ? parts[2] : null };
}

function clampZoom(z) {
  return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
}

// Metres between two points; plenty accurate for deciding jump or animate.
function metresBetween(a, b) {
  const r = 6371000;
  const rad = Math.PI / 180;
  const x = (b.lng - a.lng) * rad * Math.cos(((a.lat + b.lat) / 2) * rad);
  const y = (b.lat - a.lat) * rad;
  return Math.sqrt(x * x + y * y) * r;
}

function shouldJump(from, to) {
  return !from || metresBetween(from, to) > JUMP_IF_FURTHER_M;
}

// How far to move the camera (in screen pixels, MapLibre panBy terms) so the
// point stays at least margin inside the area. Zero when it already is.
function keepInside(point, area, margin) {
  const m = Math.max(0, Math.min(margin, (area.right - area.left) / 2, (area.bottom - area.top) / 2));
  let dx = 0;
  let dy = 0;
  if (point.x < area.left + m) dx = point.x - (area.left + m);
  else if (point.x > area.right - m) dx = point.x - (area.right - m);
  if (point.y < area.top + m) dy = point.y - (area.top + m);
  else if (point.y > area.bottom - m) dy = point.y - (area.bottom - m);
  return { dx, dy };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    carPadding, toCssArea, startFromHash, clampZoom, shouldJump, keepInside, metresBetween,
    CAR_HEIGHT_IN_AREA, MIN_ZOOM, MAX_ZOOM, JUMP_IF_FURTHER_M,
  };
} else {
  const start = startFromHash(window.location.hash);
  // Calls can arrive before the map has loaded; the latest of each is kept and
  // applied once it is ready.
  const pending = { route: null, position: start, area: null };
  let ready = false;
  let heading = start && Number.isFinite(start.heading) ? start.heading : 0;
  let arrow = null;
  let following = true;
  let followZoom = CAR_ZOOM;

  const map = new maplibregl.Map({
    container: 'map',
    style: STYLE_URL,
    center: start ? [start.lng, start.lat] : [115.86, -31.95],
    zoom: CAR_ZOOM,
    pitch: CAR_PITCH,
    bearing: heading,
    interactive: false,
    attributionControl: false,
    fadeDuration: 0,
  });

  function arrowElement() {
    const el = document.createElement('div');
    // Fixed markup written here, not from any outside input.
    el.innerHTML =
      '<svg class="car-arrow" viewBox="0 0 44 44" aria-hidden="true">' +
      '<path d="M22 4 L37 38 L22 30 L7 38 Z" fill="#1a8cff" stroke="#fff" stroke-width="3" stroke-linejoin="round"/>' +
      '</svg>';
    return el;
  }

  // The uncovered area in CSS pixels; the whole page until the host reports one.
  function cssArea() {
    return pending.area
      ? toCssArea(pending.area, window.devicePixelRatio)
      : { left: 0, top: 0, right: window.innerWidth, bottom: window.innerHeight };
  }

  function applyArea() {
    if (!pending.area) return;
    if (following) map.setPadding(carPadding(cssArea(), window.innerWidth, window.innerHeight));
  }

  function applyRoute() {
    if (pending.route) setRouteGeometry(map, pending.route);
  }

  // While looking around, nudge the view so your position stays on screen.
  function keepYouInView() {
    const p = pending.position;
    if (!p || following) return;
    const pt = map.project([p.lng, p.lat]);
    const fix = keepInside(pt, cssArea(), KEEP_IN_MARGIN_PX);
    if (fix.dx || fix.dy) map.panBy([fix.dx, fix.dy], { duration: 0 });
  }

  function applyPosition() {
    const p = pending.position;
    if (!p) return;
    if (Number.isFinite(p.heading)) heading = p.heading;
    if (!arrow) {
      arrow = new maplibregl.Marker({ element: arrowElement(), rotationAlignment: 'map', pitchAlignment: 'map' })
        .setLngLat([p.lng, p.lat])
        .addTo(map);
    } else {
      arrow.setLngLat([p.lng, p.lat]);
    }
    // The arrow is drawn pointing north and turns with the map, so it gets the
    // same heading as the map's bearing: on screen it then points up.
    arrow.setRotation(heading);
    if (!following) {
      keepYouInView();
      return;
    }
    const centre = map.getCenter();
    const camera = { center: [p.lng, p.lat], bearing: heading, zoom: followZoom, pitch: CAR_PITCH };
    if (shouldJump({ lat: centre.lat, lng: centre.lng }, p)) map.jumpTo(camera);
    else map.easeTo({ ...camera, duration: 900 });
  }

  map.on('load', () => {
    applyWazeLook(map);
    addRouteLayers(map);
    ready = true;
    applyArea();
    applyRoute();
  });
  // The arrow and camera do not need the style, so they go on at once.
  applyPosition();

  window.carMap = {
    // geometry: [[lat, lng], ...], the same shape as the phone's route; [] clears it.
    setRoute(geometry) {
      pending.route = geometry;
      if (ready) applyRoute();
    },
    setPosition(lat, lng, h) {
      pending.position = { lat, lng, heading: h };
      applyPosition();
    },
    // Surface pixels, as the host reports them.
    setArea(left, top, right, bottom) {
      pending.area = { left, top, right, bottom };
      applyArea();
    },
    // Drag, in surface pixels as the host reports them.
    pan(dx, dy) {
      following = false;
      const r = window.devicePixelRatio || 1;
      map.panBy([dx / r, dy / r], { duration: 0 });
      keepYouInView();
    },
    // Pinch: factor above 1 zooms in. Focus in surface pixels, or negative for
    // the middle of the uncovered area.
    scale(factor, fx, fy) {
      following = false;
      const r = window.devicePixelRatio || 1;
      const area = cssArea();
      const around = fx >= 0 && fy >= 0
        ? map.unproject([fx / r, fy / r])
        : map.unproject([(area.left + area.right) / 2, (area.top + area.bottom) / 2]);
      map.jumpTo({ zoom: clampZoom(map.getZoom() + Math.log2(factor > 0 ? factor : 1)), around });
      keepYouInView();
    },
    // The zoom buttons: around your position, so you stay where you were on screen.
    zoomStep(step) {
      following = false;
      const p = pending.position;
      const around = p ? [p.lng, p.lat] : map.getCenter();
      map.easeTo({ zoom: clampZoom(map.getZoom() + step), around, duration: 250 });
    },
    // Back to following your position, heading-up, at the current zoom.
    follow() {
      followZoom = clampZoom(map.getZoom());
      following = true;
      applyArea();
      applyPosition();
    },
  };
}
