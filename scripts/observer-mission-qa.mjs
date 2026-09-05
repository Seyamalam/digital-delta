// Explicitly simulated, local-only fixtures for the mission selection/SLA browser check.
import { readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";

const phase = process.argv[2] ?? "initial";
if (!["initial", "recover", "no-route"].includes(phase)) throw new Error("Use initial, recover, or no-route");
const secretFile = new URL("../services/headquarters-archive/.dev.vars", import.meta.url);
const contents = await readFile(secretFile, "utf8");
const keys = JSON.parse(contents.match(/^PUBLISHER_KEYS='(.*)'$/m)?.[1] ?? "{}");
const entry = Object.entries(keys).find(([, token]) => typeof token === "string" && token.length >= 32);
if (!entry) throw new Error("Configure a local observer publisher first. Credentials are never printed.");
const [sourceNodeId, token] = entry;
async function publish(kind, presentation) {
  const eventId = `qa-${randomUUID()}`;
  const event = { eventId, sourceNodeId, kind, occurredAtUnixMs: Date.now(), simulated: true, scenarioSeed: "mission-qa-v1", presentation };
  const response = await fetch("http://127.0.0.1:7071/v1/observations", {
    method: "POST", headers: { "Content-Type": "application/json", "X-Source-Node": sourceNodeId, Authorization: `Bearer ${token}` }, body: JSON.stringify(event),
  });
  if (!response.ok) throw new Error(`Local publication failed: ${response.status}`);
  return eventId;
}
async function plan(missionId, edgeIds, etaMinutes, priority, slaMinutes, stateCode) {
  const routeEventId = await publish("routePlanned", {
    missionId, vehicleId: "qa-planning-truck", mode: "TRANSPORT_MODE_ROAD", edgeIds, etaMinutes,
    riskAdjusted: false, explanationCode: edgeIds.length ? "PACKAGED_NETWORK_ESTIMATE" : "NO_FEASIBLE_GROUND_ROUTE",
  });
  await publish("slaEvaluated", { missionId, routeEventId, priority, stateCode, baselineArrivalMinutes: etaMinutes,
    slowedArrivalMinutes: Math.round(etaMinutes * 1.3), slaMinutes, policyVersion: "triage-v1-packaged-estimate" });
}
if (phase === "initial") {
  for (const [requestId, destinationNodeId] of [["QA-MEDICAL", "N6"], ["QA-SUPPLIES", "N4"]]) {
    await publish("reliefRequestCreated", { requestId, requesterNodeId: "N1", originNodeId: "N1", destinationNodeId, cargoCount: 1 });
  }
  await plan("QA-MEDICAL", ["E5"], 120, "PRIORITY_CLASS_P0", 120, "BREACH");
  await plan("QA-SUPPLIES", ["E1", "E3"], 65, "PRIORITY_CLASS_P2", 1440, "WITHIN_SLA");
} else if (phase === "recover") {
  // A test route update, not a claim that a medical delivery was expedited.
  await plan("QA-MEDICAL", ["E1", "E3"], 65, "PRIORITY_CLASS_P0", 120, "WITHIN_SLA");
} else {
  await plan("QA-MEDICAL", [], 0, "PRIORITY_CLASS_P0", 120, "NO_ROUTE");
}
console.log(`Published ${phase} SIMULATED mission QA fixtures to local observer only.`);
