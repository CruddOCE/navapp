// Search and routing for map.js. Every failure becomes a thrown Error with a
// message the UI can show, so map.js never has to read raw responses.
//
// In the Android app, search and routing call Nominatim and OSRM directly, so
// the app works anywhere with mobile data and does not need the PC server.
// Requests go through Capacitor's native HTTP, which can send the identifying
// User-Agent Nominatim requires (a page's own fetch cannot). In a desktop
// browser, the dev server does the same calls as a proxy.

const API_BASE = window.NAV_API_BASE || '';
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org';
const OSRM_URL = 'https://router.project-osrm.org';
const USER_AGENT = 'navapp/0.01 (personal test build; https://github.com/CruddOCE/navapp)';
const HTTP_TIMEOUT_MS = 15000;

function nativeHttp() {
  const cap = window.Capacitor;
  const isApp = Boolean(cap && cap.isNativePlatform && cap.isNativePlatform());
  return isApp && cap.Plugins && cap.Plugins.CapacitorHttp ? cap.Plugins.CapacitorHttp : null;
}

// The fetch shape geocode.js and routing.js expect ({ ok, status, json }),
// on top of native HTTP. Called per request, not switched on for every fetch,
// so MapLibre's tile loading is left alone.
function nativeFetch(http) {
  return (url, options) =>
    http
      .request({
        url,
        method: 'GET',
        headers: (options && options.headers) || {},
        connectTimeout: HTTP_TIMEOUT_MS,
        readTimeout: HTTP_TIMEOUT_MS,
      })
      .then((res) => ({
        ok: res.status >= 200 && res.status < 300,
        status: res.status,
        json: async () => (typeof res.data === 'string' ? JSON.parse(res.data) : res.data),
      }));
}

function createDirect(http) {
  const fetchImpl = nativeFetch(http);
  return {
    geocoder: createGeocoder({ fetchImpl, baseUrl: NOMINATIM_URL, userAgent: USER_AGENT }),
    router: createRouter({ fetchImpl, baseUrl: OSRM_URL, userAgent: USER_AGENT }),
  };
}

const http = nativeHttp();
const direct = http ? createDirect(http) : null;

async function getJson(url) {
  const res = await fetch(API_BASE + url);
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || `request failed (${res.status})`);
  return body;
}

// Native HTTP rejects with its own error when the network is down; say so plainly.
function offlineMessage(err) {
  return err && /timeout|timed out|unable to resolve|failed to connect|network/i.test(String(err.message))
    ? new Error('No internet connection. Check mobile data and try again.')
    : err;
}

// near is the device position ({lat, lng}) or null. Resolves to { nearby, further }.
function searchPlaces(query, near) {
  if (direct) return direct.geocoder.search(query, near || undefined).catch((err) => { throw offlineMessage(err); });
  const qs = new URLSearchParams({ q: query });
  if (near) {
    qs.set('lat', near.lat);
    qs.set('lng', near.lng);
  }
  return getJson(`/api/geocode?${qs}`);
}

function fetchRoute(from, to) {
  if (direct) {
    return direct.router
      .getRoute({ from: { lat: from.lat, lng: from.lng }, to: { lat: to.lat, lng: to.lng } })
      .catch((err) => { throw offlineMessage(err); });
  }
  const qs = new URLSearchParams({
    fromLat: from.lat,
    fromLng: from.lng,
    toLat: to.lat,
    toLng: to.lng,
  });
  return getJson(`/api/route?${qs}`);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { searchPlaces, fetchRoute, isDirect: () => Boolean(direct) };
}
