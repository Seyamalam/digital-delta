import { allowedEventKinds } from "./observation";

export type PublicObservation = {
  eventId: string;
  sourceNodeId: string;
  kind: typeof allowedEventKinds[number];
  occurredAtUnixMs: number;
  simulated: boolean;
  scenarioSeed?: string;
  presentation: Record<string, string | number | boolean | string[]>;
};

type Rule = "id" | "ids" | "bool" | "probability" | "percent" | "latitude" | "longitude" | "minutes" | "mode" | "priority" | "count";
const fields: Record<PublicObservation["kind"], Record<string, Rule>> = {
  reliefRequestCreated: { requestId: "id", requesterNodeId: "id", originNodeId: "id", destinationNodeId: "id", cargoCount: "count" },
  routePlanned: { missionId: "id", vehicleId: "id", mode: "mode", edgeIds: "ids", etaMinutes: "minutes", riskAdjusted: "bool", explanationCode: "id" },
  edgeStatusChanged: { edgeId: "id", failed: "bool", reasonCode: "id", simulated: "bool" },
  edgeRiskPredicted: { edgeId: "id", probability: "probability", threshold: "probability", modelVersion: "id", simulatedInputs: "bool" },
  slaBreachPredicted: { missionId: "id", priority: "priority", baselineEtaMinutes: "minutes", slowedEtaMinutes: "minutes", slaMinutes: "minutes", policyVersion: "id" },
  rendezvousPlanned: { missionId: "id", boatVehicleId: "id", droneVehicleId: "id", candidateId: "id", latitudeDegrees: "latitude", longitudeDegrees: "longitude", boatEtaMinutes: "minutes", droneEtaMinutes: "minutes", deliveryEtaMinutes: "minutes", projectedDroneBatteryPercent: "percent", reserveBatteryPercent: "percent", objectiveCode: "id", simulated: "bool" },
  vehicleStateChanged: { vehicleId: "id", mode: "mode", stateCode: "id", nodeId: "id", latitudeDegrees: "latitude", longitudeDegrees: "longitude", batteryPercent: "percent", simulated: "bool" },
};
const required: Record<PublicObservation["kind"], string[]> = {
  reliefRequestCreated: ["requestId", "destinationNodeId"], routePlanned: ["vehicleId", "mode", "edgeIds", "etaMinutes"], edgeStatusChanged: ["edgeId", "failed"], edgeRiskPredicted: ["edgeId", "probability"], slaBreachPredicted: ["missionId", "slowedEtaMinutes", "slaMinutes"], rendezvousPlanned: ["candidateId", "latitudeDegrees", "longitudeDegrees"], vehicleStateChanged: ["vehicleId", "stateCode"],
};
export const validIdentifier = (value: unknown): value is string => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$/.test(value);
const record = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null && !Array.isArray(value);

export function parsePublicObservation(value: unknown): PublicObservation | null {
  if (!record(value) || Object.keys(value).some((key) => !["eventId", "sourceNodeId", "kind", "occurredAtUnixMs", "simulated", "scenarioSeed", "presentation"].includes(key))) return null;
  if (!validIdentifier(value.eventId) || !validIdentifier(value.sourceNodeId)) return null;
  if (typeof value.kind !== "string" || !allowedEventKinds.includes(value.kind as PublicObservation["kind"])) return null;
  if (!Number.isSafeInteger(value.occurredAtUnixMs) || Number(value.occurredAtUnixMs) <= 0 || Number(value.occurredAtUnixMs) > 8_640_000_000_000_000) return null;
  if (typeof value.simulated !== "boolean" || (value.scenarioSeed !== undefined && !validIdentifier(value.scenarioSeed))) return null;
  if (!record(value.presentation)) return null;
  const kind = value.kind as PublicObservation["kind"];
  if (Object.keys(value.presentation).some((key) => !Object.hasOwn(fields[kind], key))) return null;
  if (required[kind].some((key) => value.presentation && !(key in (value.presentation as object)))) return null;
  const presentation: PublicObservation["presentation"] = {};
  // Canonical field order gives stable idempotency even if JSON input keys are reordered.
  for (const [key, rule] of Object.entries(fields[kind])) {
    const item = value.presentation[key];
    if (item === undefined) continue;
    if (!validate(rule, item)) return null;
    presentation[key] = item as string | number | boolean | string[];
  }
  return { eventId: value.eventId, sourceNodeId: value.sourceNodeId, kind, occurredAtUnixMs: value.occurredAtUnixMs as number, simulated: value.simulated, ...(value.scenarioSeed ? { scenarioSeed: value.scenarioSeed as string } : {}), presentation };
}

function validate(rule: Rule, value: unknown): boolean {
  if (rule === "id") return validIdentifier(value);
  if (rule === "ids") return Array.isArray(value) && value.length <= 200 && value.every(validIdentifier);
  if (rule === "bool") return typeof value === "boolean";
  if (rule === "mode") return ["TRANSPORT_MODE_ROAD", "TRANSPORT_MODE_WATERWAY", "TRANSPORT_MODE_AIRWAY"].includes(String(value));
  if (rule === "priority") return /^PRIORITY_CLASS_P[0-3]$/.test(String(value));
  if (typeof value !== "number" || !Number.isFinite(value)) return false;
  if (rule === "probability") return value >= 0 && value <= 1;
  if (rule === "percent") return value >= 0 && value <= 100;
  if (rule === "latitude") return value >= -90 && value <= 90;
  if (rule === "longitude") return value >= -180 && value <= 180;
  return Number.isSafeInteger(value) && value >= 0 && value <= (rule === "count" ? 100_000 : 525_600);
}
