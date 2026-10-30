const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { build } = require('../scripts/make-nav-expectations');

module.exports = [
  {
    name: 'nav-expected.json matches nav.js and voice.js (regenerate it and rerun the Java tests if not)',
    fn: async () => {
      const file = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'nav-expected.json'), 'utf8'));
      assert.deepStrictEqual(file, build(), 'run: node scripts/make-nav-expectations.js, then npm run android:test');
    },
  },
];
