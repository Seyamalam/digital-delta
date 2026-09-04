export const allowedEventKinds = [
  "reliefRequestCreated",
  "edgeRiskPredicted",
  "edgeStatusChanged",
  "routePlanned",
  "slaBreachPredicted",
  "rendezvousPlanned",
  "vehicleStateChanged",
] as const;

export type ArchiveObservation = {
  eventId: string;
  sequence: number;
  sourceNodeId: string;
  kind: (typeof allowedEventKinds)[number];
  occurredAtUnixMs: number;
  simulated: boolean;
  summary: string;
};

const identifier = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$/;

export function parseArchiveObservation(value: unknown): ArchiveObservation | null {
  if (!isRecord(value)) return null;
  if (Object.keys(value).some((key) => !["eventId", "sequence", "sourceNodeId", "kind", "occurredAtUnixMs", "simulated", "summary"].includes(key))) return null;
  if (typeof value.eventId !== "string" || !identifier.test(value.eventId)) return null;
  if (!Number.isSafeInteger(value.sequence) || Number(value.sequence) <= 0) return null;
  if (typeof value.sourceNodeId !== "string" || !identifier.test(value.sourceNodeId)) return null;
  if (typeof value.kind !== "string" || !allowedEventKinds.includes(value.kind as ArchiveObservation["kind"])) return null;
  if (!Number.isSafeInteger(value.occurredAtUnixMs) || Number(value.occurredAtUnixMs) <= 0) return null;
  if (typeof value.simulated !== "boolean") return null;
  if (typeof value.summary !== "string" || value.summary.length < 1 || value.summary.length > 240) return null;
  if (/cipher|payload|private.?key|signature/i.test(value.summary)) return null;
  return value as ArchiveObservation;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
