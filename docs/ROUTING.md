# Offline routing runbook

The route screen is driven by the bundled `packages/scenario/sylhet_map.json` graph. It does not call a map, routing, or internet service at runtime. `packages/scenario/sylhet_route_geometry.json` gives every graph edge its reviewed display geometry.

## Implemented path

1. `SylhetMapParser` decodes the fixture, validates unique node and edge identifiers, rejects missing references and unsupported modes, and normalizes `river` to `WATERWAY`.
2. `RoutePlanner` runs Dijkstra on open edges for exactly one vehicle mode with deterministic node and edge ordering.
3. `DynamicRouteEngine` first tries the assigned truck, then a boat, then the visibly simulated drone. It records elapsed monotonic nanoseconds.
4. `OfflineRouteScenario` owns the current in-memory edge state and supports repeatable fail/reset actions without internet.
5. MapLibre Native resolves the returned edge IDs against the bundled geometry file, animates the vehicle along the complete polyline, and displays the exact nodes, edges, ETA, reason, and measured time in Bangla or English.

Road edges contain multi-point OpenStreetMap geometry exported once through OSRM. Water edges use connected OpenStreetMap waterway centerlines from the checksum-pinned field basemap. Only the visibly simulated air edges remain direct dashed lines. Each road or water line begins and ends at its scenario node, with a short connector where that node does not sit directly on the OSM network.

## Current fixture proof

| State | Preferred vehicle | Selected edges | ETA | Explanation |
|---|---|---|---:|---|
| Initial | Truck | `E1 + E3` | 65 min | Both directed road edges are open |
| `E3` failed | Truck | `E6 + E7` by boat | 200 min | No truck path reaches `N4`; waterway fallback precedes simulated air |

The `A1` and `A2` airways are synthetic and carry `"simulated": true`. They test drone constraints and M8 reachability; they are not approved flight corridors.

## Live demo

1. Open **Route & mesh / পথ ও মেশ** with commercial internet unavailable.
2. Show `Truck • N1 → N2 → N4 • E1 + E3` and the 65-minute ETA.
3. Tap **Fail E3 and recompute offline / E3 বন্ধ করে অফলাইনে পুনর্গণনা করুন**.
4. Watch the route redraw as a boat path.
5. Show `Boat • N1 → N3 → N4 • E6 + E7`, the 200-minute ETA, and local recomputation time.
6. Point out the separate `CONFIRMED FAILURE` and `SIMULATED` labels. A future M7 prediction will add cost, not masquerade as this closure.
7. Tap reset and repeat.

## Verification

```bash
scripts/verify-local.sh --connected
```

- `RoutePlannerTest`: weighted path, constraints, failure filtering, and risk-cost seam.
- `RouteScenarioTest`: JSON normalization, deterministic fallback, monotonic timing, and reset.
- `SylhetMapAssetTest`: parses the exact graph, OSM basemap, and route geometry packaged in the APK. It rejects two-point road or water geometry and requires simulated direct airways.
- `MainScreenViewModelTest`: state transition from assigned truck to boat fallback.
- `MainScreenTest`: small-screen interaction, route facts, latency visibility, and language parity.

Regenerate water geometry, the canonical file, and the byte-identical dashboard build copy with `pnpm --dir apps/command export:route-geometry`. Pass `-- --refresh-roads` only when intentionally replacing the road export through the external OSRM service. Review the diff and update `packages/scenario/SHA256SUMS` plus `OfflineMapContract.ROUTE_GEOMETRY_SHA256` after an accepted change. The fair runtime never invokes OSRM.

Record the under-two-second target on the actual fair phone before using it as a performance claim.
