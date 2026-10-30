const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

// api.js is a browser script that uses the geocode.js and routing.js globals,
// so the three load into one sandbox the way index.html loads them.
function loadApi(windowObj, fetchImpl) {
  const sandbox = { window: windowObj, fetch: fetchImpl, URLSearchParams, setTimeout, Promise, console };
  vm.createContext(sandbox);
  for (const f of ['geocode.js', 'routing.js', 'api.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'web', 'js', f), 'utf8'), sandbox, { filename: f });
  }
  return sandbox;
}

function appWindow(request) {
  return {
    NAV_API_BASE: 'http://pc.test:8090',
    Capacitor: { isNativePlatform: () => true, Plugins: { CapacitorHttp: { request } } },
  };
}

const osrm = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'osrm-route.json'), 'utf8'));
const noFetch = () => { throw new Error('the PC server must not be called'); };

module.exports = [
  {
    name: 'in the Android app, search goes straight to Nominatim with an identifying User-Agent',
    fn: async () => {
      const seen = [];
      const api = loadApi(
        appWindow(async (req) => {
          seen.push(req);
          return { status: 200, data: [{ display_name: 'Fremantle', lat: '-32.05', lon: '115.75' }] };
        }),
        noFetch
      );
      const res = await api.searchPlaces('Fremantle', null);
      assert.strictEqual(res.further[0].name, 'Fremantle');
      assert.ok(seen[0].url.startsWith('https://nominatim.openstreetmap.org/search?'), seen[0].url);
      assert.match(seen[0].headers['User-Agent'], /^navapp\//);
    },
  },
  {
    name: 'in the Android app, routing goes straight to OSRM, and a JSON string body is parsed',
    fn: async () => {
      const seen = [];
      const api = loadApi(
        appWindow(async (req) => {
          seen.push(req);
          return { status: 200, data: JSON.stringify(osrm) };
        }),
        noFetch
      );
      const route = await api.fetchRoute({ lat: -31.9505, lng: 115.8575 }, { lat: -31.9535, lng: 115.8602 });
      assert.strictEqual(route.steps.length, 3);
      assert.ok(seen[0].url.startsWith('https://router.project-osrm.org/route/v1/driving/115.8575,-31.9505;'), seen[0].url);
      // The OSRM demo server answers 403 to Android's default User-Agent.
      assert.match(seen[0].headers['User-Agent'], /^navapp\//);
    },
  },
  {
    name: 'in the Android app, no network gives a plain message',
    fn: async () => {
      const api = loadApi(
        appWindow(async () => { throw new Error('Unable to resolve host "router.project-osrm.org"'); }),
        noFetch
      );
      await assert.rejects(
        () => api.fetchRoute({ lat: 1, lng: 1 }, { lat: 2, lng: 2 }),
        /No internet connection/
      );
    },
  },
  {
    name: 'in a desktop browser, search and routing still go through the dev server',
    fn: async () => {
      const urls = [];
      const api = loadApi({ NAV_API_BASE: '' }, async (url) => {
        urls.push(url);
        return { ok: true, status: 200, json: async () => ({ nearby: [], further: [] }) };
      });
      await api.searchPlaces('perth', { lat: -31.95, lng: 115.86 });
      await api.fetchRoute({ lat: 1, lng: 1 }, { lat: 2, lng: 2 });
      assert.ok(urls[0].startsWith('/api/geocode?q=perth&lat=-31.95'), urls[0]);
      assert.ok(urls[1].startsWith('/api/route?fromLat=1'), urls[1]);
    },
  },
];
