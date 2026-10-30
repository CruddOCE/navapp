const assert = require('assert');
const { createNativeWatch, STALL_MS, UPDATE_INTERVAL_MS } = require('../web/js/gps');

// Behaves like the Capacitor Geolocation plugin: watchPosition returns an id
// straight away, and clearWatch rejects for an id it no longer has (the real
// plugin drops a watch by itself after an error).
function fakePlugin() {
  const active = new Map();
  let next = 1;
  const p = {
    starts: 0,
    active,
    watchPosition(opts, cb) {
      p.starts += 1;
      p.lastOptions = opts;
      const id = String(next++);
      active.set(id, cb);
      return id;
    },
    clearWatch({ id }) {
      if (!active.delete(id)) return Promise.reject(new Error('WatchId not found.'));
      return Promise.resolve();
    },
    // Sends to every watch the plugin still holds, as stray callbacks would.
    send(pos, err) {
      for (const cb of active.values()) cb(pos, err);
    },
  };
  return p;
}

const settle = () => new Promise((r) => setImmediate(r));
const fix = { coords: { latitude: -31.95, longitude: 115.86 } };

function setup() {
  let t = 0;
  const plugin = fakePlugin();
  const fixes = [];
  let waiting = 0;
  const watch = createNativeWatch({
    plugin,
    onFix: (c) => fixes.push(c),
    onWaiting: () => { waiting += 1; },
    now: () => t,
  });
  return { plugin, fixes, watch, advance: (ms) => { t += ms; }, waiting: () => waiting };
}

module.exports = [
  {
    name: 'the watch asks for a fix every second, faster than the watchdog, whatever the timeout',
    fn: async () => {
      const s = setup();
      await s.watch.start();
      const o = s.plugin.lastOptions;
      // Without interval, the plugin uses the timeout as the update interval.
      assert.strictEqual(o.interval, UPDATE_INTERVAL_MS);
      assert.strictEqual(o.minimumUpdateInterval, UPDATE_INTERVAL_MS);
      assert.ok(o.interval < STALL_MS);
      assert.ok(o.timeout > STALL_MS);
    },
  },
  {
    name: 'errors from the plugin do not start more watches',
    fn: async () => {
      const s = setup();
      await s.watch.start();
      for (let i = 0; i < 5; i += 1) s.plugin.send(null, new Error('Could not obtain location in time.'));
      await settle();
      assert.strictEqual(s.plugin.starts, 1);
      assert.strictEqual(s.waiting(), 5);
    },
  },
  {
    name: 'a quiet watch is replaced by the watchdog, never run alongside it',
    fn: async () => {
      const s = setup();
      await s.watch.start();
      for (let i = 0; i < 10; i += 1) {
        s.advance(STALL_MS + 5000);
        s.plugin.send(null, new Error('Could not obtain location in time.'));
        await s.watch.tick();
        await settle();
        assert.ok(s.plugin.active.size <= 1, `${s.plugin.active.size} watches running`);
      }
      assert.strictEqual(s.plugin.starts, 11);
    },
  },
  {
    name: 'fixes keep the watchdog quiet',
    fn: async () => {
      const s = setup();
      await s.watch.start();
      for (let i = 0; i < 10; i += 1) {
        s.advance(5000);
        s.plugin.send(fix);
        await s.watch.tick();
      }
      assert.strictEqual(s.plugin.starts, 1);
      assert.strictEqual(s.fixes.length, 10);
    },
  },
  {
    name: 'a fix from a replaced watch is ignored',
    fn: async () => {
      const s = setup();
      await s.watch.start();
      const oldCallback = [...s.plugin.active.values()][0];
      s.advance(STALL_MS + 1);
      await s.watch.tick();
      oldCallback(fix);
      assert.strictEqual(s.fixes.length, 0);
    },
  },
  {
    name: 'clearing a watch the plugin already dropped does not throw',
    fn: async () => {
      const s = setup();
      let unhandled = 0;
      const onUnhandled = () => { unhandled += 1; };
      process.on('unhandledRejection', onUnhandled);
      try {
        await s.watch.start();
        s.plugin.active.clear();
        s.advance(STALL_MS + 1);
        await s.watch.tick();
        await settle();
        assert.strictEqual(unhandled, 0);
        assert.strictEqual(s.plugin.active.size, 1);
      } finally {
        process.off('unhandledRejection', onUnhandled);
      }
    },
  },
];
