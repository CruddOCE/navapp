const assert = require('assert');
const {
  carPadding, toCssArea, startFromHash, clampZoom, shouldJump, keepInside, metresBetween,
  CAR_HEIGHT_IN_AREA, MIN_ZOOM, MAX_ZOOM,
} = require('../web/js/car-map');

// Where MapLibre puts the map's centre for a given padding.
function centre(p, width, height) {
  return { x: p.left + (width - p.left - p.right) / 2, y: p.top + (height - p.top - p.bottom) / 2 };
}

module.exports = [
  {
    name: 'the car sits across the middle of the uncovered area and low down in it',
    fn: async () => {
      // A 800 x 480 car screen; the turn card covers the top left, so the host
      // reports this as the uncovered area.
      const area = { left: 340, top: 0, right: 800, bottom: 400 };
      const c = centre(carPadding(area, 800, 480), 800, 480);
      assert.strictEqual(c.x, (340 + 800) / 2);
      assert.ok(Math.abs(c.y - 400 * CAR_HEIGHT_IN_AREA) < 1e-9, `y was ${c.y}`);
    },
  },
  {
    name: 'with the whole screen uncovered the car is centred across and low down',
    fn: async () => {
      const c = centre(carPadding({ left: 0, top: 0, right: 800, bottom: 480 }, 800, 480), 800, 480);
      assert.strictEqual(c.x, 400);
      assert.ok(c.y > 240 && c.y < 480, `y was ${c.y}`);
    },
  },
  {
    name: 'padding is never negative, even for an odd area',
    fn: async () => {
      const p = carPadding({ left: -10, top: 0, right: 900, bottom: 30 }, 800, 480);
      for (const k of ['top', 'bottom', 'left', 'right']) assert.ok(p[k] >= 0, `${k} was ${p[k]}`);
    },
  },
  {
    name: 'the page opens at the position in its address, so the first frame is already there',
    fn: async () => {
      assert.deepStrictEqual(startFromHash('#-32.7892,116.5202,90.5'), { lat: -32.7892, lng: 116.5202, heading: 90.5 });
      assert.deepStrictEqual(startFromHash('#-32.7892,116.5202'), { lat: -32.7892, lng: 116.5202, heading: null });
      assert.strictEqual(startFromHash(''), null);
      assert.strictEqual(startFromHash('#nope'), null);
    },
  },
  {
    name: 'a far position jumps, a near one animates',
    fn: async () => {
      const here = { lat: -32.7892, lng: 116.5202 };
      assert.strictEqual(shouldJump(null, here), true);
      assert.strictEqual(shouldJump(here, { lat: -32.7894, lng: 116.5204 }), false);
      // Perth to Boddington, about 115 km.
      assert.strictEqual(shouldJump({ lat: -31.95, lng: 115.86 }, here), true);
      assert.ok(Math.abs(metresBetween({ lat: 0, lng: 0 }, { lat: 0.001, lng: 0 }) - 111.2) < 0.5);
    },
  },
  {
    name: 'zoom stays between street detail and a useful wide view',
    fn: async () => {
      assert.strictEqual(clampZoom(25), MAX_ZOOM);
      assert.strictEqual(clampZoom(3), MIN_ZOOM);
      assert.strictEqual(clampZoom(15), 15);
    },
  },
  {
    name: 'looking around never lets your position leave the uncovered area',
    fn: async () => {
      const area = { left: 340, top: 0, right: 800, bottom: 400 };
      // Inside: no correction.
      assert.deepStrictEqual(keepInside({ x: 500, y: 200 }, area, 28), { dx: 0, dy: 0 });
      // Dragged off to the left, under the turn card: pulled back to 28 px inside.
      assert.deepStrictEqual(keepInside({ x: 100, y: 200 }, area, 28), { dx: 100 - 368, dy: 0 });
      // Off the bottom and the right at once.
      assert.deepStrictEqual(keepInside({ x: 900, y: 450 }, area, 28), { dx: 900 - 772, dy: 450 - 372 });
      // After the correction the point is inside.
      const p = { x: 100, y: -50 };
      const f = keepInside(p, area, 28);
      assert.deepStrictEqual(keepInside({ x: p.x - f.dx, y: p.y - f.dy }, area, 28), { dx: 0, dy: 0 });
    },
  },
  {
    name: 'surface pixels become CSS pixels by the device pixel ratio',
    fn: async () => {
      assert.deepStrictEqual(toCssArea({ left: 200, top: 0, right: 1600, bottom: 800 }, 2), { left: 100, top: 0, right: 800, bottom: 400 });
      assert.deepStrictEqual(toCssArea({ left: 10, top: 20, right: 30, bottom: 40 }, 0), { left: 10, top: 20, right: 30, bottom: 40 });
    },
  },
];
