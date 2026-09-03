# Offline routing runbook

The route screen is driven by the bundled `packages/scenario/sylhet_map.json` graph. It does not call a map, routing, or internet service.

## Implemented path

1. `SylhetMapParser` decodes the fixture, validates unique node and edge identifiers, rejects missing references and unsupported modes, and normalizes `river` to `WATERWAY`.
2. `RoutePlanner` runs Dijkstra on open edges for exactly one vehicle mode with deterministic node and edge ordering.
3. `DynamicRouteEngine` first tries the assigned truck, then a boat, then the visibly simulated drone. It records elapsed monotonic nanoseconds.
4. `OfflineRouteScenario` owns the current in-memory edge state and supports repeatable fail/reset actions without internet.
5. Compose animates the returned edge mode and displays the exact nodes, edges, ETA, reason, and measured time in Bangla or English.

## Current fixture proof

| State | Preferred vehicle | Selected edges | ETA | Explanation |
|---|---|---|---:|---|
| Initial | Truck | `E1 + E3` | 65 min | Both directed road edges are open |
| `E3` failed | Truck | `E6 + E7` by boat | 200 min | No truck path reaches `N4`; waterway fallback precedes simulated air |

The `A1` airway is synthetic and carries `"simulated": true`. It exists to test drone constraints and later M8 reachability, not to imply a real approved flight corridor.

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
- `SylhetMapAssetTest`: parses the exact asset packaged in the APK.
- `MainScreenViewModelTest`: state transition from assigned truck to boat fallback.
- `MainScreenTest`: small-screen interaction, route facts, latency visibility, and language parity.

The Compose canvas is a working offline schematic, not yet the final MapLibre/OSM map. Record the under-two-second target on the actual fair phone before using it as a performance claim.
