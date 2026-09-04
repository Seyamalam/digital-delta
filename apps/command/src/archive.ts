import type { PresentationObservation } from "./observer";

export type ArchiveStatus = "disabled" | "checking" | "ready" | "unavailable";

export type ArchiveObservation = {
  eventId: string;
  sequence: number;
  sourceNodeId: string;
  kind: string;
  occurredAtUnixMs: number;
  simulated: boolean;
  summary: string;
};

export function toArchiveObservation(observation: PresentationObservation): ArchiveObservation {
  const presentation = observation.presentation ?? {};
  const facts = [
    readString(presentation, "vehicleId"),
    readString(presentation, "edgeId"),
    readString(presentation, "candidateId"),
    readString(presentation, "mode"),
    readNumber(presentation, "etaMinutes")?.toString(),
    readNumber(presentation, "riskProbability")?.toFixed(3),
  ].filter(Boolean);
  return {
    eventId: observation.eventId,
    sequence: observation.sequence,
    sourceNodeId: observation.sourceNodeId,
    kind: observation.kind,
    occurredAtUnixMs: observation.occurredAtUnixMs,
    simulated: observation.simulated,
    summary: `${observation.kind}${facts.length ? ` · ${facts.join(" · ")}` : ""}`.slice(0, 240),
  };
}

export async function probeArchive(baseUrl: string, signal?: AbortSignal): Promise<boolean> {
  try {
    const response = await fetch(new URL("health", ensureTrailingSlash(baseUrl)), { signal, cache: "no-store" });
    if (!response.ok) return false;
    const body = await response.json() as { role?: unknown };
    return body.role === "optional_sanitized_archive";
  } catch {
    return false;
  }
}

export async function archiveObservation(baseUrl: string, observation: PresentationObservation): Promise<boolean> {
  try {
    const response = await fetch(new URL("v1/observations", ensureTrailingSlash(baseUrl)), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(toArchiveObservation(observation)),
    });
    return response.ok;
  } catch {
    return false;
  }
}

function ensureTrailingSlash(value: string): URL {
  const url = new URL(value);
  if (!url.pathname.endsWith("/")) url.pathname += "/";
  return url;
}

function readString(value: Record<string, unknown>, key: string): string | undefined {
  const candidate = value[key];
  return typeof candidate === "string" && candidate.length <= 64 ? candidate : undefined;
}

function readNumber(value: Record<string, unknown>, key: string): number | undefined {
  const candidate = value[key];
  return typeof candidate === "number" && Number.isFinite(candidate) ? candidate : undefined;
}
