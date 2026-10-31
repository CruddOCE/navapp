# Issue N7: Bundled map library

Status: Built, awaiting test
Phase: Android Auto car map (board #66)

Done when: Both maps load MapLibre from the app with no CDN request, including with no internet for the library.

## Status log

- 2026-10-31: Built, awaiting test. MapLibre 5.24.0 copied from npm into web/vendor at build and dev server start; same SHA-256 as the CDN files. Not yet checked offline.
