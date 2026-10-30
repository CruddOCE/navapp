// GPS watch for the Android app (the Capacitor Geolocation plugin). Kept apart
// from map.js so it runs in Node tests against a fake plugin.
//
// The plugin ends a watch with an error when no fix arrives within the timeout,
// and can also go quiet without any error. A watchdog restarts a watch that has
// had no fix for STALL_MS, and it is the only thing that restarts one. When
// errors also scheduled restarts, old watches were left running and restarted
// in bursts, dropping the GPS lock.

// Long, so a tunnel or a slow first fix indoors does not end the watch.
const LOCATION_TIMEOUT_MS = 10 * 60 * 1000;
const STALL_MS = 20000;
// How often to ask for a fix. The plugin uses the timeout as the update
// interval when none is given, so this must always be passed: with the old 30 s
// timeout the phone sent a fix every 30 s, and the 20 s watchdog restarted the
// watch before each one arrived.
const UPDATE_INTERVAL_MS = 1000;

const WATCH_OPTIONS = {
  enableHighAccuracy: true,
  maximumAge: 2000,
  timeout: LOCATION_TIMEOUT_MS,
  interval: UPDATE_INTERVAL_MS,
  minimumUpdateInterval: UPDATE_INTERVAL_MS,
};

function createNativeWatch(options) {
  const plugin = options.plugin;
  const onFix = options.onFix;
  const onWaiting = options.onWaiting || (() => {});
  const now = options.now || (() => Date.now());

  let lastFixAt = 0;
  let watchId = null;
  // Identifies the current watch, so callbacks from a replaced one are ignored.
  let current = null;

  function clear(id) {
    // The plugin drops a watch by itself after an error, so clearing can fail.
    Promise.resolve()
      .then(() => plugin.clearWatch({ id }))
      .catch(() => {});
  }

  function start() {
    if (watchId !== null) clear(watchId);
    watchId = null;
    const token = {};
    current = token;
    lastFixAt = now();
    const started = plugin.watchPosition(
      WATCH_OPTIONS,
      (pos, err) => {
        if (current !== token) return;
        if (err || !pos) {
          onWaiting();
          return;
        }
        lastFixAt = now();
        onFix(pos.coords);
      }
    );
    // The id comes back directly on this plugin version; Promise.resolve also
    // covers a version that returns it as a promise.
    return Promise.resolve(started)
      .then((id) => {
        if (current === token) watchId = id;
        else clear(id);
      })
      .catch(() => {});
  }

  // Called on a timer by map.js. Restarts the watch once it has been quiet too long.
  function tick() {
    if (current && now() - lastFixAt > STALL_MS) {
      onWaiting();
      return start();
    }
    return Promise.resolve();
  }

  return { start, tick };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { createNativeWatch, LOCATION_TIMEOUT_MS, STALL_MS, UPDATE_INTERVAL_MS };
}
