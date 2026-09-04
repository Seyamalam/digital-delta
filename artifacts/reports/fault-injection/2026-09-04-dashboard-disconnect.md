# Dashboard disconnect fault evidence

Status: **Passed automatically on 2026-09-04. Physical-phone booth evidence remains required.**

## Fault

The browser SSE observer disconnects after receiving sequence 1. Two field-originated domain events are then published while no dashboard client is attached. A new dashboard connection resumes with `after=1`.

## Expected result

- Field event publication does not depend on the projector browser.
- Both events are durably retained while the browser is absent.
- Reconnection replays sequences 2 and 3 exactly once and in order.
- The presentation bridge does not reveal encrypted mesh payload fields.

## Automated result

`TestFieldEventsContinueWhileDashboardIsDisconnectedAndReplayInOrder` passed under Go's race detector. The existing SSE presentation test also confirms that ciphertext, wrapped AES keys, and sender signatures are absent from browser output.

```bash
cd services/node
go test -race ./internal/observer
```

This proves process-level independence and durable replay in the implemented observer boundary. It does not claim a completed physical three-phone radio test or a booth cable/power-loss rehearsal.
