// Map screen: MapLibre map, search, one route, and turn-by-turn navigation.
// Navigation logic lives in nav.js and voice.js; this file wires them to GPS,
// the map and the on-screen panels, and decides when to reroute.
// Map colours and the route layers live in map-style.js.

// MapLibre takes [lng, lat]; the app's own positions are {lat, lng}.
const DEFAULT_CENTER = [115.86, -31.95];
const DEFAULT_ZOOM = 13;
const NAV_ZOOM = 17;
const NAV_PITCH = 45;
const REROUTE_MIN_GAP_MS = 10000;

const map = new maplibregl.Map({
  container: 'map',
  style: STYLE_URL,
  center: DEFAULT_CENTER,
  zoom: DEFAULT_ZOOM,
  attributionControl: { compact: true },
});
map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right');
map.on('load', () => {
  applyWazeLook(map);
  addRouteLayers(map);
  mapReady = true;
  if (pendingRoute) drawRoute(pendingRoute);
});
let mapReady = false;

const searchInput = document.getElementById('search');
const resultsList = document.getElementById('results');
const statusLine = document.getElementById('status');
const startBtn = document.getElementById('start');
const navBanner = document.getElementById('nav-banner');
const navDistance = document.getElementById('nav-distance');
const navStreet = document.getElementById('nav-street');
const navBottom = document.getElementById('nav-bottom');
const navEta = document.getElementById('nav-eta');
const navRemaining = document.getElementById('nav-remaining');
const endBtn = document.getElementById('end');

const voice = createVoice();
let youMarker = null;
let devicePosition = null;
let destination = null;
let pendingRoute = null;
// Named navigator_ so it does not shadow the browser's global navigator.
let navigator_ = null;
let lastRerouteAt = 0;
let navigating = false;

function setStatus(text) {
  statusLine.textContent = text || '';
}

// The status line is hidden while navigating, so a message meant for the
// driver then shows in the navigation banner instead, held for a few seconds
// before the next instruction takes the banner back.
const BANNER_MESSAGE_MS = 4000;
let bannerMessageUntil = 0;

function showMessage(text) {
  if (!navigating) {
    setStatus(text);
    return;
  }
  navStreet.textContent = text;
  bannerMessageUntil = Date.now() + BANNER_MESSAGE_MS;
}

function formatMetres(m) {
  return m >= 1000 ? `${(m / 1000).toFixed(1)} km` : `${Math.round(m / 10) * 10} m`;
}

// Blue dot at rest, blue arrow while navigating. The map turns to face the
// direction of travel during navigation, so the arrow always points up.
function youElement() {
  const el = document.createElement('div');
  el.className = 'you';
  return el;
}

function showYou(pos) {
  devicePosition = { lat: pos.lat, lng: pos.lng };
  if (!youMarker) {
    youMarker = new maplibregl.Marker({ element: youElement(), rotationAlignment: 'map' })
      .setLngLat([pos.lng, pos.lat])
      .addTo(map);
  } else {
    youMarker.setLngLat([pos.lng, pos.lat]);
  }
  youMarker.getElement().classList.toggle('navigating', navigating);
}

function onPosition(coords) {
  const pos = { lat: coords.latitude, lng: coords.longitude };
  showYou(pos);
  if (navigating) followNavigation(pos, coords.heading);
}

if (window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform()) {
  document.documentElement.classList.add('native');
}

const onLocationError = () => setStatus('Location unavailable. Search still works.');

// In the Android app, the Capacitor Geolocation plugin asks for the location
// permission and then reports fixes (watch and watchdog in gps.js). In a
// normal browser, use the standard API.
const nativeGeo = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Geolocation;

const nativeWatch = nativeGeo
  ? createNativeWatch({
      plugin: nativeGeo,
      onFix: (coords) => {
        if (statusLine.textContent.startsWith('Finding your location')) setStatus('');
        onPosition(coords);
      },
      onWaiting: () => setStatus('Finding your location...'),
    })
  : null;

if (nativeWatch) setInterval(nativeWatch.tick, 5000);

async function startLocation() {
  if (nativeWatch) {
    await nativeGeo.requestPermissions();
    nativeWatch.start();
    return;
  }
  if ('geolocation' in navigator) {
    navigator.geolocation.watchPosition(
      (p) => onPosition(p.coords),
      onLocationError,
      { enableHighAccuracy: true, maximumAge: 2000, timeout: LOCATION_TIMEOUT_MS }
    );
  }
}

startLocation();

