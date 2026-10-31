// Copies MapLibre GL JS from node_modules into web/vendor/, where the phone map
// (index.html) and the car map (car-map.html) load it. Bundling it means no CDN
// download at start, so maps come up faster and the library works offline.
// The files are the same ones jsDelivr serves for maplibre-gl@5.24.0 (same
// SHA-256). Run before the dev server and before each Android build;
// web/vendor/ is not committed.

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const FROM = path.join(ROOT, 'node_modules', 'maplibre-gl', 'dist');
const TO = path.join(ROOT, 'web', 'vendor');
const FILES = ['maplibre-gl.js', 'maplibre-gl.css'];

function copyVendor() {
  if (!fs.existsSync(FROM)) throw new Error('maplibre-gl is not installed; run npm install');
  fs.mkdirSync(TO, { recursive: true });
  for (const f of FILES) fs.copyFileSync(path.join(FROM, f), path.join(TO, f));
  return FILES.map((f) => path.relative(ROOT, path.join(TO, f)));
}

if (require.main === module) {
  console.log('copied ' + copyVendor().join(', '));
}

module.exports = { copyVendor };
