import type { PresentationObservation } from "./observer";

export type ProjectedRoute = {
  vehicleId?: string;
  mode?: string;
  edgeIds: string[];
  etaMinutes?: number;
  simulated: boolean;
};

export type ProjectedRendezvous = {
  candidateId?: string;
  latitudeDegrees?: number;
  longitudeDegrees?: number;
};

export type ObserverProjection = {
  latestSequence: number;
  failedEdges: Set<string>;
  edgeRisks: Map<string, number>;
  delayedVehicleIds: Set<string>;
  route?: ProjectedRoute;
  rendezvous?: ProjectedRendezvous;
  requests: Map<string, PresentationObservation>;
  sources: Map<string, number>;
};

export function projectObservations(observations: PresentationObservation[], previous?: ObserverProjection): ObserverProjection {
  const projection: ObserverProjection = {
    ...previous,
    latestSequence: previous?.latestSequence ?? 0,
    failedEdges: new Set(previous?.failedEdges),
    edgeRisks: new Map(previous?.edgeRisks),
    delayedVehicleIds: new Set(previous?.delayedVehicleIds),
    requests: new Map(previous?.requests),
    sources: new Map(previous?.sources),
  };
  const ordered = [...observations].sort((left, right) => left.sequence - right.sequence);
  for (const observation of ordered) {
    if (observation.sequence <= projection.latestSequence) continue;
    projection.latestSequence = Math.max(projection.latestSequence, observation.sequence);
    projection.sources.set(observation.sourceNodeId, observation.occurredAtUnixMs);
    const value = observation.presentation ?? {};
    if (observation.kind === "reliefRequestCreated") {
      const requestId = asString(value.requestId) ?? observation.eventId;
      projection.requests.set(requestId, observation);
    }
    if (observation.kind === "edgeStatusChanged") {
      const edgeId = asString(value.edgeId);
      if (edgeId) {
        if (value.failed === true) projection.failedEdges.add(edgeId);
        if (value.failed === false) projection.failedEdges.delete(edgeId);
      }
    }
    if (observation.kind === "edgeRiskPredicted") {
      const edgeId = asString(value.edgeId);
      const probability = asNumber(value.probability);
      if (edgeId && probability !== undefined) projection.edgeRisks.set(edgeId, probability);
    }
    if (observation.kind === "routePlanned") {
      projection.route = {
        vehicleId: asString(value.vehicleId),
        mode: asString(value.mode),
        edgeIds: asStrings(value.edgeIds),
        etaMinutes: asNumber(value.etaMinutes),
        simulated: observation.simulated,
      };
    }
    if (observation.kind === "rendezvousPlanned") {
      projection.rendezvous = {
        candidateId: asString(value.candidateId),
        latitudeDegrees: asNumber(value.latitudeDegrees),
        longitudeDegrees: asNumber(value.longitudeDegrees),
      };
    }
    if (observation.kind === "vehicleStateChanged") {
      const vehicleId = asString(value.vehicleId);
      const stateCode = asString(value.stateCode);
      if (vehicleId) {
        if (stateCode?.startsWith("DELAYED")) projection.delayedVehicleIds.add(vehicleId);
        else projection.delayedVehicleIds.delete(vehicleId);
      }
    }
  }
  return projection;
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function asStrings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}
