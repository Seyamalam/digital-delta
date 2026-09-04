# Headquarters workspace redesign

The Next.js dashboard now has seven real App Router destinations. Shared language,
observer connection, projected state and exercise state live in the root layout;
changing pages does not reconnect the stream or reset the exercise.

| Route | Purpose |
| --- | --- |
| `/` | Four summary metrics, map preview, priority work and recent activity |
| `/map` | Geographic workspace, route layers, locations and labelled exercise zones |
| `/missions` | Searchable relief request register and latest received route |
| `/resources` | Scenario supply/medical/shelter directory with verification warnings |
| `/network` | Observer connection controls and reporting-source history |
| `/activity` | Searchable recent event history |
| `/exercise` | Isolated deterministic rehearsal controls |

Design: shadcn sidebar/cards/buttons, navy navigation, light workspaces, 16px body
text, 44–48px primary controls, larger headings, responsive mobile navigation.
Bangla remains the initial language. Switching language also updates document `lang`.

## Correctness changes

- Projection accumulates independently of the most recent 100 visible events.
- Map uses observed edge IDs, not a hard-coded road/water boolean.
- Predicted risk and a selected boat route no longer imply a confirmed E3 closure.
- Airway observations are labelled Drone, not Truck.
- Received rendezvous coordinates replace the fixed demonstration rendezvous.
- Browser no longer writes anonymous observations into the cloud archive.
- Operational views do not mix seeded missions with received field observations.
- Source counts mean sources seen, not proof of current radio connectivity.
- Scenario shelters and assembly polygons are not verified safe facilities.

## Verification checkpoint

- Dashboard: 19 automated tests passed, including state retention beyond 100 events,
  navigation destinations, preserved provider lifecycle, language semantics,
  mission/activity search, exercise isolation and shelter provenance.
- Next production build succeeded with all seven routes.
- Browser: map rendered from bundled PMTiles, route navigation preserved English,
  layer controls toggled, active sidebar destination was correct.
- At 390px the map route had no horizontal document overflow; controls measured
  44px (map navigation/markers) and 48px (application map tools).

This checkpoint does **not** certify the Hono migration, Android security changes,
physical relay recovery, or field validation as complete. Those remain separate
workstreams with their own evidence requirements.
