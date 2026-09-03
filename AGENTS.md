# Digital Delta agent guide

Digital Delta is an offline-first disaster logistics demonstration for Bangladesh. Read [README.md](README.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [docs/TESTING.md](docs/TESTING.md) before changing architecture or public claims.

## Non-negotiable rules

- Bangla and English are equal, bundled interfaces.
- Field workflows cannot depend on commercial internet or the command laptop.
- Simulated environmental and vehicle data must remain visibly labelled.
- Do not add GitHub Actions or hosted CI. Verification is local through `scripts/verify-local.sh`.
- Do not claim a module is complete without its automated and live evidence.
- Mesh domain payloads use Protocol Buffers, not JSON.
- No physical sensors, IoT devices, or drone control are part of the fair build.

## Android verification

Run local unit and build checks:

```bash
scripts/verify-local.sh
```

Run the connected Android journey tests when an emulator or device is available:

```bash
scripts/verify-local.sh --connected
```

See [docs/TESTING.md](docs/TESTING.md) for test seams, evidence paths, and the complete matrix.