// Nearby places first, then a "Further away" heading and the rest of the world.
function renderResults({ nearby, further }) {
  resultsList.replaceChildren();
  const addHeading = (text) => {
    const li = document.createElement('li');
    li.className = 'group-heading';
    li.textContent = text;
    resultsList.appendChild(li);
  };
  const addPlace = (place) => {
    const li = document.createElement('li');
    li.textContent = place.name;
    li.addEventListener('click', () => chooseDestination(place));
    resultsList.appendChild(li);
  };
  if (nearby.length) {
    addHeading('Nearby');
    nearby.forEach(addPlace);
  }
  if (further.length) {
    addHeading('Further away');
    further.forEach(addPlace);
  }
  const total = nearby.length + further.length;
  resultsList.hidden = total === 0;
  if (total === 0) setStatus('No places found.');
}

async function runSearch(query) {
  if (!query.trim()) {
    resultsList.hidden = true;
    setStatus('');
    return;
  }
  setStatus('Searching...');
  try {
    renderResults(await searchPlaces(query, devicePosition));
    if (resultsList.hidden === false) setStatus('');
  } catch (err) {
    resultsList.hidden = true;
    setStatus(err.message);
  }
}

async function chooseDestination(place) {
  resultsList.hidden = true;
  searchInput.value = place.name;
  destination = place;
  if (!devicePosition) {
    setStatus('Waiting for your location before routing.');
    return;
  }
  setStatus('Finding a route...');
  try {
    pendingRoute = await fetchRoute(devicePosition, place);
    drawRoute(pendingRoute);
    const minutes = Math.round(pendingRoute.durationS / 60);
    setStatus(`${minutes} min · ${formatMetres(pendingRoute.distanceM)}`);
    startBtn.hidden = false;
  } catch (err) {
    startBtn.hidden = true;
    setStatus(err.message);
  }
}

function drawRoute(route) {
  if (!mapReady) return;
  setRouteGeometry(map, route.geometry);
  const lats = route.geometry.map((p) => p[0]);
  const lngs = route.geometry.map((p) => p[1]);
  map.fitBounds(
    [
      [Math.min(...lngs), Math.min(...lats)],
      [Math.max(...lngs), Math.max(...lats)],
    ],
    { padding: 60, duration: 0 }
  );
}

// In the Android app, navigation runs in the native location service
// (NavLocationService, through the NavState plugin): turn tracking, voice, the
// car screen and rerouting all keep going there while this page is hidden,
// which a hidden WebView page cannot do. This page then only shows what the
// service sends. In a desktop browser the JavaScript navigator and voice below
// do the work instead.
const nativeNav =
  window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform()
    ? window.Capacitor.Plugins.NavState
    : null;

function startNavigation() {
  if (!pendingRoute || !devicePosition) return;
  navigating = true;
  searchInput.hidden = true;
  resultsList.hidden = true;
  startBtn.hidden = true;
  statusLine.hidden = true;
  navBanner.hidden = false;
  navBottom.hidden = false;
  youMarker.getElement().classList.add('navigating');
  map.jumpTo({ center: [devicePosition.lng, devicePosition.lat], zoom: NAV_ZOOM, pitch: NAV_PITCH });
  if (nativeNav) {
    navStreet.textContent = 'Starting...';
    nativeNav
      .startRoute({ route: pendingRoute, destination: { lat: destination.lat, lng: destination.lng } })
      .catch((err) => showMessage(err.message));
    return;
  }
  navigator_ = createNavigator(pendingRoute);
  lastRerouteAt = Date.now();
  followNavigation(devicePosition);
}

// Banner, bottom bar and camera for one navigation update, from either the
// JavaScript navigator or the native service.
function showNavState(state, pos, heading) {
  navDistance.textContent = formatMetres(state.distanceToNextM);
  if (Date.now() >= bannerMessageUntil) navStreet.textContent = state.instruction;
  navRemaining.textContent = `${formatMetres(state.remainingM)} left`;
  // From what is left to drive, so the arrival time holds steady instead of
  // sliding later with every fix.
  const etaMs = Date.now() + state.remainingS * 1000;
  navEta.textContent = new Date(etaMs).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  // Heading-up: turn the map to the direction of travel when the phone reports one.
  const easeOptions = { center: [pos.lng, pos.lat], duration: 500 };
  if (Number.isFinite(heading)) easeOptions.bearing = heading;
  map.easeTo(easeOptions);
}

function finishNavigation(arrived) {
  stopNavigation();
  if (arrived) setStatus('You have arrived.');
}

if (nativeNav) {
  nativeNav.addListener('navState', (state) => {
    if (!navigating) return;
    const pos = { lat: state.lat, lng: state.lng };
    showYou(pos);
    showNavState(state, pos, state.heading);
  });
  nativeNav.addListener('rerouted', ({ route }) => {
    if (!navigating) return;
    pendingRoute = route;
    drawRoute(route);
  });
  nativeNav.addListener('message', ({ text }) => showMessage(text));
  nativeNav.addListener('ended', ({ arrived }) => {
    if (navigating) finishNavigation(arrived);
  });
}

