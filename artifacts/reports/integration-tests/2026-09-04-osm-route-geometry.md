# OSM route geometry verification

Date: 2026-09-04

Source state: this report's repository commit

Scenario: `packages/scenario/sylhet_map.json`

Geometry: `packages/scenario/sylhet_route_geometry.json`

## Result

- `scripts/verify-local.sh`: passed.
- Next.js Vitest suite: 17/17 passed.
- Android 15 `Mento_API_35`: 61/61 connected tests passed, zero skipped.
- Android 16 `Pixel_10_Pro_XL`: 61/61 connected tests passed, zero skipped.
- Next.js production build: passed.
- Android debug and minified release builds: passed.

## Geometry checks

- E1 through E5 use committed multi-point OpenStreetMap road geometry exported through OSRM.
- E6 and E7 use connected OpenStreetMap waterway centerlines from the pinned offline basemap.
- A1 and A2 remain two-point direct lines and carry `simulated: true`.
- The local gate verifies the route file checksum and its scenario and basemap source hashes.
- Android verifies the route file checksum before MapLibre receives the mission source.
- The dashboard has no runtime route API or public tile request.

## Visual evidence

- `artifacts/screenshots/command-en/02-command-en-live-overview-1920x1080.png`
- `artifacts/screenshots/command-bn/01-command-bn-live-overview-1366x768.png`
- `artifacts/screenshots/field-en/24-field-en-osm-route-geometry-1280x2856.png`
- `artifacts/screenshots/field-en/25-field-en-osm-waterway-reroute-1280x2856.png`

The Android captures show the truck line tracking the local road network and the boat fallback tracking the river network. These emulator captures verify rendering and wiring. They do not replace the final physical-phone rehearsal.
