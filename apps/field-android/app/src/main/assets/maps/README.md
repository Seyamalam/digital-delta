# Android offline geographic map

`sylhet_osm_basemap.geojson` is a deterministic, RAM-bounded field-map extract from `apps/command/public/maps/sylhet.pmtiles`. It contains real OpenStreetMap-derived and Natural Earth geography; it does not contain simulated mission state.

Regenerate it from the reviewed PMTiles archive:

```bash
cd apps/command
pnpm export:android-map
```

The export records the source archive SHA-256, zoom, bounds, and attribution inside the GeoJSON. `scripts/verify-local.sh` checks both the asset checksum and its source-archive binding. MapLibre Native receives this file as a local source and receives route, risk, node, and simulated-airway state through a separate generated source. The Android style has no online tile, glyph, or sprite URL.

Map data is © OpenStreetMap contributors and is distributed under the Open Database License.