function stopNavigation() {
  if (nativeNav && navigating) nativeNav.stopRoute().catch(() => {});
  navigating = false;
  navigator_ = null;
  navBanner.hidden = true;
  navBottom.hidden = true;
  searchInput.hidden = false;
  statusLine.hidden = false;
  setStatus('');
  if (mapReady) clearRouteGeometry(map);
  pendingRoute = null;
  startBtn.hidden = true;
  youMarker.getElement().classList.remove('navigating');
  map.jumpTo({
    center: devicePosition ? [devicePosition.lng, devicePosition.lat] : DEFAULT_CENTER,
    zoom: DEFAULT_ZOOM,
    pitch: 0,
    bearing: 0,
  });
}

async function reroute() {
  if (!devicePosition || !destination) return;
  lastRerouteAt = Date.now();
  try {
    pendingRoute = await fetchRoute(devicePosition, destination);
    navigator_ = createNavigator(pendingRoute);
    drawRoute(pendingRoute);
    showMessage('New route found.');
  } catch (err) {
    showMessage('Off route. Reroute failed, still following old route.');
  }
}

// Browser only: the JavaScript navigator and voice, fed by the page's own GPS.
function followNavigation(pos, heading) {
  if (!navigator_) return;
  const state = navigator_.update(pos);
  showNavState(state, pos, heading);
  voice.onUpdate(state);
  if (state.arrived) {
    finishNavigation(true);
    return;
  }
  if (state.offRoute && Date.now() - lastRerouteAt >= REROUTE_MIN_GAP_MS) reroute();
}

startBtn.addEventListener('click', startNavigation);
endBtn.addEventListener('click', stopNavigation);

// Crowd reports. Markers and the live channel are in reports.js.
const reportSheet = document.getElementById('report-sheet');
const reportCard = document.getElementById('report-card');
const reportCardTitle = document.getElementById('report-card-title');
const reportCardCount = document.getElementById('report-card-count');
const voteConfirm = document.getElementById('vote-confirm');
const voteDismiss = document.getElementById('vote-dismiss');

function minutesAgo(ms) {
  const m = Math.max(0, Math.round((Date.now() - ms) / 60000));
  return m === 0 ? 'just now' : `${m} min ago`;
}

function showReportCard(report) {
  if (!report) {
    reportCard.hidden = true;
    return;
  }
  reportCard.hidden = false;
  reportCardTitle.textContent = `${REPORT_TYPES[report.type].label}, ${minutesAgo(report.createdAt)}`;
  reportCardCount.textContent = `${report.confirms} still there · ${report.dismisses} gone`;
  voteConfirm.onclick = () => reports.vote(report.id, 'confirm').catch((err) => showMessage(err.message));
  voteDismiss.onclick = () => reports.vote(report.id, 'dismiss').catch((err) => showMessage(err.message));
}

const reports = createReports(map, { onSelect: showReportCard });

const typeGrid = document.getElementById('type-grid');
for (const [type, style] of Object.entries(REPORT_TYPES)) {
  const btn = document.createElement('button');
  btn.className = 'type-btn';
  btn.style.background = style.colour;
  btn.textContent = style.label;
  btn.addEventListener('click', () => postReport(type));
  typeGrid.appendChild(btn);
}

function postReport(type) {
  reportSheet.hidden = true;
  if (!devicePosition) {
    showMessage('Waiting for your location before reporting.');
    return;
  }
  reports
    .create(type, devicePosition)
    .then(() => showMessage('Report posted. Thanks!'))
    // fetch throws a TypeError when the server cannot be reached at all.
    .catch((err) => showMessage(err instanceof TypeError ? REPORTS_OFFLINE : err.message));
}

// Reports live on the shared report server, which is the PC for now. Away from
// it, say so instead of opening a sheet whose buttons would all fail.
const REPORTS_OFFLINE = 'Reports need the home server. Search and routes still work.';

document.getElementById('report-btn').addEventListener('click', () => {
  if (!reports.isReachable()) {
    showMessage(REPORTS_OFFLINE);
    return;
  }
  reportSheet.hidden = false;
});
document.getElementById('report-cancel').addEventListener('click', () => {
  reportSheet.hidden = true;
});

document.getElementById('recenter').addEventListener('click', () => {
  if (devicePosition) map.easeTo({ center: [devicePosition.lng, devicePosition.lat], duration: 500 });
});

let searchTimer = null;
searchInput.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => runSearch(searchInput.value), 350);
});
