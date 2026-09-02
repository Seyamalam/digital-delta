# Screenshot and recording plan

Screenshots are evidence, pitch material, and recovery assets. Capture the final build only. Do not use mockups to imply working behavior.

## Capture rules

- Capture Bangla and English pairs for every critical field screen.
- Keep device time, scenario seed, map state, and sample IDs consistent.
- Do not show real phone numbers, identity documents, private keys, or unredacted personal data.
- Include the offline indicator where it matters.
- Use visible `SIMULATED` and `সিমুলেটেড` labels for environmental feeds and vehicles.
- Capture clean application frames and wider booth-context photographs separately.
- Store originals as PNG. Do not overwrite originals with compressed pitch versions.
- Record the commit hash, device, viewport, language, scenario seed, and capture date in the manifest.

## File convention

```text
artifacts/screenshots/
  manifest.csv
  field-bn/
  field-en/
  command-bn/
  command-en/
  evidence/
  booth/
```

File names use:

```text
<order>-<surface>-<language>-<state>-<viewport>.png
```

Example:

```text
03-field-bn-p0-request-1080x2400.png
```

## Required hero set

| ID | Shot | Required state | Purpose |
|---|---|---|---|
| H01 | Command overview | Offline, three nodes visible, active P0 request | Website and opening slide |
| H02 | Bangla field request | P0 medicine request ready to send | Prove Bangladesh-first interface |
| H03 | Mesh relay | A to B to C, B carrying encrypted envelope | Prove distributed behavior |
| H04 | Route failure | Flooded edge and new boat route side by side | Explain dynamic routing |
| H05 | Verified handoff | Signed QR accepted and custody updated | Security proof |
| H06 | Replay rejected | Same QR rejected with reason | Memorable evidence shot |
| H07 | Conflict resolution | Two versions and selected resolution | Prove offline convergence |
| H08 | Drone rendezvous | Drone-required zone and computed coordinate | Explain M8 without physical hardware |

## Field application shots

- [ ] First-run language choice
- [ ] Bangla sign-in and offline identity status
- [ ] English sign-in and offline identity status
- [ ] Role-restricted action denial
- [ ] New request form in Bangla
- [ ] New request form in English
- [ ] P0 to P3 priority explanation
- [ ] Local outbox with pending relay
- [ ] Nearby-node list
- [ ] Syncing state
- [ ] Conflict-detected state
- [ ] Human conflict resolution
- [ ] Route and vehicle assignment
- [ ] SLA breach warning
- [ ] Drop-and-reroute confirmation
- [ ] QR handoff generation
- [ ] Valid receipt verification
- [ ] Tampered receipt rejection
- [ ] Replay rejection
- [ ] Chain-of-custody timeline
- [ ] Low-battery broadcast reduction
- [ ] Simulated drone handoff
- [ ] Accessibility large-text state
- [ ] Empty and error states

## Command dashboard shots

- [ ] Full network overview at 1920 by 1080
- [ ] Full network overview at 1366 by 768
- [ ] Offline map with road, water, and air legend
- [ ] Node status and battery panel
- [ ] Mesh queue and relay topology
- [ ] Supply inventory panel
- [ ] Before-and-after route comparison
- [ ] Risk overlay with prediction legend
- [ ] SLA and triage queue
- [ ] Conflict convergence inspector
- [ ] Receipt-chain audit view
- [ ] Disaster Control console
- [ ] Scenario reset confirmation
- [ ] Bangla projector layout
- [ ] English projector layout

## Engineering evidence shots

- [ ] Airplane mode or internet-unavailable proof on all phones
- [ ] A to B to C relay test result
- [ ] Relay recovery after Phone B restart
- [ ] Route latency result
- [ ] Memory measurement on target phone
- [ ] Protobuf message inspection with encrypted payload redacted
- [ ] Load-test summary and test conditions
- [ ] ML confusion matrix and threshold
- [ ] Automated test summary
- [ ] Three-pass rehearsal log

## Recording plan

Record these as separate clips so a failed edit does not destroy the whole walkthrough:

1. Problem and system setup, 20 to 30 seconds
2. Offline identity and P0 request, 30 seconds
3. Interrupted store-and-forward relay, 45 seconds
4. Flood, prediction, reroute, and triage, 60 seconds
5. Drone-required rendezvous and custody transfer, 45 seconds
6. Valid handoff, tamper, replay rejection, and audit chain, 60 seconds
7. Results, limitations, and adoption plan, 30 seconds

## Screenshot manifest fields

```csv
file,commit,device,os,viewport,language,scenario_seed,module,real_or_simulated,captured_at,notes
```

